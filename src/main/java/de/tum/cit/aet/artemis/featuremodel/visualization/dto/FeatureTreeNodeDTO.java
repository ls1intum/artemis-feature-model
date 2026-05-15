package de.tum.cit.aet.artemis.featuremodel.visualization.dto;

import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.catalog.dto.FeatureDTO;

public record FeatureTreeNodeDTO(FeatureDTO feature, IncomingRelationDTO incomingRelation, List<FeatureTreeNodeDTO> children) {

    public FeatureTreeNodeDTO {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
