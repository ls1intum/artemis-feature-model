package de.tum.cit.aet.artemis.featuremodel.deployment.domain;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Deployment Profile represents maintainer-approved technical assumptions and infrastructure capabilities for a
 * deployment context. Teachers configure functional features through the guided workflow while the active profile
 * decides which technical capabilities are available and supplies the parameters later phases use for artifact
 * generation.
 *
 * <p>
 * Profiles are loaded from JSON files. Secret values are stored only as references or placeholders (for example
 * {@code env:PYRIS_SECRET} or {@code vault:artemis/pyris/shared-secret}), never as plaintext secrets.
 *
 * @param id stable profile id, also the file base name.
 * @param name human-readable profile name.
 * @param version profile version.
 * @param status lifecycle status, for example {@code published}.
 * @param editableBy roles allowed to edit this profile.
 * @param providedCapabilities technical capabilities this profile makes available.
 * @param parameters non-secret parameters and secret references keyed by parameter name.
 * @param unresolvedNotes optional notes about entries that could not be confidently resolved.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeploymentProfile(String id, String name, String version, String status, List<String> editableBy, List<String> providedCapabilities,
        Map<String, String> parameters, List<String> unresolvedNotes) {

    /**
     * Creates a deployment profile and normalizes nullable collections to immutable empty collections.
     *
     * @param id stable profile id.
     * @param name human-readable profile name.
     * @param version profile version.
     * @param status lifecycle status.
     * @param editableBy roles allowed to edit this profile.
     * @param providedCapabilities technical capabilities this profile provides.
     * @param parameters non-secret parameters and secret references.
     * @param unresolvedNotes optional notes about unresolved entries.
     */
    public DeploymentProfile {
        editableBy = editableBy == null ? List.of() : List.copyOf(editableBy);
        providedCapabilities = providedCapabilities == null ? List.of() : List.copyOf(providedCapabilities);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        unresolvedNotes = unresolvedNotes == null ? List.of() : List.copyOf(unresolvedNotes);
    }

    /**
     * Checks whether this profile provides a given technical capability.
     *
     * @param capability capability id to check.
     * @return true if the capability is in this profile's provided capabilities.
     */
    public boolean providesCapability(String capability) {
        return providedCapabilities.contains(capability);
    }
}
