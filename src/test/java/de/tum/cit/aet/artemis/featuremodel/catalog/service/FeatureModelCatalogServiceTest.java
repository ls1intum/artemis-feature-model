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
        var model = service.loadActiveModel();
        var response = service.getActiveFeatureModelResponse();

        assertThat(response.model().name()).isEqualTo("Artemis Functional Feature Tree");
        assertThat(response.model().status()).isEqualTo("published");
        assertThat(response.model().sourceCommitSha()).isNull();
        assertThat(response.features()).hasSameSizeAs(model.features());
        assertThat(response.relations()).hasSameSizeAs(model.relations());
        assertThat(response.constraints()).isEmpty();
        assertThat(response.tree().feature().id()).isEqualTo("artemis");
        assertThat(response.tree().feature().category()).isEqualTo("derived");
        assertThat(response.features()).anySatisfy(feature -> {
            assertThat(feature.id()).isEqualTo("text");
            assertThat(feature.category()).isEqualTo("functional");
            assertThat(feature.visibleTo()).containsExactly("teacher", "maintainer");
            assertThat(feature.configurableBy()).containsExactly("teacher", "maintainer");
            assertThat(feature.requiresCapabilities()).isEmpty();
            assertThat(feature.artifactMappings()).singleElement().satisfies(mapping -> assertThat(mapping.path()).isEqualTo("artemis.text.enabled"));
            assertThat(feature.extraction().status()).isEqualTo("manually_confirmed");
        });
        assertThat(response.defaultSelectedFeatureIds()).containsAll(List.of("programming", "quiz", "atlas"));
        assertThat(response.warnings()).isEmpty();
    }

    private FeatureModelCatalogService catalogService() {
        JsonFeatureModelStore store = new JsonFeatureModelStore(new DefaultResourceLoader(), new ObjectMapper());
        return new FeatureModelCatalogService(store, new FeatureModelIntegrityService(), treeService);
    }
}
