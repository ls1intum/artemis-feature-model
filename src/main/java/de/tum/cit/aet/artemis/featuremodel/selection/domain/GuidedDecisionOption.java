package de.tum.cit.aet.artemis.featuremodel.selection.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One selectable option of a guided decision. The authored workflow resource carries only decision structure and
 * teacher prose; {@code requiresCapabilities} and {@code artifactImpacts} are model-owned wiring that the serve-time
 * enrichment derives from the active feature model, so the served record keeps the shape the client already consumes.
 * The authored lifecycle {@code status} is {@code draft} or {@code published}; an absent status is tolerated for
 * payloads generated before the lifecycle existed and is treated as {@code published}, while an unknown value is a
 * hard schema error.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GuidedDecisionOption(String id, String label, String description, List<String> selects, List<String> deselects,
        List<String> requiresCapabilities, List<String> artifactImpacts, List<String> enabledOutcome,
        List<String> recommendedWhen, List<String> thingsToKnow, List<String> warnings, String status) {

    /** Authored lifecycle status of an option that is still being written and must never be served. */
    public static final String STATUS_DRAFT = "draft";

    /** Authored lifecycle status of an option that is complete and may be served. */
    public static final String STATUS_PUBLISHED = "published";

    /**
     * Creates a guided decision option, normalizes nullable collections to immutable empty lists, and rejects unknown
     * lifecycle values.
     *
     * @param id stable option id.
     * @param label display label.
     * @param description user-facing option description.
     * @param selects feature ids selected by this option.
     * @param deselects feature ids deselected by this option.
     * @param requiresCapabilities deployment capabilities required by the selected features; derived at serve time.
     * @param artifactImpacts advanced artifact generation impact text; derived at serve time.
     * @param enabledOutcome regular-user text describing what the option enables.
     * @param recommendedWhen regular-user guidance for when the option fits.
     * @param thingsToKnow regular-user notes and caveats.
     * @param warnings user-facing warning text.
     * @param status authored lifecycle status, {@code draft} or {@code published}; null when the payload predates the lifecycle.
     * @throws IllegalArgumentException if the status is neither absent, {@code draft}, nor {@code published}.
     */
    public GuidedDecisionOption {
        selects = selects == null ? List.of() : List.copyOf(selects);
        deselects = deselects == null ? List.of() : List.copyOf(deselects);
        requiresCapabilities = requiresCapabilities == null ? List.of() : List.copyOf(requiresCapabilities);
        artifactImpacts = artifactImpacts == null ? List.of() : List.copyOf(artifactImpacts);
        enabledOutcome = enabledOutcome == null ? List.of() : List.copyOf(enabledOutcome);
        recommendedWhen = recommendedWhen == null ? List.of() : List.copyOf(recommendedWhen);
        thingsToKnow = thingsToKnow == null ? List.of() : List.copyOf(thingsToKnow);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        if (status != null && !STATUS_DRAFT.equals(status) && !STATUS_PUBLISHED.equals(status)) {
            throw new IllegalArgumentException("Guided decision option '" + id + "' declares unknown lifecycle status '" + status + "'.");
        }
    }

    /**
     * Checks whether this option is an authored draft.
     *
     * @return true if the status is {@code draft}; an absent status counts as {@code published}.
     */
    public boolean isDraft() {
        return STATUS_DRAFT.equals(status);
    }

    /**
     * Checks whether this option declares its lifecycle status explicitly.
     *
     * @return true if the status is present.
     */
    public boolean hasExplicitStatus() {
        return status != null;
    }
}
