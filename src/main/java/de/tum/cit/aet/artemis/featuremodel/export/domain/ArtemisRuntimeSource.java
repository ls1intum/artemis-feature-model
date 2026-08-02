package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * Provenance and image coordinates for the Artemis runtime used by a generated deployment package.
 *
 * @param sourceCommit Artemis source commit associated with the active feature model.
 * @param imageRepository official Artemis application image repository.
 * @param imageDigest original configured digest, or the special value {@code latest}.
 */
public record ArtemisRuntimeSource(String sourceCommit, String imageRepository, String imageDigest) {
}
