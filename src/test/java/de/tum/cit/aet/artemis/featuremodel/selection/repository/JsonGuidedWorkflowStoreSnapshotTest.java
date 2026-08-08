package de.tum.cit.aet.artemis.featuremodel.selection.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import tools.jackson.databind.ObjectMapper;

class JsonGuidedWorkflowStoreSnapshotTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadsWorkflowFromValidatedClasspathBundle() {
        GuidedWorkflow workflow = new JsonGuidedWorkflowStore(new DefaultResourceLoader(), objectMapper).loadActiveWorkflow();

        assertThat(workflow.workflow().id()).isEqualTo("artemis-guided-configuration");
    }
}
