package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Verifies local Docker package determinism without pinning expected bytes to the evolving classpath model and
 * configuration-key catalog. Repeated generation from the same inputs must be byte-identical, and explicitly choosing
 * the default deployment mode may only add that mode to the package manifest.
 */
class DeploymentPackageDeterminismTest {

    private static final String MANIFEST_PATH = "metadata/package-manifest.json";

    @TempDir
    Path dataRoot;

    private DeploymentPackageService service;

    private ObjectMapper objectMapper;

    private List<String> selectedFeatureIds;

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
        ArtifactMappingResolver mappingResolver = new ArtifactMappingResolver(ArtifactMappingResolverTest.classpathCatalog());
        ArtifactGenerationService artifactGenerationService = new ArtifactGenerationService(catalogService, validationService, profileService, mappingResolver,
                new YamlOverlayWriter(), new EnvExampleWriter(), objectMapper);
        RuntimeFeatureModelBundleLoader bundleLoader = new RuntimeFeatureModelBundleLoader(SnapshotProperties.classpathFallback(), resourceLoader,
                objectMapper);
        ArtemisRuntimeSourceResolver runtimeSourceResolver = new ArtemisRuntimeSourceResolver(bundleLoader.load(),
                new ArtemisRuntimeProperties("b1e27eeaaa03e4b41d72cbfe7f503e648dd544a6", "latest"));
        selectedFeatureIds = catalogService.defaultSelectedFeatureIds(catalogService.loadActiveModel());
        service = new DeploymentPackageService(artifactGenerationService, catalogService, profileService, new TechnicalSelectionResolver(),
                new StaticConfigValidationService(resourceLoader, objectMapper), new RuntimeTemplateWriter(), new RuntimeStackWriter(),
                new RemoteImageStackWriter(), new RuntimeScriptWriter(), new ActiveProfilesDeriver(), new DevIdeTemplateWriter(), new EnvExampleWriter(),
                runtimeSourceResolver, objectMapper);
    }

    @Test
    void repeatedDefaultRequestsProduceByteIdenticalPackages() {
        ArtifactGenerationRequest request = new ArtifactGenerationRequest(selectedFeatureIds, null, null);

        GeneratedArtifactPackage first = service.generate(request);
        GeneratedArtifactPackage second = service.generate(request);

        assertThat(second).isEqualTo(first);
        ArtifactPackageService packageService = new ArtifactPackageService();
        byte[] firstArchive = packageService.zip(first, RuntimePackageConstants.PACKAGE_ROOT_DIR);
        byte[] secondArchive = packageService.zip(second, RuntimePackageConstants.PACKAGE_ROOT_DIR);
        assertThat(secondArchive).as("ZIP bytes from repeated generation").isEqualTo(firstArchive);
    }

    @Test
    void explicitLocalDockerRequestDiffersOnlyByTheRecordedDeploymentMode() {
        GeneratedArtifactPackage defaultPackage = service.generate(new ArtifactGenerationRequest(selectedFeatureIds, null, null));
        GeneratedArtifactPackage explicitPackage = service.generate(new ArtifactGenerationRequest(selectedFeatureIds, null, null, "local-docker"));

        assertThat(explicitPackage.report()).isEqualTo(defaultPackage.report());
        assertThat(explicitPackage.files()).extracting(GeneratedArtifactFile::path)
                .containsExactlyElementsOf(defaultPackage.files().stream().map(GeneratedArtifactFile::path).toList());
        for (GeneratedArtifactFile defaultFile : defaultPackage.files()) {
            if (!MANIFEST_PATH.equals(defaultFile.path())) {
                assertThat(file(explicitPackage, defaultFile.path())).as("generated file %s", defaultFile.path()).isEqualTo(defaultFile);
            }
        }

        ObjectNode defaultManifest = manifest(defaultPackage);
        ObjectNode explicitManifest = manifest(explicitPackage);
        assertThat(defaultManifest.has("deploymentMode")).isFalse();
        JsonNode recordedMode = explicitManifest.remove("deploymentMode");
        assertThat(recordedMode).isNotNull();
        assertThat(recordedMode.asString()).isEqualTo("local-docker");
        assertThat(explicitManifest).isEqualTo(defaultManifest);
    }

    private ObjectNode manifest(GeneratedArtifactPackage artifactPackage) {
        return (ObjectNode) objectMapper.readTree(file(artifactPackage, MANIFEST_PATH).content());
    }

    private GeneratedArtifactFile file(GeneratedArtifactPackage artifactPackage, String path) {
        return artifactPackage.files().stream().filter(file -> path.equals(file.path())).findFirst().orElseThrow();
    }
}
