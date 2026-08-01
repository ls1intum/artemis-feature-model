package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * Contract of the workflow preparation stage, written as {@code workflow/workflow-result.json}. It binds the prepared
 * workflow to the generated model it was validated against and to the authored file it was prepared from.
 *
 * @param schemaVersion schema version of this envelope.
 * @param extractorVersion version of the extraction pipeline that prepared the workflow.
 * @param artemisCommit resolved git commit of the run.
 * @param generatedModelDigest digest of the generated feature model the workflow was validated against.
 * @param authoredWorkflowDigest digest of the authored guided workflow the preparation consumed.
 * @param preparedWorkflowDigest digest of the prepared guided workflow file.
 * @param workflowIntegrityValid whether the workflow passed its hard reference validation against the model.
 */
public record WorkflowResult(int schemaVersion, String extractorVersion, String artemisCommit, String generatedModelDigest, String authoredWorkflowDigest,
        String preparedWorkflowDigest, boolean workflowIntegrityValid) {

    /** Current schema version of the workflow envelope. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
