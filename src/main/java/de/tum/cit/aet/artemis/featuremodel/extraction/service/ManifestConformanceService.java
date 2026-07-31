package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConstraintEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ManifestConformance;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.RelationCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;

/**
 * Decides whether the manifest describes the scanned source completely enough to assemble a model. It runs before
 * assembly, because a model built from incomplete curation would look valid while silently omitting whatever the
 * manifest never decided about.
 */
class ManifestConformanceService {

    /**
     * Conformance evaluation result.
     *
     * @param conformance verdict with every finding of this run.
     * @param items diagnostics the evaluation itself produced.
     */
    record Result(ManifestConformance conformance, List<ReportItem> items) {
    }

    /**
     * Evaluates the conformance of one run.
     *
     * @param manifest loaded scope manifest.
     * @param includedFeatures resolved include semantics of the curation step.
     * @param relationCandidates relation candidates the scan discovered.
     * @param curation manifest classification section.
     * @param curationItems diagnostics of the curation step.
     * @param scanItems diagnostics of the scan that produced the source facts.
     * @return verdict and relation diagnostics.
     */
    Result evaluate(FeatureScopeManifest manifest, List<ResolvedFeatureScope> includedFeatures, List<RelationCandidate> relationCandidates,
            CurationReport curation, List<ReportItem> curationItems, List<ReportItem> scanItems) {
        List<ReportItem> items = new ArrayList<>();
        List<String> undeclaredRelations = evaluateRelationDecisions(manifest, includedFeatures, relationCandidates, items);
        ManifestConformance conformance = ManifestConformance.from(List.copyOf(curation.undeclaredCandidateIds()), undeclaredRelations,
                subjectsOf(curationItems, ReportItem.CODE_MANIFEST_ORPHAN_ANCHOR), subjectsOf(curationItems, ReportItem.CODE_MANIFEST_CURATION_CONFLICT),
                subjectsOf(scanItems, ReportItem.CODE_EXTRACTOR_ERROR));
        return new Result(conformance, List.copyOf(items));
    }

    /**
     * Requires a decision for every relation candidate whose members are all included: either a declared constraint
     * covering the pair, or an explicit ignore entry. Relations touching an excluded candidate need no decision, since
     * the excluded side is already outside the model.
     *
     * @param manifest loaded scope manifest.
     * @param includedFeatures resolved include semantics.
     * @param relationCandidates relation candidates the scan discovered.
     * @param items diagnostics sink.
     * @return ids of relation candidates without a decision, sorted.
     */
    private List<String> evaluateRelationDecisions(FeatureScopeManifest manifest, List<ResolvedFeatureScope> includedFeatures,
            List<RelationCandidate> relationCandidates, List<ReportItem> items) {
        Map<String, String> includedIdByCandidate = new LinkedHashMap<>();
        includedFeatures.forEach(included -> includedIdByCandidate.put(included.candidateId(), included.id()));
        Set<String> declaredPairs = declaredConstraintPairs(manifest);
        Set<String> ignoredRelationIds = new LinkedHashSet<>();
        manifest.ignoredRelations().forEach(entry -> ignoredRelationIds.add(entry.id()));

        Set<String> undeclared = new LinkedHashSet<>();
        for (RelationCandidate relationCandidate : relationCandidates) {
            List<String> memberIds = includedMemberIds(relationCandidate, includedIdByCandidate);
            if (memberIds.size() < 2 || ignoredRelationIds.contains(relationCandidate.id()) || isCoveredByConstraint(memberIds, declaredPairs)) {
                continue;
            }
            undeclared.add(relationCandidate.id());
            items.add(ReportItem.error(ReportItem.CODE_RELATION_CANDIDATE_UNDECLARED, relationCandidate.id(),
                    "Condition '" + relationCandidate.conditionClass() + "' relates the included features " + memberIds
                            + ", but the manifest declares neither a constraint between them nor an ignoredRelations entry for this relation."));
        }
        return List.copyOf(undeclared);
    }

    /**
     * Collects the feature id pairs a declared constraint covers, in both directions.
     *
     * @param manifest loaded scope manifest.
     * @return covered pairs.
     */
    private Set<String> declaredConstraintPairs(FeatureScopeManifest manifest) {
        Set<String> declaredPairs = new LinkedHashSet<>();
        for (ConstraintEntry constraint : manifest.constraints()) {
            declaredPairs.add(constraint.source() + "->" + constraint.target());
            declaredPairs.add(constraint.target() + "->" + constraint.source());
        }
        return declaredPairs;
    }

    /**
     * Maps the members of a relation candidate onto the ids of the included features, dropping members outside scope.
     *
     * @param relationCandidate relation candidate.
     * @param includedIdByCandidate curated id per included candidate id.
     * @return curated ids of the included members, in member order.
     */
    private List<String> includedMemberIds(RelationCandidate relationCandidate, Map<String, String> includedIdByCandidate) {
        List<String> memberIds = new ArrayList<>();
        for (String memberCandidateId : relationCandidate.memberCandidateIds()) {
            String includedId = includedIdByCandidate.get(memberCandidateId);
            if (includedId == null) {
                return List.of();
            }
            memberIds.add(includedId);
        }
        return memberIds;
    }

    /**
     * Checks whether a declared constraint already covers every member pair of a relation.
     *
     * @param memberIds curated ids of the relation members.
     * @param declaredPairs pairs covered by declared constraints.
     * @return true when every member pair is covered.
     */
    private boolean isCoveredByConstraint(List<String> memberIds, Set<String> declaredPairs) {
        for (int source = 0; source < memberIds.size(); source++) {
            for (int target = source + 1; target < memberIds.size(); target++) {
                if (!declaredPairs.contains(memberIds.get(source) + "->" + memberIds.get(target))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Collects the distinct sorted subjects of all items carrying one diagnostic code.
     *
     * @param items diagnostics to filter.
     * @param code diagnostic code.
     * @return sorted distinct subjects.
     */
    private List<String> subjectsOf(List<ReportItem> items, String code) {
        return items.stream().filter(item -> code.equals(item.code())).map(ReportItem::subject).distinct().sorted().toList();
    }

}
