package de.tum.cit.aet.artemis.featuremodel.export.dto;

import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;

/**
 * Metadata for the generated {@code metadata/deployment-profile-summary.json} file. Carries the identifying profile
 * fields and provided capabilities, but never the parameter values, so no secret references are duplicated into the
 * package.
 *
 * @param id profile id.
 * @param name profile name.
 * @param version profile version.
 * @param status profile lifecycle status.
 * @param providedCapabilities technical capabilities the profile provides.
 */
public record DeploymentProfileSummaryMetadata(String id, String name, String version, String status, List<String> providedCapabilities) {

    /**
     * Builds a summary metadata record from a deployment profile.
     *
     * @param profile deployment profile.
     * @return summary metadata.
     */
    public static DeploymentProfileSummaryMetadata from(DeploymentProfile profile) {
        return new DeploymentProfileSummaryMetadata(profile.id(), profile.name(), profile.version(), profile.status(), List.copyOf(profile.providedCapabilities()));
    }
}
