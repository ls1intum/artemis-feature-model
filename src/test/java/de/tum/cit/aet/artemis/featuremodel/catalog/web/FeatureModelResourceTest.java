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

    private final FeatureModelCatalogService catalogService = catalogService();

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FeatureModelResource(catalogService)).build();

    @Test
    void returnsActiveFeatureModelContract() throws Exception {
        var model = catalogService.loadActiveModel();

        mockMvc.perform(get("/api/feature-model")).andExpect(status().isOk())
                .andExpect(jsonPath("$.model.name").value("Artemis Generated Feature Model"))
                .andExpect(jsonPath("$.model.status").value("generated"))
                .andExpect(jsonPath("$.model.sourceCommitSha").isNotEmpty())
                .andExpect(jsonPath("$.features.length()").value(model.features().size()))
                .andExpect(jsonPath("$.features[?(@.id == 'artemis')].category", hasItem("derived")))
                .andExpect(jsonPath("$.features[?(@.id == 'text')].category", hasItem("functional")))
                .andExpect(jsonPath("$.features[?(@.id == 'text')].visibleTo[0]", hasItem("teacher")))
                .andExpect(jsonPath("$.features[?(@.id == 'text')].configurableBy[0]", hasItem("teacher")))
                .andExpect(jsonPath("$.features[?(@.id == 'text')].requiresCapabilities.length()", hasItem(0)))
                .andExpect(jsonPath("$.features[?(@.id == 'text')].artifactMappings[0].path", hasItem("artemis.text.enabled")))
                .andExpect(jsonPath("$.features[?(@.id == 'text')].extraction.status", hasItem("generated")))
                .andExpect(jsonPath("$.features[?(@.id == 'database')].category", hasItem("technical")))
                .andExpect(jsonPath("$.features[?(@.id == 'jenkins')].visibleTo[0]", hasItem("maintainer")))
                .andExpect(jsonPath("$.relations.length()").value(model.relations().size()))
                .andExpect(jsonPath("$.constraints.length()").value(3))
                .andExpect(jsonPath("$.constraints[?(@.id == 'apollon-requires-modeling')].source", hasItem("apollon")))
                .andExpect(jsonPath("$.constraints[?(@.id == 'apollon-requires-modeling')].target", hasItem("modeling")))
                .andExpect(jsonPath("$.tree.feature.id").value("artemis"))
                .andExpect(jsonPath("$.tree.feature.category").value("derived"))
                .andExpect(jsonPath("$.tree.incomingRelation").doesNotExist())
                .andExpect(jsonPath("$.defaultSelectedFeatureIds", hasItem("programming")))
                .andExpect(jsonPath("$.defaultSelectedFeatureIds", hasItem("mysql")))
                .andExpect(jsonPath("$.defaultSelectedFeatureIds", hasItem("integrated-code-lifecycle")))
                .andExpect(jsonPath("$.defaultSelectedFeatureIds", hasItem("localvc")))
                .andExpect(jsonPath("$.warnings.length()").value(0));
    }

    private FeatureModelCatalogService catalogService() {
        FeatureModelTreeService treeService = new FeatureModelTreeService();
        JsonFeatureModelStore store = new JsonFeatureModelStore(new DefaultResourceLoader(), new ObjectMapper());
        return new FeatureModelCatalogService(store, new FeatureModelIntegrityService(), treeService);
    }
}
