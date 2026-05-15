package de.tum.cit.aet.artemis.featuremodel.validation.domain;

import java.util.List;
import java.util.Set;

public record NormalizedSelection(List<String> selectedFeatureIds, Set<String> submittedFeatureIds) {

    /**
     * Creates a normalized selection and normalizes nullable collections to immutable empty collections.
     *
     * @param selectedFeatureIds known selected feature ids in normalized order.
     * @param submittedFeatureIds unique submitted feature ids.
     */
    public NormalizedSelection {
        selectedFeatureIds = selectedFeatureIds == null ? List.of() : List.copyOf(selectedFeatureIds);
        submittedFeatureIds = submittedFeatureIds == null ? Set.of() : Set.copyOf(submittedFeatureIds);
    }
}
