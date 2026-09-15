package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedFeatureUsage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedFeatureUsage.MethodLabel;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;

/**
 * Joins the persisted {@code @FeatureUsage} placements to the members that guard them. A functional member owns every
 * annotated type whose {@code @Conditional} guards name its condition class; a technical profile member owns every
 * annotated type whose {@code @Profile} guards name its {@code PROFILE_*} constant or its profile literal, unless a
 * functional member already claimed the type through a condition class. One sub-feature per owner and distinct
 * effective label results, ordered by area and then feature; its evidence lists every annotated type and method that
 * carries the label. Types whose condition guards name no member are reported as information; unguarded types produce
 * nothing, because attaching them to the mandatory conceptual modules would need a package join this track does not
 * perform. The join is pure and stateless so the conformance step can recompute its expectations independently.
 */
final class FeatureUsageJoin {

    private static final String LABEL_SEPARATOR = "/";

    private FeatureUsageJoin() {
    }

    /**
     * One sub-feature of an owner.
     *
     * @param label raw {@code area/feature} label.
     * @param area label segment before the slash.
     * @param feature label segment after the slash.
     * @param guard guard the owner was matched through: its condition class or its profile constant.
     * @param module Artemis module of the annotated types, from the first type in file order.
     * @param evidence sorted {@code File.java:line} references of every annotated type and method carrying the label.
     */
    record SubFeature(String label, String area, String feature, String guard, String module, List<String> evidence) {
    }

    /**
     * Join result.
     *
     * @param subFeaturesByOwner sub-features per owner id, owners in member order, sub-features by area then feature.
     * @param items join diagnostics.
     */
    record Result(Map<String, List<SubFeature>> subFeaturesByOwner, List<ReportItem> items) {
    }

    /**
     * Joins the usages to their owning members.
     *
     * @param members resolved members of the run.
     * @param candidates scanned candidates supplying the members' guards.
     * @param usages persisted feature usage placements.
     * @return sub-features per owner and diagnostics.
     */
    static Result join(List<ResolvedFeatureScope> members, List<FeatureCandidate> candidates, List<ExtractedFeatureUsage> usages) {
        Map<String, FeatureCandidate> candidatesById = new LinkedHashMap<>();
        candidates.forEach(candidate -> candidatesById.putIfAbsent(candidate.id(), candidate));
        Map<String, String> ownerByConditionClass = new LinkedHashMap<>();
        Map<String, String> ownerByProfileToken = new LinkedHashMap<>();
        Map<String, String> guardByOwner = new LinkedHashMap<>();
        for (ResolvedFeatureScope member : members) {
            FeatureCandidate candidate = candidatesById.get(member.candidateId());
            if (candidate == null) {
                continue;
            }
            if (CurationReport.SOURCE_TECHNICAL.equals(member.membershipSource())) {
                if (FeatureCandidate.KIND_SPRING_PROFILE.equals(candidate.kind()) && candidate.springProfile() != null) {
                    String constant = candidate.serverConstant() != null ? candidate.serverConstant() : candidate.springProfile();
                    ownerByProfileToken.putIfAbsent(constant, member.id());
                    ownerByProfileToken.putIfAbsent(candidate.springProfile(), member.id());
                    guardByOwner.put(member.id(), constant);
                }
            }
            else if (candidate.serverConditionClass() != null) {
                ownerByConditionClass.putIfAbsent(candidate.serverConditionClass(), member.id());
                guardByOwner.put(member.id(), candidate.serverConditionClass());
            }
        }

        Map<String, Map<String, TreeSet<String>>> evidenceByOwnerAndLabel = new LinkedHashMap<>();
        Map<String, Map<String, String>> moduleByOwnerAndLabel = new LinkedHashMap<>();
        List<ReportItem> items = new ArrayList<>();
        for (ExtractedFeatureUsage usage : usages) {
            Set<String> owners = new LinkedHashSet<>();
            usage.conditionGuards().stream().map(ownerByConditionClass::get).filter(owner -> owner != null).forEach(owners::add);
            if (owners.isEmpty()) {
                usage.profileGuards().stream().map(ownerByProfileToken::get).filter(owner -> owner != null).forEach(owners::add);
            }
            if (owners.isEmpty()) {
                if (!usage.conditionGuards().isEmpty()) {
                    items.add(ReportItem.info(ReportItem.CODE_FEATURE_USAGE_UNATTACHED, usage.file(), "@FeatureUsage type '" + usage.type()
                            + "' is guarded by " + usage.conditionGuards() + ", which names no model member; its labels " + usage.effectiveLabels()
                            + " attach to no sub-feature."));
                }
                continue;
            }
            for (String owner : owners) {
                Map<String, TreeSet<String>> evidenceByLabel = evidenceByOwnerAndLabel.computeIfAbsent(owner, unused -> new LinkedHashMap<>());
                Map<String, String> moduleByLabel = moduleByOwnerAndLabel.computeIfAbsent(owner, unused -> new LinkedHashMap<>());
                String fileName = usage.file().substring(usage.file().lastIndexOf('/') + 1);
                if (usage.classLabel() != null) {
                    evidenceByLabel.computeIfAbsent(usage.classLabel(), unused -> new TreeSet<>()).add(fileName + ":" + usage.line());
                    moduleByLabel.putIfAbsent(usage.classLabel(), usage.module());
                }
                for (MethodLabel methodLabel : usage.methodLabels()) {
                    evidenceByLabel.computeIfAbsent(methodLabel.label(), unused -> new TreeSet<>()).add(fileName + ":" + methodLabel.line());
                    moduleByLabel.putIfAbsent(methodLabel.label(), usage.module());
                }
            }
        }

        Map<String, List<SubFeature>> subFeaturesByOwner = new LinkedHashMap<>();
        for (ResolvedFeatureScope member : members) {
            Map<String, TreeSet<String>> evidenceByLabel = evidenceByOwnerAndLabel.get(member.id());
            if (evidenceByLabel == null) {
                continue;
            }
            Map<String, String> moduleByLabel = moduleByOwnerAndLabel.get(member.id());
            TreeMap<List<String>, SubFeature> ordered = new TreeMap<>(Comparator.<List<String>, String>comparing(key -> key.get(0)).thenComparing(key -> key.get(1)));
            for (Map.Entry<String, TreeSet<String>> entry : evidenceByLabel.entrySet()) {
                String label = entry.getKey();
                int separator = label.indexOf(LABEL_SEPARATOR);
                String area = separator < 0 ? label : label.substring(0, separator);
                String feature = separator < 0 ? label : label.substring(separator + 1);
                ordered.put(List.of(area, feature), new SubFeature(label, area, feature, guardByOwner.get(member.id()), moduleByLabel.get(label),
                        List.copyOf(entry.getValue())));
            }
            subFeaturesByOwner.put(member.id(), List.copyOf(ordered.values()));
        }
        return new Result(subFeaturesByOwner, List.copyOf(items));
    }
}
