package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GenerationReport;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class ArtifactGenerationServiceTest {

    private static final List<String> MINIMAL_SELECTION = List.of("course-workflow", "communication", "exercise-common", "programming", "quiz", "mysql",
            "integrated-code-lifecycle", "localvc");

    @TempDir
    Path dataRoot;

    private ArtifactGenerationService service;

    @BeforeEach
    void setUp() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        ObjectMapper objectMapper = new ObjectMapper();
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        JsonFeatureModelStore store = new JsonFeatureModelStore(resourceLoader, objectMapper);
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(store, new FeatureModelIntegrityService(), treeService);
        FeatureModelValidationService validationService = new FeatureModelValidationService(catalogService, treeService);
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), objectMapper);
        DeploymentProfileService profileService = new DeploymentProfileService(repository);
        ArtifactMappingResolver mappingResolver = new ArtifactMappingResolver(ArtifactMappingResolverTest.classpathCatalog());
        service = new ArtifactGenerationService(catalogService, validationService, profileService, mappingResolver, new YamlOverlayWriter(), new EnvExampleWriter(), objectMapper);
    }

    @Test
    void generatesAllExpectedFilesForAValidSelection() {
        GeneratedArtifactPackage result = service.generate(request(MINIMAL_SELECTION, null));

        assertThat(result.files()).extracting("path").containsExactly("README.md", "config/application-feature-model.yml", "env/.env.example",
                "metadata/selected-features.json", "metadata/deployment-profile-summary.json", "metadata/generation-report.json");
        assertThat(result.report().status()).isEqualTo(GenerationReport.STATUS_GENERATED);
        assertThat(content(result, "config/application-feature-model.yml")).contains("artemis:").contains("lecture:").contains("enabled: false");
    }

    @Test
    void generatesServiceConfigurationAndEnvironmentReferencesForExternalFeatures() {
        List<String> selection = withExtra("iris", "athena");

        GeneratedArtifactPackage result = service.generate(request(selection, null));

        String overlay = content(result, "config/application-feature-model.yml");
        assertThat(overlay).contains("iris:").contains("enabled: true").contains("url: ${ARTEMIS_IRIS_URL}").contains("secret-token: ${ARTEMIS_IRIS_SECRET_TOKEN}");
        assertThat(overlay).doesNotContain("env:").doesNotContain("example.com");
        String env = content(result, "env/.env.example");
        assertThat(env).contains("ARTEMIS_ATHENA_SECRET=").contains("ARTEMIS_IRIS_SECRET_TOKEN=").contains("# Config key: artemis.iris.secret-token")
                .contains("# SECRET — obtain from the deployment secret store");
        assertThat(result.report().status()).isEqualTo(GenerationReport.STATUS_GENERATED_WITH_WARNINGS);
        assertThat(result.report().environmentRequirements()).anySatisfy(requirement -> {
            assertThat(requirement.configKey()).isEqualTo("artemis.iris.secret-token");
            assertThat(requirement.name()).isEqualTo("ARTEMIS_IRIS_SECRET_TOKEN");
            assertThat(requirement.secret()).isTrue();
            assertThat(requirement.source()).isEqualTo("artifact-mapping");
        });
    }

    @Test
    void everyEnvironmentPlaceholderInTheOverlayHasAnEnvExampleEntry() {
        GeneratedArtifactPackage result = service.generate(request(withExtra("iris", "athena", "atlas", "hyperion"), null));

        String overlay = content(result, "config/application-feature-model.yml");
        String env = content(result, "env/.env.example");
        Matcher matcher = Pattern.compile("\\$\\{([A-Z0-9_]+)}").matcher(overlay);
        int placeholderCount = 0;
        while (matcher.find()) {
            placeholderCount++;
            assertThat(env).contains(matcher.group(1) + "=");
        }
        assertThat(placeholderCount).isGreaterThan(0);
    }

    @Test
    void doesNotWritePlaintextSecretsIntoAnyGeneratedFile() {
        GeneratedArtifactPackage result = service.generate(request(withExtra("iris", "athena", "hyperion"), null));

        for (var file : result.files()) {
            assertThat(file.content()).doesNotContain("env:ARTEMIS").doesNotContain("env:SPRING");
        }
    }

    @Test
    void warnsThatLtiNeedsManualRegistration() {
        GeneratedArtifactPackage result = service.generate(request(withExtra("lti"), null));

        assertThat(result.report().warnings()).anyMatch(message -> "lti".equals(message.featureId()) && message.message().toLowerCase().contains("registration"));
    }

    @Test
    void rejectsAnInvalidSelection() {
        assertThatThrownBy(() -> service.generate(request(List.of("iris"), null))).isInstanceOf(ArtifactGenerationException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void rejectsAnUnknownProfile() {
        assertThatThrownBy(() -> service.generate(request(MINIMAL_SELECTION, "missing-profile"))).isInstanceOf(DeploymentProfileException.class);
    }

    private List<String> withExtra(String... extra) {
        return java.util.stream.Stream.concat(MINIMAL_SELECTION.stream(), java.util.Arrays.stream(extra)).toList();
    }

    private ArtifactGenerationRequest request(List<String> selectedFeatureIds, String profileId) {
        return new ArtifactGenerationRequest(selectedFeatureIds, profileId, null);
    }

    private String content(GeneratedArtifactPackage result, String path) {
        return result.files().stream().filter(file -> file.path().equals(path)).findFirst().orElseThrow().content();
    }
}
