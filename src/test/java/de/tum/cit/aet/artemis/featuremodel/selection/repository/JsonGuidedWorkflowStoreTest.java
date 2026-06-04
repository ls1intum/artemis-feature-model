package de.tum.cit.aet.artemis.featuremodel.selection.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.JsonFeatureModelStore;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecision;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowIntegrityService;
import tools.jackson.databind.ObjectMapper;

class JsonGuidedWorkflowStoreTest {

    private static final String ACTIVE_SCHEMA_RESOURCE = "classpath:feature-model/guided-workflow.schema.json";

    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JsonGuidedWorkflowStore store = new JsonGuidedWorkflowStore(resourceLoader, objectMapper);

    @Test
    void loadsRuntimeGuidedWorkflowFromClasspath() {
        GuidedWorkflow workflow = store.loadActiveWorkflow();

        assertThat(workflow.workflow().id()).isEqualTo("artemis-guided-configuration");
        assertThat(workflow.workflow().featureModelId()).isEqualTo("artemis-functional-feature-tree");
        assertThat(workflow.workflow().featureModelVersion()).isEqualTo("0.1.0");
        assertThat(workflow.workflow().defaultTemplateId()).isEqualTo("custom-configuration");
        assertThat(workflow.useCaseTemplates()).extracting("id").containsExactly("minimal-teaching-setup", "programming-course", "exam-focused-course",
                "course-with-lecture-materials", "ai-enabled-course", "custom-configuration");
        assertThat(workflow.steps()).extracting("id").containsExactly("configuration-goal", "teaching-content", "exercise-types",
                "assessment-and-integrity", "ai-and-integrations", "review-and-consequences", "artifact-generation");
        assertThat(workflow.finalReviewGroups()).extracting("id").containsExactly("teaching-content", "exercise-types", "assessment-and-integrity",
                "ai-and-adaptive-learning", "integrations");
        GuidedDecisionOption irisOption = findOption(workflow, "enable-iris");
        assertThat(irisOption.selects()).containsExactly("iris");
        assertThat(irisOption.requiresCapabilities()).containsExactly("pyris-service", "pyris-secret");
        assertThat(irisOption.artifactImpacts()).anyMatch(impact -> impact.contains("artemis.iris.enabled"));
    }

    @Test
    void cachesLoadedRuntimeWorkflow() {
        assertThat(store.loadActiveWorkflow()).isSameAs(store.loadActiveWorkflow());
    }

    @Test
    void runtimeGuidedWorkflowReferencesOnlyKnownFeatureIds() {
        GuidedWorkflow workflow = store.loadActiveWorkflow();
        var featureModel = new JsonFeatureModelStore(resourceLoader, objectMapper).loadActiveModel();

        new GuidedWorkflowIntegrityService().validate(workflow, featureModel);
    }

    @Test
    void parsesGuidedWorkflowSchemaResource() throws Exception {
        Resource resource = resourceLoader.getResource(ACTIVE_SCHEMA_RESOURCE);

        try (InputStream inputStream = resource.getInputStream()) {
            Object schema = objectMapper.readValue(inputStream, Object.class);
            assertThat(schema).isInstanceOf(Map.class);
        }
    }

    private GuidedDecisionOption findOption(GuidedWorkflow workflow, String optionId) {
        for (GuidedWorkflowStep step : workflow.steps()) {
            for (GuidedDecision decision : step.decisions()) {
                for (GuidedDecisionOption option : decision.options()) {
                    if (option.id().equals(optionId)) {
                        return option;
                    }
                }
            }
        }
        throw new AssertionError("Missing guided workflow option '" + optionId + "'.");
    }
}
