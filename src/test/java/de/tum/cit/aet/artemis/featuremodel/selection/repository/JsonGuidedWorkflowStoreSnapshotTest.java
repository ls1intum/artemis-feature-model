package de.tum.cit.aet.artemis.featuremodel.selection.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.LocalSnapshotRepository;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.snapshot.SnapshotTestFixtures;
import tools.jackson.databind.ObjectMapper;

class JsonGuidedWorkflowStoreSnapshotTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path dataRoot;

    private JsonGuidedWorkflowStore store(String activeSnapshotId) {
        LocalSnapshotRepository snapshotRepository = new LocalSnapshotRepository(new SnapshotProperties(dataRoot.toString(), activeSnapshotId), objectMapper);
        return new JsonGuidedWorkflowStore(new DefaultResourceLoader(), objectMapper, snapshotRepository);
    }

    @Test
    void loadsWorkflowFromActiveLocalSnapshot() {
        SnapshotTestFixtures.writeValidSnapshot(dataRoot.resolve("imported-models").resolve("active"));

        GuidedWorkflow workflow = store("active").loadActiveWorkflow();

        assertThat(workflow.workflow().id()).isEqualTo("snapshot-workflow");
        assertThat(workflow.workflow().featureModelId()).isEqualTo(SnapshotTestFixtures.MODEL_ID);
        assertThat(workflow.useCaseTemplates()).extracting("id").containsExactly("custom");
    }

    @Test
    void fallsBackToClasspathWhenNoActiveSnapshotConfigured() {
        GuidedWorkflow workflow = store(null).loadActiveWorkflow();

        assertThat(workflow.workflow().id()).isEqualTo("artemis-guided-configuration");
    }
}
