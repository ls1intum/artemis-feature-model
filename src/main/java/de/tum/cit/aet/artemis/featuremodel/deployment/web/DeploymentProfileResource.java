package de.tum.cit.aet.artemis.featuremodel.deployment.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.featuremodel.deployment.dto.DeploymentProfileDetailDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.dto.DeploymentProfileSummaryDTO;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;

@RestController
@RequestMapping("/api/deployment-profiles")
public class DeploymentProfileResource {

    private static final Logger log = LoggerFactory.getLogger(DeploymentProfileResource.class);

    private final DeploymentProfileService deploymentProfileService;

    /**
     * Creates the deployment profile resource.
     *
     * @param deploymentProfileService service used to load deployment profiles.
     */
    public DeploymentProfileResource(DeploymentProfileService deploymentProfileService) {
        this.deploymentProfileService = deploymentProfileService;
    }

    /**
     * Lists the available deployment profiles with the default profile flagged.
     *
     * @return deployment profile summaries.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException if profiles cannot be loaded.
     */
    @GetMapping
    public List<DeploymentProfileSummaryDTO> listProfiles() {
        log.debug("REST request to list deployment profiles.");
        List<DeploymentProfileSummaryDTO> profiles = deploymentProfileService.listProfiles();
        log.info("REST response lists {} deployment profiles.", profiles.size());
        return profiles;
    }

    /**
     * Returns the detail of a single deployment profile.
     *
     * @param profileId profile id.
     * @return deployment profile detail.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException if the profile does not exist or cannot be loaded.
     */
    @GetMapping("/{profileId}")
    public DeploymentProfileDetailDTO getProfile(@PathVariable String profileId) {
        log.debug("REST request to get deployment profile '{}'.", profileId);
        return deploymentProfileService.getProfileDetail(profileId);
    }
}
