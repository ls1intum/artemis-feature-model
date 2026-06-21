package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.JsonNode;

/**
 * Mapping from a feature selection to a single configuration entry in the generated overlay.
 *
 * <p>
 * A mapping is either a <em>toggle</em> (it writes {@link #valueWhenSelected} or {@link #valueWhenDeselected} based on
 * whether the owning feature is selected) or a <em>profile value</em> (it copies the value of the deployment profile
 * parameter named by {@link #valueFromProfile}, and is written only when the owning feature is selected).
 *
 * @param target generated overlay file the entry belongs to.
 * @param path dotted configuration path written into the overlay.
 * @param valueWhenSelected value written when the owning feature is selected, for toggle mappings.
 * @param valueWhenDeselected value written when the owning feature is not selected, for toggle mappings.
 * @param valueFromProfile deployment profile parameter key whose value is written, for profile mappings.
 * @param requiredWhenSelected whether a missing profile value should be reported when the owning feature is selected.
 * @param secret whether the value is a secret reference that must never be emitted as plaintext.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtifactMapping(String target, String path, JsonNode valueWhenSelected, JsonNode valueWhenDeselected, String valueFromProfile,
        Boolean requiredWhenSelected, Boolean secret) {

    /**
     * Normalizes the optional boolean flags so absent JSON fields default to {@code false}.
     *
     * @param target generated overlay file the entry belongs to.
     * @param path dotted configuration path written into the overlay.
     * @param valueWhenSelected value written when the owning feature is selected, for toggle mappings.
     * @param valueWhenDeselected value written when the owning feature is not selected, for toggle mappings.
     * @param valueFromProfile deployment profile parameter key whose value is written, for profile mappings.
     * @param requiredWhenSelected whether a missing profile value should be reported when the owning feature is selected.
     * @param secret whether the value is a secret reference that must never be emitted as plaintext.
     */
    public ArtifactMapping {
        requiredWhenSelected = requiredWhenSelected != null && requiredWhenSelected;
        secret = secret != null && secret;
    }

    /**
     * Checks whether this mapping resolves its value from a deployment profile parameter.
     *
     * @return true if {@link #valueFromProfile} names a non-blank profile parameter.
     */
    public boolean isProfileValue() {
        return valueFromProfile != null && !valueFromProfile.isBlank();
    }

    /**
     * Checks whether this mapping writes a static selected/deselected toggle value rather than a profile value.
     *
     * @return true if this is a toggle mapping with at least one toggle value.
     */
    public boolean isToggle() {
        return !isProfileValue() && (valueWhenSelected != null || valueWhenDeselected != null);
    }
}
