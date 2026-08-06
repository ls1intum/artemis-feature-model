package de.tum.cit.aet.artemis.featuremodel.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.FeatureModelSourceMode;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundle;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundleLoader;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentModes;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.CapabilityResolutionService;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.FeatureAvailabilityDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.OptionAvailabilityDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.WorkflowAvailabilityDTO;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelectionMetadata;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.export.service.ActiveProfilesDeriver;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactGenerationService;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtemisRuntimeProperties;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtemisRuntimeSourceResolver;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactMappingResolver;
import de.tum.cit.aet.artemis.featuremodel.export.service.DeploymentPackageService;
import de.tum.cit.aet.artemis.featuremodel.export.service.DevIdeTemplateWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.EnvExampleWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.ProfileParameterResolver;
import de.tum.cit.aet.artemis.featuremodel.export.service.RuntimeScriptWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.RuntimeStackWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.RemoteImageStackWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.RuntimeTemplateWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.StaticConfigValidationService;
import de.tum.cit.aet.artemis.featuremodel.export.service.TechnicalSelectionResolver;
import de.tum.cit.aet.artemis.featuremodel.export.service.YamlOverlayWriter;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GuidedWorkflowValidationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotValidationResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.FeatureModelSnapshotValidator;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ModelStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.PackageStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ScanStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.WorkflowStageService;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecision;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;
import de.tum.cit.aet.artemis.featuremodel.selection.repository.JsonGuidedWorkflowStore;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowAssembler;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowDiagnosticsService;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowService;
import de.tum.cit.aet.artemis.featuremodel.validation.dto.ValidationRequest;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

