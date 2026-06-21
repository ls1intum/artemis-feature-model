package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * Result of classifying and resolving a single deployment profile parameter value.
 *
 * @param kind value classification.
 * @param yamlValue typed value to write into the overlay (Boolean, Long, Double, or String), or {@code null} when the
 *            value must not be written (for example an unresolved Vault reference).
 * @param envVarName environment variable name for {@link ProfileValueKind#ENV} values, otherwise {@code null}.
 * @param placeholder whether a literal value looks like a demo placeholder that must be replaced before deployment.
 */
public record ResolvedProfileValue(ProfileValueKind kind, Object yamlValue, String envVarName, boolean placeholder) {

    /**
     * Checks whether the value can be written into the overlay.
     *
     * @return true if {@link #yamlValue} is present.
     */
    public boolean isWritable() {
        return yamlValue != null;
    }
}
