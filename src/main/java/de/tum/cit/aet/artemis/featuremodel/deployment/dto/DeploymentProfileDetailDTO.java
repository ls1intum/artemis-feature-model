package de.tum.cit.aet.artemis.featuremodel.deployment.dto;

import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;

/**
 * Full detail of a deployment profile, including provided capabilities and parameters. Parameters expose only non-secret
 * values and secret references, matching how they are stored in the profile JSON.
 *
 * @param id stable profile id.
 * @param name human-readable profile name.
 * @param version profile version.
 * @param status lifecycle status.
 * @param editableBy roles allowed to edit this profile.
 * @param providedCapabilities technical capabilities this profile provides.
 * @param parameters non-secret parameters and secret references keyed by parameter name.
 * @param unresolvedNotes notes about entries that could not be confidently resolved.
 * @param defaultProfile whether this profile is the prototype default profile.
 */
public record DeploymentProfileDetailDTO(String id, String name, String version, String status, List<String> editableBy, List<String> providedCapabilities,
        Map<String, String> parameters, List<String> unresolvedNotes, boolean defaultProfile) {

    /**
     * Builds a detail DTO from a deployment profile.
     *
     * @param profile deployment profile.
     * @param defaultProfile whether this profile is the prototype default profile.
     * @return deployment profile detail DTO.
     */
    public static DeploymentProfileDetailDTO from(DeploymentProfile profile, boolean defaultProfile) {
        return new DeploymentProfileDetailDTO(profile.id(), profile.name(), profile.version(), profile.status(), profile.editableBy(),
                profile.providedCapabilities(), profile.parameters(), profile.unresolvedNotes(), defaultProfile);
    }
}
