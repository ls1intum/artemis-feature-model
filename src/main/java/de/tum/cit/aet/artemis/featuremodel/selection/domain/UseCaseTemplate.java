package de.tum.cit.aet.artemis.featuremodel.selection.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UseCaseTemplate(String id, String label, String description, List<String> selectedFeatureIds, List<String> deselectedFeatureIds,
        List<String> recommendedStepIds, List<String> consequences, List<String> warnings) {

    /**
     * Creates a use-case template and normalizes nullable collections to immutable empty lists.
     *
     * @param id stable template id.
     * @param label display label.
     * @param description user-facing description.
     * @param selectedFeatureIds feature ids selected by this template.
     * @param deselectedFeatureIds feature ids deselected by this template.
     * @param recommendedStepIds workflow step ids recommended for this template.
     * @param consequences user-facing consequence text.
     * @param warnings user-facing warning text.
     */
    public UseCaseTemplate {
        selectedFeatureIds = selectedFeatureIds == null ? List.of() : List.copyOf(selectedFeatureIds);
        deselectedFeatureIds = deselectedFeatureIds == null ? List.of() : List.copyOf(deselectedFeatureIds);
        recommendedStepIds = recommendedStepIds == null ? List.of() : List.copyOf(recommendedStepIds);
        consequences = consequences == null ? List.of() : List.copyOf(consequences);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
