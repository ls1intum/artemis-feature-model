package de.tum.cit.aet.artemis.featuremodel.visualization.dto;

import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.catalog.dto.FeatureDTO;

public record FeatureTreeNodeDTO(FeatureDTO feature, IncomingRelationDTO incomingRelation, List<FeatureTreeNodeDTO> children) {

    /**
     * Creates a tree node DTO and normalizes nullable children to an immutable empty list.
     *
     * @param feature feature represented by this tree node.
     * @param incomingRelation relation from the parent node, or null for the root.
     * @param children child tree nodes.
     */
    public FeatureTreeNodeDTO {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
