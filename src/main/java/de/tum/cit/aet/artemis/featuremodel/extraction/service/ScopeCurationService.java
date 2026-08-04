package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport.CurationDecision;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotation;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotationSemantics;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ExcludeEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.IncludeEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;

/**
 * Applies manifest membership to the extracted candidates and merges source annotation semantics for included
 * anchors. Manifest problems that only this scan can reveal — candidates without a decision, anchors that no longer
 * resolve, entries colliding on one candidate, or resolved semantics conflicting after annotation precedence — become
 * error report items rather than aborting the run, so one run reports every curation gap at once instead of the first
 * one. {@link ManifestConformanceService} turns those errors into the blocking verdict; statically detectable
 * authoring errors are rejected earlier by {@link FeatureManifestLoader}.
 */
class ScopeCurationService {

    static final String STATE_INCLUDE = "include";

    static final String STATE_EXCLUDE = "exclude";

    static final String STATE_UNDECLARED = "undeclared";

    private static final String SEMANTIC_SOURCE_MANIFEST = "manifest";

    private static final String SEMANTIC_SOURCE_ANNOTATION = "annotation";

    /**
     * Curation result.
     *
     * @param report structured curation section.
     * @param includedFeatures resolved included semantics sorted by candidate id.
     * @param items curation diagnostics.
     */
    record Result(CurationReport report, List<ResolvedFeatureScope> includedFeatures, List<ReportItem> items) {
    }

    /** Manifest membership of one candidate; exactly one of the two entries is set. */
    private record Membership(IncludeEntry include, ExcludeEntry exclude) {
    }

    /**
     * Applies the manifest to extracted candidates.
     *
     * @param manifest loaded manifest.
     * @param candidates extracted candidates.
     * @param annotations parsed source annotations.
     * @return classifications, resolved include semantics, and diagnostics.
     */
    Result curate(FeatureScopeManifest manifest, List<FeatureCandidate> candidates, List<ExtractedAnnotation> annotations) {
        List<ReportItem> items = new ArrayList<>();
        CandidateResolver resolver = new CandidateResolver(candidates);
        Map<String, Membership> membershipByCandidate = resolveMembership(manifest, resolver, items);
        Map<String, ExtractedAnnotation> annotationsByCandidate = resolveAnnotations(annotations, resolver, items);

        List<CurationDecision> decisions = new ArrayList<>();
        List<ResolvedFeatureScope> includedFeatures = new ArrayList<>();
        for (FeatureCandidate candidate : candidates) {
            classifyCandidate(candidate, membershipByCandidate.get(candidate.id()), annotationsByCandidate.get(candidate.id()), decisions, includedFeatures, items);
        }

        includedFeatures.sort(Comparator.comparing(ResolvedFeatureScope::candidateId));
        reportResolvedSemanticConflicts(manifest, includedFeatures, items);
        decisions.sort(Comparator.comparingInt((CurationDecision decision) -> stateOrder(decision.state())).thenComparing(CurationDecision::candidateId));
        CurationReport report = assembleReport(manifest, decisions);
        return new Result(report, List.copyOf(includedFeatures), List.copyOf(items));
    }

    /**
     * Resolves the include and exclude anchors onto candidates. An anchor that resolves to no candidate or to several
     * candidates yields an orphan diagnostic and is skipped; several entries resolving to the same candidate yield a
     * conflict diagnostic and the first entry wins. Runtime-toggle entries without written rationale are flagged.
     *
     * @param manifest loaded manifest.
     * @param resolver candidate resolver.
     * @param items report item sink.
     * @return membership per resolved candidate id.
     */
    private Map<String, Membership> resolveMembership(FeatureScopeManifest manifest, CandidateResolver resolver, List<ReportItem> items) {
        Map<String, Membership> membershipByCandidate = new LinkedHashMap<>();
        for (IncludeEntry entry : manifest.include()) {
            String candidateId = resolveAnchor(entry.anchor(), resolver, items);
            if (candidateId == null || conflictsWithExistingEntry(membershipByCandidate, candidateId, entry.anchor(), items)) {
                continue;
            }
            membershipByCandidate.put(candidateId, new Membership(entry, null));
            requireToggleRationale(resolver.candidate(candidateId), entry.anchor(), entry.rationale(), items);
        }
        for (ExcludeEntry entry : manifest.exclude()) {
            String candidateId = resolveAnchor(entry.anchor(), resolver, items);
            if (candidateId == null || conflictsWithExistingEntry(membershipByCandidate, candidateId, entry.anchor(), items)) {
                continue;
            }
            membershipByCandidate.put(candidateId, new Membership(null, entry));
            requireToggleRationale(resolver.candidate(candidateId), entry.anchor(), entry.rationale(), items);
        }
        return membershipByCandidate;
    }

