package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * Immutable provenance of a generated snapshot. Volatile run ids, timestamps, checkout paths, and runner details are
 * deliberately absent so equal immutable inputs produce equal bytes.
 *
 * @param snapshotFormatVersion snapshot contract version.
 * @param artemisCommit full pinned Artemis commit.
 * @param manifestDigest digest of the scope manifest.
 * @param featureModelRepositoryCommit feature-model repository commit that produced the snapshot.
 * @param extractorVersion extraction implementation version.
 * @param featureModelDigest feature model payload digest.
 * @param workflowDigest guided workflow payload digest.
 * @param catalogDigest config-key catalog payload digest.
 * @param generationReportDigest generation report payload digest.
 * @param deploymentProfileDigest digest of the validated deployment profile.
 * @param manifestSource resolution mode the manifest bytes came from, one of {@link #MANIFEST_SOURCE_REPOSITORY} or
 *            {@link #MANIFEST_SOURCE_CHECKOUT}.
 */
public record SnapshotProvenance(int snapshotFormatVersion, String artemisCommit, String manifestDigest, String featureModelRepositoryCommit,
        String extractorVersion, String featureModelDigest, String workflowDigest, String catalogDigest, String generationReportDigest,
        String deploymentProfileDigest, String manifestSource) {

    /** Current complete snapshot format. */
    public static final int CURRENT_FORMAT_VERSION = 3;

    /** The manifest bytes were read from the committed file in this repository. */
    public static final String MANIFEST_SOURCE_REPOSITORY = "repository";

    /** The manifest bytes were read from the canonical path inside the Artemis checkout. */
    public static final String MANIFEST_SOURCE_CHECKOUT = "checkout";
}
