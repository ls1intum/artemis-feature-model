package de.tum.cit.aet.artemis.featuremodel.validation.dto;

import java.util.List;

public record ValidationResultDTO(boolean valid, List<String> normalizedSelection, List<ValidationViolationDTO> violations,
        List<ValidationWarningDTO> warnings) {

    public ValidationResultDTO {
        normalizedSelection = normalizedSelection == null ? List.of() : List.copyOf(normalizedSelection);
        violations = violations == null ? List.of() : List.copyOf(violations);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
