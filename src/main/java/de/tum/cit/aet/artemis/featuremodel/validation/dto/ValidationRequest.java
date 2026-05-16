package de.tum.cit.aet.artemis.featuremodel.validation.dto;

import java.util.List;

public record ValidationRequest(List<String> selectedFeatureIds) {

    /**
     * Creates a validation request and normalizes nullable selected ids to an immutable empty list.
     *
     * @param selectedFeatureIds submitted selected feature ids.
     */
    public ValidationRequest {
        selectedFeatureIds = selectedFeatureIds == null ? List.of() : List.copyOf(selectedFeatureIds);
    }
}
