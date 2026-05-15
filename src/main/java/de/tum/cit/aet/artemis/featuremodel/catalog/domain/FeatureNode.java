package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

public record FeatureNode(String id, String name, String kind, boolean selectable, String description, String defaultState, FeatureSource source) {

    public boolean isRoot() {
        return "root".equals(kind);
    }

    public boolean isGroup() {
        return "group".equals(kind);
    }
}
