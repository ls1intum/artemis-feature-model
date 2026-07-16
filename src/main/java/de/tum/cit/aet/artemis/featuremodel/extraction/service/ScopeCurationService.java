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
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureManifestException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ExcludeEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.IncludeEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ArtemisFeatureAnnotationScan.AnnotatedAnchor;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ArtemisFeatureAnnotationScan.AnnotationSemantics;

/** Validates and applies manifest membership, then merges source annotation semantics for included anchors. */
class ScopeCurationService {

    static final String STATE_INCLUDE = "include";

    static final String STATE_EXCLUDE = "exclude";

    static final String STATE_PENDING = "pending";

    /**
     * Curation result.
     *
     * @param report structured curation section.
     * @param includedFeatures resolved included semantics sorted by candidate id.
     * @param items curation diagnostics.
     */
    record Result(CurationReport report, List<ResolvedFeatureScope> includedFeatures, List<ReportItem> items) {
    }

    /**
     * Applies the manifest to extracted candidates.
     *
     * @param manifest loaded manifest.
     * @param candidates extracted candidates.
     * @param annotations parsed source annotations.
     * @param scannedCommit resolved checkout commit.
     * @return classifications, resolved include semantics, and diagnostics.
     * @throws FeatureManifestException if manifest anchors or hierarchy are invalid.
     */
    Result curate(FeatureScopeManifest manifest, List<FeatureCandidate> candidates, List<AnnotatedAnchor> annotations, String scannedCommit) {
        CandidateResolver resolver = new CandidateResolver(candidates);
        Map<String, IncludeEntry> includes = resolveIncludes(manifest, resolver);
        Map<String, ExcludeEntry> excludes = resolveExcludes(manifest, resolver, includes.keySet());
        validateConceptualHierarchy(manifest, includes.values());

        Map<String, AnnotatedAnchor> annotationsByCandidate = resolveAnnotations(annotations, resolver);
        List<ReportItem> items = new ArrayList<>();
        if (!manifest.verifiedAgainstArtemisCommit().equals(scannedCommit)) {
            items.add(ReportItem.warning(ReportItem.CODE_MANIFEST_COMMIT_MISMATCH, scannedCommit,
                    "Scope manifest was verified against Artemis commit '" + manifest.verifiedAgainstArtemisCommit() + "'."));
        }

        List<CurationDecision> decisions = new ArrayList<>();
        List<ResolvedFeatureScope> includedFeatures = new ArrayList<>();
        for (FeatureCandidate candidate : candidates) {
            IncludeEntry include = includes.get(candidate.id());
            ExcludeEntry exclude = excludes.get(candidate.id());
            AnnotatedAnchor annotation = annotationsByCandidate.get(candidate.id());
            if (include != null) {
                ResolvedFeatureScope scope = resolveSemantics(candidate, include, annotation);
                includedFeatures.add(scope);
                decisions.add(new CurationDecision(candidate.id(), candidate.kind(), STATE_INCLUDE, scope.id(), null, scope.semanticSource()));
                if (annotation != null) {
                    items.add(ReportItem.info(ReportItem.CODE_ANNOTATION_OVERRIDES_MANIFEST, candidate.id(),
                            "Source annotation at " + annotation.file() + ":" + annotation.line() + " overrides manifest-entry semantics."));
                }
            }
            else if (exclude != null) {
                decisions.add(new CurationDecision(candidate.id(), candidate.kind(), STATE_EXCLUDE, null, exclude.reason(), null));
                if (annotation != null) {
                    items.add(annotatedButUnscoped(candidate, annotation, STATE_EXCLUDE));
                }
            }
            else {
                decisions.add(new CurationDecision(candidate.id(), candidate.kind(), STATE_PENDING, null, null, null));
                items.add(ReportItem.info(ReportItem.CODE_PENDING_SCOPE_DECISION, candidate.id(), "Candidate is unlisted and remains outside the generated model."));
                if (annotation != null) {
                    items.add(annotatedButUnscoped(candidate, annotation, STATE_PENDING));
                }
            }
        }
        for (AnnotatedAnchor annotation : annotations) {
            if (resolver.resolveOrNull(annotation.anchor()) == null) {
                items.add(ReportItem.warning(ReportItem.CODE_ANNOTATED_ANCHOR_NOT_EXTRACTED, annotation.anchor(),
                        "Annotated source anchor at " + annotation.file() + ":" + annotation.line() + " did not match an extraction candidate."));
            }
        }

        includedFeatures.sort(Comparator.comparing(ResolvedFeatureScope::candidateId));
        validateResolvedHierarchy(manifest, includedFeatures);
        decisions.sort(Comparator.comparingInt((CurationDecision decision) -> stateOrder(decision.state())).thenComparing(CurationDecision::candidateId));
        CurationReport report = assembleReport(manifest, decisions);
        return new Result(report, List.copyOf(includedFeatures), List.copyOf(items));
    }

