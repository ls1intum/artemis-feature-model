package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.DockerSnapshotContext;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.model.ModelStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.FixtureArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.scan.ScanStageService;
import tools.jackson.databind.ObjectMapper;

class DockerSnapshotContextStagerTest {

    private static final String ARTEMIS_COMMIT = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee";

    private static final String REPOSITORY_COMMIT = "fedcba9876543210fedcba9876543210fedcba98";

    private static final Path FIXTURE_ROOT = Path.of("src/test/resources/extraction");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path workingDirectory;

    private Path snapshot;

    private Path contextRoot;

    @BeforeEach
    void publishValidSnapshot() throws Exception {
        Path inputsRoot = FIXTURE_ROOT.resolve("fixture-inputs");
        Path extractionRoot = workingDirectory.resolve("extraction");
        FeatureExtractionInputs inputs = new FeatureExtractionInputs(FIXTURE_ROOT.resolve("mini-artemis"),
                FIXTURE_ROOT.resolve("mini-artemis-manifest.yml"), inputsRoot.resolve("guided-workflow.json"),
                inputsRoot.resolve("deployment-profile.json"), inputsRoot.resolve("artemis-runtime-image.json"), extractionRoot);
        new ScanStageService(objectMapper).run(inputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new ModelStageService(objectMapper).run(inputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new WorkflowStageService(objectMapper).run(inputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new PackageStageService(objectMapper, REPOSITORY_COMMIT).run(inputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        snapshot = ExtractionArtifactLayout.forCommit(extractionRoot, ARTEMIS_COMMIT).snapshotDirectory();
        contextRoot = workingDirectory.resolve("docker-context");
    }

    @Test
    void stagesOnlyTheValidatedSnapshotAndDeterministicBuildArguments() throws Exception {
        DockerSnapshotContext context = new DockerSnapshotContextStager(objectMapper).stage(snapshot, contextRoot);

        assertThat(context.snapshotId()).startsWith("generated-aaaaaaaa");
        assertThat(context.snapshotDigest()).matches("sha256:[0-9a-f]{64}");
        try (var files = Files.list(context.snapshotDirectory())) {
            assertThat(files.map(path -> path.getFileName().toString()).sorted().toList()).containsExactly("checksums.txt", "config-key-catalog.json",
                    "feature-model.json", "generation-report.json", "guided-workflow.json", "metadata.json", "provenance.json");
        }
        assertThat(Files.readAllLines(context.propertiesFile())).startsWith("ARTEMIS_COMMIT=" + ARTEMIS_COMMIT, "EXTRACTOR_VERSION=" + ScanResult.EXTRACTOR_VERSION,
                "FEATURE_MODEL_REPOSITORY_COMMIT=" + REPOSITORY_COMMIT).anyMatch(line -> line.startsWith("MANIFEST_DIGEST=sha256:"))
                .anyMatch(line -> line.startsWith("SNAPSHOT_DIGEST=sha256:")).anyMatch(line -> line.startsWith("SNAPSHOT_ID=generated-"));
    }

    @Test
    void invalidSourceDoesNotReplacePreviouslyStagedContext() throws Exception {
        DockerSnapshotContextStager stager = new DockerSnapshotContextStager(objectMapper);
        stager.stage(snapshot, contextRoot);
        List<String> originalProperties = Files.readAllLines(contextRoot.resolve(DockerSnapshotContextStager.BUILD_PROPERTIES_FILE));
        Files.writeString(snapshot.resolve("feature-model.json"), "corrupt\n");

        assertThatThrownBy(() -> stager.stage(snapshot, contextRoot)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("feature-model.json");
        assertThat(Files.readAllLines(contextRoot.resolve(DockerSnapshotContextStager.BUILD_PROPERTIES_FILE))).isEqualTo(originalProperties);
    }

    @Test
    void rejectsOutputThatCouldReplaceItsSource() {
        assertThatThrownBy(() -> new DockerSnapshotContextStager(objectMapper).stage(snapshot, snapshot.resolve("nested")))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("must not contain");
    }
}
