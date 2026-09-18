package de.tum.cit.aet.artemis.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Synthetic unguarded core configuration class. It injects a key that a guarded Alpha class also injects, so the
 * injection scan proves that a key with an unguarded injection site is never attributed to a feature.
 */
@Configuration
public class SharedInstanceConfiguration {

    @Value("${artemis.shared.instance-name}")
    private String instanceName;
}
