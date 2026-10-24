package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Configuration injection facts of one Java source file, persisted under {@code scan/config-injections.json}. The
 * granularity is deliberately the file: the guards of a file apply to every key and prefix recorded for it, and the
 * configuration deriver attributes keys to features on that basis.
 *
 * @param file checkout-relative path of the scanned Java file.
 * @param packageName declared package of the file, empty for the default package.
 * @param conditionGuards simple names of {@code *Enabled} condition classes referenced by {@code @Conditional}
 *            annotations anywhere in the file, sorted.
 * @param profileGuards profile expressions of {@code @Profile} annotations anywhere in the file — string literal
 *            values and referenced constant names — sorted.
 * @param valueKeys configuration keys injected through {@code @Value("${...}")} placeholders, without default
 *            values, sorted.
 * @param propertyPrefixes {@code @ConfigurationProperties} prefixes declared in the file with their declaring type,
 *            sorted by prefix and declaring type.
 */
public record ExtractedConfigInjection(String file, String packageName, List<String> conditionGuards, List<String> profileGuards, List<String> valueKeys,
        List<ConfigurationPropertiesPrefix> propertyPrefixes) {

    /** Normalizes every fact collection to an immutable copy. */
    public ExtractedConfigInjection {
        conditionGuards = conditionGuards == null ? List.of() : List.copyOf(conditionGuards);
        profileGuards = profileGuards == null ? List.of() : List.copyOf(profileGuards);
        valueKeys = valueKeys == null ? List.of() : List.copyOf(valueKeys);
        propertyPrefixes = propertyPrefixes == null ? List.of() : List.copyOf(propertyPrefixes);
    }

    /**
     * One {@code @ConfigurationProperties} declaration.
     *
     * @param prefix declared configuration key prefix.
     * @param declaringType simple name of the nearest enclosing type declaration.
     */
    public record ConfigurationPropertiesPrefix(String prefix, String declaringType) {
    }
}
