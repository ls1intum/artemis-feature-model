package de.tum.cit.aet.artemis.featuremodel.selection.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GuidedDecision(String id, String question, String description, String selectionMode, List<GuidedDecisionOption> options) {

    /**
     * Creates a guided decision and normalizes nullable options to an immutable empty list.
     *
     * @param id stable decision id.
     * @param question user-facing decision question.
     * @param description user-facing decision context.
     * @param selectionMode option selection mode.
     * @param options available decision options.
     */
    public GuidedDecision {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
