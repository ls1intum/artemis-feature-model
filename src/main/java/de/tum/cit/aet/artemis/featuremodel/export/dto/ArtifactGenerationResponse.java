package de.tum.cit.aet.artemis.featuremodel.export.dto;

import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GenerationReport;

/**
 * Preview response for an artifact generation run: the status, the generated files with their content, the full
 * generation report, and whether a ZIP download is available.
 *
 * @param status generation status mirrored from the report.
 * @param files generated files with preview content.
 * @param report generation report describing the run.
 * @param downloadAvailable whether the same input can be downloaded as a ZIP package.
 */
public record ArtifactGenerationResponse(String status, List<GeneratedArtifactFileDTO> files, GenerationReport report, boolean downloadAvailable) {

    /**
     * Builds a preview response from a generated artifact package.
     *
     * @param artifactPackage generated artifact package.
     * @return preview response.
     */
    public static ArtifactGenerationResponse from(GeneratedArtifactPackage artifactPackage) {
        List<GeneratedArtifactFileDTO> files = artifactPackage.files().stream().map(GeneratedArtifactFileDTO::from).toList();
        return new ArtifactGenerationResponse(artifactPackage.report().status(), files, artifactPackage.report(), true);
    }
}
