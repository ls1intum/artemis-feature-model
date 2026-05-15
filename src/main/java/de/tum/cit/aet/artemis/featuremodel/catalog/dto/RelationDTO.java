package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;

public record RelationDTO(String parentId, String childId, String relationType, String groupType, int order) {

    public static RelationDTO fromDomain(FeatureRelation relation) {
        return new RelationDTO(relation.parentId(), relation.childId(), relation.relationType(), relation.groupType(), relation.order());
    }
}
