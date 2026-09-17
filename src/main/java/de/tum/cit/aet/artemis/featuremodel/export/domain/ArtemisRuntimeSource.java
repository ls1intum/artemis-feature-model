package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * Image coordinates for the Artemis runtime used by a generated deployment package.
 *
 * @param imageRepository official Artemis application image repository.
 * @param imageDigest original configured digest, or the special value {@code latest}.
 */
public record ArtemisRuntimeSource(String imageRepository, String imageDigest) {
}
