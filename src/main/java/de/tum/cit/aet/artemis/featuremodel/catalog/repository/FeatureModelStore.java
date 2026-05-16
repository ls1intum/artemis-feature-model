package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;

public interface FeatureModelStore {

    /**
     * Loads the currently active feature model.
     *
     * @return active feature model.
     */
    FeatureModel loadActiveModel();
}
