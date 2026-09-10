package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;
import java.util.Map;


/**
 * Curated catalog that binds feature-model identities to Ansible collection variables for the remote-ansible
 * deployment package. The catalog is an application classpath resource in both runtime source modes; its version axis
 * is the pinned collection commit, not the Artemis revision, so it never travels in a model snapshot. Content is
 * curated from the live-verified sample-to-lab transformation table; per-entry evidence records where a value shape
 * comes from.
 *
 * <p>
 * Content blocks are emitted verbatim into the generated inventory files. No environment value is ever baked into a
 * line: every admin-owned or secret value is expressed as a {@code lookup('ansible.builtin.env', …)} expression over
 * the user-provisioned environment-variable names, which the execution environment resolves on the control node.
 *
 * @param catalogVersion catalog format and content version.
 * @param collectionPin commit of the pinned Ansible collection this catalog was curated against.
 * @param curationSource human-readable reference to the curation sources.
 * @param files package values content and derived environment references.
 * @param technical bindings of the technical database and CI-provider axes.
 * @param features classification and binding of every selectable functional feature.
 */
public record AnsibleBindingCatalog(int catalogVersion, String collectionPin, String curationSource, PackageFiles files, TechnicalBindings technical, Map<String, FeatureBinding> features) {

    /** Classification of a feature with a rendered deployment-plane binding. */
    public static final String BINDING_BOUND = "bound";

    /** Classification of a feature that needs no deployment-plane configuration. */
    public static final String BINDING_NO_OP = "no-op";

    /** Classification of a feature state the pinned collection cannot express. */
    public static final String BINDING_UNSUPPORTED = "unsupported";

    /** Gating of a bound feature binding that is emitted when the feature is deselected instead of selected. */
    public static final String GATING_DESELECTED = "deselected";

    /** Unsupported direction: deselecting the feature is inexpressible. */
    public static final String UNSUPPORTED_WHEN_DESELECTED = "deselected";

    /** Unsupported direction: selecting the feature is inexpressible. */
    public static final String UNSUPPORTED_WHEN_SELECTED = "selected";

    /**
     * Returns the three binding sections in a fixed order: technical database, technical CI provider, features.
     *
     * @return binding sections by feature id.
     */
    public List<Map<String, FeatureBinding>> sections() {
        return List.of(technical.database(), technical.ciProvider(), features);
    }

    /**
     * Finds the classification of a feature id across the binding sections.
     *
     * @param featureId feature id.
     * @return binding, or {@code null} if the catalog does not classify the feature.
     */
    public FeatureBinding bindingFor(String featureId) {
        for (Map<String, FeatureBinding> section : sections()) {
            FeatureBinding binding = section.get(featureId);
            if (binding != null) {
                return binding;
            }
        }
        return null;
    }

    /**
     * Values files included in every package.
     * @param targetMain target identity values.
     * @param targetSecrets target secret lookups.
     * @param commonConfig shared configuration.
     */
    public record PackageFiles(ValuesFile targetMain, ValuesFile targetSecrets, ValuesFile commonConfig) {
    }

    /**
     * Parsed values content with derived references in document order.
     * @param content annotation-stripped YAML.
     * @param envReferences derived environment references.
     */
    public record ValuesFile(String content, List<EnvReference> envReferences) {
    }

    /**
     * Bindings of the technical axes.
     *
     * @param database database bindings by feature id.
     * @param ciProvider CI-provider bindings by feature id.
     */
    public record TechnicalBindings(Map<String, FeatureBinding> database, Map<String, FeatureBinding> ciProvider) {

        /**
         * Normalizes nullable maps to immutable empty maps.
         *
         * @param database database bindings.
         * @param ciProvider CI-provider bindings.
         */
        public TechnicalBindings {
            database = database == null ? Map.of() : Map.copyOf(database);
            ciProvider = ciProvider == null ? Map.of() : Map.copyOf(ciProvider);
        }
    }

    /**
     * Classification and binding of one feature or technical choice.
     *
     * @param binding {@link #BINDING_BOUND}, {@link #BINDING_NO_OP}, or {@link #BINDING_UNSUPPORTED}.
     * @param gating emission gating of a bound feature binding: {@code null} for the presence-gated default (emitted
     *            when the feature is selected) or {@link #GATING_DESELECTED} (emitted when the feature is deselected,
     *            the shape of the collection's module off-switches).
     * @param membership inventory group the target joins when the binding applies.
     * @param content rendered YAML of the bound block.
     * @param envReferences environment references the rendered content contain.
     * @param unsupportedWhen direction of an unsupported binding: {@link #UNSUPPORTED_WHEN_DESELECTED} or
     *            {@link #UNSUPPORTED_WHEN_SELECTED}.
     * @param missingVariable missing-variable reason of an unsupported feature binding.
     * @param reason curation reason of a no-op or unsupported binding.
     * @param evidence curation evidence reference.
     */
    public record FeatureBinding(String binding, String gating, String membership, String content,
            List<EnvReference> envReferences, String unsupportedWhen, String missingVariable, String reason, String evidence) {

        /**
         * Derives the values filename from inventory membership.
         * @return group values filename.
         */
        public String groupVarsFile() {
            return membership + ".yml";
        }

        /**
         * Returns whether the binding is emitted when its feature is deselected instead of selected.
         *
         * @return {@code true} for a deselection-gated binding.
         */
        public boolean emittedWhenDeselected() {
            return GATING_DESELECTED.equals(gating);
        }
    }

    /**
     * One environment reference contained in rendered content.
     *
     * @param envVar user-provisioned environment-variable name.
     * @param consumer collection variable path that consumes the resolved value.
     */
    public record EnvReference(String envVar, String consumer) {
    }
}