    /**
     * Resolves one manifest anchor and reports resolution failures as orphan diagnostics.
     *
     * @param anchor manifest anchor.
     * @param resolver candidate resolver.
     * @param items report item sink.
     * @return resolved candidate id, or null when the anchor is unknown or ambiguous.
     */
    private String resolveAnchor(String anchor, CandidateResolver resolver, List<ReportItem> items) {
        CandidateResolver.Resolution resolution = resolver.resolve(anchor);
        if (resolution.problem() != null) {
            items.add(ReportItem.error(ReportItem.CODE_MANIFEST_ORPHAN_ANCHOR, anchor, resolution.problem() + " The entry is skipped for this scan."));
            return null;
        }
        return resolution.candidateId();
    }

    /**
     * Reports a conflict when a candidate already has a manifest entry.
     *
     * @param membershipByCandidate memberships resolved so far.
     * @param candidateId resolved candidate id.
     * @param anchor anchor of the newly resolved entry.
     * @param items report item sink.
     * @return true if the candidate was already claimed and the new entry must be skipped.
     */
    private boolean conflictsWithExistingEntry(Map<String, Membership> membershipByCandidate, String candidateId, String anchor, List<ReportItem> items) {
        if (!membershipByCandidate.containsKey(candidateId)) {
            return false;
        }
        items.add(ReportItem.error(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, candidateId,
                "Manifest anchor '" + anchor + "' resolves to a candidate that already has a manifest entry; the first entry wins."));
        return true;
    }

    /**
     * Flags runtime-toggle manifest entries without documented reasoning; every toggle decision must record why.
     *
     * @param candidate resolved candidate.
     * @param anchor manifest anchor of the entry.
     * @param rationale documented reasoning, or null.
     * @param items report item sink.
     */
    private void requireToggleRationale(FeatureCandidate candidate, String anchor, String rationale, List<ReportItem> items) {
        if (FeatureCandidate.KIND_RUNTIME_TOGGLE.equals(candidate.kind()) && rationale == null) {
            items.add(ReportItem.error(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, candidate.id(),
                    "Runtime toggle entry '" + anchor + "' has no rationale; every toggle decision must document its reasoning."));
        }
    }

    /**
     * Resolves source annotations onto candidates. Unmatched annotations are reported; several annotations resolving
     * to the same candidate yield a conflict diagnostic and the first annotation wins.
     *
     * @param annotations parsed source annotations.
     * @param resolver candidate resolver.
     * @param items report item sink.
     * @return annotation per resolved candidate id.
     */
    private Map<String, ExtractedAnnotation> resolveAnnotations(List<ExtractedAnnotation> annotations, CandidateResolver resolver, List<ReportItem> items) {
        Map<String, ExtractedAnnotation> annotationsByCandidate = new LinkedHashMap<>();
        for (ExtractedAnnotation annotation : annotations) {
            CandidateResolver.Resolution resolution = resolver.resolve(annotation.anchor());
            if (resolution.problem() != null) {
                items.add(ReportItem.warning(ReportItem.CODE_ANNOTATED_ANCHOR_NOT_EXTRACTED, annotation.anchor(),
                        "Annotated source anchor at " + annotation.file() + ":" + annotation.line() + " did not match an extraction candidate. " + resolution.problem()));
                continue;
            }
            if (annotationsByCandidate.putIfAbsent(resolution.candidateId(), annotation) != null) {
                items.add(ReportItem.error(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, resolution.candidateId(),
                        "Several @ArtemisFeature annotations resolve to this candidate; the annotation at " + annotation.file() + ":" + annotation.line()
                                + " is ignored."));
            }
        }
        return annotationsByCandidate;
    }

