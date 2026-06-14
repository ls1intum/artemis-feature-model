package de.tum.cit.aet.artemis.featuremodel.snapshot.dto;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.SnapshotMetadata;

/**
 * Detailed view of an imported local snapshot, including which snapshot files are available.
 *
 * @param snapshotId snapshot id.
 * @param modelId feature model id the snapshot was generated for.
 * @param version model or snapshot version.
 * @param status snapshot lifecycle status.
 * @param sourceRepo source repository the snapshot was generated from.
 * @param sourceRef source ref the snapshot was generated from.
 * @param sourceCommit source commit the snapshot was generated from.
 * @param extractorVersion version of the extractor/generator that produced the snapshot.
 * @param active whether this snapshot is the configured active snapshot.
 * @param modelFileAvailable whether the feature model file is present.
 * @param workflowFileAvailable whether the guided workflow file is present.
 * @param reportAvailable whether the generation report file is present.
 * @param checksumAvailable whether the checksum file is present.
 */
public record SnapshotDetailDTO(String snapshotId, String modelId, String version, String status, String sourceRepo, String sourceRef, String sourceCommit,
        String extractorVersion, boolean active, boolean modelFileAvailable, boolean workflowFileAvailable, boolean reportAvailable, boolean checksumAvailable) {

    /**
     * Builds a detail DTO from snapshot metadata and file availability.
     *
     * @param metadata snapshot metadata.
     * @param active whether this snapshot is the configured active snapshot.
     * @param modelFileAvailable whether the feature model file is present.
     * @param workflowFileAvailable whether the guided workflow file is present.
     * @param reportAvailable whether the generation report file is present.
     * @param checksumAvailable whether the checksum file is present.
     * @return snapshot detail DTO.
     */
    public static SnapshotDetailDTO from(SnapshotMetadata metadata, boolean active, boolean modelFileAvailable, boolean workflowFileAvailable,
            boolean reportAvailable, boolean checksumAvailable) {
        return new SnapshotDetailDTO(metadata.snapshotId(), metadata.modelId(), metadata.version(), metadata.status(), metadata.sourceRepo(),
                metadata.sourceRef(), metadata.sourceCommit(), metadata.extractorVersion(), active, modelFileAvailable, workflowFileAvailable, reportAvailable,
                checksumAvailable);
    }
}
