package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;
import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentPackageManifest;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RuntimeCheck;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RuntimeChecksReport;
import de.tum.cit.aet.artemis.featuremodel.export.domain.StaticConfigValidationReport;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class DeploymentPackageServiceTest {

    private static final List<String> MINIMAL_SELECTION = List.of("course-workflow", "communication", "exercise-common", "programming", "quiz");

    @TempDir
    Path dataRoot;

    private DeploymentPackageService service;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        objectMapper = new ObjectMapper();
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        JsonFeatureModelStore store = new JsonFeatureModelStore(resourceLoader, objectMapper);
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(store, new FeatureModelIntegrityService(), treeService);
        FeatureModelValidationService validationService = new FeatureModelValidationService(catalogService, treeService);
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), objectMapper);
        DeploymentProfileService profileService = new DeploymentProfileService(repository);
        ArtifactMappingResolver mappingResolver = new ArtifactMappingResolver(new ProfileParameterResolver());
        ArtifactGenerationService artifactGenerationService = new ArtifactGenerationService(catalogService, validationService, profileService, mappingResolver,
                new YamlOverlayWriter(), new EnvExampleWriter(), objectMapper);
        service = new DeploymentPackageService(artifactGenerationService, catalogService, profileService, new TechnicalSelectionResolver(),
                new StaticConfigValidationService(resourceLoader, objectMapper), new RuntimeTemplateWriter(), new RuntimeStackWriter(),
                new RuntimeScriptWriter(), new ActiveProfilesDeriver(), new DevIdeTemplateWriter(), new EnvExampleWriter(), objectMapper);
    }

    @Test
    void includesAllPhase5AndPhase6FilesInOrder() {
        GeneratedArtifactPackage result = service.generate(request(MINIMAL_SELECTION, null));

        assertThat(result.files()).extracting("path").containsExactly("README.md", "config/application-feature-model.yml", "env/.env.example", "env/.env.demo",
                "env/README.md", "metadata/selected-features.json", "metadata/deployment-profile-summary.json", "metadata/generation-report.json",
                "metadata/package-manifest.json", "metadata/runtime-checks.json", "metadata/static-config-validation.json",
                "deployment/local-repo/docker-compose.override.example.yml", "deployment/local-repo/README.md", "scripts/prepare-env.sh", "scripts/start-demo.sh",
                "scripts/validate-package.sh", "scripts/start-local-repo.sh", "scripts/stop-local-repo.sh", "scripts/print-runtime-summary.sh");
    }

    @Test
    void generatesAManifestForTheLocalRepoRuntimeMode() {
        GeneratedArtifactPackage result = service.generate(request(withExtra("iris", "athena"), null));

        DeploymentPackageManifest manifest = objectMapper.readValue(content(result, "metadata/package-manifest.json"), DeploymentPackageManifest.class);
        assertThat(manifest.packageType()).isEqualTo("local-runtime-deployment-package");
        assertThat(manifest.mode()).isEqualTo("DEMO");
        assertThat(manifest.supportedRuntimeModes()).containsExactly("local-repo");
        assertThat(manifest.readiness().productionReady()).isFalse();
        assertThat(manifest.readiness().localRuntimeReady()).isTrue();
        assertThat(manifest.generatedFiles()).hasSize(19);
        assertThat(manifest.requiredEnvironmentVariables()).contains("ARTEMIS_IRIS_SECRET_TOKEN", "ARTEMIS_ATHENA_SECRET");
        assertThat(manifest.artemisRuntime().verifiedAgainstArtemisCommit()).isEqualTo(RuntimePackageConstants.VERIFIED_ARTEMIS_COMMIT);
        assertThat(manifest.database().type()).isEqualTo("mysql");
        assertThat(manifest.database().mode()).isEqualTo("local-container");
    }

    @Test
    void generatesRuntimeChecksThatPassForAValidSelection() {
        GeneratedArtifactPackage result = service.generate(request(withExtra("iris", "athena"), null));

        RuntimeChecksReport checks = objectMapper.readValue(content(result, "metadata/runtime-checks.json"), RuntimeChecksReport.class);
        assertThat(checks.mode()).isEqualTo("DEMO");
        assertThat(checks.overallStatus()).isEqualTo("PASS");
        assertThat(checks.checks()).extracting(RuntimeCheck::id).contains("required-files-present", "overlay-no-env-leaks", "env-placeholders-declared",
                "static-config-keys", "no-plaintext-secrets", "placeholder-values-reported");
        assertThat(statusOf(checks, "overlay-no-env-leaks")).isEqualTo("PASS");
        assertThat(statusOf(checks, "no-plaintext-secrets")).isEqualTo("PASS");
        assertThat(statusOf(checks, "env-placeholders-declared")).isEqualTo("PASS");
        assertThat(statusOf(checks, "static-config-keys")).isEqualTo("PASS");
    }

    /**
     * CI gate for the static config validation (Workstream A1): the comprehensive reference selection must validate
     * as PASS, and the report is exported under {@code build/reports/static-config-validation/} so the CI workflow can
     * publish the machine-readable JSON as an artifact.
     */
    @Test
    void staticConfigValidationPassesForTheComprehensiveSelectionAndIsExportedForCi() throws IOException {
        List<String> comprehensiveSelection = withExtra("lecture", "tutorialgroup", "text", "modeling", "athena", "atlas", "iris", "hyperion", "theia", "apollon");
        GeneratedArtifactPackage result = service.generate(request(comprehensiveSelection, null));

        String reportJson = content(result, "metadata/static-config-validation.json");
        Path reportDir = Path.of("build", "reports", "static-config-validation");
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("static-config-validation.json"), reportJson);

        StaticConfigValidationReport report = objectMapper.readValue(reportJson, StaticConfigValidationReport.class);
        assertThat(report.overallStatus()).isEqualTo("PASS");
        assertThat(report.findings()).isEmpty();
        assertThat(report.checkedEntryCount()).isGreaterThan(15);
        assertThat(report.catalogVersion()).isEqualTo("1.0.0");
        assertThat(report.verifiedAgainstArtemisCommit()).isEqualTo(RuntimePackageConstants.VERIFIED_ARTEMIS_COMMIT);
    }

    @Test
    void generatesADemoEnvFileWithDummyValuesForRequiredVariables() {
        GeneratedArtifactPackage result = service.generate(request(withExtra("iris", "athena"), null));

        String envDemo = content(result, "env/.env.demo");
        assertThat(envDemo).contains("ARTEMIS_IRIS_SECRET_TOKEN=demo-change-me").contains("ARTEMIS_ATHENA_SECRET=demo-change-me").containsIgnoringCase("DEMO ONLY");
    }

    @Test
    void generatesALocalRepoComposeOverrideThatLayersTheOverlay() {
        GeneratedArtifactPackage result = service.generate(request(MINIMAL_SELECTION, null));

        String override = content(result, "deployment/local-repo/docker-compose.override.example.yml");
        assertThat(override).contains("artemis-app:").contains("/opt/artemis/config/application-feature-model.yml:ro")
                .contains("SPRING_CONFIG_ADDITIONAL_LOCATION").contains("${FM_OVERLAY_HOST_PATH}").contains("${FM_ENV_FILE}");
        // The CI-capable MySQL stack: pin the datasource to the mysql service and use isolated container names.
        assertThat(override).contains("SPRING_DATASOURCE_URL").contains("jdbc:mysql://mysql:3306").contains("mysql:")
                .contains("container_name: artemis-feature-model-local-app");
        assertThat(override).doesNotContain("postgres").doesNotContain("image:");
    }

    @Test
    void generatesHelperScriptsWithSafeDefaults() {
        GeneratedArtifactPackage result = service.generate(request(MINIMAL_SELECTION, null));

        for (String script : List.of("scripts/prepare-env.sh", "scripts/start-demo.sh", "scripts/validate-package.sh", "scripts/start-local-repo.sh",
                "scripts/stop-local-repo.sh", "scripts/print-runtime-summary.sh")) {
            assertThat(content(result, script)).startsWith("#!/usr/bin/env bash").contains("set -euo pipefail");
        }
        // The single-command DEMO entry point chains chmod, demo env preparation, and the local-repo start.
        String startDemo = content(result, "scripts/start-demo.sh");
        assertThat(startDemo).contains("chmod +x").contains("prepare-env.sh\" --demo").contains("start-local-repo.sh");
        assertThat(content(result, "README.md")).contains("bash scripts/start-demo.sh /path/to/Artemis");
        String startScript = content(result, "scripts/start-local-repo.sh");
        assertThat(startScript).contains("docker compose").contains("up -d");
        // The Artemis repo-root .env must be passed for Compose interpolation (e.g. POSTGRES_VERSION), otherwise the
        // Artemis stack resolves an empty postgres image tag and fails with "invalid reference format".
        assertThat(startScript).contains("--env-file").contains("FM_ARTEMIS_ENV_FILE").contains("$ARTEMIS_REPO/.env");
        // stop keeps volumes by default: down --volumes only appears in the explicit branch, never unconditionally.
        assertThat(content(result, "scripts/stop-local-repo.sh")).contains("down").contains("--volumes");
        assertThat(content(result, "scripts/prepare-env.sh")).contains("already exists");
    }

    @Test
    void keepsHelperScriptsInSyncWithRuntimeConstants() {
        GeneratedArtifactPackage result = service.generate(request(MINIMAL_SELECTION, null));

        String override = content(result, "deployment/local-repo/docker-compose.override.example.yml");
        String start = content(result, "scripts/start-local-repo.sh");
        String stop = content(result, "scripts/stop-local-repo.sh");
        assertThat(override).contains(RuntimePackageConstants.OVERLAY_HOST_PATH_ENV).contains(RuntimePackageConstants.ENV_FILE_ENV)
                .contains(RuntimePackageConstants.CONTAINER_OVERLAY_PATH).contains(RuntimePackageConstants.SPRING_CONFIG_ENV)
                .contains(RuntimePackageConstants.DATASOURCE_URL).contains(RuntimePackageConstants.CONTAINER_APP_NAME).contains(RuntimePackageConstants.CONTAINER_DB_NAME);
        assertThat(start).contains(RuntimePackageConstants.COMPOSE_PROJECT_NAME).contains(RuntimePackageConstants.OVERLAY_HOST_PATH_ENV)
                .contains(RuntimePackageConstants.DEFAULT_ARTEMIS_COMPOSE_FILE).contains(RuntimePackageConstants.ARTEMIS_COMPOSE_ENV);
        assertThat(stop).contains(RuntimePackageConstants.COMPOSE_PROJECT_NAME);
    }

    @Test
    void neverLeaksPlaintextSecretsIntoAnyPackageFile() {
        GeneratedArtifactPackage result = service.generate(request(withExtra("iris", "athena", "hyperion"), null));

        for (var file : result.files()) {
            assertThat(file.content()).doesNotContain("env:ARTEMIS").doesNotContain("env:SPRING");
        }
        assertThat(content(result, "config/application-feature-model.yml")).doesNotContain("env:");
    }

    @Test
    void omitsTheDeploymentModeFromTheManifestForADefaultRequest() {
        GeneratedArtifactPackage result = service.generate(request(MINIMAL_SELECTION, null));

        assertThat(content(result, "metadata/package-manifest.json")).doesNotContain("deploymentMode");
    }

    @Test
    void recordsAnExplicitlyChosenDeploymentModeInTheManifest() {
        GeneratedArtifactPackage result = service.generate(new ArtifactGenerationRequest(MINIMAL_SELECTION, null, null, "local-docker"));

        DeploymentPackageManifest manifest = objectMapper.readValue(content(result, "metadata/package-manifest.json"), DeploymentPackageManifest.class);
        assertThat(manifest.deploymentMode()).isEqualTo("local-docker");
        assertThat(manifest.supportedRuntimeModes()).containsExactly("local-repo");
    }

    @Test
    void composesTheDevIdePackageWithoutComposeFilesOrRuntimeScripts() {
        GeneratedArtifactPackage result = service.generate(new ArtifactGenerationRequest(withExtra("iris", "hyperion"), null, null, "dev-ide"));

        assertThat(result.files()).extracting("path").containsExactly("README.md", "config/application-feature-model.yml",
                "config/application-feature-model-demo.yml", "env/.env.example",
                "intellij/runConfigurations/Artemis_Server__Feature_Model_Selection_.xml", "metadata/selected-features.json",
                "metadata/deployment-profile-summary.json", "metadata/generation-report.json", "metadata/package-manifest.json",
                "metadata/static-config-validation.json");
        String runConfiguration = content(result, "intellij/runConfigurations/Artemis_Server__Feature_Model_Selection_.xml");
        assertThat(runConfiguration)
                .contains("<option name=\"ACTIVE_PROFILES\" value=\"artemis,localci,localvc,scheduling,buildagent,core,dev,feature-model,feature-model-demo,local\" />");
        assertThat(content(result, "README.md")).contains("application-local.yml").contains(".idea/runConfigurations/");
        // The demo defaults cover every ${VARIABLE} the overlay references, so a DEMO run resolves all placeholders.
        assertThat(content(result, "config/application-feature-model-demo.yml")).contains("ARTEMIS_IRIS_SECRET_TOKEN: demo-change-me");
    }

    @Test
    void recordsTheConfigurationOnlyNatureInTheDevIdeManifest() {
        GeneratedArtifactPackage result = service.generate(new ArtifactGenerationRequest(MINIMAL_SELECTION, null, null, "dev-ide"));

        DeploymentPackageManifest manifest = objectMapper.readValue(content(result, "metadata/package-manifest.json"), DeploymentPackageManifest.class);
        assertThat(manifest.packageType()).isEqualTo("dev-ide-configuration-package");
        assertThat(manifest.deploymentMode()).isEqualTo("dev-ide");
        assertThat(manifest.supportedRuntimeModes()).isEmpty();
        assertThat(manifest.database()).isNull();
        assertThat(manifest.readiness().localRuntimeReady()).isFalse();
        assertThat(manifest.readiness().productionReady()).isFalse();
        assertThat(manifest.generatedFiles()).hasSize(10);
    }

    @Test
    void omitsTechnicalSelectionMetadataForTheCuratedModelInBothModes() {
        GeneratedArtifactPackage localDocker = service.generate(request(MINIMAL_SELECTION, null));
        GeneratedArtifactPackage devIde = service.generate(new ArtifactGenerationRequest(MINIMAL_SELECTION, null, null, "dev-ide"));

        for (GeneratedArtifactPackage result : List.of(localDocker, devIde)) {
            assertThat(result.report().technicalSelection()).isNull();
            assertThat(content(result, ArtifactGenerationService.REPORT_FILE)).doesNotContain("\"technicalSelection\"");
            assertThat(content(result, DeploymentPackageService.MANIFEST_FILE)).doesNotContain("\"technicalSelection\"");
        }
    }

    @Test
    void neverLeaksPlaintextSecretsIntoTheDevIdePackage() {
        GeneratedArtifactPackage result = service.generate(new ArtifactGenerationRequest(withExtra("iris", "athena", "hyperion"), null, null, "dev-ide"));

        for (var file : result.files()) {
            assertThat(file.content()).doesNotContain("env:ARTEMIS").doesNotContain("env:SPRING");
        }
        assertThat(content(result, "config/application-feature-model.yml")).doesNotContain("env:");
    }

    @Test
    void rejectsAnUnknownDeploymentMode() {
        assertThatThrownBy(() -> service.generate(new ArtifactGenerationRequest(MINIMAL_SELECTION, null, null, "cloud-magic")))
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("Unknown deployment mode");
    }

    @Test
    void rejectsADeploymentModeTheActiveProfileDoesNotSupport() throws IOException {
        Path profileDirectory = dataRoot.resolve("deployment-profiles");
        Files.createDirectories(profileDirectory);
        Files.writeString(profileDirectory.resolve("restricted-profile.json"),
                "{\"id\":\"restricted-profile\",\"name\":\"Restricted\",\"version\":\"1.0.0\",\"status\":\"published\",\"supportedDeploymentModes\":[]}");

        assertThatThrownBy(() -> service.generate(new ArtifactGenerationRequest(MINIMAL_SELECTION, "restricted-profile", null, "local-docker")))
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("not supported");
    }

    @Test
    void rejectsAnInvalidSelection() {
        assertThatThrownBy(() -> service.generate(request(List.of("iris"), null))).isInstanceOf(ArtifactGenerationException.class);
    }

    @Test
    void rejectsAnUnknownProfile() {
        assertThatThrownBy(() -> service.generate(request(MINIMAL_SELECTION, "missing-profile"))).isInstanceOf(DeploymentProfileException.class);
    }

    private String statusOf(RuntimeChecksReport checks, String id) {
        return checks.checks().stream().filter(check -> id.equals(check.id())).findFirst().orElseThrow().status();
    }

    private List<String> withExtra(String... extra) {
        return Stream.concat(MINIMAL_SELECTION.stream(), Arrays.stream(extra)).toList();
    }

    private ArtifactGenerationRequest request(List<String> selectedFeatureIds, String profileId) {
        return new ArtifactGenerationRequest(selectedFeatureIds, profileId, null);
    }

    private String content(GeneratedArtifactPackage result, String path) {
        return result.files().stream().filter(file -> file.path().equals(path)).findFirst().orElseThrow().content();
    }
}
