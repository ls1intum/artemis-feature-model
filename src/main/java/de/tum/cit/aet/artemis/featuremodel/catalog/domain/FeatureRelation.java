package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

public record FeatureRelation(String parentId, String childId, String relationType, String groupType, int order) {

    private static final String RELATION_TYPE_MANDATORY = "mandatory";

    /**
     * Checks whether this relation requires its child whenever the parent path is active.
     *
     * @return true if this relation has type {@code mandatory}.
     */
    public boolean isMandatory() {
        return FeatureRelation.RELATION_TYPE_MANDATORY.equals(relationType);
    }
}
