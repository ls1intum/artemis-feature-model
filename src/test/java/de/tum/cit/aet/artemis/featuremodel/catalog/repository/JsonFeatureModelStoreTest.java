package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import tools.jackson.databind.ObjectMapper;

class JsonFeatureModelStoreTest {

    private final JsonFeatureModelStore store = new JsonFeatureModelStore(new DefaultResourceLoader(), new ObjectMapper());

    @Test
    void loadsRuntimeFeatureModelFromClasspath() {
        var model = store.loadActiveModel();

        assertThat(model.model().id()).isEqualTo("artemis-functional-feature-tree");
        assertThat(model.model().name()).isEqualTo("Artemis Functional Feature Tree");
        assertThat(model.model().version()).isEqualTo("0.1.0");
        assertThat(model.model().status()).isEqualTo("published");
        assertThat(model.model().sourceCommitSha()).isNull();
        assertThat(model.features()).isNotEmpty();
        assertThat(model.relations()).isNotEmpty();
        assertThat(model.constraints()).isEmpty();
        Set<String> featureIds = model.features().stream().map(feature -> feature.id()).collect(Collectors.toSet());
        assertThat(model.relations()).allSatisfy(relation -> {
            assertThat(featureIds).contains(relation.parentId());
            assertThat(featureIds).contains(relation.childId());
        });
        assertThat(model.features()).anySatisfy(feature -> {
            assertThat(feature.id()).isEqualTo("artemis");
            assertThat(feature.category()).isEqualTo("derived");
            assertThat(feature.configurableBy()).isEmpty();
        });
        assertThat(model.features()).anySatisfy(feature -> {
            assertThat(feature.id()).isEqualTo("text");
            assertThat(feature.category()).isEqualTo("functional");
            assertThat(feature.visibleTo()).containsExactly("teacher", "maintainer");
            assertThat(feature.configurableBy()).containsExactly("teacher", "maintainer");
            assertThat(feature.requiresCapabilities()).isEmpty();
            assertThat(feature.artifactMappings()).singleElement().satisfies(mapping -> {
                assertThat(mapping.target()).isEqualTo("application-core.yml");
                assertThat(mapping.path()).isEqualTo("artemis.text.enabled");
                assertThat(mapping.valueWhenSelected().booleanValue()).isTrue();
                assertThat(mapping.valueWhenDeselected().booleanValue()).isFalse();
                assertThat(mapping.valueFromProfile()).isNull();
            });
            assertThat(feature.extraction().method()).isEqualTo("manual-curation");
            assertThat(feature.extraction().confidence()).isEqualTo("high");
            assertThat(feature.extraction().status()).isEqualTo("manually_confirmed");
        });
    }

    @Test
    void cachesLoadedRuntimeModel() {
        assertThat(store.loadActiveModel()).isSameAs(store.loadActiveModel());
    }

    @Test
    void loadsOlderJsonSnapshotWithoutPhaseOneFields() throws Exception {
        String snapshot = """
                {
                  "model": {
                    "id": "legacy-model",
                    "name": "Legacy Model",
                    "version": "0.0.1"
                  },
                  "features": [
                    {
                      "id": "artemis",
                      "name": "Artemis",
                      "kind": "root",
                      "selectable": false,
                      "defaultState": "not_applicable"
                    },
                    {
                      "id": "lecture",
                      "name": "Lecture",
                      "kind": "module",
                      "selectable": true,
                      "defaultState": "enabled"
                    }
                  ],
                  "relations": [],
                  "constraints": []
                }
                """;

        FeatureModel model = new ObjectMapper().readValue(snapshot, FeatureModel.class);

        assertThat(model.model().status()).isNull();
        assertThat(model.model().sourceCommitSha()).isNull();
        assertThat(model.features()).hasSize(2);
        assertThat(model.features().getFirst().category()).isEqualTo("derived");
        assertThat(model.features().getFirst().visibleTo()).isEmpty();
        assertThat(model.features().getFirst().configurableBy()).isEmpty();
        assertThat(model.features().get(1).category()).isEqualTo("functional");
        assertThat(model.features().get(1).configurableBy()).containsExactly("teacher", "maintainer");
        assertThat(model.features().get(1).requiresCapabilities()).isEmpty();
        assertThat(model.features().get(1).artifactMappings()).isEmpty();
        assertThat(model.features().get(1).extraction()).isNull();
    }
}
