package de.tum.cit.aet.artemis.alpha.web;

import org.springframework.context.annotation.Conditional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.alpha.config.AlphaEnabled;
import de.tum.cit.aet.artemis.alpha.config.AlphaWithBetaEnabled;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;

/**
 * Synthetic resource providing usage evidence sites.
 */
@Conditional(AlphaEnabled.class)
@RestController
public class AlphaResource {

    @FeatureToggle(Feature.ToggleOne)
    @GetMapping("alpha/export")
    public String exportAlpha() {
        return "alpha";
    }

    @Conditional(AlphaWithBetaEnabled.class)
    @GetMapping("alpha/beta")
    public String alphaWithBeta() {
        return "alpha-beta";
    }
}
