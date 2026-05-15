package de.tum.cit.aet.artemis.featuremodel.catalog.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class FeatureModelResourceTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FeatureModelResource(catalogService())).build();

    @Test
    void returnsActiveFeatureModelContract() throws Exception {
        mockMvc.perform(get("/api/feature-model")).andExpect(status().isOk()).andExpect(jsonPath("$.model.name").value("Artemis Functional Feature Tree"))
                .andExpect(jsonPath("$.features.length()").value(24)).andExpect(jsonPath("$.relations.length()").value(23)).andExpect(jsonPath("$.constraints.length()").value(0))
                .andExpect(jsonPath("$.tree.feature.id").value("artemis")).andExpect(jsonPath("$.tree.incomingRelation").doesNotExist())
                .andExpect(jsonPath("$.defaultSelectedFeatureIds", hasItem("programming"))).andExpect(jsonPath("$.warnings.length()").value(0));
    }

    private FeatureModelCatalogService catalogService() {
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        return new FeatureModelCatalogService(new JsonFeatureModelStore(new DefaultResourceLoader(), new ObjectMapper()), new FeatureModelIntegrityService(), treeService);
    }
}
