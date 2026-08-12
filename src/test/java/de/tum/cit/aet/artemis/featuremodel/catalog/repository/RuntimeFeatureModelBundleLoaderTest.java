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
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

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
                inputsRoot.resolve("deployment-profile.json"), inputsRoot.resolve("artemis-runtime-image.json"), extractionRoot);
        new ScanStageService(objectMapper).run(inputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new ModelStageService(objectMapper).run(inputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new WorkflowStageService(objectMapper).run(inputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new PackageStageService(objectMapper).run(inputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));

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
        assertThat(bundle.model().model().id()).isEqualTo("artemis-generated-feature-model");
        assertThat(bundle.workflow().workflow().id()).isEqualTo("artemis-guided-configuration");
        assertThat(bundle.catalog().catalogVersion()).isEqualTo(bundle.model().model().version());
        assertThat(bundle.snapshotMetadata()).isNull();
    }

    @Test
    void classpathStartupFailsActionablyWhenAPublishedOptionReferencesAFeatureTheFixtureLacks() throws Exception {
        String workflow = Files.readString(Path.of("src/main/resources/feature-model/guided-workflow.json"))
                .replace("\"lecture\"", "\"feature-the-fixture-lacks\"");
        DefaultResourceLoader alteredLoader = new DefaultResourceLoader() {

            @Override
            public org.springframework.core.io.Resource getResource(String location) {
                if (location.endsWith("guided-workflow.json")) {
                    return new org.springframework.core.io.ByteArrayResource(workflow.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                return super.getResource(location);
            }
        };

        assertThatThrownBy(() -> new RuntimeFeatureModelBundleLoader(SnapshotProperties.classpathFallback(), alteredLoader, objectMapper).load())
                .isInstanceOf(FeatureModelLoadException.class).hasMessageContaining("feature-the-fixture-lacks")
                .hasMessageContaining("refreshFeatureModelFixture").hasMessageContaining("draft");
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
        assertThat(bundle.catalog().catalogVersion()).isEqualTo("0.1.0+" + ARTEMIS_COMMIT.substring(0, 12));
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
    void draftOptionsNeverEnterTheRuntimeBundleOfASnapshot() throws Exception {
        Path draftWorkflow = writeDraftAugmentedFixtureWorkflow();
        Path draftExtractionRoot = workingDirectory.resolve("draft-extraction");
        FeatureExtractionInputs draftInputs = new FeatureExtractionInputs(FIXTURE_ROOT.resolve("mini-artemis"),
                FIXTURE_ROOT.resolve("mini-artemis-manifest.yml"), draftWorkflow,
                FIXTURE_ROOT.resolve("fixture-inputs").resolve("deployment-profile.json"),
                FIXTURE_ROOT.resolve("fixture-inputs").resolve("artemis-runtime-image.json"), draftExtractionRoot);
        new ScanStageService(objectMapper).run(draftInputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new ModelStageService(objectMapper).run(draftInputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new WorkflowStageService(objectMapper).run(draftInputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new PackageStageService(objectMapper).run(draftInputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        Path published = ExtractionArtifactLayout.forCommit(draftExtractionRoot, ARTEMIS_COMMIT).snapshotDirectory();
        Path draftDataRoot = workingDirectory.resolve("draft-data");
        Path draftSnapshot = Files.createDirectories(draftDataRoot.resolve("imported-models").resolve(snapshotId));
        try (var files = Files.list(published)) {
            for (Path file : files.toList()) {
                Files.copy(file, draftSnapshot.resolve(file.getFileName()));
            }
        }
        // The published snapshot still carries the draft option; only the served runtime bundle omits it.
        assertThat(Files.readString(draftSnapshot.resolve("guided-workflow.json"))).contains("enable-fixture-draft");

        RuntimeFeatureModelBundle bundle = loader(
                new SnapshotProperties(FeatureModelSourceMode.SNAPSHOT, draftDataRoot.toString(), snapshotId, false)).load();

        assertThat(bundle.workflow().steps().stream().flatMap(step -> step.decisions().stream()).flatMap(decision -> decision.options().stream())
                .map(GuidedDecisionOption::id)).containsExactly("enable-alpha");
    }

    @Test
    void prePhaseSnapshotWithoutStatusFieldsLoadsAndServesItsOptions() throws Exception {
        ObjectNode workflow = (ObjectNode) objectMapper.readTree(Files.readAllBytes(FIXTURE_ROOT.resolve("fixture-inputs").resolve("guided-workflow.json")));
        ObjectNode step = (ObjectNode) workflow.withArrayProperty("steps").get(0);
        ObjectNode decision = (ObjectNode) step.withArrayProperty("decisions").get(0);
        ((ObjectNode) decision.withArrayProperty("options").get(0)).remove("status");
        Path statusFreeWorkflow = workingDirectory.resolve("status-free-guided-workflow.json");
        Files.writeString(statusFreeWorkflow, objectMapper.writeValueAsString(workflow));
        Path extractionRoot = workingDirectory.resolve("status-free-extraction");
        FeatureExtractionInputs statusFreeInputs = new FeatureExtractionInputs(FIXTURE_ROOT.resolve("mini-artemis"),
                FIXTURE_ROOT.resolve("mini-artemis-manifest.yml"), statusFreeWorkflow,
                FIXTURE_ROOT.resolve("fixture-inputs").resolve("deployment-profile.json"),
                FIXTURE_ROOT.resolve("fixture-inputs").resolve("artemis-runtime-image.json"), extractionRoot);
        new ScanStageService(objectMapper).run(statusFreeInputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new ModelStageService(objectMapper).run(statusFreeInputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new WorkflowStageService(objectMapper).run(statusFreeInputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        new PackageStageService(objectMapper).run(statusFreeInputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, ARTEMIS_COMMIT));
        Path published = ExtractionArtifactLayout.forCommit(extractionRoot, ARTEMIS_COMMIT).snapshotDirectory();
        Path dataRoot = workingDirectory.resolve("status-free-data");
        Path snapshot = Files.createDirectories(dataRoot.resolve("imported-models").resolve(snapshotId));
        try (var files = Files.list(published)) {
            for (Path file : files.toList()) {
                Files.copy(file, snapshot.resolve(file.getFileName()));
            }
        }

        RuntimeFeatureModelBundle bundle = loader(new SnapshotProperties(FeatureModelSourceMode.SNAPSHOT, dataRoot.toString(), snapshotId, false)).load();

        // The status-free option is treated as published and stays served, so pre-phase payloads keep working.
        assertThat(bundle.workflow().steps().stream().flatMap(workflowStep -> workflowStep.decisions().stream())
                .flatMap(workflowDecision -> workflowDecision.options().stream()).map(GuidedDecisionOption::id)).containsExactly("enable-alpha");
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

    /**
     * Writes a copy of the fixture workflow with one additional complete draft option, so the extraction pipeline
     * publishes a snapshot containing a draft.
     *
     * @return path of the augmented authored workflow.
     */
    private Path writeDraftAugmentedFixtureWorkflow() throws Exception {
        ObjectNode workflow = (ObjectNode) objectMapper.readTree(Files.readAllBytes(FIXTURE_ROOT.resolve("fixture-inputs").resolve("guided-workflow.json")));
        ObjectNode step = (ObjectNode) workflow.withArrayProperty("steps").get(0);
        ObjectNode decision = (ObjectNode) step.withArrayProperty("decisions").get(0);
        ArrayNode options = decision.withArrayProperty("options");
        ObjectNode draft = objectMapper.createObjectNode();
        draft.put("id", "enable-fixture-draft");
        draft.put("status", "draft");
        draft.put("label", "Fixture Draft");
        draft.put("description", "Complete draft description.");
        draft.withArrayProperty("selects").add("alpha-feature");
        draft.withArrayProperty("enabledOutcome").add("Outcome.");
        draft.withArrayProperty("recommendedWhen").add("Fits.");
        draft.withArrayProperty("thingsToKnow").add("Notes.");
        options.add(draft);
        Path augmented = workingDirectory.resolve("draft-guided-workflow.json");
        Files.writeString(augmented, objectMapper.writeValueAsString(workflow));
        return augmented;
    }

    private SnapshotProperties snapshotProperties(String id) {
        return new SnapshotProperties(FeatureModelSourceMode.SNAPSHOT, workingDirectory.resolve("data").toString(), id, false);
    }

    private RuntimeFeatureModelBundleLoader loader(SnapshotProperties properties) {
        return new RuntimeFeatureModelBundleLoader(properties, resourceLoader, objectMapper);
    }
}
