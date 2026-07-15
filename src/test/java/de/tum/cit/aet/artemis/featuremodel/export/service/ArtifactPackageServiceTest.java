package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GenerationReport;

class ArtifactPackageServiceTest {

    private final ArtifactPackageService packageService = new ArtifactPackageService();

    @Test
    void packagesAllFilesUnderASingleRootDirectory() throws IOException {
        byte[] archive = packageService.zip(samplePackage());

        List<String> names = entryNames(archive);
        assertThat(names).containsExactly("artemis-feature-model-artifacts/README.md", "artemis-feature-model-artifacts/config/application-feature-model.yml",
                "artemis-feature-model-artifacts/metadata/generation-report.json");
    }

    @Test
    void producesDeterministicArchivesForTheSameInput() {
        assertThat(packageService.zip(samplePackage())).isEqualTo(packageService.zip(samplePackage()));
    }

    private GeneratedArtifactPackage samplePackage() {
        GenerationReport report = new GenerationReport(GenerationReport.STATUS_GENERATED, GenerationReport.MODE_DEMO, "model", "1.0.0", "profile", "1.0.0",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        List<GeneratedArtifactFile> files = List.of(new GeneratedArtifactFile("README.md", "text/markdown", "# readme\n"),
                new GeneratedArtifactFile("config/application-feature-model.yml", "application/x-yaml", "artemis:\n  iris:\n    enabled: true\n"),
                new GeneratedArtifactFile("metadata/generation-report.json", "application/json", "{}\n"));
        return new GeneratedArtifactPackage(files, report);
    }

    private List<String> entryNames(byte[] archive) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
