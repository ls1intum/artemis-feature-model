package de.tum.cit.aet.artemis.featuremodel.export.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

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
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactGenerationService;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactMappingResolver;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactPackageService;
import de.tum.cit.aet.artemis.featuremodel.export.service.EnvExampleWriter;
import de.tum.cit.aet.artemis.featuremodel.export.service.ProfileParameterResolver;
import de.tum.cit.aet.artemis.featuremodel.export.service.YamlOverlayWriter;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelExceptionHandler;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class ArtifactGenerationResourceTest {

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
        ArtifactGenerationService service = new ArtifactGenerationService(catalogService, validationService, profileService, mappingResolver, new YamlOverlayWriter(),
                new EnvExampleWriter(), objectMapper);
        ArtifactGenerationResource resource = new ArtifactGenerationResource(service, new ArtifactPackageService());
        mockMvc = MockMvcBuilders.standaloneSetup(resource).setControllerAdvice(new FeatureModelExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(), new ResourceHttpMessageConverter()).build();
    }

    @Test
    void previewReturnsGeneratedFilesAndReport() throws Exception {
        mockMvc.perform(post("/api/feature-model/artifacts/preview").contentType(MediaType.APPLICATION_JSON).content("{\"selectedFeatureIds\":" + MINIMAL + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.files[*].path", hasItem("config/application-feature-model.yml")))
                .andExpect(jsonPath("$.report.mode").value("DEMO")).andExpect(jsonPath("$.downloadAvailable").value(true));
    }

    @Test
    void previewRejectsInvalidSelectionWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/feature-model/artifacts/preview").contentType(MediaType.APPLICATION_JSON).content("{\"selectedFeatureIds\":[\"iris\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ARTIFACT_GENERATION_INVALID_SELECTION"));
    }

    @Test
    void previewReturnsNotFoundForUnknownProfile() throws Exception {
        mockMvc.perform(post("/api/feature-model/artifacts/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedFeatureIds\":" + MINIMAL + ",\"profileId\":\"missing-profile\"}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPLOYMENT_PROFILE_NOT_FOUND"));
    }

    @Test
    void downloadReturnsZipAttachment() throws Exception {
        mockMvc.perform(post("/api/feature-model/artifacts/download").contentType(MediaType.APPLICATION_JSON).content("{\"selectedFeatureIds\":" + MINIMAL + "}"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("application/zip")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("artemis-feature-model-artifacts.zip")));
    }
}
