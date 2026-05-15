package de.tum.cit.aet.artemis.featuremodel.validation.dto;

import java.util.List;

public record ValidationWarningDTO(String code, String message, List<String> featureIds, String constraintId, String suggestion) {

    public ValidationWarningDTO {
        featureIds = featureIds == null ? List.of() : List.copyOf(featureIds);
    }
}
