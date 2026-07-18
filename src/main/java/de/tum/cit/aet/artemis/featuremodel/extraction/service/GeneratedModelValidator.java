package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GuidedWorkflowValidationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowFinding;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowDiagnosticsService;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException;

/**
 * Validates the assembled generated model through the same code paths the running app uses: the structural model
 * integrity rules, the guided workflow's hard reference validation, and the shared coverage/capability/consistency
 * diagnostics — plus the two E3-specific rules: no technical feature may be visible or configurable for teachers, and
 * every capability an included technical feature provides must be listed by the bundled deployment profile.
 */
class GeneratedModelValidator {

    private static final String ROLE_TEACHER = "teacher";

    /**
     * Validation result.
     *
     * @param guidedValidation coverage and consistency findings of the workflow against the generated model.
     * @param items validation diagnostics for the extraction report.
     */
    record Result(GuidedWorkflowValidationReport guidedValidation, List<ReportItem> items) {
    }

    /**
     * Validates the generated model.
     *
     * @param generatedModel assembled generated model.
     * @param includedFeatures resolved include semantics carrying the manifest-declared provided capabilities.
     * @param bundledWorkflow lean bundled guided workflow.
     * @param bundledProfile bundled deployment profile.
     * @return guided validation report and report items.
     */
    Result validate(FeatureModel generatedModel, List<ResolvedFeatureScope> includedFeatures, GuidedWorkflow bundledWorkflow, DeploymentProfile bundledProfile) {
        List<ReportItem> items = new ArrayList<>();
        validateModelIntegrity(generatedModel, items);
        validateWorkflowReferences(generatedModel, bundledWorkflow, items);
        validateRoleVisibility(generatedModel, items);
        validateProvidedCapabilities(includedFeatures, bundledProfile, items);
        GuidedWorkflowValidationReport guidedValidation = guidedValidation(generatedModel, bundledWorkflow, bundledProfile, items);
        return new Result(guidedValidation, List.copyOf(items));
    }

    /**
     * Runs the shared structural integrity rules on the generated model.
     *
     * @param generatedModel assembled generated model.
     * @param items diagnostics sink.
     */
    private void validateModelIntegrity(FeatureModel generatedModel, List<ReportItem> items) {
        try {
            new FeatureModelIntegrityService().validate(generatedModel);
        }
        catch (FeatureModelIntegrityException e) {
            items.add(ReportItem.error(ReportItem.CODE_GENERATED_MODEL_INVALID, e.getCode(), "Generated model failed integrity validation: " + e.getMessage()));
        }
    }

    /**
     * Runs the guided workflow's hard reference validation against the generated model.
     *
     * @param generatedModel assembled generated model.
     * @param bundledWorkflow lean bundled guided workflow.
     * @param items diagnostics sink.
     */
    private void validateWorkflowReferences(FeatureModel generatedModel, GuidedWorkflow bundledWorkflow, List<ReportItem> items) {
        try {
            new GuidedWorkflowIntegrityService().validate(bundledWorkflow, generatedModel);
        }
        catch (FeatureModelIntegrityException e) {
            items.add(ReportItem.error(ReportItem.CODE_GENERATED_WORKFLOW_INVALID, e.getCode(),
                    "Bundled guided workflow failed validation against the generated model: " + e.getMessage()));
        }
    }

    /**
     * Enforces the role-visibility rule: no technical feature may be visible to or configurable by teachers.
     *
     * @param generatedModel assembled generated model.
     * @param items diagnostics sink.
     */
    private void validateRoleVisibility(FeatureModel generatedModel, List<ReportItem> items) {
        for (FeatureNode feature : generatedModel.features()) {
            if (!FeatureScopeManifest.CATEGORY_TECHNICAL.equals(feature.category())) {
                continue;
            }
            if (feature.visibleTo().contains(ROLE_TEACHER) || feature.configurableBy().contains(ROLE_TEACHER)) {
                items.add(ReportItem.error(ReportItem.CODE_TECHNICAL_FEATURE_ROLE_LEAK, feature.id(),
                        "Technical feature '" + feature.id() + "' is visible or configurable for teachers."));
            }
        }
    }

    /**
     * Cross-checks the capabilities included technical features provide against the bundled profile's provided
     * capabilities. A mismatch is a warning: the profile and the technical selection describe the same deployment
     * context and should agree. The model schema carries no provides list, so the check consumes the resolved
     * manifest declarations directly.
     *
     * @param includedFeatures resolved include semantics.
     * @param bundledProfile bundled deployment profile.
     * @param items diagnostics sink.
     */
    private void validateProvidedCapabilities(List<ResolvedFeatureScope> includedFeatures, DeploymentProfile bundledProfile, List<ReportItem> items) {
        for (ResolvedFeatureScope included : includedFeatures) {
            if (!FeatureScopeManifest.CATEGORY_TECHNICAL.equals(included.category())) {
                continue;
            }
            for (String capability : included.providesCapabilities()) {
                if (!bundledProfile.providesCapability(capability)) {
                    items.add(ReportItem.warning(ReportItem.CODE_PROFILE_CAPABILITY_MISMATCH, included.id(), "Technical feature '" + included.id()
                            + "' provides capability '" + capability + "' which the bundled profile '" + bundledProfile.id() + "' does not list."));
                }
            }
        }
    }

    /**
     * Runs the shared coverage/capability/consistency diagnostics of the bundled workflow against the generated model
     * and assembles the validation report with its automation status.
     *
     * @param generatedModel assembled generated model.
     * @param bundledWorkflow lean bundled guided workflow.
     * @param bundledProfile bundled deployment profile.
     * @param items diagnostics sink for the summary item.
     * @return guided workflow validation report.
     */
    private GuidedWorkflowValidationReport guidedValidation(FeatureModel generatedModel, GuidedWorkflow bundledWorkflow, DeploymentProfile bundledProfile,
            List<ReportItem> items) {
        Set<String> knownCapabilities = new LinkedHashSet<>(bundledProfile.providedCapabilities());
        List<GuidedWorkflowFinding> findings = new GuidedWorkflowDiagnosticsService().findings(bundledWorkflow, generatedModel, knownCapabilities);
        Map<String, Integer> codeCounts = new TreeMap<>();
        findings.forEach(finding -> codeCounts.merge(finding.code(), 1, Integer::sum));
        String status = findings.isEmpty() ? GuidedWorkflowValidationReport.STATUS_PASS : GuidedWorkflowValidationReport.STATUS_FINDINGS;
        if (!findings.isEmpty()) {
            items.add(ReportItem.warning(ReportItem.CODE_GUIDED_WORKFLOW_FINDINGS, generatedModel.model().id(),
                    "Guided workflow validation against the generated model produced " + findings.size() + " finding(s); see guided-workflow-validation.json."));
        }
        return new GuidedWorkflowValidationReport(status, generatedModel.model().id(), generatedModel.model().version(), codeCounts, findings);
    }
}
