package de.tum.cit.aet.artemis.featuremodel.deployment.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.featuremodel.deployment.dto.WorkflowAvailabilityDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.CapabilityResolutionService;

@RestController
@RequestMapping("/api/feature-model")
public class ProfileAvailabilityResource {

    private static final Logger log = LoggerFactory.getLogger(ProfileAvailabilityResource.class);

    private final CapabilityResolutionService capabilityResolutionService;

    /**
     * Creates the profile availability resource.
     *
     * @param capabilityResolutionService service used to resolve profile-aware availability.
     */
    public ProfileAvailabilityResource(CapabilityResolutionService capabilityResolutionService) {
        this.capabilityResolutionService = capabilityResolutionService;
    }

    /**
     * Returns profile-aware availability of the active guided workflow and feature model. Resolves against the requested
     * profile when {@code profileId} is provided, otherwise against the default profile.
     *
     * @param profileId optional profile id to resolve availability against.
     * @return profile-aware workflow availability.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException if the requested profile cannot be resolved.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException if the model or workflow cannot be loaded.
     */
    @GetMapping("/profile-availability")
    public WorkflowAvailabilityDTO getProfileAvailability(@RequestParam(required = false) String profileId) {
        log.debug("REST request for profile-aware availability with profileId '{}'.", profileId);
        WorkflowAvailabilityDTO availability = capabilityResolutionService.resolveAvailability(profileId);
        log.info("REST response for availability resolved against profile '{}' with {} options and {} features.", availability.activeProfile().id(),
                availability.options().size(), availability.features().size());
        return availability;
    }
}
