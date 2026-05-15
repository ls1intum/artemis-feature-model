package de.tum.cit.aet.artemis.featuremodel.validation.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.validation.domain.ValidationCode;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class FeatureModelValidationResourceTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FeatureModelValidationResource(validationService())).build();

    @Test
    void validatesSubmittedSelection() throws Exception {
        mockMvc.perform(post("/api/feature-model/validate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedFeatureIds\":[\"course-workflow\",\"communication\",\"exercise-common\"]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false)).andExpect(jsonPath("$.normalizedSelection", hasItem("course-workflow")))
                .andExpect(jsonPath("$.violations[*].code", hasItem(ValidationCode.MANDATORY_FEATURE_MISSING.name()))).andExpect(jsonPath("$.warnings.length()").value(0));
    }

    private FeatureModelValidationService validationService() {
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        FeatureModelCatalogService catalogService = new FeatureModelCatalogService(new JsonFeatureModelStore(new DefaultResourceLoader(), new ObjectMapper()),
                new FeatureModelIntegrityService(), treeService);
        return new FeatureModelValidationService(catalogService, treeService);
    }
}