    /**
     * Classifies one candidate into include, exclude, or undeclared and records the annotation diagnostics that belong
     * to the classification.
     *
     * @param candidate extracted candidate.
     * @param membership manifest membership, or null when the candidate has no decision.
     * @param annotation source annotation resolved to the candidate, or null.
     * @param decisions decision sink.
     * @param includedFeatures resolved include semantics sink.
     * @param items report item sink.
     */
    private void classifyCandidate(FeatureCandidate candidate, Membership membership, ExtractedAnnotation annotation, List<CurationDecision> decisions,
            List<ResolvedFeatureScope> includedFeatures, List<ReportItem> items) {
        if (membership != null && membership.include() != null) {
            ResolvedFeatureScope scope = resolveSemantics(candidate, membership.include(), annotation, items);
            includedFeatures.add(scope);
            decisions.add(new CurationDecision(candidate.id(), candidate.kind(), STATE_INCLUDE, scope.id(), null, scope.semanticSource()));
            return;
        }
        if (membership != null) {
            decisions.add(new CurationDecision(candidate.id(), candidate.kind(), STATE_EXCLUDE, null, membership.exclude().reason(), null));
            if (annotation != null) {
                items.add(annotatedButUnscoped(candidate, annotation, STATE_EXCLUDE));
            }
            return;
        }
        decisions.add(new CurationDecision(candidate.id(), candidate.kind(), STATE_UNDECLARED, null, null, null));
        items.add(ReportItem.error(ReportItem.CODE_UNDECLARED_CANDIDATE, candidate.id(),
                "Candidate has no manifest decision; add it to include or exclude before this scan can produce a model."));
        if (annotation != null) {
            items.add(annotatedButUnscoped(candidate, annotation, STATE_UNDECLARED));
        }
    }

    /**
     * Reports conflicts in the resolved include semantics: duplicate curated ids and parent or group references that
     * no longer resolve after annotation precedence or skipped orphan entries. The manifest-internal references were
     * already validated statically by the loader, so every conflict here is scan- or annotation-induced.
     *
     * @param manifest loaded manifest.
     * @param includedFeatures resolved include semantics.
     * @param items report item sink.
     */
    private void reportResolvedSemanticConflicts(FeatureScopeManifest manifest, List<ResolvedFeatureScope> includedFeatures, List<ReportItem> items) {
        Set<String> resolvedIds = new LinkedHashSet<>();
        manifest.conceptualNodes().forEach(node -> resolvedIds.add(node.id()));
        for (ResolvedFeatureScope feature : includedFeatures) {
            if (!resolvedIds.add(feature.id())) {
                items.add(ReportItem.error(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, feature.candidateId(),
                        "Resolved curated id '" + feature.id() + "' is already used by another included feature or conceptual node."));
            }
        }
        for (ResolvedFeatureScope feature : includedFeatures) {
            reportUnresolvedReference(feature, feature.parent(), resolvedIds, items);
            reportUnresolvedReference(feature, feature.group(), resolvedIds, items);
        }
    }

    /**
     * Reports a parent or group reference that does not resolve within the included and conceptual ids.
     *
     * @param feature resolved feature carrying the reference.
     * @param reference referenced parent or group id, or null when not set.
     * @param resolvedIds all resolved curated ids.
     * @param items report item sink.
     */
    private void reportUnresolvedReference(ResolvedFeatureScope feature, String reference, Set<String> resolvedIds, List<ReportItem> items) {
        if (reference != null && !resolvedIds.contains(reference)) {
            items.add(ReportItem.error(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, feature.candidateId(),
                    "Resolved feature '" + feature.id() + "' references parent/group '" + reference + "' which is not part of the resolved scope."));
        }
    }

