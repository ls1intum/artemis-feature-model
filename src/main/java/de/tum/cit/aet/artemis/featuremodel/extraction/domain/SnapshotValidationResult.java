package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * Successful offline snapshot validation summary.
 *
 * @param snapshotId validated content-addressed id.
 * @param snapshotDigest digest of {@code checksums.txt}, identifying the complete payload set.
 * @param artemisCommit validated Artemis source commit.
 * @param manifestDigest validated manifest digest.
 * @param payloadCount number of checksummed payload files.
 */
public record SnapshotValidationResult(String snapshotId, String snapshotDigest, String artemisCommit, String manifestDigest, int payloadCount) {
}
