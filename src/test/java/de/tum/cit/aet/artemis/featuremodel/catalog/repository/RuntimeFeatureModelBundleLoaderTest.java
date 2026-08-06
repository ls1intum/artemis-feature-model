package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GeneratedSnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.FixtureArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ModelStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.PackageStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ScanStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.WorkflowStageService;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import tools.jackson.databind.ObjectMapper;

class RuntimeFeatureModelBundleLoaderTest {

    private static final String ARTEMIS_COMMIT = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee";

    private static final Path FIXTURE_ROOT = Path.of("src/test/resources/extraction");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

    @TempDir
    Path workingDirectory;

    private String snapshotId;

    private Path runtimeSnapshot;

    @BeforeEach
    void publishCompleteRuntimeSnapshot() throws Exception {
        Path extractionRoot = workingDirectory.resolve("extraction");
        Path inputsRoot = FIXTURE_ROOT.resolve("fixture-inputs");
        FeatureExtractionInputs inputs = new FeatureExtractionInputs(FIXTURE_ROOT.resolve("mini-artemis"),
                FIXTURE_ROOT.resolve("mini-artemis-manifest.yml"), inputsRoot.resolve("guided-workflow.json"),
                inputsRoot.resolve("deployment-profile.json"), extractionRoot);
        new ScanStageService(objectMapper).run(inputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new ModelStageService(objectMapper).run(inputs);
        new WorkflowStageService(objectMapper).run(inputs);
        new PackageStageService(objectMapper).run(inputs);

        Path published = ExtractionArtifactLayout.forCommit(extractionRoot, ARTEMIS_COMMIT).snapshotDirectory();
        snapshotId = objectMapper.readValue(Files.readAllBytes(published.resolve("metadata.json")), GeneratedSnapshotMetadata.class).snapshotId();
        runtimeSnapshot = Files.createDirectories(workingDirectory.resolve("data/imported-models").resolve(snapshotId));
        try (var files = Files.list(published)) {
            for (Path file : files.toList()) {
                Files.copy(file, runtimeSnapshot.resolve(file.getFileName()));
            }
        }
    }

    @Test
    void loadsAndValidatesAllThreeClasspathArtifactsAsOneBundle() {
        RuntimeFeatureModelBundle bundle = loader(SnapshotProperties.classpathFallback()).load();

        assertThat(bundle.provenance().sourceMode()).isEqualTo(FeatureModelSourceMode.CLASSPATH);
        assertThat(bundle.model().model().id()).isEqualTo("artemis-functional-feature-tree");
        assertThat(bundle.workflow().workflow().id()).isEqualTo("artemis-guided-configuration");
        assertThat(bundle.catalog().catalogVersion()).isEqualTo("1.0.0");
        assertThat(bundle.snapshotMetadata()).isNull();
    }

    @Test
    void loadsModelWorkflowAndCatalogOnlyFromTheValidatedSnapshot() {
        RuntimeFeatureModelBundle bundle = loader(snapshotProperties(snapshotId)).load();

        assertThat(bundle.provenance().sourceMode()).isEqualTo(FeatureModelSourceMode.SNAPSHOT);
        assertThat(bundle.provenance().snapshotId()).isEqualTo(snapshotId);
        assertThat(bundle.provenance().snapshotDigest()).matches("sha256:[0-9a-f]{64}");
        assertThat(bundle.model().model().id()).isEqualTo("artemis-generated-feature-model");
        assertThat(bundle.workflow().workflow().id()).isEqualTo("fixture-workflow");
        assertThat(bundle.catalog().verifiedAgainstArtemisCommit()).isEqualTo(ARTEMIS_COMMIT);
        assertThat(bundle.catalog().catalogVersion()).isNotEqualTo("1.0.0");
    }

    @ParameterizedTest
    @ValueSource(strings = { "config-key-catalog.json", "feature-model.json", "generation-report.json", "guided-workflow.json", "metadata.json",
            "provenance.json" })
    void corruptionOfEverySnapshotPayloadStopsLoadingWithoutClasspathFallback(String fileName) throws Exception {
        Files.writeString(runtimeSnapshot.resolve(fileName), "corrupt\n");

        assertThatThrownBy(() -> loader(snapshotProperties(snapshotId)).load()).isInstanceOf(FeatureModelLoadException.class)
                .hasMessageContaining("failed complete validation").hasMessageContaining(fileName);
    }

    @Test
    void missingSnapshotStopsLoadingWithoutClasspathFallback() {
        assertThatThrownBy(() -> loader(snapshotProperties("generated-missing")).load()).isInstanceOf(FeatureModelLoadException.class)
                .hasMessageContaining("failed complete validation");
    }

    @Test
    void configuredDirectoryNameMustMatchValidatedSnapshotIdentity() throws Exception {
        String alias = "generated-alias";
        Path aliasDirectory = Files.createDirectories(workingDirectory.resolve("data/imported-models").resolve(alias));
        try (var files = Files.list(runtimeSnapshot)) {
            for (Path file : files.toList()) {
                Files.copy(file, aliasDirectory.resolve(file.getFileName()));
            }
        }

        assertThatThrownBy(() -> loader(snapshotProperties(alias)).load()).isInstanceOf(FeatureModelLoadException.class)
                .hasMessageContaining("does not match");
    }

    private SnapshotProperties snapshotProperties(String id) {
        return new SnapshotProperties(FeatureModelSourceMode.SNAPSHOT, workingDirectory.resolve("data").toString(), id, false);
    }

    private RuntimeFeatureModelBundleLoader loader(SnapshotProperties properties) {
        return new RuntimeFeatureModelBundleLoader(properties, resourceLoader, objectMapper);
    }
}