    /**
     * Resolves the final semantics of one included candidate. The manifest is the authored contract, so every value it
     * declares wins; the annotation only fills attributes the manifest leaves open, and a contradiction is reported
     * rather than silently applied. Membership itself is never annotation-driven.
     *
     * @param candidate extracted candidate.
     * @param manifest include entry of the candidate.
     * @param annotated source annotation resolved to the candidate, or null.
     * @param items report item sink.
     * @return resolved semantics with their source marker.
     */
    private ResolvedFeatureScope resolveSemantics(FeatureCandidate candidate, IncludeEntry manifest, ExtractedAnnotation annotated, List<ReportItem> items) {
        String optionality = firstNonNull(manifest.optionality(), FeatureScopeManifest.OPTIONALITY_OPTIONAL);
        if (annotated == null) {
            return new ResolvedFeatureScope(candidate.id(), manifest.id(), manifest.group(), manifest.parent(), kind(manifest.kind(), candidate), optionality,
                    manifest.category(), manifest.defaultState(), manifest.order(), manifest.requiresCapabilities(), manifest.providesCapabilities(),
                    manifest.artifactMappings(), manifest.name(), manifest.description(), manifest.documentationUrl(), SEMANTIC_SOURCE_MANIFEST);
        }
        // The annotation contract carries no category, default state, order, or mapping hints yet; those stay manifest data.
        ExtractedAnnotationSemantics annotation = annotated.semantics();
        reportContradictedAnnotationAttributes(candidate, manifest, annotated, items);
        String semanticSource = annotationFilledAnOpenAttribute(manifest, annotation) ? SEMANTIC_SOURCE_ANNOTATION : SEMANTIC_SOURCE_MANIFEST;
        return new ResolvedFeatureScope(candidate.id(), manifest.id(), firstNonNull(manifest.group(), annotation.group()),
                firstNonNull(manifest.parent(), annotation.parent()), kind(firstNonNull(manifest.kind(), annotation.kind()), candidate), optionality,
                manifest.category(), manifest.defaultState(), manifest.order(),
                declaredCapabilities(manifest.requiresCapabilities(), annotation.requiresCapabilities()),
                declaredCapabilities(manifest.providesCapabilities(), annotation.providesCapabilities()), manifest.artifactMappings(),
                firstNonNull(manifest.name(), annotation.name()), firstNonNull(manifest.description(), annotation.description()),
                firstNonNull(manifest.documentationUrl(), annotation.documentationUrl()), semanticSource);
    }

    /**
     * Reports every attribute the annotation declares differently from the manifest. The manifest value is used; the
     * warning exists so an annotation drifting away from the authored contract stays visible.
     *
     * @param candidate extracted candidate.
     * @param manifest include entry of the candidate.
     * @param annotated source annotation resolved to the candidate.
     * @param items report item sink.
     */
    private void reportContradictedAnnotationAttributes(FeatureCandidate candidate, IncludeEntry manifest, ExtractedAnnotation annotated,
            List<ReportItem> items) {
        ExtractedAnnotationSemantics annotation = annotated.semantics();
        List<String> contradicted = new ArrayList<>();
        addContradiction(contradicted, "id", manifest.id(), annotation.id());
        addContradiction(contradicted, "group", manifest.group(), annotation.group());
        addContradiction(contradicted, "parent", manifest.parent(), annotation.parent());
        addContradiction(contradicted, "kind", manifest.kind(), annotation.kind());
        addContradiction(contradicted, "name", manifest.name(), annotation.name());
        addContradiction(contradicted, "description", manifest.description(), annotation.description());
        addContradiction(contradicted, "documentationUrl", manifest.documentationUrl(), annotation.documentationUrl());
        addContradiction(contradicted, "requiresCapabilities", declaredList(manifest.requiresCapabilities()), annotation.requiresCapabilities());
        addContradiction(contradicted, "providesCapabilities", declaredList(manifest.providesCapabilities()), annotation.providesCapabilities());
        if (!contradicted.isEmpty()) {
            items.add(ReportItem.warning(ReportItem.CODE_MANIFEST_OVERRIDES_ANNOTATION, candidate.id(),
                    "Source annotation at " + annotated.file() + ":" + annotated.line() + " declares " + String.join(", ", contradicted)
                            + " differently from the manifest entry; the manifest value is used."));
        }
    }

    /**
     * Records one attribute both sides declare with different values.
     *
     * @param contradicted sink of contradicted attribute names.
     * @param attribute attribute name.
     * @param manifestValue value the manifest declares, or null when it leaves the attribute open.
     * @param annotationValue value the annotation declares, or null when it does not declare the attribute.
     */
    private void addContradiction(List<String> contradicted, String attribute, Object manifestValue, Object annotationValue) {
        if (manifestValue != null && annotationValue != null && !manifestValue.equals(annotationValue)) {
            contradicted.add(attribute);
        }
    }

