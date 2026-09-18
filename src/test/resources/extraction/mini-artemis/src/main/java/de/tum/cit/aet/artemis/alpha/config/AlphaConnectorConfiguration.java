package de.tum.cit.aet.artemis.alpha.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Synthetic guarded configuration class providing injection-site evidence for the Alpha module.
 */
@Conditional(AlphaEnabled.class)
@Configuration
public class AlphaConnectorConfiguration {

    @Value("${artemis.alpha.token}")
    private String token;

    @Value("${artemis.alpha.timeout:30}")
    private int timeoutSeconds;

    @Value("${artemis.shared.instance-name}")
    private String instanceName;
}
