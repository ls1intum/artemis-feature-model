package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * A single generated artifact file held in memory before preview or packaging.
 *
 * @param path relative path inside the artifact package, for example {@code config/application-feature-model.yml}.
 * @param contentType MIME type used for preview and download metadata.
 * @param content textual file content.
 */
public record GeneratedArtifactFile(String path, String contentType, String content) {
}
