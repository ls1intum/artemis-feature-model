package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundleLoader;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.export.dto.RemoteEnvironmentInput;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

/**
 * Golden-fixture acceptance of the remote-ansible package: a request with the lab target name must reproduce the
 * expected inventory bytes exactly. The fixtures under {@code fixtures/remote-ansible/} are derived from the
 * deploy-lab repository and split into two classes since the environment-channel switch: structural files stay
 * byte-identical to the lab's tracked files, while value-bearing files are package-own expectations rendering
 * environment lookup expressions. Provenance and the class split are recorded in {@code PROVENANCE.md} next to the
 * fixtures. Updating a fixture is a deliberate, reviewed act.
 */
class RemoteAnsibleGoldenFixtureTest {

    private static final String FIXTURE_DIR = "/fixtures/remote-ansible/";

    private static final List<String> FULL_MYSQL_SELECTION = List.of("lecture", "tutorialgroup", "course-workflow", "communication", "exercise-common",
            "programming", "quiz", "text", "modeling", "file-upload", "exam", "plagiarism", "mysql", "integrated-code-lifecycle", "localvc");

    @TempDir
    Path dataRoot;

    private DeploymentPackageService service;

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
        ArtifactGenerationService artifactGenerationService = new ArtifactGenerationService(catalogService, validationService, profileService, mappingResolver,
                new YamlOverlayWriter(), new EnvExampleWriter(), objectMapper);
        service = new DeploymentPackageService(artifactGenerationService, catalogService, profileService, new TechnicalSelectionResolver(),
                new StaticConfigValidationService(resourceLoader, objectMapper), new RuntimeTemplateWriter(), new RuntimeStackWriter(),
                new RemoteImageStackWriter(), new RuntimeScriptWriter(), new ActiveProfilesDeriver(), new DevIdeTemplateWriter(),
                new RemoteAnsibleValuesWriter(new AnsibleBindingCatalogLoader(resourceLoader, objectMapper)), new EnvExampleWriter(),
                new ArtemisRuntimeSourceResolver(new RuntimeFeatureModelBundleLoader(SnapshotProperties.classpathFallback(), resourceLoader, objectMapper).load(),
                        new ArtemisRuntimeProperties("b1e27eeaaa03e4b41d72cbfe7f503e648dd544a6", "latest")), objectMapper);
    }

    @Test
    void labEnvironmentRequestReproducesTheLabInventoryByteForByte() {
        GeneratedArtifactPackage result = service.generate(labRequest(FULL_MYSQL_SELECTION));

        assertFixtureMatch(result, "inventory/hosts", "hosts");
        assertFixtureMatch(result, "inventory/group_vars/artemislocal/main.yml", "artemislocal-main.yml");
        assertFixtureMatch(result, "inventory/group_vars/artemislocal/secrets.yml", "artemislocal-secrets.yml");
        assertFixtureMatch(result, "inventory/group_vars/artemistests_common_config.yml", "artemistests_common_config.yml");
        assertFixtureMatch(result, "inventory/group_vars/artemistests_mysql.yml", "artemistests_mysql.yml");
        assertFixtureMatch(result, "inventory/group_vars/artemistests_local_vc_ci.yml", "artemistests_local_vc_ci.yml");
    }

    @Test
    void reducedVariantReproducesTheLabWithoutGroupValuesByteForByte() {
        List<String> selection = FULL_MYSQL_SELECTION.stream().filter(id -> !"exam".equals(id) && !"tutorialgroup".equals(id)).toList();

        GeneratedArtifactPackage result = service.generate(labRequest(selection));

        assertFixtureMatch(result, "inventory/group_vars/artemistests_without_exam.yml", "artemistests_without_exam.yml");
        assertFixtureMatch(result, "inventory/group_vars/artemistests_without_tutorialgroup.yml", "artemistests_without_tutorialgroup.yml");
    }

    @Test
    void postgresVariantReproducesTheInertLabPostgresValuesByteForByte() {
        List<String> selection = FULL_MYSQL_SELECTION.stream().map(id -> "mysql".equals(id) ? "postgresql" : id).toList();

        GeneratedArtifactPackage result = service.generate(labRequest(selection));

        assertFixtureMatch(result, "inventory/group_vars/artemistests_postgres.yml", "artemistests_postgres.yml");
        assertFixtureMatch(result, "inventory/group_vars/artemistests_common_config.yml", "artemistests_common_config.yml");
    }

    private ArtifactGenerationRequest labRequest(List<String> selection) {
        return new ArtifactGenerationRequest(selection, null, null, "remote-ansible", new RemoteEnvironmentInput("artemis-local"));
    }

    private void assertFixtureMatch(GeneratedArtifactPackage result, String packagePath, String fixtureName) {
        String generated = result.files().stream().filter(file -> file.path().equals(packagePath)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing package file " + packagePath)).content();
        assertThat(generated).as("byte content of %s vs fixture %s", packagePath, fixtureName).isEqualTo(fixtureContent(fixtureName));
    }

    private String fixtureContent(String fixtureName) {
        try (InputStream inputStream = getClass().getResourceAsStream(FIXTURE_DIR + fixtureName)) {
            assertThat(inputStream).as("fixture resource %s", fixtureName).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read fixture " + fixtureName + ".", e);
        }
    }
}
