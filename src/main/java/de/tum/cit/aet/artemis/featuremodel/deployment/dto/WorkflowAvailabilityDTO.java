package de.tum.cit.aet.artemis.featuremodel.deployment.dto;

import java.util.List;

/**
 * Profile-aware availability of the active guided workflow and feature model under one deployment profile.
 *
 * <p>
 * The Configurator reads this in a single call: {@code activeProfile} drives the review header, {@code availableProfiles}
 * populates the profile selector, and the option and feature availability lists drive disabled states and the
 * profile-dependent feature summary.
 *
 * @param activeProfile the profile the availability was resolved against.
 * @param availableProfiles all selectable profiles, for the profile selector.
 * @param options availability of each guided decision option.
 * @param features availability of each model feature.
 */
public record WorkflowAvailabilityDTO(DeploymentProfileSummaryDTO activeProfile, List<DeploymentProfileSummaryDTO> availableProfiles,
        List<OptionAvailabilityDTO> options, List<FeatureAvailabilityDTO> features) {

    /**
     * Creates a workflow availability DTO and normalizes nullable lists to immutable empty lists.
     *
     * @param activeProfile the profile the availability was resolved against.
     * @param availableProfiles all selectable profiles.
     * @param options availability of each guided decision option.
     * @param features availability of each model feature.
     */
    public WorkflowAvailabilityDTO {
        availableProfiles = availableProfiles == null ? List.of() : List.copyOf(availableProfiles);
        options = options == null ? List.of() : List.copyOf(options);
        features = features == null ? List.of() : List.copyOf(features);
    }
}
