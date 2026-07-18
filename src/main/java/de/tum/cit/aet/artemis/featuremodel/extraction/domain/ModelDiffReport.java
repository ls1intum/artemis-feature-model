package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;
import java.util.Map;

/**
 * Payload of {@code model-diff-report.json}: the machine-produced comparison between the generated and the curated
 * feature model, plus the regenerated config-key catalog diff. Every difference carries exactly one classification,
 * so nothing about the generated model's divergence from the curated model stays unexplained.
 *
 * @param generatedModelId id of the generated model.
 * @param generatedModelVersion version of the generated model.
 * @param curatedModelId id of the curated model.
 * @param curatedModelVersion version of the curated model.
 * @param artemisCommit resolved commit of the scanned checkout.
 * @param classificationCounts entry counts per classification, all four classes always present.
 * @param entries classified differences sorted by subject, aspect, and classification.
 * @param catalogDiff diff of the regenerated config-key catalog against the curated catalog.
 */
public record ModelDiffReport(String generatedModelId, String generatedModelVersion, String curatedModelId, String curatedModelVersion, String artemisCommit,
        Map<String, Integer> classificationCounts, List<DiffEntry> entries, CatalogDiff catalogDiff) {

    /** The curated model deliberately deviates: curated prose, hand-picked evidence, or the deliberate E3 additions. */
    public static final String CLASS_INTENTIONAL_CURATION = "intentional-curation";

    /** The manifest lacks a declaration that would reproduce the curated value. */
    public static final String CLASS_MISSING_MANIFEST_ENTRY = "missing-manifest-entry";

    /** The scanned Artemis state disagrees with the curated declaration; the curated model is stale or Artemis moved. */
    public static final String CLASS_ARTEMIS_DRIFT = "artemis-drift";

    /** The extractor cannot currently express or recover the curated information. */
    public static final String CLASS_EXTRACTOR_GAP = "extractor-gap";

    /**
     * Normalizes the entry list to an immutable copy.
     */
    public ModelDiffReport {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /**
     * One classified difference between the generated and the curated model.
     *
     * @param classification one of the four difference classes.
     * @param aspect compared aspect, for example {@code feature-name} or {@code relation}.
     * @param subject feature id, relation key, constraint id, or model aspect the entry is about.
     * @param curated curated value rendering, or null when only the generated model has the subject.
     * @param generated generated value rendering, or null when only the curated model has the subject.
     * @param explanation human-readable reason for the classification.
     */
    public record DiffEntry(String classification, String aspect, String subject, String curated, String generated, String explanation) {
    }

    /**
     * Diff of the regenerated config-key catalog against the curated catalog.
     *
     * @param curatedCatalogVersion version of the curated catalog.
     * @param curatedVerifiedAgainstArtemisCommit commit pin of the curated catalog.
     * @param generatedVerifiedAgainstArtemisCommit commit pin of the regenerated catalog.
     * @param addedKeys keys only the regenerated catalog contains, sorted.
     * @param removedKeys keys only the curated catalog contains, sorted.
     * @param typeChanges keys present in both catalogs with different value types, sorted by key.
     */
    public record CatalogDiff(String curatedCatalogVersion, String curatedVerifiedAgainstArtemisCommit, String generatedVerifiedAgainstArtemisCommit,
            List<String> addedKeys, List<String> removedKeys, List<TypeChange> typeChanges) {

        /**
         * One key whose accepted value type differs between the catalogs.
         *
         * @param key dotted configuration key.
         * @param curatedType type the curated catalog declares.
         * @param generatedType type the regenerated catalog declares.
         */
        public record TypeChange(String key, String curatedType, String generatedType) {
        }
    }
}
