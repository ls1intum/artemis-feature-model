package de.tum.cit.aet.artemis.featuremodel.export.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request to generate deployment configuration artifacts from a feature selection and a deployment profile.
 *
 * @param selectedFeatureIds selected functional feature ids.
 * @param profileId deployment profile id to generate against, or {@code null}/blank for the default profile.
 * @param mode generation mode, or {@code null}/blank for the default {@code DEMO} mode.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtifactGenerationRequest(List<String> selectedFeatureIds, String profileId, String mode) {

    /**
     * Normalizes the selected feature ids to an immutable list.
     *
     * @param selectedFeatureIds selected functional feature ids.
     * @param profileId deployment profile id, or {@code null}/blank for the default profile.
     * @param mode generation mode, or {@code null}/blank for the default mode.
     */
    public ArtifactGenerationRequest {
        selectedFeatureIds = selectedFeatureIds == null ? List.of() : List.copyOf(selectedFeatureIds);
    }
}
