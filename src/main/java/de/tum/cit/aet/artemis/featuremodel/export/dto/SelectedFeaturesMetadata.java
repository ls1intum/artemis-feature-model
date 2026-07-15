package de.tum.cit.aet.artemis.featuremodel.export.dto;

import java.util.List;

/**
 * Metadata for the generated {@code metadata/selected-features.json} file.
 *
 * @param modelId active feature model id.
 * @param modelVersion active feature model version.
 * @param mode generation mode.
 * @param selectedFeatures selected functional features.
 */
public record SelectedFeaturesMetadata(String modelId, String modelVersion, String mode, List<SelectedFeatureRef> selectedFeatures) {

    /**
     * Normalizes the selected features to an immutable list.
     *
     * @param modelId active feature model id.
     * @param modelVersion active feature model version.
     * @param mode generation mode.
     * @param selectedFeatures selected functional features.
     */
    public SelectedFeaturesMetadata {
        selectedFeatures = selectedFeatures == null ? List.of() : List.copyOf(selectedFeatures);
    }
}
