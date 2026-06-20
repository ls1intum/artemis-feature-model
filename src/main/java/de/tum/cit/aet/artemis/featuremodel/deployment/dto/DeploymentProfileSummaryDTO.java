package de.tum.cit.aet.artemis.featuremodel.deployment.dto;

import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;

/**
 * Summary of a deployment profile for listing and selection. Excludes capabilities and parameters so the listing stays
 * small; the detail DTO carries the full content.
 *
 * @param id stable profile id.
 * @param name human-readable profile name.
 * @param version profile version.
 * @param status lifecycle status.
 * @param defaultProfile whether this profile is the prototype default profile.
 */
public record DeploymentProfileSummaryDTO(String id, String name, String version, String status, boolean defaultProfile) {

    /**
     * Builds a summary from a deployment profile.
     *
     * @param profile deployment profile.
     * @param defaultProfile whether this profile is the prototype default profile.
     * @return deployment profile summary DTO.
     */
    public static DeploymentProfileSummaryDTO from(DeploymentProfile profile, boolean defaultProfile) {
        return new DeploymentProfileSummaryDTO(profile.id(), profile.name(), profile.version(), profile.status(), defaultProfile);
    }
}