    /**
     * Indicates whether the annotation supplied a value for an attribute the manifest left open.
     *
     * @param manifest include entry of the candidate.
     * @param annotation parsed annotation semantics.
     * @return true when at least one resolved value came from the annotation.
     */
    private boolean annotationFilledAnOpenAttribute(IncludeEntry manifest, ExtractedAnnotationSemantics annotation) {
        return fillsGap(manifest.group(), annotation.group()) || fillsGap(manifest.parent(), annotation.parent())
                || fillsGap(manifest.kind(), annotation.kind()) || fillsGap(manifest.name(), annotation.name())
                || fillsGap(manifest.description(), annotation.description()) || fillsGap(manifest.documentationUrl(), annotation.documentationUrl())
                || fillsGap(declaredList(manifest.requiresCapabilities()), annotation.requiresCapabilities())
                || fillsGap(declaredList(manifest.providesCapabilities()), annotation.providesCapabilities());
    }

    /**
     * Checks whether an annotation value fills an attribute the manifest leaves open.
     *
     * @param manifestValue value the manifest declares, or null.
     * @param annotationValue value the annotation declares, or null.
     * @return true when only the annotation declares the attribute.
     */
    private boolean fillsGap(Object manifestValue, Object annotationValue) {
        return manifestValue == null && annotationValue != null;
    }

    /**
     * Chooses the capability list of an included candidate: the manifest list when it declares one, otherwise the
     * annotation list.
     *
     * @param manifestCapabilities capability list of the manifest entry, empty when not declared.
     * @param annotationCapabilities capability list of the annotation, or null when not declared.
     * @return resolved capability list.
     */
    private List<String> declaredCapabilities(List<String> manifestCapabilities, List<String> annotationCapabilities) {
        List<String> declared = declaredList(manifestCapabilities);
        if (declared != null) {
            return declared;
        }
        return annotationCapabilities == null ? List.of() : annotationCapabilities;
    }

    /**
     * Treats an empty manifest capability list as an attribute the manifest leaves open, because the manifest record
     * normalizes an absent list to an empty one.
     *
     * @param capabilities capability list of the manifest entry.
     * @return the list when it declares capabilities, otherwise null.
     */
    private List<String> declaredList(List<String> capabilities) {
        return capabilities == null || capabilities.isEmpty() ? null : capabilities;
    }

    /**
     * Chooses the model kind of an included candidate: the explicit override when present, otherwise a default derived
     * from the extraction candidate kind.
     *
     * @param declaredKind explicit kind from manifest or annotation, or null.
     * @param candidate extracted candidate.
     * @return model kind.
     */
    private String kind(String declaredKind, FeatureCandidate candidate) {
        if (declaredKind != null) {
            return declaredKind;
        }
        return switch (candidate.kind()) {
            case FeatureCandidate.KIND_MODULE_FEATURE -> "module";
            case FeatureCandidate.KIND_RUNTIME_TOGGLE -> "runtime-toggle";
            default -> "technical";
        };
    }

    /**
     * Returns the preferred value when present, otherwise the fallback.
     *
     * @param <T> value type.
     * @param preferred preferred value, or null.
     * @param fallback fallback value.
     * @return preferred value or fallback.
     */
    private <T> T firstNonNull(T preferred, T fallback) {
        return preferred == null ? fallback : preferred;
    }

    /**
     * Creates the diagnostic for an annotation whose candidate is not included by the manifest.
     *
     * @param candidate annotated candidate.
     * @param annotation source annotation.
     * @param state manifest state of the candidate.
     * @return annotated-but-unscoped warning.
     */
    private ReportItem annotatedButUnscoped(FeatureCandidate candidate, ExtractedAnnotation annotation, String state) {
        return ReportItem.warning(ReportItem.CODE_ANNOTATED_BUT_UNSCOPED, candidate.id(),
                "Source annotation at " + annotation.file() + ":" + annotation.line() + " does not grant membership; manifest state is '" + state + "'.");
    }

    /**
     * Assembles the curation report section with deterministic counts and ordering.
     *
     * @param manifest loaded manifest.
     * @param decisions sorted candidate decisions.
     * @return curation report section.
     */
    private CurationReport assembleReport(FeatureScopeManifest manifest, List<CurationDecision> decisions) {
        Map<String, Integer> stateCounts = initializedCounts();
        Map<String, Map<String, Integer>> byKind = new TreeMap<>();
        List<String> undeclared = new ArrayList<>();
        for (CurationDecision decision : decisions) {
            stateCounts.merge(decision.state(), 1, Integer::sum);
            byKind.computeIfAbsent(decision.candidateKind(), ignored -> initializedCounts()).merge(decision.state(), 1, Integer::sum);
            if (STATE_UNDECLARED.equals(decision.state())) {
                undeclared.add(decision.candidateId());
            }
        }
        undeclared.sort(String::compareTo);
        return new CurationReport(manifest.manifestVersion(), manifest.artemisCommitSha(), new LinkedHashMap<>(stateCounts), deepImmutable(byKind),
                List.copyOf(undeclared), List.copyOf(decisions));
    }

