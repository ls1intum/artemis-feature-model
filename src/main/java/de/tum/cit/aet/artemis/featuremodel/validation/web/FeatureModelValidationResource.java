package de.tum.cit.aet.artemis.featuremodel.validation.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.featuremodel.validation.dto.ValidationRequest;
import de.tum.cit.aet.artemis.featuremodel.validation.dto.ValidationResultDTO;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;

@RestController
@RequestMapping("/api/feature-model")
public class FeatureModelValidationResource {

    private final FeatureModelValidationService featureModelValidationService;

    /**
     * Creates the validation resource.
     *
     * @param featureModelValidationService validation service used to validate submitted selections.
     */
    public FeatureModelValidationResource(FeatureModelValidationService featureModelValidationService) {
        this.featureModelValidationService = featureModelValidationService;
    }

    /**
     * Validates a submitted feature selection.
     *
     * @param request validation request.
     * @return validation result.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException if the active model cannot be loaded.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException if the active model is structurally invalid.
     */
    @PostMapping("/validate")
    public ValidationResultDTO validateSelection(@RequestBody ValidationRequest request) {
        return featureModelValidationService.validateSelection(request);
    }
}
