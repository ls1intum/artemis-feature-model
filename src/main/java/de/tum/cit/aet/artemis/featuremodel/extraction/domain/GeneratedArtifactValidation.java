package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * Hard integrity state of the generated artifacts, separate from fail-soft scan and workflow diagnostics.
 *
 * @param modelIntegrityValid whether the generated model passed shared structural integrity validation.
 * @param workflowIntegrityValid whether the bundled workflow passed hard reference validation against the model.
 */
public record GeneratedArtifactValidation(boolean modelIntegrityValid, boolean workflowIntegrityValid) {

    /**
     * Indicates whether the generated model and workflow may be published as an importable snapshot.
     *
     * @return true only when both hard integrity checks passed.
     */
    public boolean snapshotEligible() {
        return modelIntegrityValid && workflowIntegrityValid;
    }
}
