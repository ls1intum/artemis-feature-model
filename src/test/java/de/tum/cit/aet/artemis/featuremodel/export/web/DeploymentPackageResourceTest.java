package de.tum.cit.aet.artemis.featuremodel.export.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;
import de.tum.cit.aet.artemis.featuremodel.export.service.ActiveProfilesDeriver;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactGenerationService;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactMappingResolver;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactPackageService;
import de.tum.cit.aet.artemis.featuremodel.export.service.DevIdeTemplateWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.DeploymentPackageService;
import de.tum.cit.aet.artemis.featuremodel.export.service.EnvExampleWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.ProfileParameterResolver;
import de.tum.cit.aet.artemis.featuremodel.export.service.RuntimeScriptWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.RuntimeStackWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.RuntimeTemplateWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.StaticConfigValidationService;
import de.tum.cit.aet.artemis.featuremodel.export.service.TechnicalSelectionResolver;
import de.tum.cit.aet.artemis.featuremodel.export.service.YamlOverlayWriter;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelExceptionHandler;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class DeploymentPackageResourceTest {

    private static final String MINIMAL = "[\"course-workflow\",\"communication\",\"exercise-common\",\"programming\",\"quiz\"]";

    @TempDir
    Path dataRoot;

    private MockMvc mockMvc;

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
        ArtifactMappingResolver mappingResolver = new ArtifactMappingResolver(new ProfileParameterResolver());
        ArtifactGenerationService artifactGenerationService = new ArtifactGenerationService(catalogService, validationService, profileService, mappingResolver,
                new YamlOverlayWriter(), new EnvExampleWriter(), objectMapper);
        DeploymentPackageService deploymentPackageService = new DeploymentPackageService(artifactGenerationService, catalogService, profileService,
                new TechnicalSelectionResolver(), new StaticConfigValidationService(resourceLoader, objectMapper), new RuntimeTemplateWriter(),
                new RuntimeStackWriter(), new RuntimeScriptWriter(), new ActiveProfilesDeriver(), new DevIdeTemplateWriter(), new EnvExampleWriter(),
                objectMapper);
        DeploymentPackageResource resource = new DeploymentPackageResource(deploymentPackageService, new ArtifactPackageService());
        mockMvc = MockMvcBuilders.standaloneSetup(resource).setControllerAdvice(new FeatureModelExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(), new ResourceHttpMessageConverter()).build();
    }

    @Test
    void previewReturnsPhase5AndPhase6Files() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/preview").contentType(MediaType.APPLICATION_JSON).content("{\"selectedFeatureIds\":" + MINIMAL + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.files[*].path", hasItem("config/application-feature-model.yml")))
                .andExpect(jsonPath("$.files[*].path", hasItem("metadata/package-manifest.json")))
                .andExpect(jsonPath("$.files[*].path", hasItem("deployment/local-repo/docker-compose.override.example.yml")))
                .andExpect(jsonPath("$.files[*].path", hasItem("scripts/start-local-repo.sh"))).andExpect(jsonPath("$.report.mode").value("DEMO"))
                .andExpect(jsonPath("$.downloadAvailable").value(true));
    }

    @Test
    void previewRejectsInvalidSelectionWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/preview").contentType(MediaType.APPLICATION_JSON).content("{\"selectedFeatureIds\":[\"iris\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARTIFACT_GENERATION_INVALID_SELECTION"));
    }

    @Test
    void previewReturnsNotFoundForUnknownProfile() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedFeatureIds\":" + MINIMAL + ",\"profileId\":\"missing-profile\"}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPLOYMENT_PROFILE_NOT_FOUND"));
    }

    @Test
    void previewRejectsAnUnknownDeploymentModeWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedFeatureIds\":" + MINIMAL + ",\"deploymentMode\":\"cloud-magic\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARTIFACT_GENERATION_UNKNOWN_DEPLOYMENT_MODE"));
    }

    @Test
    void previewRejectsADeploymentModeTheProfileDoesNotSupportWithBadRequest() throws Exception {
        Path profileDirectory = dataRoot.resolve("deployment-profiles");
        Files.createDirectories(profileDirectory);
        Files.writeString(profileDirectory.resolve("docker-only-profile.json"),
                "{\"id\":\"docker-only-profile\",\"name\":\"Docker Only\",\"version\":\"1.0.0\",\"status\":\"published\",\"supportedDeploymentModes\":[\"local-docker\"]}");

        mockMvc.perform(post("/api/feature-model/deployment-package/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedFeatureIds\":" + MINIMAL + ",\"profileId\":\"docker-only-profile\",\"deploymentMode\":\"dev-ide\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARTIFACT_GENERATION_UNSUPPORTED_DEPLOYMENT_MODE"));
    }

    @Test
    void downloadReturnsZipAttachment() throws Exception {
        mockMvc.perform(post("/api/feature-model/deployment-package/download").contentType(MediaType.APPLICATION_JSON).content("{\"selectedFeatureIds\":" + MINIMAL + "}"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("application/zip")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("artemis-feature-model-deployment-package.zip")));
    }

    @Test
    void downloadZipContainsExpectedRootDirectoryAndFiles() throws Exception {
        byte[] archive = mockMvc
                .perform(post("/api/feature-model/deployment-package/download").contentType(MediaType.APPLICATION_JSON).content("{\"selectedFeatureIds\":" + MINIMAL + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        List<String> names = entryNames(archive);
        assertThat(names).contains("artemis-feature-model-deployment-package/README.md", "artemis-feature-model-deployment-package/metadata/package-manifest.json",
                "artemis-feature-model-deployment-package/deployment/local-repo/docker-compose.override.example.yml",
                "artemis-feature-model-deployment-package/scripts/start-local-repo.sh");
        assertThat(names).allMatch(name -> name.startsWith("artemis-feature-model-deployment-package/"));
    }

    @Test
    void downloadsTheDevIdePackageWithTheRunConfigurationAndWithoutRuntimeScripts() throws Exception {
        byte[] archive = mockMvc
                .perform(post("/api/feature-model/deployment-package/download").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedFeatureIds\":" + MINIMAL + ",\"deploymentMode\":\"dev-ide\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        List<String> names = entryNames(archive);
        assertThat(names).contains("artemis-feature-model-deployment-package/intellij/runConfigurations/Artemis_Server__Feature_Model_Selection_.xml",
                "artemis-feature-model-deployment-package/config/application-feature-model.yml",
                "artemis-feature-model-deployment-package/metadata/static-config-validation.json");
        assertThat(names).noneMatch(name -> name.contains("scripts/")).noneMatch(name -> name.contains("deployment/local-repo/"));
    }

    private List<String> entryNames(byte[] archive) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zipStream = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
