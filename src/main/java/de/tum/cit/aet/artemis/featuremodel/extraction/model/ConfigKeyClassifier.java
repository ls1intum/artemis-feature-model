package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Classifies derived configuration-key candidates. A key becomes a deployment input when its preferred YAML default is
 * placeholder-like or its last segment names an endpoint or credential; every other key is a tunable that is listed
 * but never emitted. A key is a secret when any segment matches the credential lexicon or its placeholder mentions a
 * secret or password — over-classification as secret is the accepted safe direction, because it only moves the value
 * into the placeholder-only channel. The lexicons and placeholder patterns are version-controlled conventions, not
 * runtime configuration.
 */
final class ConfigKeyClassifier {

    /** Key segments naming an endpoint a deployment must supply. */
    static final Set<String> ENDPOINT_LEXICON = Set.of("url", "base-url", "serverurl", "portal-url");

    /** Key segments naming a credential a deployment must supply. */
    static final Set<String> CREDENTIAL_LEXICON = Set.of("secret", "secret-token", "token", "password", "api-key", "apikey", "auth-token", "credentials");

    /** Sample secrets the Artemis defaults are known to carry; matched case-insensitively against the whole value. */
    static final Set<String> WELL_KNOWN_SAMPLE_SECRETS = Set.of("abcdef12345", "my-api-key-for-azure-openai");

    /** Lower-case fragments marking a default value as a placeholder. */
    private static final List<String> PLACEHOLDER_FRAGMENTS = List.of("your-", "placeholder", "dummy", "changeme");

    /** URLs pointing at the developer machine; production deployments must replace them. */
    private static final Pattern LOCAL_URL_PATTERN = Pattern.compile("^https?://(localhost|127\\.0\\.0\\.1)([:/].*)?$");

    private ConfigKeyClassifier() {
    }

    /**
     * Decides whether a candidate key is a deployment input.
     *
     * @param key dotted configuration key.
     * @param preferredDefault preferred scanned YAML default of the key, or null when the key has none.
     * @return true when the key must be supplied by the deployment environment.
     */
    static boolean isDeploymentInput(String key, Object preferredDefault) {
        String lastSegment = lastSegmentOf(key);
        return ENDPOINT_LEXICON.contains(lastSegment) || CREDENTIAL_LEXICON.contains(lastSegment) || isPlaceholderLike(preferredDefault);
    }

    /**
     * Decides whether a key is a secret.
     *
     * @param key dotted configuration key.
     * @param preferredDefault preferred scanned YAML default of the key, or null when the key has none.
     * @return true when the key value must never be emitted as plaintext.
     */
    static boolean isSecret(String key, Object preferredDefault) {
        for (String segment : key.split("\\.")) {
            if (CREDENTIAL_LEXICON.contains(segment)) {
                return true;
            }
        }
        if (!isPlaceholderLike(preferredDefault)) {
            return false;
        }
        String value = ((String) preferredDefault).toLowerCase(Locale.ROOT);
        return value.contains("secret") || value.contains("password");
    }

    /**
     * Decides whether a scanned default is a placeholder rather than a working value.
     *
     * @param value preferred scanned YAML default, or null.
     * @return true when the value only marks where a deployment has to put its own value.
     */
    static boolean isPlaceholderLike(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        String normalized = text.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("<") && normalized.endsWith(">")) {
            return true;
        }
        if (PLACEHOLDER_FRAGMENTS.stream().anyMatch(normalized::contains)) {
            return true;
        }
        if (WELL_KNOWN_SAMPLE_SECRETS.contains(normalized)) {
            return true;
        }
        return LOCAL_URL_PATTERN.matcher(normalized).matches();
    }

    /**
     * Returns the last dotted segment of a key.
     *
     * @param key dotted configuration key.
     * @return last segment.
     */
    private static String lastSegmentOf(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }
}
