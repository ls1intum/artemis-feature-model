package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import tools.jackson.databind.ObjectMapper;

class JsonFeatureModelStoreTest {

    private final JsonFeatureModelStore store = new JsonFeatureModelStore(new DefaultResourceLoader(), new ObjectMapper());

    @Test
    void loadsRuntimeFeatureModelFromClasspath() {
        var model = store.loadActiveModel();

        assertThat(model.model().id()).isEqualTo("artemis-functional-feature-tree");
        assertThat(model.model().name()).isEqualTo("Artemis Functional Feature Tree");
        assertThat(model.model().version()).isEqualTo("0.1.0");
        assertThat(model.features()).hasSize(24);
        assertThat(model.relations()).hasSize(23);
        assertThat(model.constraints()).isEmpty();
        assertThat(model.features()).anySatisfy(feature -> assertThat(feature.id()).isEqualTo("artemis"));
    }

    @Test
    void cachesLoadedRuntimeModel() {
        assertThat(store.loadActiveModel()).isSameAs(store.loadActiveModel());
    }
}
