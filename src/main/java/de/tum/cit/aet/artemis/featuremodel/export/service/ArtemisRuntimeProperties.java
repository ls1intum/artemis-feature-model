package de.tum.cit.aet.artemis.featuremodel.export.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Classpath fallback runtime image configuration for local-docker package generation.
 *
 * @param imageDigest remote Artemis image digest, or the special value {@code latest}.
 */
@ConfigurationProperties(prefix = "artemis.feature-model.runtime")
public record ArtemisRuntimeProperties(String imageDigest) {
}
