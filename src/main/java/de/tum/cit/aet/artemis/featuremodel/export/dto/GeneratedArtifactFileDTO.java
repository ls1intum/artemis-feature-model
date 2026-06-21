package de.tum.cit.aet.artemis.featuremodel.export.dto;

import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;

/**
 * Preview DTO for a single generated artifact file. The preview carries the full textual content because generated
 * files are small in this phase.
 *
 * @param path relative path inside the artifact package.
 * @param contentType MIME type of the file.
 * @param preview textual file content.
 */
public record GeneratedArtifactFileDTO(String path, String contentType, String preview) {

    /**
     * Converts a generated artifact file to its preview DTO.
     *
     * @param file generated artifact file.
     * @return preview DTO carrying the same content.
     */
    public static GeneratedArtifactFileDTO from(GeneratedArtifactFile file) {
        return new GeneratedArtifactFileDTO(file.path(), file.contentType(), file.content());
    }
}
