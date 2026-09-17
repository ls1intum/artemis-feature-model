package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * Image coordinates for the Artemis runtime used by a generated deployment package.
 *
 * @param imageRepository official Artemis application image repository.
 * @param imageDigest original configured digest, or the special value {@code latest}.
 */
public record ArtemisRuntimeSource(String imageRepository, String imageDigest) {

    /** Special digest value that selects the mutable {@code latest} tag instead of an exact digest. */
    private static final String LATEST = "latest";

    /**
     * Renders the configured Artemis image reference.
     *
     * @return tag reference for {@code latest}, otherwise a digest reference.
     */
    public String imageReference() {
        if (LATEST.equals(imageDigest)) {
            return imageRepository + ":" + LATEST;
        }
        return imageRepository + "@" + imageDigest;
    }
}
