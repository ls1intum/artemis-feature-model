package de.tum.cit.aet.artemis.featuremodel.catalog.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.featuremodel.catalog.dto.FeatureModelResponse;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;

@RestController
@RequestMapping("/api/feature-model")
public class FeatureModelResource {

    private final FeatureModelCatalogService featureModelCatalogService;

    public FeatureModelResource(FeatureModelCatalogService featureModelCatalogService) {
        this.featureModelCatalogService = featureModelCatalogService;
    }

    @GetMapping
    public FeatureModelResponse getActiveFeatureModel() {
        return featureModelCatalogService.getActiveFeatureModelResponse();
    }
}
