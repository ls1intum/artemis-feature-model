package de.tum.cit.aet.artemis.featuremodel.selection.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowService;

@RestController
@RequestMapping("/api/feature-model")
public class GuidedWorkflowResource {

    private static final Logger log = LoggerFactory.getLogger(GuidedWorkflowResource.class);

    private final GuidedWorkflowService guidedWorkflowService;

    /**
     * Creates the guided workflow resource.
     *
     * @param guidedWorkflowService service used to load and validate the guided workflow.
     */
    public GuidedWorkflowResource(GuidedWorkflowService guidedWorkflowService) {
        this.guidedWorkflowService = guidedWorkflowService;
    }

    /**
     * Returns the active guided workflow used by the Configurator.
     *
     * @return active guided workflow.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException if the model or workflow cannot be loaded.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException if the workflow references unknown model data.
     */
    @GetMapping("/guided-workflow")
    public GuidedWorkflow getActiveGuidedWorkflow() {
        log.debug("REST request to get the active guided workflow.");

        GuidedWorkflow workflow = guidedWorkflowService.getActiveGuidedWorkflow();
        log.info("REST response for guided workflow '{}' contains {} templates, {} steps, and {} review groups.", workflow.workflow().id(),
                workflow.useCaseTemplates().size(), workflow.steps().size(), workflow.finalReviewGroups().size());
        return workflow;
    }
}
