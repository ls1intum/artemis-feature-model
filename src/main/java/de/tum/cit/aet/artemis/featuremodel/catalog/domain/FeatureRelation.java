package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

public record FeatureRelation(String parentId, String childId, String relationType, String groupType, int order) {

    public static final String RELATION_TYPE_MANDATORY = "mandatory";

    public boolean isMandatory() {
        return FeatureRelation.RELATION_TYPE_MANDATORY.equals(relationType);
    }
}
