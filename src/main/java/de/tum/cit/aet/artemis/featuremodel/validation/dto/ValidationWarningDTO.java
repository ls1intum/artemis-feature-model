package de.tum.cit.aet.artemis.featuremodel.validation.dto;

import java.util.List;

public record ValidationWarningDTO(String code, String message, List<String> featureIds, String constraintId, String suggestion) {

    /**
     * Creates a validation warning and normalizes nullable feature ids to an immutable empty list.
     *
     * @param code stable warning code.
     * @param message human-readable warning message.
     * @param featureIds related feature ids.
     * @param constraintId related constraint id, if applicable.
     * @param suggestion suggested manual action, if available.
     */
    public ValidationWarningDTO {
        featureIds = featureIds == null ? List.of() : List.copyOf(featureIds);
    }
}
