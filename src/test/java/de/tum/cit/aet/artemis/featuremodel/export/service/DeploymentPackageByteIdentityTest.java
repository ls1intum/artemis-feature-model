package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Guards the local-docker package bytes against accidental drift: the default deployment package for a fixed
 * selection must stay byte-for-byte identical to the recorded baseline in
 * {@code fixtures/local-docker-package-sha256.json}, and an explicit {@code local-docker} request may differ only by
 * the deployment mode recorded in the manifest. The fixture was first recorded from the pre-mode-axis output to prove
 * the D1 refactor byte-identical, and is re-baselined only for deliberate content changes (last: the
 * {@code scripts/start-demo.sh} single-command entry point).
 */
class DeploymentPackageByteIdentityTest {

    private static final String FIXTURE_RESOURCE = "/fixtures/local-docker-package-sha256.json";

    private static final String MANIFEST_PATH = "metadata/package-manifest.json";

    private static final String ZIP_KEY = "__zip__";

    private static final List<String> FIXED_SELECTION = List.of("course-workflow", "communication", "exercise-common", "programming", "quiz", "iris", "athena",
            "hyperion");

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
                new RuntimeScriptWriter(), new ActiveProfilesDeriver(), new DevIdeTemplateWriter(), objectMapper);
    }

    @Test
    void defaultRequestOutputIsByteIdenticalToThePreModeAxisFixture() {
        Map<String, String> expectedHashes = loadFixture();

        GeneratedArtifactPackage result = service.generate(new ArtifactGenerationRequest(FIXED_SELECTION, null, null));

        assertThat(result.files()).hasSize(expectedHashes.size() - 1);
        for (GeneratedArtifactFile file : result.files()) {
            assertThat(sha256(file.content().getBytes(StandardCharsets.UTF_8))).as("content hash of %s", file.path()).isEqualTo(expectedHashes.get(file.path()));
        }
        byte[] archive = new ArtifactPackageService().zip(result, RuntimePackageConstants.PACKAGE_ROOT_DIR);
        assertThat(sha256(archive)).as("hash of the package ZIP").isEqualTo(expectedHashes.get(ZIP_KEY));
    }

    @Test
    void explicitLocalDockerRequestDiffersOnlyByTheRecordedDeploymentMode() {
        Map<String, String> expectedHashes = loadFixture();

        GeneratedArtifactPackage result = service.generate(new ArtifactGenerationRequest(FIXED_SELECTION, null, null, "local-docker"));

        for (GeneratedArtifactFile file : result.files()) {
            if (MANIFEST_PATH.equals(file.path())) {
                continue;
            }
            assertThat(sha256(file.content().getBytes(StandardCharsets.UTF_8))).as("content hash of %s", file.path()).isEqualTo(expectedHashes.get(file.path()));
        }
        String manifest = result.files().stream().filter(file -> MANIFEST_PATH.equals(file.path())).findFirst().orElseThrow().content();
        String withoutRecordedMode = manifest.replaceFirst("\\s*\"deploymentMode\"\\s*:\\s*\"local-docker\",", "");
        assertThat(withoutRecordedMode).isNotEqualTo(manifest);
        assertThat(sha256(withoutRecordedMode.getBytes(StandardCharsets.UTF_8))).as("manifest without the recorded mode").isEqualTo(expectedHashes.get(MANIFEST_PATH));
    }

    private Map<String, String> loadFixture() {
        try (InputStream inputStream = getClass().getResourceAsStream(FIXTURE_RESOURCE)) {
            assertThat(inputStream).as("fixture resource %s", FIXTURE_RESOURCE).isNotNull();
            return objectMapper.readValue(inputStream, new TypeReference<Map<String, String>>() {
            });
        }
        catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read the byte-identity fixture.", e);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }
}
