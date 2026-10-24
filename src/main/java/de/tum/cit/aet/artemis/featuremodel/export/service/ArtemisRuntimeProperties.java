package de.tum.cit.aet.artemis.featuremodel.export.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Classpath fallback runtime image configuration for local-docker package generation.
 *
 * @param imageDigest pinned Artemis image digest, or {@code latest} to run the per-commit image of the model's source
 *            commit.
 */
@ConfigurationProperties(prefix = "artemis.feature-model.runtime")
public record ArtemisRuntimeProperties(String imageDigest) {
}
