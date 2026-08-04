package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Standalone release verdict that binds source-to-manifest conformance to the identifiers emitted by model
 * generation. The generated identifiers make accidental omissions or additions visible without comparing against
 * the classpath development model.
 *
 * @param schemaVersion report schema version.
 * @param status {@code pass} when the manifest is conformant, otherwise {@code fail}.
 * @param artemisCommit immutable Artemis source commit.
 * @param manifestDigest digest of the manifest bytes used by the model stage.
 * @param conformance source-fact conformance result.
 * @param curation complete include, exclude, and undeclared classification.
 * @param generatedFeatureIds feature ids emitted after a conformant decision.
 * @param generatedRelationIds relation ids emitted after a conformant decision.
 * @param generatedConstraintIds constraint ids emitted after a conformant decision.
 * @param generatedModelDigest digest of the generated model, or null when generation was blocked.
 */
public record ManifestConformanceReport(int schemaVersion, String status, String artemisCommit, String manifestDigest,
        ManifestConformance conformance, CurationReport curation, List<String> generatedFeatureIds, List<String> generatedRelationIds,
        List<String> generatedConstraintIds, String generatedModelDigest) {

    /** Current schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Successful conformance status. */
    public static final String STATUS_PASS = "pass";

    /** Blocking conformance status. */
    public static final String STATUS_FAIL = "fail";

    /** Normalizes identifier lists to immutable values. */
    public ManifestConformanceReport {
        generatedFeatureIds = generatedFeatureIds == null ? List.of() : List.copyOf(generatedFeatureIds);
        generatedRelationIds = generatedRelationIds == null ? List.of() : List.copyOf(generatedRelationIds);
        generatedConstraintIds = generatedConstraintIds == null ? List.of() : List.copyOf(generatedConstraintIds);
    }
}
