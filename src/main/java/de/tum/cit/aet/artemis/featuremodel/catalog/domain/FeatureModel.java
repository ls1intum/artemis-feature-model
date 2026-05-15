package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FeatureModel(ModelMetadata model, List<FeatureNode> features, List<FeatureRelation> relations, List<FeatureConstraint> constraints) {

    /**
     * Creates a feature model and normalizes nullable collections to immutable empty lists.
     *
     * @param model model metadata.
     * @param features feature nodes in source order.
     * @param relations feature relations in source order.
     * @param constraints cross-tree constraints in source order.
     */
    public FeatureModel {
        features = features == null ? List.of() : List.copyOf(features);
        relations = relations == null ? List.of() : List.copyOf(relations);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
    }
}
