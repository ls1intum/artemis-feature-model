package de.tum.cit.aet.artemis.featuremodel.selection.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.selection.repository.JsonGuidedWorkflowStore;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowAssembler;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowDiagnosticsService;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class GuidedWorkflowResourceTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new GuidedWorkflowResource(guidedWorkflowService())).build();

    @Test
    void returnsTheActiveGuidedWorkflow() throws Exception {
        mockMvc.perform(get("/api/feature-model/guided-workflow")).andExpect(status().isOk())
                .andExpect(jsonPath("$.workflow.id").value("artemis-guided-configuration"))
                .andExpect(jsonPath("$.workflow.defaultTemplateId").value("custom-configuration")).andExpect(jsonPath("$.useCaseTemplates", hasSize(6)))
                .andExpect(jsonPath("$.useCaseTemplates[0].label").value("Minimal teaching setup"))
                .andExpect(jsonPath("$.steps[4].decisions[0].options[1].enabledOutcome[0]").value(containsString("AI tutoring support")))
                .andExpect(jsonPath("$.steps[4].decisions[0].options[1].artifactImpacts[0]")
                        .value(containsString("artemis.iris.enabled")));
    }

    private GuidedWorkflowService guidedWorkflowService() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        ObjectMapper objectMapper = new ObjectMapper();
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        JsonFeatureModelStore featureModelStore = new JsonFeatureModelStore(resourceLoader, objectMapper);
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(featureModelStore, new FeatureModelIntegrityService(), treeService);
        JsonGuidedWorkflowStore workflowStore = new JsonGuidedWorkflowStore(resourceLoader, objectMapper);
        return new GuidedWorkflowService(workflowStore, catalogService, new GuidedWorkflowIntegrityService(), new GuidedWorkflowAssembler(),
                new GuidedWorkflowDiagnosticsService());
    }
}
