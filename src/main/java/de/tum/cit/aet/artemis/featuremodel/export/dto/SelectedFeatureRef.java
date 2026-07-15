package de.tum.cit.aet.artemis.featuremodel.export.dto;

/**
 * Reference to a selected feature in the generated {@code selected-features.json} metadata.
 *
 * @param id feature id.
 * @param name feature display name.
 */
public record SelectedFeatureRef(String id, String name) {
}
