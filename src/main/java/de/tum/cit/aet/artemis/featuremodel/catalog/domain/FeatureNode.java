package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

public record FeatureNode(String id, String name, String kind, boolean selectable, String description, String defaultState, FeatureSource source) {

    private static final String KIND_ROOT = "root";

    private static final String KIND_GROUP = "group";

    private static final String DEFAULT_STATE_ENABLED = "enabled";

    /**
     * Checks whether this node is the model root.
     *
     * @return true if this node has kind {@code root}.
     */
    public boolean isRoot() {
        return FeatureNode.KIND_ROOT.equals(kind);
    }

    /**
     * Checks whether this node is a structural group.
     *
     * @return true if this node has kind {@code group}.
     */
    public boolean isGroup() {
        return FeatureNode.KIND_GROUP.equals(kind);
    }

    /**
     * Checks whether this feature should be selected in the initial backend-derived selection.
     *
     * @return true if this node has default state {@code enabled}.
     */
    public boolean isEnabledByDefault() {
        return FeatureNode.DEFAULT_STATE_ENABLED.equals(defaultState);
    }
}
