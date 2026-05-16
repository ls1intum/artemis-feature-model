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

    /**
     * Creates the feature model resource.
     *
     * @param featureModelCatalogService catalog service used to build feature model responses.
     */
    public FeatureModelResource(FeatureModelCatalogService featureModelCatalogService) {
        this.featureModelCatalogService = featureModelCatalogService;
    }

    /**
     * Returns the active feature model API response.
     *
     * @return active feature model response.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException if the active model cannot be loaded.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException if the loaded model is structurally invalid.
     */
    @GetMapping
    public FeatureModelResponse getActiveFeatureModel() {
        return featureModelCatalogService.getActiveFeatureModelResponse();
    }
}
