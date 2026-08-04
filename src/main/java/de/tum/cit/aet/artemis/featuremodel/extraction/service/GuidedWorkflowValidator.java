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
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException;

/**
 * Validates the authored guided workflow against a generated model through the same code paths the running app uses:
 * the hard reference validation that decides snapshot eligibility, and the shared coverage, capability, and
 * consistency diagnostics that become the guided workflow validation report.
 */
class GuidedWorkflowValidator {

    /**
     * Validation result.
     *
     * @param workflowIntegrityValid whether the workflow passed hard reference validation against the model.
     * @param deliveryEligible whether hard references and all automation diagnostics passed.
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
        boolean workflowIntegrityValid = validateWorkflowReferences(generatedModel, authoredWorkflow, items);
        GuidedWorkflowValidationReport guidedValidation = guidedValidation(generatedModel, authoredWorkflow, bundledProfile, items);
        boolean deliveryEligible = workflowIntegrityValid && GuidedWorkflowValidationReport.STATUS_PASS.equals(guidedValidation.status());
        return new Result(workflowIntegrityValid, deliveryEligible, guidedValidation, List.copyOf(items));
    }

    /**
     * Runs the guided workflow's hard reference validation against the generated model.
     *
     * @param generatedModel assembled generated model.
     * @param authoredWorkflow authored lean guided workflow.
     * @param items diagnostics sink.
     * @return true when hard workflow reference validation passes.
     */
    private boolean validateWorkflowReferences(FeatureModel generatedModel, GuidedWorkflow authoredWorkflow, List<ReportItem> items) {
        try {
            new GuidedWorkflowIntegrityService().validate(authoredWorkflow, generatedModel);
            return true;
        }
        catch (FeatureModelIntegrityException e) {
            items.add(ReportItem.error(ReportItem.CODE_GENERATED_WORKFLOW_INVALID, e.getCode(),
                    "Bundled guided workflow failed validation against the generated model: " + e.getMessage()));
            return false;
        }
    }

    /**
     * Runs the shared coverage/capability/consistency diagnostics of the authored workflow against the generated model
     * and assembles the validation report with its automation status.
     *
     * @param generatedModel assembled generated model.
     * @param authoredWorkflow authored lean guided workflow.
     * @param bundledProfile bundled deployment profile.
     * @param items diagnostics sink for the summary item.
     * @return guided workflow validation report.
     */
    private GuidedWorkflowValidationReport guidedValidation(FeatureModel generatedModel, GuidedWorkflow authoredWorkflow, DeploymentProfile bundledProfile,
            List<ReportItem> items) {
        Set<String> knownCapabilities = new LinkedHashSet<>(bundledProfile.providedCapabilities());
        List<GuidedWorkflowFinding> findings = new GuidedWorkflowDiagnosticsService().findings(authoredWorkflow, generatedModel, knownCapabilities);
        Map<String, Integer> codeCounts = new TreeMap<>();
        findings.forEach(finding -> codeCounts.merge(finding.code(), 1, Integer::sum));
        String status = findings.isEmpty() ? GuidedWorkflowValidationReport.STATUS_PASS : GuidedWorkflowValidationReport.STATUS_FINDINGS;
        if (!findings.isEmpty()) {
            items.add(ReportItem.error(ReportItem.CODE_GUIDED_WORKFLOW_FINDINGS, generatedModel.model().id(),
                    "Guided workflow validation against the generated model produced " + findings.size() + " finding(s); see guided-workflow-validation.json."));
        }
        return new GuidedWorkflowValidationReport(status, generatedModel.model().id(), generatedModel.model().version(), codeCounts, findings);
    }
}
