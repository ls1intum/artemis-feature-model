package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import tools.jackson.databind.JsonNode;

/**
 * Mapping from a feature selection to a single configuration entry in the generated overlay.
 *
 * <p>
 * Every mapping declares its value source explicitly. A {@code selection} mapping writes {@link #valueWhenSelected} or
 * {@link #valueWhenDeselected} based on whether the owning feature is selected. An {@code environment} mapping is
 * emitted only when the owning feature is selected and always writes a {@code ${VARIABLE}} placeholder whose value the
 * deployment environment supplies; this application never invents the value. JSON deserialization is lenient about
 * unknown fields, so a mapping authored in the retired {@code valueFromProfile} shape parses with a {@code null}
 * source and is rejected by {@code FeatureModelIntegrityService} instead of being silently reinterpreted.
 *
 * @param target generated overlay file the entry belongs to.
 * @param path dotted configuration path written into the overlay.
 * @param source explicit value source, one of {@link ArtifactMappingSource#SELECTION} or
 *            {@link ArtifactMappingSource#ENVIRONMENT}; validated by shared model integrity.
 * @param valueWhenSelected value written when the owning feature is selected, for selection mappings.
 * @param valueWhenDeselected value written when the owning feature is not selected, for selection mappings.
 * @param secret whether the value is a secret that must never be emitted as plaintext; classification metadata,
 *            meaningful for environment mappings.
 */
public record ArtifactMapping(String target, String path, String source, JsonNode valueWhenSelected, JsonNode valueWhenDeselected, Boolean secret) {

    /**
     * Normalizes the optional secret flag so an absent JSON field defaults to {@code false}, and explicit JSON null
     * values to absent values, so a serialized {@code null} round-trips identically to an omitted field.
     *
     * @param target generated overlay file the entry belongs to.
     * @param path dotted configuration path written into the overlay.
     * @param source explicit value source of the mapping.
     * @param valueWhenSelected value written when the owning feature is selected, for selection mappings.
     * @param valueWhenDeselected value written when the owning feature is not selected, for selection mappings.
     * @param secret whether the value is a secret that must never be emitted as plaintext.
     */
    public ArtifactMapping {
        valueWhenSelected = valueWhenSelected != null && valueWhenSelected.isNull() ? null : valueWhenSelected;
        valueWhenDeselected = valueWhenDeselected != null && valueWhenDeselected.isNull() ? null : valueWhenDeselected;
        secret = secret != null && secret;
    }

    /**
     * Checks whether this mapping writes a static selected/deselected value.
     *
     * @return true if the declared source is {@code selection}.
     */
    @JsonIgnore
    public boolean isSelection() {
        return ArtifactMappingSource.SELECTION.equals(source);
    }

    /**
     * Checks whether this mapping writes an environment placeholder supplied by the deployment environment.
     *
     * @return true if the declared source is {@code environment}.
     */
    @JsonIgnore
    public boolean isEnvironment() {
        return ArtifactMappingSource.ENVIRONMENT.equals(source);
    }
}
