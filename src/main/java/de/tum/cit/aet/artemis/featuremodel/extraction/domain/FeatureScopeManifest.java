package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Curation manifest for extracted Artemis candidates, schema version 5. The manifest carries modeling judgments only:
 * a {@code features} entry keyed by the Artemis module id declares the membership and semantics of a functional
 * feature, its anchor being implied as {@code module:<id>}; {@code technical} entries declare the maintainer-only
 * technical and infrastructure members together with their anchor; {@code notModeled} entries record the deliberate
 * exclusions; conceptual nodes provide hierarchy without a source anchor; declared constraints add the cross-tree
 * relations the generated model enforces beyond the hierarchy and the derived alternative-group exclusions. Everything
 * else is derived at run time: functional anchors, required capabilities, technical compose, profile, and environment
 * mappings, the pairwise exclusions of every {@code alternative} group, and the root node when none is declared. The
 * Artemis source revision is derived from the verified checkout and the runtime image reference is delivery
 * configuration, so neither identity lives here.
 *
 * @param manifestVersion manifest schema version.
 * @param features functional members keyed by Artemis module id, each carrying its modeling semantics.
 * @param technical manifest-declared technical and infrastructure members.
 * @param notModeled explicitly excluded candidates.
 * @param conceptualNodes unanchored model nodes.
 * @param constraints declared cross-tree constraints of the generated model.
 * @param ignoredRelations relation candidates between members that deliberately stay unenforced.
 */
