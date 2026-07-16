package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Opt-in curation manifest for extracted Artemis candidates. Include and exclude entries decide membership; an
 * unlisted candidate is pending. Conceptual nodes provide model scaffolding without claiming a source anchor.
 *
 * @param manifestVersion manifest schema version.
 * @param verifiedAgainstArtemisCommit Artemis commit against which the decisions were reviewed.
 * @param include explicitly included candidates.
 * @param exclude explicitly excluded candidates.
 * @param conceptualNodes unanchored model nodes.
 */
public record FeatureScopeManifest(int manifestVersion, String verifiedAgainstArtemisCommit, List<IncludeEntry> include, List<ExcludeEntry> exclude,
        List<ConceptualNode> conceptualNodes) {

    /** Current manifest schema version. */
    public static final int CURRENT_VERSION = 1;

    /** Optionality of a feature whose selection is enforced by validation and rendered as a filled circle. */
    public static final String OPTIONALITY_MANDATORY = "mandatory";

    /** Optionality of a feature users may freely select or deselect; the default when not declared. */
    public static final String OPTIONALITY_OPTIONAL = "optional";

    /**
     * Normalizes manifest collections to immutable lists.
     */
    public FeatureScopeManifest {
        include = include == null ? List.of() : List.copyOf(include);
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
        conceptualNodes = conceptualNodes == null ? List.of() : List.copyOf(conceptualNodes);
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
     * @param requiresCapabilities required deployment capabilities.
     * @param providesCapabilities capabilities supplied by the feature.
     * @param name explicit name override, or null.
     * @param description explicit description override, or null.
     * @param documentationUrl explicit documentation URL override, or null.
     * @param rationale documented reason for the scope decision, or null.
     */
    public record IncludeEntry(String anchor, String id, String group, String parent, String kind, String optionality, List<String> requiresCapabilities,
            List<String> providesCapabilities, String name, String description, String documentationUrl, String rationale) {

        /**
         * Normalizes capability collections to immutable lists.
         */
        public IncludeEntry {
            requiresCapabilities = requiresCapabilities == null ? List.of() : List.copyOf(requiresCapabilities);
            providesCapabilities = providesCapabilities == null ? List.of() : List.copyOf(providesCapabilities);
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
     * @param name optional explicit name.
     * @param description optional explicit description.
     */
    public record ConceptualNode(String id, String parent, String kind, String optionality, String name, String description) {
    }
}
