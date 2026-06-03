package de.tum.cit.aet.artemis.featuremodel.selection.repository;

import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;

public interface GuidedWorkflowStore {

    /**
     * Loads the active guided workflow definition.
     *
     * @return active guided workflow.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException if the workflow cannot be loaded.
     */
    GuidedWorkflow loadActiveWorkflow();
}
