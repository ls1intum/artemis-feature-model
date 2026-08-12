package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.regex.Pattern;

/**
 * Delivery-configuration reference to the remote Artemis runtime image consumed at snapshot packaging time. It
 * replaces the retired manifest {@code artemisImageDigest} field: which runtime image generated Compose stacks
 * reference is a delivery decision of this repository, not curation content. The goal state is a pinned digest; the
 * mutable {@code latest} rendering stays accepted during migration.
 *
 * @param image remote image repository, for example {@code ghcr.io/ls1intum/artemis}.
 * @param digest image digest in {@code sha256:<hex>} form, or the special migration value {@code latest}.
 */
public record ArtemisRuntimeImage(String image, String digest) {

    /** Mutable migration value accepted until the reference is pinned to a digest. */
    public static final String DIGEST_LATEST = "latest";

    private static final Pattern IMAGE_DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");

    /**
     * Validates that the reference names an image and either a pinned digest or the accepted migration value.
     */
    public ArtemisRuntimeImage {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("The Artemis runtime image reference must name a remote image repository.");
        }
        if (digest == null || (!DIGEST_LATEST.equals(digest) && !IMAGE_DIGEST.matcher(digest).matches())) {
            throw new IllegalArgumentException("The Artemis runtime image digest must be 'sha256:<64 hex>' or the migration value '" + DIGEST_LATEST
                    + "', but was '" + digest + "'.");
        }
    }
}
