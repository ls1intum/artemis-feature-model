package de.tum.cit.aet.artemis.featuremodel.validation.dto;

import java.util.List;

public record ValidationViolationDTO(String code, String message, List<String> featureIds, ValidationRelationDTO relation, String suggestion) {

    /**
     * Creates a validation violation and normalizes nullable feature ids to an immutable empty list.
     *
     * @param code stable violation code.
     * @param message human-readable violation message.
     * @param featureIds related feature ids.
     * @param relation related relation, if applicable.
     * @param suggestion suggested repair action, if available.
     */
    public ValidationViolationDTO {
        featureIds = featureIds == null ? List.of() : List.copyOf(featureIds);
    }
}
