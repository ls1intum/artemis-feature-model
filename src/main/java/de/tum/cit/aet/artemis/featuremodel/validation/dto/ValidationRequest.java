package de.tum.cit.aet.artemis.featuremodel.validation.dto;

import java.util.List;

public record ValidationRequest(List<String> selectedFeatureIds) {

    public ValidationRequest {
        selectedFeatureIds = selectedFeatureIds == null ? List.of() : List.copyOf(selectedFeatureIds);
    }
}
