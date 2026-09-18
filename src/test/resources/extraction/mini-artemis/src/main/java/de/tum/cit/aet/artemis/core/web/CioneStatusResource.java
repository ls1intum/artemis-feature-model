package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CIONE;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;

/**
 * Synthetic profile-guarded resource with a class-level feature usage label.
 */
@Profile(PROFILE_CIONE)
@FeatureUsage("build/cione-status")
@RestController
public class CioneStatusResource {

    @GetMapping("cione/status")
    public String status() {
        return "ok";
    }
}
