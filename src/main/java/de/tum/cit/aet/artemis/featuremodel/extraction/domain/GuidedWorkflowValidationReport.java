package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowFinding;

/**
 * Payload of {@code guided-workflow-validation.json}: the coverage, capability, and consistency findings of the
 * bundled guided workflow validated against the generated model. The {@code status} field is the automation contract
 * for the later recurring drift job: any value other than {@code pass} is a non-zero signal, so a generated model that
 * adds a feature without guided coverage surfaces mechanically instead of through a manual re-audit.
 *
 * @param status {@code pass} when no findings exist, otherwise {@code findings}.
 * @param generatedModelId id of the generated model the workflow was validated against.
 * @param generatedModelVersion version of the generated model.
 * @param codeCounts finding counts per code, sorted by code.
 * @param findings findings in deterministic order.
 */
public record GuidedWorkflowValidationReport(String status, String generatedModelId, String generatedModelVersion, Map<String, Integer> codeCounts,
        List<GuidedWorkflowFinding> findings) {

    /** Status of a validation without findings. */
    public static final String STATUS_PASS = "pass";

    /** Status of a validation with findings; the E4 automation treats it as a non-zero exit signal. */
    public static final String STATUS_FINDINGS = "findings";

    /**
     * Normalizes the finding list to an immutable copy.
     */
    public GuidedWorkflowValidationReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