public record FeatureScopeManifest(int manifestVersion, List<FeatureEntry> features, List<TechnicalEntry> technical, List<NotModeledEntry> notModeled,
        List<ConceptualNode> conceptualNodes, List<ConstraintEntry> constraints, List<IgnoredRelationEntry> ignoredRelations) {

    /** Current manifest schema version. */
    public static final int CURRENT_VERSION = 5;

    /** Id of the root node the assembler emits when the manifest declares no node of kind {@code root}. */
    public static final String IMPLICIT_ROOT_ID = "artemis";

    /** Name of the implicit root node. */
    public static final String IMPLICIT_ROOT_NAME = "Artemis";

    /** Description of the implicit root node. */
    public static final String IMPLICIT_ROOT_DESCRIPTION = "Root of the Artemis feature model.";

    /** Kind of the conceptual node that roots the hierarchy. */
    public static final String KIND_ROOT = "root";

    /** Optionality of a feature whose selection is enforced by validation and rendered as a filled circle. */
    public static final String OPTIONALITY_MANDATORY = "mandatory";

    /** Optionality of a feature users may freely select or deselect; the default when not declared. */
    public static final String OPTIONALITY_OPTIONAL = "optional";

    /** Category of course-facing functional features; the default for module members. */
    public static final String CATEGORY_FUNCTIONAL = "functional";

    /** Category of maintainer-facing technical features that never enter the teacher surface. */
    public static final String CATEGORY_TECHNICAL = "technical";

    /** Group type of a conceptual group whose children are mutually exclusive; its pairwise exclusions are derived. */
    public static final String GROUP_TYPE_ALTERNATIVE = "alternative";

    /** Stable fallback used when an exclusion deliberately omits its optional reason code. */
    public static final String EXCLUSION_REASON_UNSPECIFIED = "unspecified";

    /** Configuration-entry action confirming or adding a key as a deployment input; the default when not declared. */
    public static final String CONFIGURATION_ACTION_INCLUDE = "include";

    /** Configuration-entry action rejecting a derived key. */
    public static final String CONFIGURATION_ACTION_EXCLUDE = "exclude";

    /**
     * Normalizes manifest collections to immutable lists.
     */
    public FeatureScopeManifest {
        features = features == null ? List.of() : List.copyOf(features);
        technical = technical == null ? List.of() : List.copyOf(technical);
        notModeled = notModeled == null ? List.of() : List.copyOf(notModeled);
        conceptualNodes = conceptualNodes == null ? List.of() : List.copyOf(conceptualNodes);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        ignoredRelations = ignoredRelations == null ? List.of() : List.copyOf(ignoredRelations);
    }

    /**
     * Returns the anchor a features entry implies: the module candidate with the entry's id.
     *
     * @param featureId features entry id, which is the Artemis module id.
     * @return namespaced module candidate id.
     */
    public static String moduleAnchor(String featureId) {
        return FeatureCandidate.NAMESPACE_MODULE + featureId;
    }

    /**
     * Checks whether the manifest declares a root conceptual node; otherwise the assembler emits the implicit root.
     *
     * @return true when a conceptual node of kind {@code root} is declared.
     */
    public boolean declaresRoot() {
        return conceptualNodes.stream().anyMatch(node -> KIND_ROOT.equals(node.kind()));
    }

    /**
     * Membership and modeling semantics of one member. In the {@code features} section the id is the Artemis module id
     * and grants membership; in a {@code technical} entry the same fields describe the member declared by the entry's
     * anchor.
     *
     * @param id feature id.
     * @param group group placement, or null.
     * @param parent direct parent placement, or null.
     * @param optionality {@code mandatory} or {@code optional}; null defaults to optional. Whether a feature is
     *            mandatory is a modeling judgment that source code cannot express, so it is declared here.
     * @param category {@code functional} or {@code technical}; null defaults by section. Technical features are
     *            maintainer-only and never enter the teacher surface.
     * @param defaultState {@code enabled} or {@code disabled}; null defers to the scanned YAML default.
     * @param order relation order under the parent; null appends after ordered siblings in manifest order.
     * @param configuration configuration-key confirmations and exceptions applied to the derived mappings.
     * @param name explicit name override, or null.
     * @param description explicit description override, or null.
     * @param documentationUrl explicit documentation URL override, or null.
     * @param rationale documented reason for the modeling decision, or null.
     */
    public record FeatureEntry(String id, String group, String parent, String optionality, String category, String defaultState, Integer order,
            List<ConfigurationEntry> configuration, String name, String description, String documentationUrl, String rationale) {

        /**
         * Normalizes the configuration collection to an immutable list.
         */
        public FeatureEntry {
            configuration = configuration == null ? List.of() : List.copyOf(configuration);
        }
    }

    /**
     * One configuration-key confirmation or exception of a member. An include entry adds or confirms a key as a
     * deployment input in declaration order; an exclude entry rejects a derived key.
     *
     * @param key dotted configuration key.
     * @param secret whether the value is a secret; null defers to the derived classification.
     * @param action {@link #CONFIGURATION_ACTION_INCLUDE} or {@link #CONFIGURATION_ACTION_EXCLUDE}; null defaults to
     *            include.
     */
    public record ConfigurationEntry(String key, Boolean secret, String action) {
    }

    /**
     * Manifest-declared technical or infrastructure member. Kind {@code feature} and category {@code technical} are
     * implied by the section; the compose, profile, and environment mappings are derived from the anchor.
     *
     * @param anchor candidate id or canonical source symbol.
     * @param feature id and modeling semantics of the member.
     * @param profiles Spring profile tokens the member activates; empty defers to the anchor's profile name.
     */
    public record TechnicalEntry(String anchor, FeatureEntry feature, List<String> profiles) {

        /**
         * Normalizes the profile list to an immutable copy.
         */
        public TechnicalEntry {
            profiles = profiles == null ? List.of() : List.copyOf(profiles);
        }
    }

    /**
     * Explicit exclusion with an optional reason code.
     *
     * @param anchor candidate id or canonical source symbol.
     * @param reason stable reason code; missing values normalize to {@link #EXCLUSION_REASON_UNSPECIFIED}.
     * @param rationale human-readable reasoning for the decision, or null.
     */
    public record NotModeledEntry(String anchor, String reason, String rationale) {

        /**
         * Normalizes an omitted reason to the stable report grouping fallback.
         */
        public NotModeledEntry {
            reason = reason == null ? EXCLUSION_REASON_UNSPECIFIED : reason;
        }
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
     *            null defaults to {@code and}. An {@code alternative} group models an xor choice whose pairwise
     *            exclusions are derived.
     * @param order relation order under the parent; null appends after ordered siblings in manifest order.
     * @param name optional explicit name.
     * @param description optional explicit description.
     */
    public record ConceptualNode(String id, String parent, String kind, String optionality, String category, String groupType, Integer order, String name,
            String description) {
    }

    /**
     * Declared cross-tree constraint of the generated model. The extraction provides exclusivity and dependency
     * evidence; declaring the enforced constraint remains a curation decision like any other inclusion. A declared
     * constraint that duplicates a derived alternative-group exclusion is reported redundant and dropped.
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
     * Relation evidence between two members that deliberately does not become a constraint. Every relation candidate
     * needs a decision just like every feature candidate, so ignoring one is written down with its reason instead of
     * being silently dropped.
     *
     * @param id relation candidate id the decision applies to.
     * @param rationale maintainer-authored reason why the relation stays unenforced.
     */
    public record IgnoredRelationEntry(String id, String rationale) {
    }

    /**
     * Resolved artifact mapping of a member in the generated model's explicit-source mapping shape. Mappings are
     * derived, never declared: the deriver produces them from the scanned structure and the manifest confirmations.
     *
     * @param target generated file the entry belongs to.
     * @param path dotted configuration path or variable name written into the target.
     * @param source explicit value source, {@code selection} or {@code environment}.
     * @param valueWhenSelected value written when the owning feature is selected, or null.
     * @param valueWhenDeselected value written when the owning feature is not selected, or null.
     * @param secret whether the value is a secret that must never be emitted as plaintext.
     */
    public record MappingHint(String target, String path, String source, Object valueWhenSelected, Object valueWhenDeselected, Boolean secret) {
    }
}
