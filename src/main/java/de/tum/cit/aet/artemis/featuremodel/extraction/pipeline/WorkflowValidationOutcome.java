package de.tum.cit.aet.artemis.featuremodel.extraction.pipeline;

import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GuidedWorkflowValidationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;

/**
 * Validation result of the authored guided workflow against the generated model, handed from the workflow stage to
 * the artifact store. Not serialized; the persisted workflow-stage contract is the envelope in
 * {@code extraction.domain}.
 *
 * @param workflowIntegrityValid whether the effective workflow passed hard reference validation against the model.
 * @param deliveryEligible whether hard references passed and no error-severity finding exists.
 * @param guidedValidation coverage and consistency findings of the workflow against the generated model.
 * @param items validation diagnostics for the extraction report.
 */
public record WorkflowValidationOutcome(boolean workflowIntegrityValid, boolean deliveryEligible, GuidedWorkflowValidationReport guidedValidation,
        List<ReportItem> items) {
}
