package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.visualization.dto.FeatureTreeNodeDTO;

public record FeatureModelResponse(ModelMetadataDTO model, List<FeatureDTO> features, List<RelationDTO> relations, List<ConstraintDTO> constraints,
        FeatureTreeNodeDTO tree, List<String> defaultSelectedFeatureIds, List<ModelWarningDTO> warnings) {

    public FeatureModelResponse {
        features = features == null ? List.of() : List.copyOf(features);
        relations = relations == null ? List.of() : List.copyOf(relations);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        defaultSelectedFeatureIds = defaultSelectedFeatureIds == null ? List.of() : List.copyOf(defaultSelectedFeatureIds);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
