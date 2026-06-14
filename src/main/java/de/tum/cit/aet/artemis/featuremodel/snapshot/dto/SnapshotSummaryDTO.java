package de.tum.cit.aet.artemis.featuremodel.snapshot.dto;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.SnapshotMetadata;

/**
 * Summary of an imported local snapshot for the snapshot listing.
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
 */
public record SnapshotSummaryDTO(String snapshotId, String modelId, String version, String status, String sourceRepo, String sourceRef, String sourceCommit,
        String extractorVersion, boolean active) {

    /**
     * Builds a summary from snapshot metadata.
     *
     * @param metadata snapshot metadata.
     * @param active whether this snapshot is the configured active snapshot.
     * @return snapshot summary DTO.
     */
    public static SnapshotSummaryDTO from(SnapshotMetadata metadata, boolean active) {
        return new SnapshotSummaryDTO(metadata.snapshotId(), metadata.modelId(), metadata.version(), metadata.status(), metadata.sourceRepo(),
                metadata.sourceRef(), metadata.sourceCommit(), metadata.extractorVersion(), active);
    }
}
