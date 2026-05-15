package de.tum.cit.aet.artemis.featuremodel.validation.dto;

import java.util.List;

public record ValidationViolationDTO(String code, String message, List<String> featureIds, ValidationRelationDTO relation, String suggestion) {

    public ValidationViolationDTO {
        featureIds = featureIds == null ? List.of() : List.copyOf(featureIds);
    }
}
