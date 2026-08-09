package de.tum.cit.aet.artemis.featuremodel.selection.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowFinding;
import de.tum.cit.aet.artemis.featuremodel.selection.repository.GuidedWorkflowStore;

@Service
public class GuidedWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(GuidedWorkflowService.class);

    private final GuidedWorkflowStore guidedWorkflowStore;

    private final FeatureModelCatalogService featureModelCatalogService;

    private final GuidedWorkflowAssembler guidedWorkflowAssembler;

    private final GuidedWorkflowDiagnosticsService guidedWorkflowDiagnosticsService;

    /**
     * Creates the guided workflow service.
     *
     * @param guidedWorkflowStore store used to load the active guided workflow.
     * @param featureModelCatalogService catalog service used to load the active feature model.
     * @param guidedWorkflowAssembler assembler that enriches the lean authored workflow with model-owned wiring.
     * @param guidedWorkflowDiagnosticsService diagnostics service that surfaces coverage and consistency warnings.
     */
    public GuidedWorkflowService(GuidedWorkflowStore guidedWorkflowStore, FeatureModelCatalogService featureModelCatalogService,
            GuidedWorkflowAssembler guidedWorkflowAssembler, GuidedWorkflowDiagnosticsService guidedWorkflowDiagnosticsService) {
        this.guidedWorkflowStore = guidedWorkflowStore;
        this.featureModelCatalogService = featureModelCatalogService;
        this.guidedWorkflowAssembler = guidedWorkflowAssembler;
        this.guidedWorkflowDiagnosticsService = guidedWorkflowDiagnosticsService;
    }

    /**
     * Loads the active effective guided workflow and enriches it with the wiring the model owns before returning it
     * to clients. The bundle loader already projected and validated the workflow against the active model, so no raw
     * workflow ever reaches this service; the served record always carries the derived capability, impact, and review
     * group data of the active model. Coverage and consistency findings are logged as warnings and never fail the
     * request.
     *
     * @return active guided workflow enriched against the active model.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException if the model or workflow cannot be loaded.
     */
    public GuidedWorkflow getActiveGuidedWorkflow() {
        FeatureModel featureModel = featureModelCatalogService.loadActiveModel();
        GuidedWorkflow workflow = guidedWorkflowStore.loadActiveWorkflow();
        logDiagnostics(workflow, featureModel);
        GuidedWorkflow enrichedWorkflow = guidedWorkflowAssembler.enrich(workflow, featureModel);
        log.debug("Validated and enriched guided workflow '{}' against feature model '{}'.", workflow.workflow().id(), featureModel.model().id());
        return enrichedWorkflow;
    }

    /**
     * Logs coverage, template-consistency, and stub-prose findings as warnings. Findings are soft by design: a
     * coverage gap must surface without turning the endpoint into an error.
     *
     * @param workflow lean guided workflow.
     * @param featureModel active feature model.
     */
    private void logDiagnostics(GuidedWorkflow workflow, FeatureModel featureModel) {
        for (GuidedWorkflowFinding finding : guidedWorkflowDiagnosticsService.findings(workflow, featureModel)) {
            log.warn("Guided workflow diagnostic {} for '{}': {}", finding.code(), finding.subject(), finding.message());
        }
    }
}
