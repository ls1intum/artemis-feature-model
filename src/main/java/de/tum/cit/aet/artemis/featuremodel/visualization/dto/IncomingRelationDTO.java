package de.tum.cit.aet.artemis.featuremodel.visualization.dto;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;

public record IncomingRelationDTO(String parentId, String childId, String relationType, String groupType, int order) {

    public static IncomingRelationDTO fromDomain(FeatureRelation relation) {
        return new IncomingRelationDTO(relation.parentId(), relation.childId(), relation.relationType(), relation.groupType(), relation.order());
    }
}
