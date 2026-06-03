package de.tum.cit.aet.artemis.featuremodel.selection.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GuidedWorkflow(GuidedWorkflowMetadata workflow, List<UseCaseTemplate> useCaseTemplates, List<GuidedWorkflowStep> steps,
        List<FinalReviewGroup> finalReviewGroups) {

    /**
     * Creates a guided workflow and normalizes nullable collections to immutable empty lists.
     *
     * @param workflow workflow metadata.
     * @param useCaseTemplates user-facing starting templates.
     * @param steps ordered guided workflow steps.
     * @param finalReviewGroups final review grouping metadata.
     */
    public GuidedWorkflow {
        useCaseTemplates = useCaseTemplates == null ? List.of() : List.copyOf(useCaseTemplates);
        steps = steps == null ? List.of() : List.copyOf(steps);
        finalReviewGroups = finalReviewGroups == null ? List.of() : List.copyOf(finalReviewGroups);
    }
}
