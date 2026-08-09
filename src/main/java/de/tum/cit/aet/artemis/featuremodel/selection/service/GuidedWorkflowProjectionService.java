package de.tum.cit.aet.artemis.featuremodel.selection.service;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecision;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.UseCaseTemplate;

/**
 * Projects a structurally parsed guided workflow onto the effective workflow every client is allowed to see. The
 * projection removes {@code draft} options, defensively removes {@code published} options whose required prose is
 * incomplete, removes decisions and steps that the removals left empty, and drops removed step ids from template
 * recommendations. It runs once inside bundle loading — before reference validation, enrichment, and availability
 * calculation — so both runtime modes and every API response share one serving semantics and no client carries any
 * draft-filtering responsibility.
 */
public class GuidedWorkflowProjectionService {

    /** Sentinel prefix that marks scaffold-generated prose awaiting a human author. */
    private static final String TODO_PROSE_PREFIX = "TODO";

    /**
     * Result of one projection.
     *
     * @param effectiveWorkflow workflow containing only servable published options.
     * @param removedDraftOptionIds ids of removed draft options.
     * @param removedIncompleteOptionIds ids of removed published options with incomplete required prose.
     * @param removedStepIds ids of steps the removals left without decisions.
     * @param optionIdsMissingStatus ids of options without an explicit lifecycle status, treated as published.
     */
    public record Projection(GuidedWorkflow effectiveWorkflow, List<String> removedDraftOptionIds, List<String> removedIncompleteOptionIds,
            List<String> removedStepIds, List<String> optionIdsMissingStatus) {
    }

    /**
     * Projects a guided workflow onto its effective, servable form.
     *
     * @param workflow structurally parsed guided workflow.
     * @return projection with the effective workflow and the removal diagnostics.
     */
    public Projection project(GuidedWorkflow workflow) {
        List<String> removedDraftOptionIds = new ArrayList<>();
        List<String> removedIncompleteOptionIds = new ArrayList<>();
        List<String> removedStepIds = new ArrayList<>();
        List<String> optionIdsMissingStatus = new ArrayList<>();

        List<GuidedWorkflowStep> effectiveSteps = new ArrayList<>();
        for (GuidedWorkflowStep step : workflow.steps()) {
            GuidedWorkflowStep effectiveStep = projectStep(step, removedDraftOptionIds, removedIncompleteOptionIds, optionIdsMissingStatus);
            if (effectiveStep.decisions().isEmpty() && !step.decisions().isEmpty()) {
                removedStepIds.add(step.id());
                continue;
            }
            effectiveSteps.add(effectiveStep);
        }
        List<UseCaseTemplate> effectiveTemplates = withoutRemovedStepReferences(workflow.useCaseTemplates(), removedStepIds);
        GuidedWorkflow effectiveWorkflow = new GuidedWorkflow(workflow.workflow(), effectiveTemplates, effectiveSteps, workflow.finalReviewGroups());
        return new Projection(effectiveWorkflow, List.copyOf(removedDraftOptionIds), List.copyOf(removedIncompleteOptionIds), List.copyOf(removedStepIds),
                List.copyOf(optionIdsMissingStatus));
    }

    /**
     * Checks whether a published option and its containing decision carry the complete required prose of the
     * publishability contract: non-blank, TODO-free label and description, non-empty, TODO-free enabled outcome,
     * recommendation, and things-to-know lists, and a complete decision question and description.
     *
     * @param option decision option to check.
     * @param decision decision containing the option.
     * @return true if every required prose field is complete.
     */
    public boolean hasCompletePublishedProse(GuidedDecisionOption option, GuidedDecision decision) {
        return isCompleteText(option.label()) && isCompleteText(option.description()) && isCompleteTextList(option.enabledOutcome())
                && isCompleteTextList(option.recommendedWhen()) && isCompleteTextList(option.thingsToKnow()) && isCompleteText(decision.question())
                && isCompleteText(decision.description());
    }

    /**
     * Projects one step by filtering unservable options and dropping decisions the filtering left empty.
     *
     * @param step authored workflow step.
     * @param removedDraftOptionIds sink for removed draft option ids.
     * @param removedIncompleteOptionIds sink for removed incomplete published option ids.
     * @param optionIdsMissingStatus sink for option ids without an explicit status.
     * @return step containing only servable options and non-empty decisions.
     */
    private GuidedWorkflowStep projectStep(GuidedWorkflowStep step, List<String> removedDraftOptionIds, List<String> removedIncompleteOptionIds,
            List<String> optionIdsMissingStatus) {
        List<GuidedDecision> effectiveDecisions = new ArrayList<>();
        for (GuidedDecision decision : step.decisions()) {
            List<GuidedDecisionOption> effectiveOptions = new ArrayList<>();
            for (GuidedDecisionOption option : decision.options()) {
                if (!option.hasExplicitStatus()) {
                    optionIdsMissingStatus.add(option.id());
                }
                if (option.isDraft()) {
                    removedDraftOptionIds.add(option.id());
                    continue;
                }
                if (!hasCompletePublishedProse(option, decision)) {
                    removedIncompleteOptionIds.add(option.id());
                    continue;
                }
                effectiveOptions.add(option);
            }
            if (effectiveOptions.isEmpty() && !decision.options().isEmpty()) {
                continue;
            }
            effectiveDecisions.add(new GuidedDecision(decision.id(), decision.question(), decision.description(), decision.selectionMode(), effectiveOptions));
        }
        return new GuidedWorkflowStep(step.id(), step.title(), step.order(), step.description(), effectiveDecisions);
    }

    /**
     * Drops removed step ids from every template's recommended steps.
     *
     * @param templates authored use-case templates.
     * @param removedStepIds ids of steps the projection removed.
     * @return templates without dangling recommended step references.
     */
    private List<UseCaseTemplate> withoutRemovedStepReferences(List<UseCaseTemplate> templates, List<String> removedStepIds) {
        if (removedStepIds.isEmpty()) {
            return templates;
        }
        List<UseCaseTemplate> effectiveTemplates = new ArrayList<>();
        for (UseCaseTemplate template : templates) {
            List<String> recommendedStepIds = template.recommendedStepIds().stream().filter(stepId -> !removedStepIds.contains(stepId)).toList();
            effectiveTemplates.add(new UseCaseTemplate(template.id(), template.label(), template.description(), template.selectedFeatureIds(),
                    template.deselectedFeatureIds(), recommendedStepIds, template.consequences(), template.warnings()));
        }
        return effectiveTemplates;
    }

    /**
     * Checks that a required text is present and does not carry the scaffold TODO sentinel.
     *
     * @param text required prose text.
     * @return true if the text is complete.
     */
    private boolean isCompleteText(String text) {
        return text != null && !text.isBlank() && !text.strip().startsWith(TODO_PROSE_PREFIX);
    }

    /**
     * Checks that a required text list is non-empty and every entry is complete.
     *
     * @param texts required prose list.
     * @return true if the list is complete.
     */
    private boolean isCompleteTextList(List<String> texts) {
        return !texts.isEmpty() && texts.stream().allMatch(this::isCompleteText);
    }
}
