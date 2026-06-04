package de.tum.cit.aet.artemis.featuremodel.selection.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GuidedWorkflowStep(String id, String title, int order, String description, List<GuidedDecision> decisions) {

    /**
     * Creates a guided workflow step and normalizes nullable decisions to an immutable empty list.
     *
     * @param id stable step id.
     * @param title display title.
     * @param order step order in the guided flow.
     * @param description user-facing step description.
     * @param decisions decisions shown in this step.
     */
    public GuidedWorkflowStep {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }
}
