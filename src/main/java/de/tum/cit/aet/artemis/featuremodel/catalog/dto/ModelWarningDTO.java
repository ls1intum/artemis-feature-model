package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import java.util.List;

public record ModelWarningDTO(String code, String message, List<String> featureIds, String constraintId) {

    /**
     * Creates a model warning DTO and normalizes nullable feature ids to an immutable empty list.
     *
     * @param code stable warning code.
     * @param message human-readable warning message.
     * @param featureIds related feature ids.
     * @param constraintId related constraint id, if applicable.
     */
    public ModelWarningDTO {
        featureIds = featureIds == null ? List.of() : List.copyOf(featureIds);
    }
}
