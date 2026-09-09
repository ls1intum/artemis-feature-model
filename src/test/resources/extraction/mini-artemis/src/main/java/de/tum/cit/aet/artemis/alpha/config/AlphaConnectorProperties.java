package de.tum.cit.aet.artemis.alpha.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Conditional;

/**
 * Synthetic guarded configuration-properties binding for the Alpha connector namespace.
 */
@Conditional(AlphaEnabled.class)
@ConfigurationProperties(prefix = "artemis.alpha.connector")
public class AlphaConnectorProperties {

    private String endpoint;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
}
