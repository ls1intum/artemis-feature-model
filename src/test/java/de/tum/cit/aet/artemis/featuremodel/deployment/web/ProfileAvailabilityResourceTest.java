package de.tum.cit.aet.artemis.featuremodel.deployment.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.repository.DeploymentProfileRepository;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.CapabilityResolutionService;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;
import de.tum.cit.aet.artemis.featuremodel.selection.repository.JsonGuidedWorkflowStore;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowService;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelExceptionHandler;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class ProfileAvailabilityResourceTest {

    @TempDir
    Path dataRoot;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        ObjectMapper objectMapper = new ObjectMapper();
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        JsonFeatureModelStore featureModelStore = new JsonFeatureModelStore(resourceLoader, objectMapper);
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(featureModelStore, new FeatureModelIntegrityService(), treeService);
        JsonGuidedWorkflowStore workflowStore = new JsonGuidedWorkflowStore(resourceLoader, objectMapper);
        GuidedWorkflowService workflowService = new GuidedWorkflowService(workflowStore, catalogService, new GuidedWorkflowIntegrityService());
        DeploymentProfileRepository repository = new DeploymentProfileRepository(new SnapshotProperties(dataRoot.toString(), null), objectMapper);
        DeploymentProfileService profileService = new DeploymentProfileService(repository);
        CapabilityResolutionService capabilityResolutionService = new CapabilityResolutionService(catalogService, workflowService, profileService);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProfileAvailabilityResource(capabilityResolutionService)).setControllerAdvice(new FeatureModelExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter()).build();
    }

    @Test
    void resolvesAgainstDefaultProfileWhenNoProfileIdGiven() throws Exception {
        mockMvc.perform(get("/api/feature-model/profile-availability")).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProfile.id").value("default-teacher-profile"))
                .andExpect(jsonPath("$.options[?(@.optionId=='enable-iris')].available", hasItem(false)))
                .andExpect(jsonPath("$.availableProfiles[?(@.id=='ai-enabled-profile')].id", hasItem("ai-enabled-profile")));
    }

    @Test
    void resolvesAgainstRequestedProfile() throws Exception {
        mockMvc.perform(get("/api/feature-model/profile-availability").param("profileId", "ai-enabled-profile")).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProfile.id").value("ai-enabled-profile"))
                .andExpect(jsonPath("$.options[?(@.optionId=='enable-iris')].available", hasItem(true)));
    }

    @Test
    void returnsNotFoundForUnknownProfile() throws Exception {
        mockMvc.perform(get("/api/feature-model/profile-availability").param("profileId", "missing-profile")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPLOYMENT_PROFILE_NOT_FOUND"));
    }
}
