package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Curated catalog that binds feature-model identities to Ansible collection variables for the remote-ansible
 * deployment package. The catalog is an application classpath resource in both runtime source modes; its version axis
 * is the pinned collection commit, not the Artemis revision, so it never travels in a model snapshot. Content is
 * curated from the live-verified sample-to-lab transformation table; per-entry evidence records where a value shape
 * comes from.
 *
 * <p>
 * Rendered lines are emitted verbatim into the generated inventory files. Two substitution tokens are supported:
 * {@code {value}} in environment entry lines (replaced with the resolved environment value) and
 * {@code {vaultServerName}} in lines and vault paths (replaced with the resolved vault server name).
 *
 * @param catalogVersion catalog format and content version.
 * @param collectionPin commit of the pinned Ansible collection this catalog was curated against.
 * @param curationSource human-readable reference to the curation sources.
 * @param baseline ordered baseline entries of the common configuration values file.
 * @param environment ordered admin-owned identity entries, keyed to environment inputs.
 * @param secrets deployment-internal secret variables rendered as vault lookups in the target secrets file.
 * @param technical bindings of the technical database and CI-provider axes.
 * @param features classification and binding of every selectable functional feature.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnsibleBindingCatalog(int catalogVersion, String collectionPin, String curationSource, List<BaselineEntry> baseline,
        List<EnvironmentEntry> environment, List<SecretEntry> secrets, TechnicalBindings technical, Map<String, FeatureBinding> features) {

    /** Emission kind of a baseline entry that is always rendered verbatim. */
    public static final String EMISSION_ALWAYS = "always";

    /** Emission kind of a baseline entry rendering an explicit null to defeat a collection default. */
    public static final String EMISSION_NULL_OVERRIDE = "null-override";

    /** Classification of a feature with a rendered deployment-plane binding. */
    public static final String BINDING_BOUND = "bound";

    /** Classification of a feature that needs no deployment-plane configuration. */
    public static final String BINDING_NO_OP = "no-op";

    /** Classification of a feature state the pinned collection cannot express. */
    public static final String BINDING_UNSUPPORTED = "unsupported";

    /** Gating of a bound feature whose block is emitted if and only if the feature is selected. */
    public static final String GATING_PRESENCE = "presence";

    /** Gating of a bound feature whose block is emitted only when every gated field is non-empty. */
    public static final String GATING_VALUE_GATED = "value-gated";

    /** Unsupported direction: deselecting the feature is inexpressible. */
    public static final String UNSUPPORTED_WHEN_DESELECTED = "deselected";

    /** Unsupported direction: selecting the feature is inexpressible. */
    public static final String UNSUPPORTED_WHEN_SELECTED = "selected";

    /** Target file marker of environment entries rendered into the common configuration values file. */
    public static final String FILE_COMMON_CONFIG = "common-config";

    /** Target file marker of environment entries rendered into the target-group main values file. */
    public static final String FILE_TARGET_MAIN = "target-main";

    /**
     * Normalizes nullable collections to immutable empty collections.
     *
     * @param catalogVersion catalog version.
     * @param collectionPin pinned collection commit.
     * @param curationSource curation source reference.
     * @param baseline baseline entries.
     * @param environment environment entries.
     * @param secrets secret entries.
     * @param technical technical bindings.
     * @param features feature bindings by feature id.
     */
    public AnsibleBindingCatalog {
        baseline = baseline == null ? List.of() : List.copyOf(baseline);
        environment = environment == null ? List.of() : List.copyOf(environment);
        secrets = secrets == null ? List.of() : List.copyOf(secrets);
        features = features == null ? Map.of() : Map.copyOf(features);
    }

    /**
     * Baseline entry of the common configuration values file.
     *
     * @param var collection variable name.
     * @param emission {@link #EMISSION_ALWAYS} or {@link #EMISSION_NULL_OVERRIDE}.
     * @param order rendering position among all common configuration entries.
     * @param group blank-line group id; a group change renders a separating blank line.
     * @param lines rendered lines, emitted verbatim.
     * @param reason curation reason, mandatory for null-override entries.
     * @param evidence curation evidence reference.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BaselineEntry(String var, String emission, int order, int group, List<String> lines, String reason, String evidence) {

        /**
         * Normalizes the line list to an immutable list.
         *
         * @param var collection variable name.
         * @param emission emission kind.
         * @param order rendering position.
         * @param group blank-line group id.
         * @param lines rendered lines.
         * @param reason curation reason.
         * @param evidence curation evidence reference.
         */
        public BaselineEntry {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /**
     * Admin-owned identity entry whose value comes from the remote environment input or a placeholder.
     *
     * @param var collection variable name.
     * @param input remote environment input name supplying the value.
     * @param file {@link #FILE_COMMON_CONFIG} or {@link #FILE_TARGET_MAIN}.
     * @param order rendering position among the entries of the target file.
     * @param group blank-line group id shared with the baseline entries of the same file.
     * @param lines rendered lines with the {@code {value}} token.
     * @param reason curation reason.
     * @param evidence curation evidence reference.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnvironmentEntry(String var, String input, String file, int order, int group, List<String> lines, String reason, String evidence) {

        /**
         * Normalizes the line list to an immutable list.
         *
         * @param var collection variable name.
         * @param input environment input name.
         * @param file target file marker.
         * @param order rendering position.
         * @param group blank-line group id.
         * @param lines rendered lines.
         * @param reason curation reason.
         * @param evidence curation evidence reference.
         */
        public EnvironmentEntry {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /**
     * Deployment-internal secret variable rendered as a vault lookup expression in the target secrets file.
     *
     * @param var collection variable name.
     * @param vaultPath vault path template with the {@code {vaultServerName}} token.
     * @param vaultField field name inside the vault secret.
     * @param evidence curation evidence reference.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SecretEntry(String var, String vaultPath, String vaultField, String evidence) {
    }

    /**
     * Bindings of the technical axes.
     *
     * @param database database bindings by feature id.
     * @param ciProvider CI-provider bindings by feature id.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
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
     * @param gating {@link #GATING_PRESENCE} or {@link #GATING_VALUE_GATED} for bound features.
     * @param membership inventory group the target joins when the binding applies.
     * @param groupVarsFile name of the rendered group values file.
     * @param lines rendered lines of a presence-gated block, emitted verbatim.
     * @param prefixLines rendered lines before the gated fields of a value-gated block.
     * @param gatedFields gated fields of a value-gated block; the block is emitted only when all are non-empty.
     * @param suffixLines rendered lines after the gated fields of a value-gated block.
     * @param vaultReferences vault references the rendered lines contain.
     * @param unsupportedWhen direction of an unsupported binding: {@link #UNSUPPORTED_WHEN_DESELECTED} or
     *            {@link #UNSUPPORTED_WHEN_SELECTED}.
     * @param missingVariable missing-variable reason of an unsupported feature binding.
     * @param reason curation reason of a no-op or unsupported binding.
     * @param evidence curation evidence reference.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeatureBinding(String binding, String gating, String membership, String groupVarsFile, List<String> lines, List<String> prefixLines,
            List<GatedField> gatedFields, List<String> suffixLines, List<VaultReference> vaultReferences, String unsupportedWhen, String missingVariable,
            String reason, String evidence) {

        /**
         * Normalizes nullable collections to immutable empty collections.
         *
         * @param binding classification.
         * @param gating gating kind.
         * @param membership inventory group.
         * @param groupVarsFile group values file name.
         * @param lines presence-gated block lines.
         * @param prefixLines value-gated prefix lines.
         * @param gatedFields value-gated fields.
         * @param suffixLines value-gated suffix lines.
         * @param vaultReferences contained vault references.
         * @param unsupportedWhen unsupported direction.
         * @param missingVariable missing-variable reason.
         * @param reason curation reason.
         * @param evidence curation evidence reference.
         */
        public FeatureBinding {
            lines = lines == null ? List.of() : List.copyOf(lines);
            prefixLines = prefixLines == null ? List.of() : List.copyOf(prefixLines);
            gatedFields = gatedFields == null ? List.of() : List.copyOf(gatedFields);
            suffixLines = suffixLines == null ? List.of() : List.copyOf(suffixLines);
            vaultReferences = vaultReferences == null ? List.of() : List.copyOf(vaultReferences);
        }
    }

    /**
     * One gated field of a value-gated block.
     *
     * @param name field name inside the value block.
     * @param line rendered line of the field, emitted verbatim when the block is complete.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GatedField(String name, String line) {
    }

    /**
     * One vault reference contained in rendered lines.
     *
     * @param path vault path, possibly with the {@code {vaultServerName}} token.
     * @param field field name inside the vault secret.
     * @param consumer collection variable path that consumes the resolved value.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VaultReference(String path, String field, String consumer) {
    }
}
