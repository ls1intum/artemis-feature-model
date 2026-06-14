package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.snapshot.SnapshotTestFixtures;
import tools.jackson.databind.ObjectMapper;

class JsonFeatureModelStoreSnapshotTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path dataRoot;

    private JsonFeatureModelStore store(String activeSnapshotId) {
        LocalSnapshotRepository snapshotRepository = new LocalSnapshotRepository(new SnapshotProperties(dataRoot.toString(), activeSnapshotId), objectMapper);
        return new JsonFeatureModelStore(new DefaultResourceLoader(), objectMapper, snapshotRepository);
    }

    @Test
    void loadsModelFromActiveLocalSnapshot() {
        SnapshotTestFixtures.writeValidSnapshot(dataRoot.resolve("imported-models").resolve("active"));

        FeatureModel model = store("active").loadActiveModel();

        assertThat(model.model().id()).isEqualTo(SnapshotTestFixtures.MODEL_ID);
        assertThat(model.features()).extracting(FeatureNode::id).contains("lecture");
    }

    @Test
    void fallsBackToClasspathWhenNoActiveSnapshotConfigured() {
        FeatureModel model = store(null).loadActiveModel();

        assertThat(model.model().id()).isEqualTo("artemis-functional-feature-tree");
    }
}
