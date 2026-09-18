package de.tum.cit.aet.artemis.featuremodel.deployment.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Deployment Profile is a pure capability manifest for a deployment context. Teachers configure functional features
 * through the guided workflow while the active profile decides which technical capabilities are available. A provided
 * capability means the context supports or permits the integration and provides a path for supplying its runtime
 * requirements; the concrete values are supplied by the deployment environment, never by the profile.
 *
 * <p>
 * Profiles are loaded from JSON files. Since profile version {@code 2.0.0} profiles carry no parameter values; a
 * legacy profile containing a {@code parameters} block fails loading with an actionable migration message. Since
 * profile version {@code 3.0.0} a profile may omit {@code providedCapabilities}: the loaded record then carries
 * {@code null} until {@link de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService} resolves
 * the list as the union of the capabilities the active feature model requires, which is what the bundled default
 * profile provides by definition. An explicit list, as a maintainer local override declares, stays authoritative.
 *
 * @param id stable profile id, also the file base name.
 * @param name human-readable profile name.
 * @param version profile version.
 * @param status lifecycle status, for example {@code published}.
 * @param editableBy roles allowed to edit this profile.
 * @param providedCapabilities technical capabilities this profile makes available; {@code null} (absent field) means
 *            the list is derived from the active feature model.
 * @param unresolvedNotes optional notes about entries that could not be confidently resolved.
 * @param supportedDeploymentModes deployment mode ids this profile supports; {@code null} (absent field) means all
 *            modes are supported, for backward compatibility with profiles authored before the mode axis existed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeploymentProfile(String id, String name, String version, String status, List<String> editableBy, List<String> providedCapabilities,
        List<String> unresolvedNotes, List<String> supportedDeploymentModes) {

    /**
     * Creates a deployment profile and normalizes nullable collections to immutable empty collections. The
     * {@code supportedDeploymentModes} list deliberately stays {@code null} when absent, because an absent field means
     * "all modes supported" while an explicit empty list means "no mode supported"; likewise {@code providedCapabilities}
     * stays {@code null} when absent, because an absent field means "derived from the active model" while an explicit
     * empty list means "no capability provided".
     *
     * @param id stable profile id.
     * @param name human-readable profile name.
     * @param version profile version.
     * @param status lifecycle status.
     * @param editableBy roles allowed to edit this profile.
     * @param providedCapabilities technical capabilities this profile provides, or {@code null} to derive them.
     * @param unresolvedNotes optional notes about unresolved entries.
     * @param supportedDeploymentModes supported deployment mode ids, or {@code null} for all modes.
     */
    public DeploymentProfile {
        editableBy = editableBy == null ? List.of() : List.copyOf(editableBy);
        providedCapabilities = providedCapabilities == null ? null : List.copyOf(providedCapabilities);
        unresolvedNotes = unresolvedNotes == null ? List.of() : List.copyOf(unresolvedNotes);
        supportedDeploymentModes = supportedDeploymentModes == null ? null : List.copyOf(supportedDeploymentModes);
    }

    /**
     * Creates a deployment profile without a deployment-mode restriction, supporting all modes. Convenient for callers
     * and tests written before the mode axis existed.
     *
     * @param id stable profile id.
     * @param name human-readable profile name.
     * @param version profile version.
     * @param status lifecycle status.
     * @param editableBy roles allowed to edit this profile.
     * @param providedCapabilities technical capabilities this profile provides.
     * @param unresolvedNotes optional notes about unresolved entries.
     */
    public DeploymentProfile(String id, String name, String version, String status, List<String> editableBy, List<String> providedCapabilities,
            List<String> unresolvedNotes) {
        this(id, name, version, status, editableBy, providedCapabilities, unresolvedNotes, null);
    }

    /**
     * Checks whether this profile supports a deployment mode. A profile without a declared restriction supports every
     * mode.
     *
     * @param modeId deployment mode id to check.
     * @return true if the mode is supported by this profile.
     */
    public boolean supportsDeploymentMode(String modeId) {
        return supportedDeploymentModes == null || supportedDeploymentModes.contains(modeId);
    }

    /**
     * Checks whether this profile provides a given technical capability. An unresolved derived list provides nothing.
     *
     * @param capability capability id to check.
     * @return true if the capability is in this profile's provided capabilities.
     */
    public boolean providesCapability(String capability) {
        return providedCapabilities != null && providedCapabilities.contains(capability);
    }

    /**
     * Returns a copy of this profile with the derived provided capabilities.
     *
     * @param derivedCapabilities capabilities the active feature model requires.
     * @return copy carrying the derived list.
     */
    public DeploymentProfile withProvidedCapabilities(List<String> derivedCapabilities) {
        return new DeploymentProfile(id, name, version, status, editableBy, derivedCapabilities, unresolvedNotes, supportedDeploymentModes);
    }
}
