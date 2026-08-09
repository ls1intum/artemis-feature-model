package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.TestFeatureModels;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMappingSource;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundleLoader;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentModes;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;
import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentPackageManifest;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RuntimeCheck;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelectionMetadata;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class DeploymentPackageTechnicalSelectionTest {

    private static final List<String> TECHNICAL_SELECTION = List.of("exercise-common", "programming", "quiz", "postgresql",
            "integrated-code-lifecycle", "localvc");

    private static final String ICL_IDE_PROFILES = "artemis,localci,localvc,scheduling,buildagent,core,dev,feature-model,feature-model-demo,local";

    private static final String JENKINS_IDE_PROFILES = "jenkins,localvc,artemis,scheduling,core,dev,feature-model,feature-model-demo,local";

    @TempDir
    Path dataRoot;

    private ObjectMapper objectMapper;

    private DeploymentPackageService service;

    @BeforeEach
    void setUp() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        objectMapper = new ObjectMapper();
        FeatureModel model = technicalModel();
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(() -> model, new FeatureModelIntegrityService(), treeService);
        FeatureModelValidationService validationService = new FeatureModelValidationService(catalogService, treeService);
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), objectMapper);
        DeploymentProfileService profileService = new DeploymentProfileService(repository);
        ArtifactGenerationService artifactService = new ArtifactGenerationService(catalogService, validationService, profileService,
                new ArtifactMappingResolver(ArtifactMappingResolverTest.classpathCatalog()), new YamlOverlayWriter(), new EnvExampleWriter(), objectMapper);
        service = new DeploymentPackageService(artifactService, catalogService, profileService, new TechnicalSelectionResolver(),
                new StaticConfigValidationService(resourceLoader, objectMapper), new RuntimeTemplateWriter(), new RuntimeStackWriter(),
                new RemoteImageStackWriter(), new RuntimeScriptWriter(), new ActiveProfilesDeriver(), new DevIdeTemplateWriter(), new EnvExampleWriter(),
                new ArtemisRuntimeSourceResolver(new RuntimeFeatureModelBundleLoader(SnapshotProperties.classpathFallback(), resourceLoader, objectMapper).load(),
                        new ArtemisRuntimeProperties("b1e27eeaaa03e4b41d72cbfe7f503e648dd544a6", "latest")), objectMapper);
    }

    @Test
    void appliesTechnicalSelectionToLocalDockerMetadata() {
        GeneratedArtifactPackage result = service.generate(new ArtifactGenerationRequest(TECHNICAL_SELECTION, null, null));

        assertTechnicalSelection(result, TechnicalSelectionMetadata.DISPOSITION_APPLIED);
        DeploymentPackageManifest manifest = manifest(result);
        assertTechnicalMetadata(manifest.technicalSelection(), TechnicalSelectionMetadata.DISPOSITION_APPLIED);
        assertThat(manifest.database().type()).isEqualTo("postgresql");
        assertThat(manifest.database().mode()).isEqualTo("local-container");
        assertThat(manifest.ciProvider().type()).isEqualTo("integrated-code-lifecycle");
    }

    @Test
    void recordsTheDeveloperManagedDatabaseAndAppliesCiInDevIdeMetadata() {
        ArtifactGenerationRequest request = new ArtifactGenerationRequest(TECHNICAL_SELECTION, null, null, DeploymentModes.DEV_IDE);

        GeneratedArtifactPackage result = service.generate(request);

        assertTechnicalSelection(result, TechnicalSelectionMetadata.DISPOSITION_NOT_APPLICABLE_DEV_IDE);
        DeploymentPackageManifest manifest = manifest(result);
        assertTechnicalMetadata(manifest.technicalSelection(), TechnicalSelectionMetadata.DISPOSITION_NOT_APPLICABLE_DEV_IDE);
        assertThat(manifest.database().type()).isEqualTo("postgresql");
        assertThat(manifest.database().mode()).isEqualTo("developer-managed");
        assertThat(manifest.ciProvider().type()).isEqualTo("integrated-code-lifecycle");
    }

    @ParameterizedTest
    @MethodSource("technicalCombinations")
    void composesEveryTechnicalCombinationInBothModes(TechnicalScenario scenario, String deploymentMode) {
        ArtifactGenerationRequest request = new ArtifactGenerationRequest(scenario.selection(), null, null, deploymentMode);

        GeneratedArtifactPackage result = service.generate(request);

        DeploymentPackageManifest manifest = manifest(result);
        assertThat(manifest.database().type()).isEqualTo(scenario.databaseId());
        assertThat(manifest.ciProvider().type()).isEqualTo(scenario.ciProviderId());
        if (DeploymentModes.DEV_IDE.equals(deploymentMode)) {
            assertDevIdeCombination(result, scenario);
        }
        else {
            assertLocalDockerCombination(result, scenario);
        }
    }

    private void assertTechnicalSelection(GeneratedArtifactPackage result, String databaseDisposition) {
        assertTechnicalMetadata(result.report().technicalSelection(), databaseDisposition);
        String reportJson = content(file(result, ArtifactGenerationService.REPORT_FILE));
        assertThat(reportJson).contains("\"technicalSelection\"", "\"" + databaseDisposition + "\"", "\"applied\"");
    }

    private void assertTechnicalMetadata(TechnicalSelectionMetadata metadata, String databaseDisposition) {
        assertThat(metadata.databaseId()).isEqualTo("postgresql");
        assertThat(metadata.databaseComposeFile()).isEqualTo("docker/postgres.yml");
        assertThat(metadata.databaseDisposition()).isEqualTo(databaseDisposition);
        assertThat(metadata.ciProviderId()).isEqualTo("integrated-code-lifecycle");
        assertThat(metadata.springProfileTokens()).containsExactly("localci", "buildagent", "localvc");
        assertThat(metadata.ciProviderDisposition()).isEqualTo(TechnicalSelectionMetadata.DISPOSITION_APPLIED);
    }

    private DeploymentPackageManifest manifest(GeneratedArtifactPackage result) {
        return objectMapper.readValue(content(file(result, DeploymentPackageService.MANIFEST_FILE)), DeploymentPackageManifest.class);
    }

    private GeneratedArtifactFile file(GeneratedArtifactPackage result, String path) {
        for (GeneratedArtifactFile file : result.files()) {
            if (path.equals(file.path())) {
                return file;
            }
        }
        throw new IllegalArgumentException("Missing generated file " + path);
    }

    private String content(GeneratedArtifactFile file) {
        return file.content();
    }

    private void assertDevIdeCombination(GeneratedArtifactPackage result, TechnicalScenario scenario) {
        assertThat(result.files()).noneMatch(file -> DeploymentPackageService.TECHNICAL_STACK_FILE.equals(file.path()));
        String runConfig = content(file(result, DeploymentPackageService.DEV_IDE_RUN_CONFIG_FILE));
        assertThat(runConfig).contains("ACTIVE_PROFILES\" value=\"");
        assertThat(runConfig).contains(scenario.ideProfiles());

        String readme = content(file(result, DeploymentPackageService.PACKAGE_README_FILE));
        assertThat(readme).contains("## Database choice", "## CI provider");
        if ("postgresql".equals(scenario.databaseId())) {
            assertThat(readme).contains("jdbc:postgresql://localhost:5432/Artemis?sslmode=disable", "password: \"\"");
        }
        else {
            assertThat(readme).contains("docker/mysql.yml", "MySQL.xml");
        }
        if ("jenkins".equals(scenario.ciProviderId())) {
            assertJenkinsOverlayAndDemoDefaults(result);
        }
        String staticValidation = content(file(result, DeploymentPackageService.STATIC_VALIDATION_FILE));
        assertThat(staticValidation).contains("\"overallStatus\" : \"PASS\"");
    }

    private void assertLocalDockerCombination(GeneratedArtifactPackage result, TechnicalScenario scenario) {
        String stack = content(file(result, DeploymentPackageService.TECHNICAL_STACK_FILE));
        String remoteStack = content(file(result, DeploymentPackageService.REMOTE_IMAGE_STACK_FILE));
        assertThat(stack).contains("${FM_ARTEMIS_REPO}/docker/artemis.yml");
        assertThat(stack).contains("${FM_ARTEMIS_REPO}/" + scenario.databaseComposeFile());
        assertThat(stack).contains("SPRING_PROFILES_ACTIVE: \"" + scenario.dockerProfiles() + "\"");
        assertThat(stack).contains("ARTEMIS_VERSIONCONTROL_URL: \"http://localhost:8080\"");
        assertThat(remoteStack).contains("image: \"ghcr.io/ls1intum/artemis:latest\"")
                .contains("SPRING_PROFILES_ACTIVE: \"" + scenario.dockerProfiles() + "\"")
                .doesNotContain("FM_ARTEMIS_REPO", "extends:");

        String readme = content(file(result, DeploymentPackageService.PACKAGE_README_FILE));
        assertDetailedDockerReadme(readme, scenario);

        if ("postgresql".equals(scenario.databaseId())) {
            assertThat(stack).contains("SPRING_DATASOURCE_USERNAME: \"Artemis\"");
            assertThat(stack).contains("jdbc:postgresql://artemis-feature-model-postgresql:5432/Artemis?sslmode=disable");
        }
        if ("integrated-code-lifecycle".equals(scenario.ciProviderId())) {
            assertThat(stack).contains("/var/run/docker.sock:/var/run/docker.sock", "group_add:",
                    "${FM_DOCKER_GID:-999}", "ARTEMIS_VERSIONCONTROL_URL: \"http://localhost:8080\"");
            String startScript = content(file(result, DeploymentPackageService.START_LOCAL_REPO_SCRIPT_FILE));
            assertThat(startScript).contains("FM_DOCKER_GID=0", "stat -Lc '%g' /var/run/docker.sock",
                    "export FM_DOCKER_GID");
            String envExample = content(file(result, ArtifactGenerationService.ENV_FILE));
            assertThat(envExample).doesNotContain(RuntimePackageConstants.VERSION_CONTROL_BUILD_AGENT_USERNAME_ENV,
                    RuntimePackageConstants.VERSION_CONTROL_BUILD_AGENT_PASSWORD_ENV);
            assertRuntimeCheckStatus(result, RuntimeCheck.STATUS_PASS);
        }
        else {
            assertThat(stack).doesNotContain("/var/run/docker.sock:/var/run/docker.sock", "group_add:");
            String envExample = content(file(result, ArtifactGenerationService.ENV_FILE));
            assertThat(envExample).contains(RuntimePackageConstants.VERSION_CONTROL_BUILD_AGENT_USERNAME_ENV + "=",
                    RuntimePackageConstants.VERSION_CONTROL_BUILD_AGENT_PASSWORD_ENV + "=");
            String envDemo = content(file(result, DeploymentPackageService.ENV_DEMO_FILE));
            assertThat(envDemo).contains(RuntimePackageConstants.VERSION_CONTROL_BUILD_AGENT_USERNAME_ENV + "=demo-change-me",
                    RuntimePackageConstants.VERSION_CONTROL_BUILD_AGENT_PASSWORD_ENV + "=demo-change-me");
            assertRuntimeCheckStatus(result, RuntimeCheck.STATUS_PASS);
            String checks = content(file(result, DeploymentPackageService.RUNTIME_CHECKS_FILE));
            assertThat(checks).contains("\"id\" : \"jenkins-stack-available\"", "\"overallStatus\" : \"FAIL\"");
            assertThat(result.report().warnings()).anyMatch(warning -> warning.message().contains("cannot DEMO-boot a Jenkins stack"));
            assertThat(result.report().environmentRequirements())
                    .anyMatch(requirement -> RuntimePackageConstants.VERSION_CONTROL_BUILD_AGENT_USERNAME_ENV.equals(requirement.name())
                            && "runtime-package".equals(requirement.source()))
                    .anyMatch(requirement -> RuntimePackageConstants.VERSION_CONTROL_BUILD_AGENT_PASSWORD_ENV.equals(requirement.name())
                            && requirement.secret());
            String reportJson = content(file(result, ArtifactGenerationService.REPORT_FILE));
            assertThat(reportJson).contains(RuntimePackageConstants.VERSION_CONTROL_BUILD_AGENT_USERNAME_ENV,
                    RuntimePackageConstants.VERSION_CONTROL_BUILD_AGENT_PASSWORD_ENV);
        }
    }

    private void assertDetailedDockerReadme(String readme, TechnicalScenario scenario) {
        assertThat(readme).contains("## Host support and Docker socket", "Linux with Docker Engine", "macOS with Docker Desktop",
                "WSL2 distribution",
                "Native PowerShell", "Command Prompt, Git Bash, and Windows containers are not supported",
                "Enhanced Container Isolation", "## Quick Start", "bash scripts/start-demo.sh /absolute/path/to/Artemis",
                "bash scripts/start-demo.sh", "## Runtime provenance", "latest", "not guaranteed", "./scripts/stop.sh");

        if ("jenkins".equals(scenario.ciProviderId())) {
            assertThat(readme).contains("Jenkins limitation", "no Jenkins service is included");
        }
        else {
            assertThat(readme).contains("Integrated Code Lifecycle mounts", "/var/run/docker.sock", "FM_DOCKER_GID");
        }
    }

    private void assertJenkinsOverlayAndDemoDefaults(GeneratedArtifactPackage result) {
        String overlay = content(file(result, ArtifactGenerationService.OVERLAY_FILE));
        assertThat(overlay).contains("continuous-integration:", "${ARTEMIS_CONTINUOUS_INTEGRATION_PASSWORD}",
                "${ARTEMIS_CONTINUOUS_INTEGRATION_ARTEMIS_AUTHENTICATION_TOKEN_VALUE}");
        String demoDefaults = content(file(result, DeploymentPackageService.DEV_IDE_DEMO_ENV_FILE));
        assertThat(demoDefaults).contains("ARTEMIS_CONTINUOUS_INTEGRATION_PASSWORD: demo-change-me",
                "ARTEMIS_CONTINUOUS_INTEGRATION_ARTEMIS_AUTHENTICATION_TOKEN_VALUE: demo-change-me");
    }

    private void assertRuntimeCheckStatus(GeneratedArtifactPackage result, String expectedStatus) {
        String checks = content(file(result, DeploymentPackageService.RUNTIME_CHECKS_FILE));
        assertThat(checks).contains("\"id\" : \"technical-selection-consistent\"");
        String expectedFragment = "\"status\" : \"" + expectedStatus + "\"";
        assertThat(checks).contains(expectedFragment);
    }

    private static Stream<Arguments> technicalCombinations() {
        List<String> functional = List.of("exercise-common", "programming", "quiz");
        TechnicalScenario mysqlIcl = scenario(functional, "mysql", "integrated-code-lifecycle");
        TechnicalScenario postgresIcl = scenario(functional, "postgresql", "integrated-code-lifecycle");
        TechnicalScenario mysqlJenkins = scenario(functional, "mysql", "jenkins");
        TechnicalScenario postgresJenkins = scenario(functional, "postgresql", "jenkins");
        List<Arguments> combinations = new ArrayList<>();
        for (TechnicalScenario scenario : List.of(mysqlIcl, postgresIcl, mysqlJenkins, postgresJenkins)) {
            combinations.add(Arguments.of(scenario, DeploymentModes.DEV_IDE));
            combinations.add(Arguments.of(scenario, DeploymentModes.LOCAL_DOCKER));
        }
        return combinations.stream();
    }

    private static TechnicalScenario scenario(List<String> functional, String databaseId, String ciProviderId) {
        List<String> selection = new ArrayList<>(functional);
        selection.add(databaseId);
        selection.add(ciProviderId);
        selection.add("localvc");
        boolean jenkins = "jenkins".equals(ciProviderId);
        String composeFile = "postgresql".equals(databaseId) ? "docker/postgres.yml" : "docker/mysql.yml";
        String ideProfiles = jenkins ? JENKINS_IDE_PROFILES : ICL_IDE_PROFILES;
        String dockerProfiles = jenkins ? RuntimeStackWriter.JENKINS_DOCKER_PROFILES : RuntimeStackWriter.ICL_DOCKER_PROFILES;
        return new TechnicalScenario(List.copyOf(selection), databaseId, composeFile, ciProviderId, ideProfiles, dockerProfiles);
    }

    private FeatureModel technicalModel() {
        FeatureModel base = TestFeatureModels.baseModel();
        List<FeatureNode> features = new ArrayList<>(base.features());
        features.add(group("database"));
        features.add(technicalFeature("mysql", TechnicalSelectionResolver.COMPOSE_TARGET,
                TechnicalSelectionResolver.DATABASE_COMPOSE_FILE_PATH, "docker/mysql.yml"));
        features.add(technicalFeature("postgresql", TechnicalSelectionResolver.COMPOSE_TARGET,
                TechnicalSelectionResolver.DATABASE_COMPOSE_FILE_PATH, "docker/postgres.yml"));
        features.add(group("ci-provider"));
        features.add(technicalFeature("integrated-code-lifecycle", TechnicalSelectionResolver.ENV_TARGET,
                TechnicalSelectionResolver.SPRING_PROFILES_PATH, "localci,buildagent"));
        features.add(jenkinsFeature());
        features.add(technicalFeature("localvc", TechnicalSelectionResolver.ENV_TARGET,
                TechnicalSelectionResolver.SPRING_PROFILES_PATH, "localvc"));

        List<FeatureRelation> relations = new ArrayList<>(base.relations());
        relations.add(new FeatureRelation("artemis", "database", "group", "alternative", 2));
        relations.add(new FeatureRelation("database", "mysql", "optional", null, 1));
        relations.add(new FeatureRelation("database", "postgresql", "optional", null, 2));
        relations.add(new FeatureRelation("artemis", "ci-provider", "group", "alternative", 3));
        relations.add(new FeatureRelation("ci-provider", "integrated-code-lifecycle", "optional", null, 1));
        relations.add(new FeatureRelation("ci-provider", "jenkins", "optional", null, 2));
        relations.add(new FeatureRelation("artemis", "localvc", "mandatory", null, 4));
        return new FeatureModel(base.model(), features, relations, base.constraints());
    }

    private FeatureNode group(String id) {
        return new FeatureNode(id, id, "group", false, null, "not_applicable", null, "technical", List.of("maintainer"), List.of(),
                List.of(), List.of(), null);
    }

    private FeatureNode technicalFeature(String id, String target, String path, String value) {
        ArtifactMapping mapping = new ArtifactMapping(target, path, ArtifactMappingSource.SELECTION, objectMapper.valueToTree(value), null, false);
        return technicalFeature(id, List.of(mapping));
    }

    private FeatureNode jenkinsFeature() {
        List<ArtifactMapping> mappings = new ArrayList<>();
        mappings.add(new ArtifactMapping(TechnicalSelectionResolver.ENV_TARGET, TechnicalSelectionResolver.SPRING_PROFILES_PATH,
                ArtifactMappingSource.SELECTION, objectMapper.valueToTree("jenkins"), null, false));
        mappings.add(profileMapping("artemis.continuous-integration.url", false));
        mappings.add(profileMapping("artemis.continuous-integration.user", false));
        mappings.add(profileMapping("artemis.continuous-integration.password", true));
        mappings.add(profileMapping("artemis.continuous-integration.token", true));
        mappings.add(profileMapping("artemis.continuous-integration.vcs-credentials", true));
        mappings.add(profileMapping("artemis.continuous-integration.artemis-authentication-token-key", true));
        mappings.add(profileMapping("artemis.continuous-integration.artemis-authentication-token-value", true));
        return technicalFeature("jenkins", mappings);
    }

    private ArtifactMapping profileMapping(String path, boolean secret) {
        return new ArtifactMapping(ArtifactMappingResolver.OVERLAY_TARGET, path, ArtifactMappingSource.ENVIRONMENT, null, null, secret);
    }

    private FeatureNode technicalFeature(String id, List<ArtifactMapping> mappings) {
        return new FeatureNode(id, id, "feature", true, null, "disabled", null, "technical", List.of("maintainer"), List.of("maintainer"),
                List.of(), mappings, null);
    }

    private record TechnicalScenario(List<String> selection, String databaseId, String databaseComposeFile, String ciProviderId,
            String ideProfiles, String dockerProfiles) {
    }
}
