package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeatureNode(String id, String name, String kind, boolean selectable, String description, String defaultState, FeatureSource source,
        String category, List<String> visibleTo, List<String> configurableBy, List<String> requiresCapabilities, List<ArtifactMapping> artifactMappings,
        ExtractionMetadata extraction) {

    private static final String KIND_ROOT = "root";

    private static final String KIND_GROUP = "group";

    private static final String CATEGORY_DERIVED = "derived";

    private static final String CATEGORY_FUNCTIONAL = "functional";

    private static final String ROLE_MAINTAINER = "maintainer";

    private static final String ROLE_TEACHER = "teacher";

    private static final String DEFAULT_STATE_ENABLED = "enabled";

    /**
     * Creates a feature node from older snapshots that do not yet contain role, capability, artifact, or extraction fields.
     *
     * @param id stable feature id.
     * @param name display name.
     * @param kind feature kind.
     * @param selectable whether users can toggle this feature.
     * @param description optional description.
     * @param defaultState default selection state.
     * @param source source evidence metadata.
     */
    public FeatureNode(String id, String name, String kind, boolean selectable, String description, String defaultState, FeatureSource source) {
        this(id, name, kind, selectable, description, defaultState, source, null, null, null, null, null, null);
    }

    /**
     * Creates a feature node and applies backward-compatible defaults for optional schema extension fields.
     *
     * @param id stable feature id.
     * @param name display name.
     * @param kind feature kind.
     * @param selectable whether users can toggle this feature.
     * @param description optional description.
     * @param defaultState default selection state.
     * @param source source evidence metadata.
     * @param category feature category.
     * @param visibleTo roles that may see this feature, or an empty list for unrestricted visibility.
     * @param configurableBy roles that may configure this feature.
     * @param requiresCapabilities deployment capabilities required by this feature.
     * @param artifactMappings future artifact generation mappings.
     * @param extraction extraction and review metadata.
     */
    public FeatureNode {
        category = category == null ? defaultCategory(selectable) : category;
        visibleTo = visibleTo == null ? List.of() : List.copyOf(visibleTo);
        configurableBy = configurableBy == null ? defaultConfigurableBy(selectable) : List.copyOf(configurableBy);
        requiresCapabilities = requiresCapabilities == null ? List.of() : List.copyOf(requiresCapabilities);
        artifactMappings = artifactMappings == null ? List.of() : List.copyOf(artifactMappings);
    }

    /**
     * Checks whether this node is the model root.
     *
     * @return true if this node has kind {@code root}.
     */
    @JsonIgnore
    public boolean isRoot() {
        return FeatureNode.KIND_ROOT.equals(kind);
    }

    /**
     * Checks whether this node is a structural group.
     *
     * @return true if this node has kind {@code group}.
     */
    @JsonIgnore
    public boolean isGroup() {
        return FeatureNode.KIND_GROUP.equals(kind);
    }

    /**
     * Checks whether this feature should be selected in the initial backend-derived selection.
     *
     * @return true if this node has default state {@code enabled}.
     */
    @JsonIgnore
    public boolean isEnabledByDefault() {
        return FeatureNode.DEFAULT_STATE_ENABLED.equals(defaultState);
    }

    /**
     * Defaults selectable legacy feature nodes to functional and structural nodes to derived.
     *
     * @param selectable whether users can toggle this feature.
     * @return backward-compatible default category.
     */
    private static String defaultCategory(boolean selectable) {
        return selectable ? CATEGORY_FUNCTIONAL : CATEGORY_DERIVED;
    }

    /**
     * Defaults configurable roles for selectable legacy feature nodes while keeping structural nodes non-configurable.
     *
     * @param selectable whether users can toggle this feature.
     * @return default configurable role list.
     */
    private static List<String> defaultConfigurableBy(boolean selectable) {
        return selectable ? List.of(ROLE_TEACHER, ROLE_MAINTAINER) : List.of();
    }
}
