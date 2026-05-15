package de.tum.cit.aet.artemis.featuremodel.validation.dto;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;

public record ValidationRelationDTO(String parentId, String childId) {

    public static ValidationRelationDTO fromDomain(FeatureRelation relation) {
        if (relation == null) {
            return null;
        }
        return new ValidationRelationDTO(relation.parentId(), relation.childId());
    }
}
