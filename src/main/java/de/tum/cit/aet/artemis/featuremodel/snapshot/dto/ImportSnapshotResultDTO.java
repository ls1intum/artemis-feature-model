package de.tum.cit.aet.artemis.featuremodel.snapshot.dto;

/**
 * Result of importing a local snapshot folder.
 *
 * @param snapshotId id the snapshot was registered under.
 * @param message human-readable import outcome.
 * @param detail detail of the imported snapshot.
 */
public record ImportSnapshotResultDTO(String snapshotId, String message, SnapshotDetailDTO detail) {
}
