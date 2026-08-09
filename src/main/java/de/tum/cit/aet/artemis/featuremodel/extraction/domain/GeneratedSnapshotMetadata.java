package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * Deterministic metadata and recognized-file declaration of a generated snapshot. Field names shared with the legacy
 * local snapshot DTO keep Stage 2 artifacts importable while Stage 3 adopts the complete validator.
 *
 * @param schemaVersion metadata schema version.
 * @param snapshotFormatVersion complete snapshot format version.
 * @param modelId generated model id.
 * @param snapshotId content-addressed snapshot id.
 * @param version generated model version.
 * @param status snapshot lifecycle status.
 * @param sourceCommit pinned Artemis commit.
 * @param imageDigest configured Artemis runtime image digest.
 * @param extractorVersion extractor identity.
 * @param modelFile model payload name.
 * @param workflowFile workflow payload name.
 * @param catalogFile catalog payload name.
 * @param reportFile generation report payload name.
 * @param provenanceFile provenance payload name.
 * @param checksumFile checksum manifest name.
 */
public record GeneratedSnapshotMetadata(int schemaVersion, int snapshotFormatVersion, String modelId, String snapshotId, String version, String status,
        String sourceCommit, String imageDigest, String extractorVersion, String modelFile, String workflowFile, String catalogFile, String reportFile,
        String provenanceFile, String checksumFile) {

    /** Current metadata schema. */
    public static final int CURRENT_SCHEMA_VERSION = 3;

    /** Lifecycle status of a snapshot produced successfully by the extraction pipeline. */
    public static final String STATUS_GENERATED = "generated";

    /** Stable prefix of the extractor identity recorded in snapshot metadata. */
    public static final String EXTRACTOR_ID_PREFIX = "feature-model-extractor@";
}
