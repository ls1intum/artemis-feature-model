package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.export.domain.ProfileValueKind;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ResolvedProfileValue;

/**
 * Classifies and resolves single deployment profile parameter values for artifact generation.
 *
 * <p>
 * Literal values are typed (boolean, integer, decimal, or string) so the overlay can write them with the right YAML
 * type. {@code env:NAME} references become {@code ${NAME}} placeholders and contribute an environment variable.
 * {@code vault:...} references are not resolved in this phase and are reported as unresolved. The resolver also detects
 * demo placeholder values and known deprecated profile keys so the report can warn about them.
 */
@Component
public class ProfileParameterResolver {

    private static final String ENV_PREFIX = "env:";

    private static final String VAULT_PREFIX = "vault:";

    private static final List<String> PLACEHOLDER_MARKERS = List.of("example.com", "your-", "<", "placeholder", "changeme", "abcdef12345");

    /** Known stale profile keys mapped to their canonical Artemis configuration keys, for migration diagnostics only. */
    private static final Map<String, String> DEPRECATED_ALIASES = deprecatedAliases();

    /**
     * Resolves and classifies a single profile parameter value.
     *
     * @param rawValue raw profile parameter value.
     * @return resolved value with its classification, typed overlay value, and placeholder flag.
     */
    public ResolvedProfileValue resolve(String rawValue) {
        if (rawValue == null) {
            return new ResolvedProfileValue(ProfileValueKind.LITERAL, null, null, true);
        }
        if (rawValue.startsWith(ENV_PREFIX)) {
            String name = rawValue.substring(ENV_PREFIX.length());
            return new ResolvedProfileValue(ProfileValueKind.ENV, "${" + name + "}", name, name.isBlank());
        }
        if (rawValue.startsWith(VAULT_PREFIX)) {
            return new ResolvedProfileValue(ProfileValueKind.VAULT, null, null, false);
        }
        return new ResolvedProfileValue(ProfileValueKind.LITERAL, typedScalar(rawValue), null, isPlaceholder(rawValue));
    }

    /**
     * Returns the known deprecated profile keys that are present in the given parameters, mapped to their canonical
     * replacements. The normal generation path uses canonical keys; this is migration diagnostics only.
     *
     * @param parameters profile parameters.
     * @return present deprecated keys mapped to canonical keys, in insertion order.
     */
    public Map<String, String> deprecatedAliasesIn(Map<String, String> parameters) {
        Map<String, String> present = new LinkedHashMap<>();
        for (Map.Entry<String, String> alias : DEPRECATED_ALIASES.entrySet()) {
            if (parameters.containsKey(alias.getKey())) {
                present.put(alias.getKey(), alias.getValue());
            }
        }
        return present;
    }

    /**
     * Types a literal value so the overlay writer can emit the correct YAML scalar type.
     *
     * @param value literal profile value.
     * @return a Boolean, Long, Double, or the original String.
     */
    private Object typedScalar(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        try {
            if (value.matches("-?\\d+")) {
                return Long.parseLong(value);
            }
            if (value.matches("-?\\d+\\.\\d+")) {
                return Double.parseDouble(value);
            }
        }
        catch (NumberFormatException ignored) {
            return value;
        }
        return value;
    }

    /**
     * Detects demo placeholder values that must be replaced before real deployment.
     *
     * @param value literal profile value.
     * @return true if the value is blank or contains a known placeholder marker.
     */
    private boolean isPlaceholder(String value) {
        if (value.isBlank()) {
            return true;
        }
        String lower = value.toLowerCase();
        for (String marker : PLACEHOLDER_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds the deprecated-alias diagnostics table.
     *
     * @return deprecated profile keys mapped to canonical Artemis keys.
     */
    private static Map<String, String> deprecatedAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("pyris.url", "artemis.iris.url");
        aliases.put("pyris.secretRef", "artemis.iris.secret-token");
        aliases.put("athena.secretRef", "artemis.athena.secret");
        aliases.put("athena.restrictedModules", "artemis.athena.restricted-modules");
        aliases.put("apollon.conversionServiceUrl", "artemis.apollon.conversion-service-url");
        aliases.put("theia.portalUrl", "artemis.theia.portal-url");
        aliases.put("sharing.serverUrl", "artemis.sharing.serverurl");
        aliases.put("sharing.apiKeyRef", "artemis.sharing.apikey");
        aliases.put("springAi.openAi.apiKeyRef", "spring.ai.openai.api-key");
        aliases.put("server.publicUrl", "server.url");
        return Map.copyOf(aliases);
    }
}
