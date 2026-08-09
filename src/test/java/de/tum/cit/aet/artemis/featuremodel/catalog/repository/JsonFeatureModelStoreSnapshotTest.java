package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import tools.jackson.databind.ObjectMapper;

class JsonFeatureModelStoreSnapshotTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadsModelFromValidatedClasspathBundle() {
        FeatureModel model = new JsonFeatureModelStore(new DefaultResourceLoader(), objectMapper).loadActiveModel();

        assertThat(model.model().id()).isEqualTo("artemis-generated-feature-model");
    }
}
