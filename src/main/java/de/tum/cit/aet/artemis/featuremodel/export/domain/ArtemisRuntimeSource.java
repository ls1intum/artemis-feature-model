package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.regex.Pattern;

/**
 * Image coordinates for the Artemis runtime used by a generated deployment package.
 *
 * @param imageRepository official Artemis application image repository.
 * @param imageDigest original configured digest, or the special value {@code latest}.
 */
public record ArtemisRuntimeSource(String imageRepository, String imageDigest) {

    /** Special digest value that selects the mutable {@code latest} tag instead of an exact digest. */
    private static final String LATEST = "latest";

    /** Prefix of the immutable per-commit tag Artemis CI publishes for every image it builds. */
    private static final String COMMIT_TAG_PREFIX = "sha-";

    private static final Pattern FULL_COMMIT_SHA = Pattern.compile("[0-9a-f]{40}");

    /**
     * Derives the image tag Artemis CI publishes for an Artemis commit. Only a full commit names a pushed tag; an
     * abbreviated commit would name a tag that was never pushed.
     *
     * @param commit Artemis commit, possibly {@code null}.
     * @return {@code sha-<commit>}, or {@code null} if the value is no full 40-character commit SHA.
     */
    public static String commitTag(String commit) {
        if (commit == null || !FULL_COMMIT_SHA.matcher(commit).matches()) {
            return null;
        }
        return COMMIT_TAG_PREFIX + commit;
    }

    /**
     * Renders the configured Artemis image reference.
     *
     * @return tag reference for {@code latest}, otherwise a digest reference.
     */
    public String imageReference() {
        if (usesLatestTag()) {
            return imageRepository + ":" + LATEST;
        }
        return imageRepository + "@" + imageDigest;
    }

    /**
     * Checks whether the configured digest selects the mutable {@code latest} tag.
     *
     * @return true if the image is referenced through the {@code latest} tag.
     */
    public boolean usesLatestTag() {
        return LATEST.equals(imageDigest);
    }
}
