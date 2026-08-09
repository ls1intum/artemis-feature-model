package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowFinding;

/**
 * Payload of {@code guided-workflow-validation.json}: the coverage, capability, lifecycle, and consistency findings of
 * the bundled guided workflow validated against the generated model. The {@code status} field states whether any
 * findings exist; the delivery gate is {@code deliveryEligible}, which follows finding severity: {@code error}
 * findings block a run, while {@code warning} and {@code info} findings publish. A generated model that adds a feature
 * without guided coverage therefore surfaces mechanically as a warning without blocking delivery.
 *
 * @param status {@code pass} when no findings exist, otherwise {@code findings}.
 * @param deliveryEligible whether hard reference validation passed and no error-severity finding exists.
 * @param generatedModelId id of the generated model the workflow was validated against.
 * @param generatedModelVersion version of the generated model.
 * @param severityCounts finding counts per severity, sorted by severity.
 * @param codeCounts finding counts per code, sorted by code.
 * @param findings findings in deterministic order.
 */
public record GuidedWorkflowValidationReport(String status, boolean deliveryEligible, String generatedModelId, String generatedModelVersion,
        Map<String, Integer> severityCounts, Map<String, Integer> codeCounts, List<GuidedWorkflowFinding> findings) {

    /** Status of a validation without findings. */
    public static final String STATUS_PASS = "pass";

    /** Status of a validation with findings; delivery eligibility depends on their severities, not on this status. */
    public static final String STATUS_FINDINGS = "findings";

    /**
     * Normalizes the finding list to an immutable copy.
     */
    public GuidedWorkflowValidationReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
