package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import java.util.List;

public record ModelWarningDTO(String code, String message, List<String> featureIds, String constraintId) {

    public ModelWarningDTO {
        featureIds = featureIds == null ? List.of() : List.copyOf(featureIds);
    }
}
