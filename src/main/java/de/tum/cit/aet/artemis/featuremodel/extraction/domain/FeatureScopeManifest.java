package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Opt-in curation manifest for extracted Artemis candidates. Include and exclude entries decide membership; an
 * unlisted candidate is pending. Conceptual nodes provide model scaffolding without claiming a source anchor, and
 * cross-tree constraints declare the relations the generated model enforces beyond the hierarchy.
 *
 * @param manifestVersion manifest schema version.
 * @param verifiedAgainstArtemisCommit Artemis commit against which the decisions were reviewed.
 * @param include explicitly included candidates.
 * @param exclude explicitly excluded candidates.
 * @param conceptualNodes unanchored model nodes.
 * @param constraints declared cross-tree constraints of the generated model.
 * @param renames explicit workflow feature-id renames authorized by a maintainer.
 */
public record FeatureScopeManifest(int manifestVersion, String verifiedAgainstArtemisCommit, List<IncludeEntry> include, List<ExcludeEntry> exclude,
        List<ConceptualNode> conceptualNodes, List<ConstraintEntry> constraints, List<RenameEntry> renames) {

    /** Current manifest schema version. */
    public static final int CURRENT_VERSION = 1;

    /** Optionality of a feature whose selection is enforced by validation and rendered as a filled circle. */
    public static final String OPTIONALITY_MANDATORY = "mandatory";

    /** Optionality of a feature users may freely select or deselect; the default when not declared. */
    public static final String OPTIONALITY_OPTIONAL = "optional";

    /** Category of course-facing functional features; the default for included module candidates. */
    public static final String CATEGORY_FUNCTIONAL = "functional";

    /** Category of maintainer-facing technical features that never enter the teacher surface. */
    public static final String CATEGORY_TECHNICAL = "technical";

    /**
     * Normalizes manifest collections to immutable lists.
     */
    public FeatureScopeManifest {
        include = include == null ? List.of() : List.copyOf(include);
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
        conceptualNodes = conceptualNodes == null ? List.of() : List.copyOf(conceptualNodes);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        renames = renames == null ? List.of() : List.copyOf(renames);
    }

    /**
     * Included candidate plus interim semantics that may later move to {@code @ArtemisFeature}.
     *
     * @param anchor candidate id or canonical source symbol.
     * @param id curated feature id.
     * @param group group placement, or null.
     * @param parent direct parent placement, or null.
     * @param kind feature kind override, or null.
     * @param optionality {@code mandatory} or {@code optional}; null defaults to optional. Whether a feature is
     *            mandatory is a modeling judgment that source code cannot express, so it is declared here.
     * @param category {@code functional} or {@code technical}; null defaults by kind. Technical features are
     *            maintainer-only and never enter the teacher surface.
     * @param defaultState {@code enabled} or {@code disabled}; null defers to the scanned YAML default.
     * @param order relation order under the parent; null appends after ordered siblings in manifest order.
     * @param requiresCapabilities required deployment capabilities.
     * @param providesCapabilities capabilities supplied by the feature.
     * @param artifactMappings declared artifact mapping hints beyond the auto-derived enabled-key mapping.
     * @param name explicit name override, or null.
     * @param description explicit description override, or null.
     * @param documentationUrl explicit documentation URL override, or null.
     * @param rationale documented reason for the scope decision, or null.
     */
    public record IncludeEntry(String anchor, String id, String group, String parent, String kind, String optionality, String category, String defaultState,
            Integer order, List<String> requiresCapabilities, List<String> providesCapabilities, List<MappingHint> artifactMappings, String name,
            String description, String documentationUrl, String rationale) {

        /**
         * Normalizes capability and mapping collections to immutable lists.
         */
        public IncludeEntry {
            requiresCapabilities = requiresCapabilities == null ? List.of() : List.copyOf(requiresCapabilities);
            providesCapabilities = providesCapabilities == null ? List.of() : List.copyOf(providesCapabilities);
            artifactMappings = artifactMappings == null ? List.of() : List.copyOf(artifactMappings);
        }
    }

    /**
     * Explicit exclusion with a mandatory reason code.
     *
     * @param anchor candidate id or canonical source symbol.
     * @param reason stable reason code.
     * @param rationale human-readable reasoning for the decision, or null.
     */
    public record ExcludeEntry(String anchor, String reason, String rationale) {
    }

    /**
     * Unanchored model node used for hierarchy or a manually curated always-on capability.
     *
     * @param id curated feature id.
     * @param parent direct parent id, or null for the root.
     * @param kind model kind, normally root, group, or module.
     * @param optionality {@code mandatory} or {@code optional} for module nodes; null for root and group nodes or to
     *            default to optional.
     * @param category {@code functional} or {@code technical}; null defaults by kind.
     * @param groupType child combination of a group node, one of {@code and}, {@code or}, or {@code alternative};
     *            null defaults to {@code and}. An {@code alternative} group models an xor choice.
     * @param order relation order under the parent; null appends after ordered siblings in manifest order.
     * @param name optional explicit name.
     * @param description optional explicit description.
     */
    public record ConceptualNode(String id, String parent, String kind, String optionality, String category, String groupType, Integer order, String name,
            String description) {
    }

    /**
     * Declared cross-tree constraint of the generated model. The extraction provides exclusivity and dependency
     * evidence; declaring the enforced constraint remains a curation decision like any other inclusion.
     *
     * @param id stable constraint id.
     * @param type constraint type, {@code requires} or {@code excludes}.
     * @param source source feature id.
     * @param target target feature id.
     * @param description human-readable constraint description, or null.
     */
    public record ConstraintEntry(String id, String type, String source, String target, String description) {
    }

    /**
     * Explicitly authorized workflow feature-id rename.
     *
     * @param from former feature id referenced by the workflow.
     * @param to current manifest-declared feature id.
     * @param rationale maintainer-authored reason why the feature semantics are unchanged.
     */
    public record RenameEntry(String from, String to, String rationale) {
    }

    /**
     * Declared artifact mapping hint mirroring the curated model's mapping shape.
     *
     * @param target generated file the entry belongs to.
     * @param path dotted configuration path or variable name written into the target.
     * @param valueWhenSelected value written when the owning feature is selected, or null.
     * @param valueWhenDeselected value written when the owning feature is not selected, or null.
     * @param valueFromProfile deployment profile parameter key whose value is written, or null.
     * @param requiredWhenSelected whether a missing profile value should be reported when the feature is selected.
     * @param secret whether the value is a secret reference that must never be emitted as plaintext.
     */
    public record MappingHint(String target, String path, Object valueWhenSelected, Object valueWhenDeselected, String valueFromProfile,
            Boolean requiredWhenSelected, Boolean secret) {
    }
}
