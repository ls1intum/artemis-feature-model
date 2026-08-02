package de.tum.cit.aet.artemis.featuremodel.export.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Classpath fallback provenance for local-docker package generation.
 *
 * @param sourceCommit Artemis source commit associated with the classpath feature model.
 * @param imageDigest remote Artemis image digest, or the special value {@code latest}.
 */
@ConfigurationProperties(prefix = "artemis.feature-model.runtime")
public record ArtemisRuntimeProperties(String sourceCommit, String imageDigest) {
}