    private Map<String, IncludeEntry> resolveIncludes(FeatureScopeManifest manifest, CandidateResolver resolver) {
        Map<String, IncludeEntry> resolved = new LinkedHashMap<>();
        for (IncludeEntry entry : manifest.include()) {
            String candidateId = resolver.resolveRequired(entry.anchor());
            if (resolved.putIfAbsent(candidateId, entry) != null) {
                throw new FeatureManifestException("Multiple include entries resolve to candidate '" + candidateId + "'.");
            }
            FeatureCandidate candidate = resolver.candidate(candidateId);
            if (FeatureCandidate.KIND_RUNTIME_TOGGLE.equals(candidate.kind()) && entry.rationale() == null) {
                throw new FeatureManifestException("Runtime toggle include entry '" + entry.anchor() + "' requires rationale.");
            }
        }
        return resolved;
    }

    private Map<String, ExcludeEntry> resolveExcludes(FeatureScopeManifest manifest, CandidateResolver resolver, Set<String> includedIds) {
        Map<String, ExcludeEntry> resolved = new LinkedHashMap<>();
        for (ExcludeEntry entry : manifest.exclude()) {
            String candidateId = resolver.resolveRequired(entry.anchor());
            if (includedIds.contains(candidateId) || resolved.putIfAbsent(candidateId, entry) != null) {
                throw new FeatureManifestException("Multiple manifest entries resolve to candidate '" + candidateId + "'.");
            }
            FeatureCandidate candidate = resolver.candidate(candidateId);
            if (FeatureCandidate.KIND_RUNTIME_TOGGLE.equals(candidate.kind()) && entry.rationale() == null) {
                throw new FeatureManifestException("Runtime toggle exclude entry '" + entry.anchor() + "' requires rationale.");
            }
        }
        return resolved;
    }

    private void validateConceptualHierarchy(FeatureScopeManifest manifest, Iterable<IncludeEntry> includes) {
        Set<String> ids = new LinkedHashSet<>();
        manifest.conceptualNodes().forEach(node -> ids.add(node.id()));
        includes.forEach(entry -> ids.add(entry.id()));
        manifest.conceptualNodes().forEach(node -> validateParent(node.id(), node.parent(), ids));
        includes.forEach(entry -> {
            validateParent(entry.id(), entry.parent(), ids);
            validateParent(entry.id(), entry.group(), ids);
        });
    }

    private void validateParent(String id, String parent, Set<String> ids) {
        if (parent != null && !ids.contains(parent)) {
            throw new FeatureManifestException("Manifest entry '" + id + "' references unknown parent/group '" + parent + "'.");
        }
    }

    private void validateResolvedHierarchy(FeatureScopeManifest manifest, List<ResolvedFeatureScope> includedFeatures) {
        Set<String> ids = new LinkedHashSet<>();
        for (FeatureScopeManifest.ConceptualNode node : manifest.conceptualNodes()) {
            if (!ids.add(node.id())) {
                throw new FeatureManifestException("Duplicate resolved curated id '" + node.id() + "'.");
            }
        }
        for (ResolvedFeatureScope feature : includedFeatures) {
            if (!ids.add(feature.id())) {
                throw new FeatureManifestException("Duplicate resolved curated id '" + feature.id() + "'.");
            }
        }
        for (ResolvedFeatureScope feature : includedFeatures) {
            validateParent(feature.id(), feature.parent(), ids);
            validateParent(feature.id(), feature.group(), ids);
        }
    }

    private Map<String, AnnotatedAnchor> resolveAnnotations(List<AnnotatedAnchor> annotations, CandidateResolver resolver) {
        Map<String, AnnotatedAnchor> resolved = new LinkedHashMap<>();
        for (AnnotatedAnchor annotation : annotations) {
            String candidateId = resolver.resolveOrNull(annotation.anchor());
            if (candidateId != null && resolved.putIfAbsent(candidateId, annotation) != null) {
                throw new FeatureManifestException("Multiple @ArtemisFeature annotations resolve to candidate '" + candidateId + "'.");
            }
        }
        return resolved;
    }

    private ResolvedFeatureScope resolveSemantics(FeatureCandidate candidate, IncludeEntry manifest, AnnotatedAnchor annotated) {
        if (annotated == null) {
            return new ResolvedFeatureScope(candidate.id(), manifest.id(), manifest.group(), manifest.parent(), kind(manifest.kind(), candidate),
                    manifest.requiresCapabilities(), manifest.providesCapabilities(), manifest.name(), manifest.description(), manifest.documentationUrl(), "manifest");
        }
        AnnotationSemantics annotation = annotated.semantics();
        return new ResolvedFeatureScope(candidate.id(), annotation.id(), firstNonNull(annotation.group(), manifest.group()),
                firstNonNull(annotation.parent(), manifest.parent()), kind(firstNonNull(annotation.kind(), manifest.kind()), candidate),
                firstNonNull(annotation.requiresCapabilities(), manifest.requiresCapabilities()), firstNonNull(annotation.providesCapabilities(), manifest.providesCapabilities()),
                firstNonNull(annotation.name(), manifest.name()), firstNonNull(annotation.description(), manifest.description()),
                firstNonNull(annotation.documentationUrl(), manifest.documentationUrl()), "annotation");
    }

