package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GuidedWorkflowValidationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowFinding;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowDiagnosticsService;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowProjectionService;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException;

/**
 * Validates the authored guided workflow against a generated model through the same code paths the running app uses:
 * the hard reference validation of the effective workflow that decides snapshot eligibility, and the shared coverage,
 * capability, lifecycle, and consistency diagnostics that become the guided workflow validation report. Hard
 * reference validation applies to the effective workflow — draft options are projected away first, so a draft
 * referencing a not-yet-delivered feature surfaces as a warning finding instead of an integrity error. Eligibility is
 * severity-based: error findings block, warning and info findings publish.
 */
class GuidedWorkflowValidator {

    /**
     * Validation result.
     *
     * @param workflowIntegrityValid whether the effective workflow passed hard reference validation against the model.
     * @param deliveryEligible whether hard references passed and no error-severity finding exists.
     * @param guidedValidation coverage and consistency findings of the workflow against the generated model.
     * @param items validation diagnostics for the extraction report.
     */
    record Result(boolean workflowIntegrityValid, boolean deliveryEligible, GuidedWorkflowValidationReport guidedValidation, List<ReportItem> items) {
    }

    /**
     * Validates the authored workflow against the generated model.
     *
     * @param generatedModel assembled generated model.
     * @param authoredWorkflow authored lean guided workflow.
     * @param bundledProfile bundled deployment profile providing the known capabilities.
     * @return workflow integrity state, validation report, and report items.
     */
    Result validate(FeatureModel generatedModel, GuidedWorkflow authoredWorkflow, DeploymentProfile bundledProfile) {
        List<ReportItem> items = new ArrayList<>();
        GuidedWorkflow effectiveWorkflow = new GuidedWorkflowProjectionService().project(authoredWorkflow).effectiveWorkflow();
        boolean workflowIntegrityValid = validateWorkflowReferences(generatedModel, effectiveWorkflow, items);
        GuidedWorkflowValidationReport guidedValidation = guidedValidation(generatedModel, authoredWorkflow, bundledProfile, workflowIntegrityValid, items);
        return new Result(workflowIntegrityValid, guidedValidation.deliveryEligible(), guidedValidation, List.copyOf(items));
    }

    /**
     * Runs the guided workflow's hard reference validation against the generated model.
     *
     * @param generatedModel assembled generated model.
     * @param effectiveWorkflow effective guided workflow without draft or incomplete published options.
     * @param items diagnostics sink.
     * @return true when hard workflow reference validation passes.
     */
    private boolean validateWorkflowReferences(FeatureModel generatedModel, GuidedWorkflow effectiveWorkflow, List<ReportItem> items) {
        try {
            new GuidedWorkflowIntegrityService().validate(effectiveWorkflow, generatedModel);
            return true;
        }
        catch (FeatureModelIntegrityException e) {
            items.add(ReportItem.error(ReportItem.CODE_GENERATED_WORKFLOW_INVALID, e.getCode(),
                    "Bundled guided workflow failed validation against the generated model: " + e.getMessage()));
            return false;
        }
    }

    /**
     * Runs the shared coverage/capability/lifecycle diagnostics of the authored workflow against the generated model
     * and assembles the validation report with its severity-based delivery eligibility.
     *
     * @param generatedModel assembled generated model.
     * @param authoredWorkflow authored lean guided workflow, drafts included.
     * @param bundledProfile bundled deployment profile.
     * @param workflowIntegrityValid whether the effective workflow passed hard reference validation.
     * @param items diagnostics sink for the summary item.
     * @return guided workflow validation report.
     */
    private GuidedWorkflowValidationReport guidedValidation(FeatureModel generatedModel, GuidedWorkflow authoredWorkflow, DeploymentProfile bundledProfile,
            boolean workflowIntegrityValid, List<ReportItem> items) {
        Set<String> knownCapabilities = new LinkedHashSet<>(bundledProfile.providedCapabilities());
        List<GuidedWorkflowFinding> findings = new GuidedWorkflowDiagnosticsService().findings(authoredWorkflow, generatedModel, knownCapabilities);
        Map<String, Integer> severityCounts = new TreeMap<>();
        Map<String, Integer> codeCounts = new TreeMap<>();
        for (GuidedWorkflowFinding finding : findings) {
            severityCounts.merge(finding.severity(), 1, Integer::sum);
            codeCounts.merge(finding.code(), 1, Integer::sum);
        }
        boolean hasErrorFinding = findings.stream().anyMatch(GuidedWorkflowFinding::isError);
        boolean deliveryEligible = workflowIntegrityValid && !hasErrorFinding;
        String status = findings.isEmpty() ? GuidedWorkflowValidationReport.STATUS_PASS : GuidedWorkflowValidationReport.STATUS_FINDINGS;
        if (!findings.isEmpty()) {
            String message = "Guided workflow validation against the generated model produced " + findings.size()
                    + " finding(s); see guided-workflow-validation.json.";
            items.add(hasErrorFinding ? ReportItem.error(ReportItem.CODE_GUIDED_WORKFLOW_FINDINGS, generatedModel.model().id(), message)
                    : ReportItem.warning(ReportItem.CODE_GUIDED_WORKFLOW_FINDINGS, generatedModel.model().id(), message));
        }
        return new GuidedWorkflowValidationReport(status, deliveryEligible, generatedModel.model().id(), generatedModel.model().version(), severityCounts,
                codeCounts, findings);
    }
}
