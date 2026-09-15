package de.tum.cit.aet.artemis.omega.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;

/**
 * Synthetic resource whose label is not an area/feature kebab-case label; the scan warns and keeps the placement.
 */
@FeatureUsage("Omega Items")
@RestController
public class OmegaResource {

    @GetMapping("omega/items")
    public String items() {
        return "items";
    }
}
