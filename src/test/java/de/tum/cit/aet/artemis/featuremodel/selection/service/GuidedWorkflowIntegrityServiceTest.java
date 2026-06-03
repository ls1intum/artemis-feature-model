package de.tum.cit.aet.artemis.featuremodel.selection.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.TestFeatureModels;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.FinalReviewGroup;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowMetadata;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.UseCaseTemplate;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException;

class GuidedWorkflowIntegrityServiceTest {

    private final GuidedWorkflowIntegrityService service = new GuidedWorkflowIntegrityService();

    @Test
    void rejectsTemplateFeatureIdsThatAreNotPartOfTheFeatureModel() {
        GuidedWorkflow workflow = new GuidedWorkflow(metadata(), List.of(templateWithUnknownFeature()), List.of(step()), List.of(reviewGroup()));

        assertThatThrownBy(() -> service.validate(workflow, TestFeatureModels.baseModel())).isInstanceOf(FeatureModelIntegrityException.class)
                .hasMessageContaining("unknown feature 'missing-feature'");
    }

    private GuidedWorkflowMetadata metadata() {
        return new GuidedWorkflowMetadata("test-guided-workflow", "Test Guided Workflow", "0.0.1", "test-model", "0.0.1", "custom");
    }

    private UseCaseTemplate templateWithUnknownFeature() {
        return new UseCaseTemplate("custom", "Custom", "Synthetic custom template.", List.of("missing-feature"), List.of(), List.of("review"),
                List.of(), List.of());
    }

    private GuidedWorkflowStep step() {
        return new GuidedWorkflowStep("review", "Review", 1, "Synthetic review step.", List.of());
    }

    private FinalReviewGroup reviewGroup() {
        return new FinalReviewGroup("summary", "Summary", 1, List.of("programming"));
    }
}
