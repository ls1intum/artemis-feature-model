package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Traceability metadata for a locally imported feature model snapshot, parsed from the snapshot's {@code metadata.json}.
 *
 * <p>
 * The file names point to the model, guided workflow, generation report, and checksum files inside the snapshot folder.
 * They default to the standard names so older snapshots that omit them remain loadable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SnapshotMetadata(String modelId, String snapshotId, String version, String status, String sourceRepo, String sourceRef, String sourceCommit,
        String imageDigest, String extractorVersion, String modelFile, String workflowFile, String reportFile, String checksumFile) {

    private static final String DEFAULT_MODEL_FILE = "feature-model.json";

    private static final String DEFAULT_WORKFLOW_FILE = "guided-workflow.json";

    private static final String DEFAULT_REPORT_FILE = "generation-report.json";

    private static final String DEFAULT_CHECKSUM_FILE = "checksum.txt";

    /**
     * Normalizes blank file names to the standard snapshot file names.
     *
     * @param modelId stable feature model id.
     * @param snapshotId snapshot id, normally the snapshot folder name.
     * @param version model or snapshot version.
     * @param status snapshot lifecycle status.
     * @param sourceRepo source repository the snapshot was generated from.
     * @param sourceRef source ref the snapshot was generated from.
     * @param sourceCommit source commit the snapshot was generated from.
     * @param imageDigest remote Artemis image digest, or the special value {@code latest}.
     * @param extractorVersion version of the extractor/generator that produced the snapshot.
     * @param modelFile feature model file name, defaulting to {@code feature-model.json}.
     * @param workflowFile guided workflow file name, defaulting to {@code guided-workflow.json}.
     * @param reportFile generation report file name, defaulting to {@code generation-report.json}.
     * @param checksumFile checksum file name, defaulting to {@code checksum.txt}.
     */
    public SnapshotMetadata {
        modelFile = orDefault(modelFile, DEFAULT_MODEL_FILE);
        workflowFile = orDefault(workflowFile, DEFAULT_WORKFLOW_FILE);
        reportFile = orDefault(reportFile, DEFAULT_REPORT_FILE);
        checksumFile = orDefault(checksumFile, DEFAULT_CHECKSUM_FILE);
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * Returns a copy of this metadata with the snapshot folder name as the authoritative snapshot id.
     *
     * @param authoritativeSnapshotId snapshot id derived from the folder name.
     * @return metadata using the given snapshot id.
     */
    public SnapshotMetadata withSnapshotId(String authoritativeSnapshotId) {
        return new SnapshotMetadata(modelId, authoritativeSnapshotId, version, status, sourceRepo, sourceRef, sourceCommit, imageDigest, extractorVersion, modelFile,
                workflowFile, reportFile, checksumFile);
    }
}