    private String kind(String manifestKind, FeatureCandidate candidate) {
        if (manifestKind != null) {
            return manifestKind;
        }
        return switch (candidate.kind()) {
            case FeatureCandidate.KIND_MODULE_FEATURE -> "module";
            case FeatureCandidate.KIND_RUNTIME_TOGGLE -> "runtime-toggle";
            default -> "technical";
        };
    }

    private <T> T firstNonNull(T preferred, T fallback) {
        return preferred == null ? fallback : preferred;
    }

    private ReportItem annotatedButUnscoped(FeatureCandidate candidate, AnnotatedAnchor annotation, String state) {
        return ReportItem.warning(ReportItem.CODE_ANNOTATED_BUT_UNSCOPED, candidate.id(),
                "Source annotation at " + annotation.file() + ":" + annotation.line() + " does not grant membership; manifest state is '" + state + "'.");
    }

    private CurationReport assembleReport(FeatureScopeManifest manifest, List<CurationDecision> decisions) {
        Map<String, Integer> stateCounts = initializedCounts();
        Map<String, Map<String, Integer>> byKind = new TreeMap<>();
        List<String> pending = new ArrayList<>();
        for (CurationDecision decision : decisions) {
            stateCounts.merge(decision.state(), 1, Integer::sum);
            byKind.computeIfAbsent(decision.candidateKind(), ignored -> initializedCounts()).merge(decision.state(), 1, Integer::sum);
            if (STATE_PENDING.equals(decision.state())) {
                pending.add(decision.candidateId());
            }
        }
        pending.sort(String::compareTo);
        return new CurationReport(manifest.manifestVersion(), manifest.verifiedAgainstArtemisCommit(), new LinkedHashMap<>(stateCounts), deepImmutable(byKind),
                List.copyOf(pending), List.copyOf(decisions));
    }

    private Map<String, Integer> initializedCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(STATE_INCLUDE, 0);
        counts.put(STATE_EXCLUDE, 0);
        counts.put(STATE_PENDING, 0);
        return counts;
    }

    private Map<String, Map<String, Integer>> deepImmutable(Map<String, Map<String, Integer>> values) {
        Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
        values.forEach((key, counts) -> copy.put(key, Collections.unmodifiableMap(new LinkedHashMap<>(counts))));
        return Collections.unmodifiableMap(copy);
    }

    private int stateOrder(String state) {
        return switch (state) {
            case STATE_PENDING -> 0;
            case STATE_INCLUDE -> 1;
            default -> 2;
        };
    }

    /** Resolves manifest and annotation symbols to the canonical namespaced candidate id. */
    private static final class CandidateResolver {

        private final Map<String, FeatureCandidate> candidatesById = new LinkedHashMap<>();

        private final List<FeatureCandidate> candidates;

        private CandidateResolver(List<FeatureCandidate> candidates) {
            this.candidates = candidates;
            candidates.forEach(candidate -> candidatesById.put(candidate.id(), candidate));
        }

        private String resolveRequired(String anchor) {
            String resolved = resolveOrNull(anchor);
            if (resolved == null) {
                throw new FeatureManifestException("Manifest anchor '" + anchor + "' does not match an extraction candidate.");
            }
            return resolved;
        }

        private String resolveOrNull(String anchor) {
            if (candidatesById.containsKey(anchor)) {
                return anchor;
            }
            List<String> matches = candidates.stream().filter(candidate -> matches(candidate, anchor)).map(FeatureCandidate::id).distinct().toList();
            if (matches.size() > 1) {
                throw new FeatureManifestException("Anchor '" + anchor + "' is ambiguous across candidates " + matches + ".");
            }
            return matches.isEmpty() ? null : matches.getFirst();
        }

        private boolean matches(FeatureCandidate candidate, String anchor) {
            return matchesSymbol(anchor, candidate.backendConditionClass()) || matchesSymbol(anchor, candidate.backendConstant())
                    || matchesSymbol(anchor, candidate.frontendConstant());
        }

        private boolean matchesSymbol(String anchor, String symbol) {
            return symbol != null && (anchor.equals(symbol) || anchor.endsWith("." + symbol));
        }

        private FeatureCandidate candidate(String id) {
            return candidatesById.get(id);
        }
    }
}
