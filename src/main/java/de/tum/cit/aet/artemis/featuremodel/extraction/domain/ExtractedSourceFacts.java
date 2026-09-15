package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Stable source facts produced by one scan command and persisted across the files under {@code scan/}.
 *
 * @param candidates feature candidates sorted by id.
 * @param evidence evidence items sorted by candidate id, file, line, kind, and symbol.
 * @param relationCandidates relation candidates sorted by id.
 * @param featureUsages {@code @FeatureUsage} placements sorted by file and type.
 * @param configDefaults scanned configuration defaults of the checkout.
 * @param configInjections guarded configuration injection sites per Java file, sorted by file.
 * @param items scan diagnostics, including drift against the curated model.
 */
public record ExtractedSourceFacts(List<FeatureCandidate> candidates, List<EvidenceItem> evidence, List<RelationCandidate> relationCandidates,
        List<ExtractedFeatureUsage> featureUsages, ExtractedConfigurationDefaults configDefaults, List<ExtractedConfigInjection> configInjections,
        List<ReportItem> items) {

    /** Normalizes every ordered fact collection to an immutable copy. */
    public ExtractedSourceFacts {
        candidates = List.copyOf(candidates);
        evidence = List.copyOf(evidence);
        relationCandidates = List.copyOf(relationCandidates);
        featureUsages = featureUsages == null ? List.of() : List.copyOf(featureUsages);
        configInjections = configInjections == null ? List.of() : List.copyOf(configInjections);
        items = List.copyOf(items);
    }
}
