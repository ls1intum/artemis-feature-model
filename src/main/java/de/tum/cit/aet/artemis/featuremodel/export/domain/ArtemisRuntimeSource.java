package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.regex.Pattern;

/**
 * Image coordinates for the Artemis runtime used by a generated deployment package. A package names its image either
 * by a pinned digest or by the per-commit tag of the model's source commit.
 *
 * @param imageRepository official Artemis application image repository.
 * @param imageDigest pinned image digest, or {@code null} when the package names its image by tag.
 * @param imageTag per-commit image tag, or {@code null} when a digest is pinned or the package names no image.
 */
public record ArtemisRuntimeSource(String imageRepository, String imageDigest, String imageTag) {

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
     * Renders the Artemis image reference.
     *
     * @return digest reference for a pinned digest, otherwise the tag reference.
     */
    public String imageReference() {
        if (imageDigest != null) {
            return imageRepository + "@" + imageDigest;
        }
        return imageRepository + ":" + imageTag;
    }
}
