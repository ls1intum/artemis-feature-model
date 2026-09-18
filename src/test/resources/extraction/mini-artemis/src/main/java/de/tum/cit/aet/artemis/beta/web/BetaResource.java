package de.tum.cit.aet.artemis.beta.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;

/**
 * Synthetic unguarded resource with a class-level feature usage label and one method-level override.
 */
@FeatureUsage("review/beta-reviews")
@RestController
public class BetaResource {

    @GetMapping("beta/reviews")
    public String listReviews() {
        return "reviews";
    }

    @FeatureUsage("review/beta-exports")
    @GetMapping("beta/reviews/export")
    public String exportReviews() {
        return "export";
    }
}
