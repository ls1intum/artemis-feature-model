package de.tum.cit.aet.artemis.featuremodel.validation.dto;

import java.util.List;

public record ValidationResultDTO(boolean valid, List<String> normalizedSelection, List<ValidationViolationDTO> violations,
        List<ValidationWarningDTO> warnings) {

    /**
     * Creates a validation result and normalizes nullable collections to immutable empty lists.
     *
     * @param valid whether the submitted selection is valid.
     * @param normalizedSelection known selected feature ids in normalized order.
     * @param violations validation violations.
     * @param warnings validation warnings.
     */
    public ValidationResultDTO {
        normalizedSelection = normalizedSelection == null ? List.of() : List.copyOf(normalizedSelection);
        violations = violations == null ? List.of() : List.copyOf(violations);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
