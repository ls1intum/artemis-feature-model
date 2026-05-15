package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

public record FeatureRelation(String parentId, String childId, String relationType, String groupType, int order) {

    public boolean isMandatory() {
        return "mandatory".equals(relationType);
    }
}
