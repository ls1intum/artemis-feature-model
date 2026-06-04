package de.tum.cit.aet.artemis.featuremodel.selection.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.repository.GuidedWorkflowStore;

@Service
public class GuidedWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(GuidedWorkflowService.class);

    private final GuidedWorkflowStore guidedWorkflowStore;

    private final FeatureModelCatalogService featureModelCatalogService;

    private final GuidedWorkflowIntegrityService guidedWorkflowIntegrityService;

    /**
     * Creates the guided workflow service.
     *
     * @param guidedWorkflowStore store used to load the active guided workflow.
     * @param featureModelCatalogService catalog service used to load the active feature model.
     * @param guidedWorkflowIntegrityService integrity service that validates workflow references.
     */
    public GuidedWorkflowService(GuidedWorkflowStore guidedWorkflowStore, FeatureModelCatalogService featureModelCatalogService,
            GuidedWorkflowIntegrityService guidedWorkflowIntegrityService) {
        this.guidedWorkflowStore = guidedWorkflowStore;
        this.featureModelCatalogService = featureModelCatalogService;
        this.guidedWorkflowIntegrityService = guidedWorkflowIntegrityService;
    }

    /**
     * Loads the active guided workflow and validates its feature references against the active
     * feature model before returning it to clients.
     *
     * @return active guided workflow.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException if the model or workflow cannot be loaded.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException if the workflow references unknown model data.
     */
    public GuidedWorkflow getActiveGuidedWorkflow() {
        FeatureModel featureModel = featureModelCatalogService.loadActiveModel();
        GuidedWorkflow workflow = guidedWorkflowStore.loadActiveWorkflow();
        guidedWorkflowIntegrityService.validate(workflow, featureModel);
        log.debug("Validated guided workflow '{}' against feature model '{}'.", workflow.workflow().id(), featureModel.model().id());
        return workflow;
    }
}
