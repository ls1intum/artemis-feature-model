package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * One environment value a generated package requires the deployment environment to supply. Every selected
 * environment-sourced artifact mapping produces exactly one requirement, and package-only runtime values (for example
 * the Jenkins build-agent credentials) are declared through the same record, so the overlay placeholders,
 * {@code env/.env.example}, the generation report, the readmes, and the Compose environment stay in agreement.
 *
 * @param name environment variable name; derived from the configuration path for artifact-mapping requirements.
 * @param featureId feature the requirement belongs to.
 * @param featureName human-readable name of the owning feature.
 * @param configKey dotted configuration key the overlay references, or {@code null} for package-only requirements.
 * @param catalogType Artemis config-key catalog type of {@link #configKey}, or {@code null} when the requirement is
 *            package-only or the key is absent from the catalog.
 * @param secret whether the value is a secret that must be obtained from the deployment secret store.
 * @param source requirement source, one of {@link #SOURCE_ARTIFACT_MAPPING} or {@link #SOURCE_RUNTIME_PACKAGE}.
 * @param purpose human-readable purpose of the value.
 */
public record EnvironmentRequirement(String name, String featureId, String featureName, String configKey, String catalogType, boolean secret, String source,
        String purpose) {

    /** The requirement backs a {@code ${VARIABLE}} placeholder written by an environment-sourced artifact mapping. */
    public static final String SOURCE_ARTIFACT_MAPPING = "artifact-mapping";

    /** The requirement is declared by the runtime package itself and has no configuration key in the overlay. */
    public static final String SOURCE_RUNTIME_PACKAGE = "runtime-package";

    /**
     * Checks whether this requirement is backed by a catalog-keyed overlay entry.
     *
     * @return true if the requirement carries a configuration key.
     */
    public boolean isCatalogKeyed() {
        return configKey != null;
    }
}
