package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

public record FeatureNode(String id, String name, String kind, boolean selectable, String description, String defaultState, FeatureSource source) {

    public static final String KIND_ROOT = "root";

    public static final String KIND_GROUP = "group";

    public static final String DEFAULT_STATE_ENABLED = "enabled";

    public boolean isRoot() {
        return FeatureNode.KIND_ROOT.equals(kind);
    }

    public boolean isGroup() {
        return FeatureNode.KIND_GROUP.equals(kind);
    }

    public boolean isEnabledByDefault() {
        return FeatureNode.DEFAULT_STATE_ENABLED.equals(defaultState);
    }
}
