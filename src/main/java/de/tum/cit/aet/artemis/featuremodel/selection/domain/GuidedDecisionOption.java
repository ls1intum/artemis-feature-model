package de.tum.cit.aet.artemis.featuremodel.selection.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GuidedDecisionOption(String id, String label, String description, List<String> selects, List<String> deselects,
        List<String> requiresCapabilities, List<String> consequences, List<String> artifactImpacts, List<String> enabledOutcome,
        List<String> recommendedWhen, List<String> thingsToKnow, List<String> warnings) {

    /**
     * Creates a guided decision option and normalizes nullable collections to immutable empty lists.
     *
     * @param id stable option id.
     * @param label display label.
     * @param description user-facing option description.
     * @param selects feature ids selected by this option.
     * @param deselects feature ids deselected by this option.
     * @param requiresCapabilities deployment capabilities required by this option.
     * @param consequences legacy consequence text retained for advanced review.
     * @param artifactImpacts advanced artifact generation impact text.
     * @param enabledOutcome regular-user text describing what the option enables.
     * @param recommendedWhen regular-user guidance for when the option fits.
     * @param thingsToKnow regular-user notes and caveats.
     * @param warnings user-facing warning text.
     */
    public GuidedDecisionOption {
        selects = selects == null ? List.of() : List.copyOf(selects);
        deselects = deselects == null ? List.of() : List.copyOf(deselects);
        requiresCapabilities = requiresCapabilities == null ? List.of() : List.copyOf(requiresCapabilities);
        consequences = consequences == null ? List.of() : List.copyOf(consequences);
        artifactImpacts = artifactImpacts == null ? List.of() : List.copyOf(artifactImpacts);
        enabledOutcome = enabledOutcome == null ? List.of() : List.copyOf(enabledOutcome);
        recommendedWhen = recommendedWhen == null ? List.of() : List.copyOf(recommendedWhen);
        thingsToKnow = thingsToKnow == null ? List.of() : List.copyOf(thingsToKnow);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