/**
 * Opt-in end-to-end proof on a real Artemis checkout: run the full extraction and generation pipeline, validate the
 * complete snapshot through the Stage 2 offline trust boundary, then spot-check API-level parity between the curated
 * and generated payloads on the curated intersection. Until Stage 3 provides the production v2 runtime loader, a
 * test-only legacy runtime fixture exposes the already validated model and workflow to existing application services.
 * Enabled only when the {@code artemisPath} system property is set; skipped silently otherwise.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "artemisPath", matches = ".+")
class GeneratedModelImportParityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

    @TempDir
    static Path workingDirectory;

    private ModelStageService.Summary modelSummary;

    private WorkflowStageService.Summary workflowSummary;

    private String snapshotId;

    private Path dataRoot;

    @BeforeAll
    void runPipelineAndValidateSnapshot() throws Exception {
        FeatureExtractionInputs inputs = new FeatureExtractionInputs(Path.of(System.getProperty("artemisPath")),
                Path.of("src/main/resources/feature-model/extraction/artemis-feature-manifest.yml"),
                Path.of("src/main/resources/feature-model/guided-workflow.json"),
                Path.of("src/main/resources/deployment-profiles/default-artemis-profile.json"),
                workingDirectory.resolve("extraction"));
        LocalArtemisSourceRepository source = new LocalArtemisSourceRepository(inputs.requireArtemisCheckout());

        new ScanStageService(objectMapper).run(inputs, LocalArtemisSourceRepository::new);
        modelSummary = new ModelStageService(objectMapper).run(inputs);
        workflowSummary = new WorkflowStageService(objectMapper).run(inputs);
        new PackageStageService(objectMapper).run(inputs);

        ExtractionArtifactLayout layout = ExtractionArtifactLayout.forCommit(inputs.outputRoot(), source.commit());
        SnapshotValidationResult validation = new FeatureModelSnapshotValidator(objectMapper).validate(layout.snapshotDirectory());
        snapshotId = validation.snapshotId();
        dataRoot = workingDirectory.resolve("data");
        prepareRuntimeFixture(layout.snapshotDirectory());
    }

    /**
     * Copies the complete validated snapshot into the runtime repository layout.
     *
     * @param validatedSnapshot complete v2 snapshot that already passed offline validation.
     * @throws Exception if the test fixture cannot be created.
     */
    private void prepareRuntimeFixture(Path validatedSnapshot) throws Exception {
        Path runtimeDirectory = Files.createDirectories(dataRoot.resolve("imported-models").resolve(snapshotId));
        try (var files = Files.list(validatedSnapshot)) {
            for (Path file : files.toList()) {
                Files.copy(file, runtimeDirectory.resolve(file.getFileName()));
            }
        }
    }

    @Test
    void generationValidatesCleanly() {
        assertThat(modelSummary.modelIntegrityValid()).isTrue();
        assertThat(workflowSummary.workflowIntegrityValid()).isTrue();
        assertThat(workflowSummary.validationStatus()).isEqualTo(GuidedWorkflowValidationReport.STATUS_PASS);
    }

    @Test
    void importedSnapshotServesEveryCuratedFeatureWithMatchingDefaults() {
        FeatureModelCatalogService curatedStack = catalogService(SnapshotProperties.classpathFallback());
        FeatureModelCatalogService generatedStack = catalogService(snapshotProperties());

        FeatureModel curated = curatedStack.loadActiveModel();
        FeatureModel generated = generatedStack.loadActiveModel();
        assertThat(generated.model().id()).isEqualTo("artemis-generated-feature-model");

        Set<String> curatedIds = curated.features().stream().map(FeatureNode::id).collect(Collectors.toSet());
        Set<String> generatedIds = generated.features().stream().map(FeatureNode::id).collect(Collectors.toSet());
        assertThat(generatedIds).containsAll(curatedIds);

        List<String> curatedDefaults = curatedStack.defaultSelectedFeatureIds(curated);
        List<String> generatedDefaults = generatedStack.defaultSelectedFeatureIds(generated);
        assertThat(generatedDefaults.stream().filter(curatedIds::contains).toList()).containsExactlyElementsOf(curatedDefaults);
        assertThat(generatedDefaults).contains("localvc", "mysql", "integrated-code-lifecycle");
    }

    @Test
    void importedSnapshotValidatesItsOwnDefaultSelection() {
        FeatureModelCatalogService generatedStack = catalogService(snapshotProperties());
        FeatureModelValidationService validationService = new FeatureModelValidationService(generatedStack, new FeatureModelTreeService());

        FeatureModel generated = generatedStack.loadActiveModel();
        var result = validationService.validateSelection(new ValidationRequest(generatedStack.defaultSelectedFeatureIds(generated)));

        assertThat(result.valid()).as("the generated default selection satisfies mandatory and xor constraints").isTrue();
    }

    @Test
    void importedSnapshotServesTheGuidedWorkflowWithIdenticalDerivedWiring() {
        GuidedWorkflow curatedServed = guidedWorkflowService(SnapshotProperties.classpathFallback()).getActiveGuidedWorkflow();
        GuidedWorkflow generatedServed = guidedWorkflowService(snapshotProperties()).getActiveGuidedWorkflow();

        assertThat(generatedServed.workflow().featureModelId()).isEqualTo("artemis-generated-feature-model");
        Map<String, GuidedDecisionOption> curatedOptions = optionsById(curatedServed);
        Map<String, GuidedDecisionOption> generatedOptions = optionsById(generatedServed);
        assertThat(generatedOptions.keySet()).isEqualTo(curatedOptions.keySet());
        curatedOptions.forEach((optionId, curatedOption) -> {
            GuidedDecisionOption generatedOption = generatedOptions.get(optionId);
            assertThat(generatedOption.requiresCapabilities()).as("capabilities of %s", optionId).isEqualTo(curatedOption.requiresCapabilities());
            assertThat(generatedOption.artifactImpacts()).as("impacts of %s", optionId).isEqualTo(curatedOption.artifactImpacts());
        });
        assertThat(generatedServed.finalReviewGroups()).usingRecursiveComparison().isEqualTo(curatedServed.finalReviewGroups());
    }

    @Test
    void importedSnapshotKeepsTechnicalFeaturesOutOfTheTeacherSurface() {
        WorkflowAvailabilityDTO curatedAvailability = capabilityResolutionService(SnapshotProperties.classpathFallback()).resolveAvailability(null);
        WorkflowAvailabilityDTO generatedAvailability = capabilityResolutionService(snapshotProperties())
                .resolveAvailability(null);

        Map<String, Boolean> curatedOptionAvailability = curatedAvailability.options().stream()
                .collect(Collectors.toMap(OptionAvailabilityDTO::optionId, OptionAvailabilityDTO::available, (first, second) -> first, LinkedHashMap::new));
        Map<String, Boolean> generatedOptionAvailability = generatedAvailability.options().stream()
                .collect(Collectors.toMap(OptionAvailabilityDTO::optionId, OptionAvailabilityDTO::available, (first, second) -> first, LinkedHashMap::new));
        assertThat(generatedOptionAvailability).isEqualTo(curatedOptionAvailability);

        for (String technicalFeatureId : List.of("database", "mysql", "postgresql", "ci-provider", "integrated-code-lifecycle", "jenkins", "localvc")) {
            FeatureAvailabilityDTO availability = generatedAvailability.features().stream()
                    .filter(feature -> feature.featureId().equals(technicalFeatureId)).findFirst().orElseThrow();
            assertThat(availability.available()).as("technical feature %s is not teacher-available", technicalFeatureId).isFalse();
        }
    }

    @Test
    void deploymentPackagesConsumeAllTechnicalCombinations() {
        SnapshotProperties properties = snapshotProperties();
        FeatureModelCatalogService generatedCatalog = catalogService(properties);
        FeatureModel generatedModel = generatedCatalog.loadActiveModel();
        List<String> defaultSelection = generatedCatalog.defaultSelectedFeatureIds(generatedModel);
        DeploymentPackageService packageService = deploymentPackageService(properties, new TechnicalSelectionResolver());

        for (String databaseId : List.of("mysql", "postgresql")) {
            for (String ciProviderId : List.of("integrated-code-lifecycle", "jenkins")) {
                List<String> selection = technicalSelection(defaultSelection, databaseId, ciProviderId);
                for (String deploymentMode : List.of(DeploymentModes.LOCAL_DOCKER, DeploymentModes.DEV_IDE)) {
                    GeneratedArtifactPackage result = packageService.generate(packageRequest(selection, deploymentMode));
                    assertTechnicalPackage(result, deploymentMode, databaseId, ciProviderId);
                }
            }
        }
    }

    private FeatureModelCatalogService catalogService(SnapshotProperties properties) {
        JsonFeatureModelStore store = new JsonFeatureModelStore(runtimeBundle(properties));
        return new FeatureModelCatalogService(store, new FeatureModelIntegrityService(), new FeatureModelTreeService());
    }

    private GuidedWorkflowService guidedWorkflowService(SnapshotProperties properties) {
        JsonGuidedWorkflowStore workflowStore = new JsonGuidedWorkflowStore(runtimeBundle(properties));
        return new GuidedWorkflowService(workflowStore, catalogService(properties), new GuidedWorkflowIntegrityService(), new GuidedWorkflowAssembler(),
                new GuidedWorkflowDiagnosticsService());
    }

    private CapabilityResolutionService capabilityResolutionService(SnapshotProperties properties) {
        DeploymentProfileRepository profileRepository = new DeploymentProfileRepository(new SnapshotProperties(workingDirectory.toString(), null), objectMapper);
        return new CapabilityResolutionService(catalogService(properties), guidedWorkflowService(properties), new DeploymentProfileService(profileRepository),
                new GuidedWorkflowDiagnosticsService());
    }

    private DeploymentPackageService deploymentPackageService(SnapshotProperties properties, TechnicalSelectionResolver technicalSelectionResolver) {
        FeatureModelCatalogService catalogService = catalogService(properties);
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        FeatureModelValidationService validationService = new FeatureModelValidationService(catalogService, treeService);
        DeploymentProfileRepository profileRepository = new DeploymentProfileRepository(new SnapshotProperties(workingDirectory.toString(), null), objectMapper);
        DeploymentProfileService profileService = new DeploymentProfileService(profileRepository);
        ArtifactGenerationService artifactService = new ArtifactGenerationService(catalogService, validationService, profileService,
                new ArtifactMappingResolver(new ProfileParameterResolver()), new YamlOverlayWriter(), new EnvExampleWriter(), objectMapper);
        return new DeploymentPackageService(artifactService, catalogService, profileService, technicalSelectionResolver,
                new StaticConfigValidationService(runtimeBundle(properties)), new RuntimeTemplateWriter(), new RuntimeStackWriter(),
                new RemoteImageStackWriter(), new RuntimeScriptWriter(), new ActiveProfilesDeriver(), new DevIdeTemplateWriter(), new EnvExampleWriter(),
                new ArtemisRuntimeSourceResolver(runtimeBundle(properties),
                        new ArtemisRuntimeProperties("b1e27eeaaa03e4b41d72cbfe7f503e648dd544a6", "latest")), objectMapper);
    }

    private SnapshotProperties snapshotProperties() {
        return new SnapshotProperties(FeatureModelSourceMode.SNAPSHOT, dataRoot.toString(), snapshotId, false);
    }

    private RuntimeFeatureModelBundle runtimeBundle(SnapshotProperties properties) {
        return new RuntimeFeatureModelBundleLoader(properties, resourceLoader, objectMapper).load();
    }

    private ArtifactGenerationRequest packageRequest(List<String> selectedFeatureIds, String deploymentMode) {
        if (DeploymentModes.LOCAL_DOCKER.equals(deploymentMode)) {
            return new ArtifactGenerationRequest(selectedFeatureIds, null, null);
        }
        return new ArtifactGenerationRequest(selectedFeatureIds, null, null, deploymentMode);
    }

    private List<String> technicalSelection(List<String> defaultSelection, String databaseId, String ciProviderId) {
        List<String> selection = new ArrayList<>(defaultSelection);
        selection.removeAll(List.of("mysql", "postgresql", "integrated-code-lifecycle", "jenkins"));
        selection.add(databaseId);
        selection.add(ciProviderId);
        return List.copyOf(selection);
    }

    private void assertTechnicalPackage(GeneratedArtifactPackage result, String deploymentMode, String databaseId,
            String ciProviderId) {
        TechnicalSelectionMetadata metadata = result.report().technicalSelection();
        String databaseComposeFile = "postgresql".equals(databaseId) ? "docker/postgres.yml" : "docker/mysql.yml";
        assertThat(metadata.databaseId()).isEqualTo(databaseId);
        assertThat(metadata.databaseComposeFile()).isEqualTo(databaseComposeFile);
        assertThat(metadata.ciProviderId()).isEqualTo(ciProviderId);
        String databaseDisposition = DeploymentModes.DEV_IDE.equals(deploymentMode)
                ? TechnicalSelectionMetadata.DISPOSITION_NOT_APPLICABLE_DEV_IDE
                : TechnicalSelectionMetadata.DISPOSITION_APPLIED;
        assertThat(metadata.databaseDisposition()).isEqualTo(databaseDisposition);
        assertThat(metadata.ciProviderDisposition()).isEqualTo(TechnicalSelectionMetadata.DISPOSITION_APPLIED);
        assertThat(fileContent(result, "metadata/static-config-validation.json"))
                .contains("\"overallStatus\" : \"PASS\"");

        if (DeploymentModes.DEV_IDE.equals(deploymentMode)) {
            assertDevIdeProfiles(result, ciProviderId);
            return;
        }
        assertLocalDockerReferences(result, databaseComposeFile);
    }

    private void assertDevIdeProfiles(GeneratedArtifactPackage result, String ciProviderId) {
        String expectedProfiles = "jenkins".equals(ciProviderId)
                ? "jenkins,localvc,artemis,scheduling,core,dev,feature-model,feature-model-demo,local"
                : "artemis,localci,localvc,scheduling,buildagent,core,dev,feature-model,feature-model-demo,local";
        String runConfiguration = fileContent(result, "intellij/runConfigurations/Artemis_Server__Feature_Model_Selection_.xml");
        assertThat(runConfiguration).contains("ACTIVE_PROFILES\" value=\"" + expectedProfiles + "\"");
    }

    private void assertLocalDockerReferences(GeneratedArtifactPackage result, String databaseComposeFile) {
        String stack = fileContent(result, "deployment/local-repo/artemis-feature-model-stack.yml");
        Path artemisCheckout = Path.of(System.getProperty("artemisPath"));
        assertThat(Files.isRegularFile(artemisCheckout.resolve("docker/artemis.yml"))).isTrue();
        assertThat(Files.isRegularFile(artemisCheckout.resolve(databaseComposeFile))).isTrue();
        assertThat(stack).contains("${FM_ARTEMIS_REPO}/docker/artemis.yml");
        assertThat(stack).contains("${FM_ARTEMIS_REPO}/" + databaseComposeFile);
    }

    private String fileContent(GeneratedArtifactPackage generatedPackage, String path) {
        for (GeneratedArtifactFile file : generatedPackage.files()) {
            if (path.equals(file.path())) {
                return file.content();
            }
        }
        throw new IllegalArgumentException("Missing generated file " + path);
    }

    private Map<String, GuidedDecisionOption> optionsById(GuidedWorkflow workflow) {
        Map<String, GuidedDecisionOption> optionsById = new LinkedHashMap<>();
        for (GuidedWorkflowStep step : workflow.steps()) {
            for (GuidedDecision decision : step.decisions()) {
                for (GuidedDecisionOption option : decision.options()) {
                    optionsById.putIfAbsent(option.id(), option);
                }
            }
        }
        return optionsById;
    }

    private <T> T readResource(String location, Class<T> type) throws Exception {
        try (InputStream inputStream = resourceLoader.getResource(location).getInputStream()) {
            return objectMapper.readValue(inputStream, type);
        }
    }

}
