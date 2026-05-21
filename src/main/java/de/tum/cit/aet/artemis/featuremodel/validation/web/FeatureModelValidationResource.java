package de.tum.cit.aet.artemis.featuremodel.validation.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(FeatureModelValidationResource.class);

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
        log.debug("REST request to validate a feature selection with {} submitted feature ids.", request.selectedFeatureIds().size());

        ValidationResultDTO result = featureModelValidationService.validateSelection(request);
        log.info("REST response for feature selection validation: valid={}, normalizedSelectionSize={}, violations={}, warnings={}.", result.valid(),
                result.normalizedSelection().size(), result.violations().size(), result.warnings().size());
        return result;
    }
}
