package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.regex.Pattern;

/**
 * Delivery-configuration reference to the remote Artemis runtime image consumed at snapshot packaging time. It
 * replaces the retired manifest {@code artemisImageDigest} field: which runtime image generated Compose stacks
 * reference is a delivery decision of this repository, not curation content. A {@code sha256:} digest pins the image;
 * the value {@code latest} pins none, so generated Docker packages run the per-commit image of the snapshot's source
 * commit.
 *
 * @param image remote image repository, for example {@code ghcr.io/ls1intum/artemis}.
 * @param digest image digest in {@code sha256:<hex>} form, or {@code latest} to pin no digest.
 */
public record ArtemisRuntimeImage(String image, String digest) {

    /** Value that pins no digest, so packages run the per-commit image of the source commit. */
    public static final String DIGEST_LATEST = "latest";

    private static final Pattern IMAGE_DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");

    /**
     * Validates that the reference names an image and either a pinned digest or the value that pins none.
     */
    public ArtemisRuntimeImage {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("The Artemis runtime image reference must name a remote image repository.");
        }
        if (digest == null || (!DIGEST_LATEST.equals(digest) && !IMAGE_DIGEST.matcher(digest).matches())) {
            throw new IllegalArgumentException("The Artemis runtime image digest must be 'sha256:<64 hex>' or the value '" + DIGEST_LATEST
                    + "', but was '" + digest + "'.");
        }
    }
}
