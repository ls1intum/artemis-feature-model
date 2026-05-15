package de.tum.cit.aet.artemis.featuremodel.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.visualization.service.FeatureModelTreeService;
import tools.jackson.databind.ObjectMapper;

class FeatureModelCatalogServiceTest {

    private final FeatureModelTreeService treeService = new FeatureModelTreeService();

    private final FeatureModelCatalogService service = catalogService();

    @Test
    void derivesDefaultSelectedFeatureIdsInTreeOrder() {
        var model = service.loadActiveModel();

        assertThat(service.defaultSelectedFeatureIds(model)).containsExactly("lecture", "tutorialgroup", "course-workflow", "communication",
                "exercise-common", "programming", "quiz", "text", "modeling", "file-upload", "exam", "plagiarism", "atlas");
    }

    @Test
    void activeModelResponseContainsSourceModelAndDerivedData() {
        var response = service.getActiveFeatureModelResponse();

        assertThat(response.model().name()).isEqualTo("Artemis Functional Feature Tree");
        assertThat(response.features()).hasSize(24);
        assertThat(response.relations()).hasSize(23);
        assertThat(response.constraints()).isEmpty();
        assertThat(response.tree().feature().id()).isEqualTo("artemis");
        assertThat(response.defaultSelectedFeatureIds()).containsAll(List.of("programming", "quiz", "atlas"));
        assertThat(response.warnings()).isEmpty();
    }

    private FeatureModelCatalogService catalogService() {
        JsonFeatureModelStore store = new JsonFeatureModelStore(new DefaultResourceLoader(), new ObjectMapper());
        return new FeatureModelCatalogService(store, new FeatureModelIntegrityService(), treeService);
    }
}
