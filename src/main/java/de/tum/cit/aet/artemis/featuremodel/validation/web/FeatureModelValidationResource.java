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

    public FeatureModelValidationResource(FeatureModelValidationService featureModelValidationService) {
        this.featureModelValidationService = featureModelValidationService;
    }

    @PostMapping("/validate")
    public ValidationResultDTO validateSelection(@RequestBody ValidationRequest request) {
        return featureModelValidationService.validateSelection(request);
    }
}
