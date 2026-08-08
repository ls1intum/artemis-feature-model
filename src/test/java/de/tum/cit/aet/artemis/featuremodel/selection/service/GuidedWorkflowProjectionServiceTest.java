package de.tum.cit.aet.artemis.featuremodel.selection.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecision;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowMetadata;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.UseCaseTemplate;

/** Covers the effective-workflow projection: draft removal, incomplete-published removal, and empty-structure pruning. */
class GuidedWorkflowProjectionServiceTest {

    private final GuidedWorkflowProjectionService service = new GuidedWorkflowProjectionService();

    @Test
    void keepsACompletePublishedWorkflowUnchanged() {
        GuidedWorkflow workflow = workflow(step("selection", decision("decision", completeOption("enable-alpha", GuidedDecisionOption.STATUS_PUBLISHED))));

        GuidedWorkflowProjectionService.Projection projection = service.project(workflow);

        assertThat(projection.effectiveWorkflow()).usingRecursiveComparison().isEqualTo(workflow);
        assertThat(projection.removedDraftOptionIds()).isEmpty();
        assertThat(projection.removedIncompleteOptionIds()).isEmpty();
        assertThat(projection.removedStepIds()).isEmpty();
        assertThat(projection.optionIdsMissingStatus()).isEmpty();
    }

    @Test
    void removesDraftOptionsAndKeepsPublishedSiblings() {
        GuidedWorkflow workflow = workflow(step("selection", decision("decision", completeOption("enable-alpha", GuidedDecisionOption.STATUS_PUBLISHED),
                completeOption("enable-draft", GuidedDecisionOption.STATUS_DRAFT))));

        GuidedWorkflowProjectionService.Projection projection = service.project(workflow);

        assertThat(optionIds(projection.effectiveWorkflow())).containsExactly("enable-alpha");
        assertThat(projection.removedDraftOptionIds()).containsExactly("enable-draft");
    }

    @Test
    void removesPublishedOptionsWithIncompleteProse() {
        GuidedDecisionOption todoDescription = option("enable-todo", "Todo", "TODO: describe this option.", List.of("Outcome."), List.of("Fits."),
                List.of("Notes."), GuidedDecisionOption.STATUS_PUBLISHED);
        GuidedDecisionOption emptyOutcome = option("enable-empty-outcome", "Empty", "Complete description.", List.of(), List.of("Fits."), List.of("Notes."),
                GuidedDecisionOption.STATUS_PUBLISHED);
        GuidedDecisionOption blankRecommendation = option("enable-blank-recommendation", "Blank", "Complete description.", List.of("Outcome."), List.of(" "),
                List.of("Notes."), GuidedDecisionOption.STATUS_PUBLISHED);
        GuidedWorkflow workflow = workflow(step("selection",
                decision("decision", completeOption("enable-alpha", GuidedDecisionOption.STATUS_PUBLISHED), todoDescription, emptyOutcome,
                        blankRecommendation)));

        GuidedWorkflowProjectionService.Projection projection = service.project(workflow);

        assertThat(optionIds(projection.effectiveWorkflow())).containsExactly("enable-alpha");
        assertThat(projection.removedIncompleteOptionIds()).containsExactly("enable-todo", "enable-empty-outcome", "enable-blank-recommendation");
    }

    @Test
    void removesPublishedOptionsOfADecisionWithIncompleteProse() {
        GuidedDecision todoDecision = new GuidedDecision("todo-decision", "TODO: ask the question.", "Complete description.", "multiple",
                List.of(completeOption("enable-alpha", GuidedDecisionOption.STATUS_PUBLISHED)));
        GuidedWorkflow workflow = workflow(step("selection", todoDecision));

        GuidedWorkflowProjectionService.Projection projection = service.project(workflow);

        assertThat(optionIds(projection.effectiveWorkflow())).isEmpty();
        assertThat(projection.removedIncompleteOptionIds()).containsExactly("enable-alpha");
    }

    @Test
    void removesDecisionsAndStepsLeftEmptyAndDropsTheirTemplateReferences() {
        GuidedWorkflowStep draftOnlyStep = step("draft-step", decision("draft-decision", completeOption("enable-draft", GuidedDecisionOption.STATUS_DRAFT)));
        GuidedWorkflowStep publishedStep = step("published-step", decision("decision", completeOption("enable-alpha", GuidedDecisionOption.STATUS_PUBLISHED)));
        GuidedWorkflowStep authoredEmptyStep = new GuidedWorkflowStep("review", "Review", 3, "Authored step without decisions.", List.of());
        UseCaseTemplate template = new UseCaseTemplate("custom", "Custom", "Synthetic template.", List.of(), List.of(),
                List.of("draft-step", "published-step", "review"), List.of(), List.of());
        GuidedWorkflow workflow = new GuidedWorkflow(metadata(), List.of(template), List.of(draftOnlyStep, publishedStep, authoredEmptyStep), List.of());

        GuidedWorkflowProjectionService.Projection projection = service.project(workflow);

        assertThat(projection.effectiveWorkflow().steps()).extracting(GuidedWorkflowStep::id).containsExactly("published-step", "review");
        assertThat(projection.removedStepIds()).containsExactly("draft-step");
        assertThat(projection.effectiveWorkflow().useCaseTemplates().getFirst().recommendedStepIds()).containsExactly("published-step", "review");
    }

    @Test
    void treatsAnAbsentStatusAsPublishedAndReportsIt() {
        GuidedWorkflow workflow = workflow(step("selection", decision("decision", completeOption("enable-alpha", null))));

        GuidedWorkflowProjectionService.Projection projection = service.project(workflow);

        assertThat(optionIds(projection.effectiveWorkflow())).containsExactly("enable-alpha");
        assertThat(projection.optionIdsMissingStatus()).containsExactly("enable-alpha");
    }

    private List<String> optionIds(GuidedWorkflow workflow) {
        return workflow.steps().stream().flatMap(step -> step.decisions().stream()).flatMap(decision -> decision.options().stream())
                .map(GuidedDecisionOption::id).toList();
    }

    private GuidedWorkflow workflow(GuidedWorkflowStep step) {
        UseCaseTemplate template = new UseCaseTemplate("custom", "Custom", "Synthetic template.", List.of(), List.of(), List.of(step.id()), List.of(),
                List.of());
        return new GuidedWorkflow(metadata(), List.of(template), List.of(step), List.of());
    }

    private GuidedWorkflowMetadata metadata() {
        return new GuidedWorkflowMetadata("test-workflow", "Test Workflow", "0.0.1", null, null, "custom");
    }

    private GuidedWorkflowStep step(String id, GuidedDecision... decisions) {
        return new GuidedWorkflowStep(id, "Step " + id, 1, "Synthetic step.", List.of(decisions));
    }

    private GuidedDecision decision(String id, GuidedDecisionOption... options) {
        return new GuidedDecision(id, "Question?", "Synthetic decision.", "multiple", List.of(options));
    }

    private GuidedDecisionOption completeOption(String id, String status) {
        return option(id, "Option " + id, "Complete description.", List.of("Outcome."), List.of("Fits."), List.of("Notes."), status);
    }

    private GuidedDecisionOption option(String id, String label, String description, List<String> enabledOutcome, List<String> recommendedWhen,
            List<String> thingsToKnow, String status) {
        return new GuidedDecisionOption(id, label, description, List.of("alpha-feature"), List.of(), null, null, enabledOutcome, recommendedWhen, thingsToKnow,
                List.of(), status);
    }
}
