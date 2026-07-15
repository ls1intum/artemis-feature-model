package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;

/**
 * Complete in-memory result of an artifact generation run: the generated files and the generation report. Both the
 * preview response and the downloadable ZIP are derived from this value.
 *
 * @param files generated files in deterministic order.
 * @param report generation report describing the run.
 */
public record GeneratedArtifactPackage(List<GeneratedArtifactFile> files, GenerationReport report) {

    /**
     * Normalizes the file list to an immutable list.
     *
     * @param files generated files.
     * @param report generation report.
     */
    public GeneratedArtifactPackage {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