    /**
     * Creates a state count map with all three states present, so report consumers see explicit zeros.
     *
     * @return mutable count map initialized to zero.
     */
    private Map<String, Integer> initializedCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(STATE_INCLUDE, 0);
        counts.put(STATE_EXCLUDE, 0);
        counts.put(STATE_UNDECLARED, 0);
        return counts;
    }

    /**
     * Copies nested count maps into unmodifiable views.
     *
     * @param values nested count maps.
     * @return unmodifiable deep copy.
     */
    private Map<String, Map<String, Integer>> deepImmutable(Map<String, Map<String, Integer>> values) {
        Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
        values.forEach((key, counts) -> copy.put(key, Collections.unmodifiableMap(new LinkedHashMap<>(counts))));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Orders decisions so undeclared candidates lead the report, followed by includes and excludes.
     *
     * @param state decision state.
     * @return sort rank of the state.
     */
    private int stateOrder(String state) {
        return switch (state) {
            case STATE_UNDECLARED -> 0;
            case STATE_INCLUDE -> 1;
            default -> 2;
        };
    }

    /** Resolves manifest and annotation symbols to the canonical namespaced candidate id. */
    private static final class CandidateResolver {

        /**
         * Outcome of one anchor resolution; exactly one component is set.
         *
         * @param candidateId resolved candidate id, or null on failure.
         * @param problem human-readable resolution failure, or null on success.
         */
        private record Resolution(String candidateId, String problem) {
        }

        private final Map<String, FeatureCandidate> candidatesById = new LinkedHashMap<>();

        private final List<FeatureCandidate> candidates;

        /**
         * Creates a resolver over the extracted candidates.
         *
         * @param candidates extracted candidates.
         */
        private CandidateResolver(List<FeatureCandidate> candidates) {
            this.candidates = candidates;
            candidates.forEach(candidate -> candidatesById.put(candidate.id(), candidate));
        }

        /**
         * Resolves an anchor written as a namespaced candidate id or as a source symbol: a condition class, a server
         * constant, or a client constant, optionally package-qualified.
         *
         * @param anchor manifest or annotation anchor.
         * @return successful resolution, or the failure description.
         */
        private Resolution resolve(String anchor) {
            if (candidatesById.containsKey(anchor)) {
                return new Resolution(anchor, null);
            }
            List<String> matches = candidates.stream().filter(candidate -> matches(candidate, anchor)).map(FeatureCandidate::id).distinct().toList();
            if (matches.isEmpty()) {
                return new Resolution(null, "Anchor '" + anchor + "' does not match an extraction candidate.");
            }
            if (matches.size() > 1) {
                return new Resolution(null, "Anchor '" + anchor + "' is ambiguous across candidates " + matches + ".");
            }
            return new Resolution(matches.getFirst(), null);
        }

        /**
         * Checks whether an anchor names one of the candidate's source symbols.
         *
         * @param candidate extracted candidate.
         * @param anchor manifest or annotation anchor.
         * @return true if the anchor matches a symbol of the candidate.
         */
        private boolean matches(FeatureCandidate candidate, String anchor) {
            return matchesSymbol(anchor, candidate.serverConditionClass()) || matchesSymbol(anchor, candidate.serverConstant())
                    || matchesSymbol(anchor, candidate.clientConstant());
        }

        /**
         * Checks whether an anchor equals a symbol or is a package-qualified form of it.
         *
         * @param anchor manifest or annotation anchor.
         * @param symbol candidate source symbol, or null.
         * @return true if the anchor names the symbol.
         */
        private boolean matchesSymbol(String anchor, String symbol) {
            return symbol != null && (anchor.equals(symbol) || anchor.endsWith("." + symbol));
        }

        /**
         * Returns the candidate registered under an id.
         *
         * @param id namespaced candidate id.
         * @return candidate.
         */
        private FeatureCandidate candidate(String id) {
            return candidatesById.get(id);
        }
    }
}
