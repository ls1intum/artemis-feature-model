package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Curation manifest for extracted Artemis candidates, schema version 4. Membership of a functional feature is declared
 * by the {@code @ArtemisFeature} annotation in Artemis source, or by a {@code provisional} entry while that annotation
 * has not landed upstream; {@code technical} entries declare the maintainer-only features without a Java anchor;
 * {@code notModeled} entries record the deliberate exclusions. Every modeling judgment of a member (placement, order,
 * optionality, category, capabilities, artifact-mapping hints, prose overrides) lives in its {@code features} entry,
 * keyed by feature id. Conceptual nodes provide hierarchy without a source anchor, and cross-tree constraints declare
 * the relations the generated model enforces beyond the hierarchy. The Artemis source revision is derived from the
 * verified checkout and the runtime image reference is delivery configuration, so neither identity lives here.
 *
 * @param manifestVersion manifest schema version.
 * @param features modeling semantics of every member, keyed by id.
 * @param provisional manifest-carried membership for anchors whose annotation has not landed upstream.
 * @param technical manifest-declared technical and infrastructure members.
 * @param notModeled explicitly excluded candidates.
 * @param conceptualNodes unanchored model nodes.
 * @param constraints declared cross-tree constraints of the generated model.
 * @param ignoredRelations relation candidates between members that deliberately stay unenforced.
 * @param renames explicit workflow feature-id renames authorized by a maintainer.
 */
public record FeatureScopeManifest(int manifestVersion, List<FeatureEntry> features, List<ProvisionalEntry> provisional, List<TechnicalEntry> technical,
        List<NotModeledEntry> notModeled, List<ConceptualNode> conceptualNodes, List<ConstraintEntry> constraints, List<IgnoredRelationEntry> ignoredRelations,
        List<RenameEntry> renames) {

    /** Current manifest schema version. */
    public static final int CURRENT_VERSION = 4;

    /** Optionality of a feature whose selection is enforced by validation and rendered as a filled circle. */
    public static final String OPTIONALITY_MANDATORY = "mandatory";

    /** Optionality of a feature users may freely select or deselect; the default when not declared. */
    public static final String OPTIONALITY_OPTIONAL = "optional";

    /** Category of course-facing functional features; the default for module members. */
    public static final String CATEGORY_FUNCTIONAL = "functional";

    /** Category of maintainer-facing technical features that never enter the teacher surface. */
    public static final String CATEGORY_TECHNICAL = "technical";

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
        provisional = provisional == null ? List.of() : List.copyOf(provisional);
        technical = technical == null ? List.of() : List.copyOf(technical);
        notModeled = notModeled == null ? List.of() : List.copyOf(notModeled);
        conceptualNodes = conceptualNodes == null ? List.of() : List.copyOf(conceptualNodes);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        ignoredRelations = ignoredRelations == null ? List.of() : List.copyOf(ignoredRelations);
        renames = renames == null ? List.of() : List.copyOf(renames);
    }

    /**
     * Modeling semantics of one member. The entry never grants membership: the id must belong to an annotated anchor,
     * a provisional entry, or a technical entry.
     *
     * @param id feature id.
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
     * @param artifactMappings declared artifact mapping hints beyond the auto-derived enabled-key mapping; only
     *            technical entries may declare them, functional features carry {@code configuration} instead.
     * @param configuration configuration-key confirmations and exceptions applied to the derived mappings.
     * @param name explicit name override, or null.
     * @param description explicit description override, or null.
     * @param documentationUrl explicit documentation URL override, or null.
     * @param rationale documented reason for the modeling decision, or null.
     */
    public record FeatureEntry(String id, String group, String parent, String kind, String optionality, String category, String defaultState, Integer order,
            List<String> requiresCapabilities, List<String> providesCapabilities, List<MappingHint> artifactMappings, List<ConfigurationEntry> configuration,
            String name, String description, String documentationUrl, String rationale) {

        /**
         * Normalizes capability, mapping, and configuration collections to immutable lists.
         */
        public FeatureEntry {
            requiresCapabilities = requiresCapabilities == null ? List.of() : List.copyOf(requiresCapabilities);
            providesCapabilities = providesCapabilities == null ? List.of() : List.copyOf(providesCapabilities);
            artifactMappings = artifactMappings == null ? List.of() : List.copyOf(artifactMappings);
            configuration = configuration == null ? List.of() : List.copyOf(configuration);
        }
    }

    /**
     * One configuration-key confirmation or exception of a functional {@code features} entry. An include entry adds
     * or confirms a key as a deployment input in declaration order; an exclude entry rejects a derived key. An entry
     * naming a key the {@code @ArtemisFeature} annotation declares can never alter or remove it.
     *
     * @param key dotted configuration key.
     * @param secret whether the value is a secret; null defers to the derived classification.
     * @param action {@link #CONFIGURATION_ACTION_INCLUDE} or {@link #CONFIGURATION_ACTION_EXCLUDE}; null defaults to
     *            include.
     */
    public record ConfigurationEntry(String key, Boolean secret, String action) {
    }

    /**
     * Manifest-carried membership for an anchor whose {@code @ArtemisFeature} annotation has not landed in upstream
     * Artemis. Once the annotation resolves to the same candidate the annotation wins and the entry is reported as
     * redundant.
     *
     * @param anchor candidate id or canonical source symbol.
     * @param id feature id the annotation is expected to declare.
     */
    public record ProvisionalEntry(String anchor, String id) {
    }

    /**
     * Manifest-declared technical or infrastructure member. Such candidates have no Java symbol an annotation could
     * sit on, so the entry carries anchor, id, and semantics together.
     *
     * @param anchor candidate id or canonical source symbol.
     * @param feature id and modeling semantics of the member.
     */
    public record TechnicalEntry(String anchor, FeatureEntry feature) {
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
     * Explicitly authorized workflow feature-id rename.
     *
     * @param from former feature id referenced by the workflow.
     * @param to current manifest-declared feature id.
     * @param rationale maintainer-authored reason why the feature semantics are unchanged.
     */
    public record RenameEntry(String from, String to, String rationale) {
    }

    /**
     * Declared artifact mapping hint mirroring the generated model's explicit-source mapping shape.
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
