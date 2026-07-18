package de.tum.cit.aet.artemis.featuremodel.selection.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.selection.repository.JsonGuidedWorkflowStore;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class GuidedWorkflowServiceTest {

    @Test
    void returnsValidatedRuntimeGuidedWorkflow() {
        GuidedWorkflowService service = guidedWorkflowService();

        var workflow = service.getActiveGuidedWorkflow();

        assertThat(workflow.workflow().id()).isEqualTo("artemis-guided-configuration");
        assertThat(workflow.useCaseTemplates()).extracting("id").contains("minimal-teaching-setup", "custom-configuration");
        assertThat(workflow.steps()).extracting("id").contains("teaching-content", "artifact-generation");
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
