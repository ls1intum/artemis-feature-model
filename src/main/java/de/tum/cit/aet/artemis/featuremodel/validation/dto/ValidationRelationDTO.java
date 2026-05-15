package de.tum.cit.aet.artemis.featuremodel.validation.dto;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureRelation;

public record ValidationRelationDTO(String parentId, String childId) {

    /**
     * Converts a domain relation to the compact validation relation DTO.
     *
     * @param relation domain relation, or null if no relation applies.
     * @return validation relation DTO, or null when {@code relation} is null.
     */
    public static ValidationRelationDTO fromDomain(FeatureRelation relation) {
        if (relation == null) {
            return null;
        }
        return new ValidationRelationDTO(relation.parentId(), relation.childId());
    }
}
