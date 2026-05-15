package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;

public record FeatureDTO(String id, String name, String kind, boolean selectable, String description, String defaultState, FeatureSourceDTO source) {

    public static FeatureDTO fromDomain(FeatureNode feature) {
        return new FeatureDTO(feature.id(), feature.name(), feature.kind(), feature.selectable(), feature.description(), feature.defaultState(),
                FeatureSourceDTO.fromDomain(feature.source()));
    }
}
