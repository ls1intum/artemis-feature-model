package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * Metadata of one extraction scan. This is the only output that may contain timestamps; all other outputs must be
 * byte-identical across reruns on the same Artemis commit.
 *
 * @param extractorVersion version of the extraction pipeline that produced the outputs.
 * @param artemisPath absolute path of the scanned Artemis checkout.
 * @param artemisCommit resolved git commit of the checkout, or {@code unknown} when the checkout is not a git work tree.
 * @param workingTreeDirty true if the checkout had uncommitted changes at scan time, null when not resolvable.
 * @param scanStartedAt ISO-8601 UTC timestamp at scan start.
 * @param scanFinishedAt ISO-8601 UTC timestamp at scan end.
 * @param candidateCount number of feature candidates written.
 * @param evidenceCount number of evidence items written.
 * @param relationCandidateCount number of relation candidates written.
 * @param reportItemCount number of report items written.
 */
public record ScanMetadata(String extractorVersion, String artemisPath, String artemisCommit, Boolean workingTreeDirty, String scanStartedAt, String scanFinishedAt,
        int candidateCount, int evidenceCount, int relationCandidateCount, int reportItemCount) {
}
