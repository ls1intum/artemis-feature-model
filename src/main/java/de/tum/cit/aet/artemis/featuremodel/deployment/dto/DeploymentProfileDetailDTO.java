package de.tum.cit.aet.artemis.featuremodel.deployment.dto;

import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;

/**
 * Full detail of a deployment profile. Since profile version {@code 2.0.0} a profile is a pure capability manifest,
 * so the detail carries capabilities and notes but no parameter values.
 *
 * @param id stable profile id.
 * @param name human-readable profile name.
 * @param version profile version.
 * @param status lifecycle status.
 * @param editableBy roles allowed to edit this profile.
 * @param providedCapabilities technical capabilities this profile provides.
 * @param unresolvedNotes notes about entries that could not be confidently resolved.
 * @param defaultProfile whether this profile is the prototype default profile.
 */
public record DeploymentProfileDetailDTO(String id, String name, String version, String status, List<String> editableBy, List<String> providedCapabilities,
        List<String> unresolvedNotes, boolean defaultProfile) {

    /**
     * Builds a detail DTO from a deployment profile.
     *
     * @param profile deployment profile.
     * @param defaultProfile whether this profile is the prototype default profile.
     * @return deployment profile detail DTO.
     */
    public static DeploymentProfileDetailDTO from(DeploymentProfile profile, boolean defaultProfile) {
        return new DeploymentProfileDetailDTO(profile.id(), profile.name(), profile.version(), profile.status(), profile.editableBy(),
                profile.providedCapabilities(), profile.unresolvedNotes(), defaultProfile);
    }
}
