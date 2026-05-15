package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FeatureModel(ModelMetadata model, List<FeatureNode> features, List<FeatureRelation> relations, List<FeatureConstraint> constraints) {

    public FeatureModel {
        features = features == null ? List.of() : List.copyOf(features);
        relations = relations == null ? List.of() : List.copyOf(relations);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
    }
}
