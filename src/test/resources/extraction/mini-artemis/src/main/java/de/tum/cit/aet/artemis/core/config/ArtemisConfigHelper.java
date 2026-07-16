package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.ALPHA_ENABLED_PROPERTY_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.GAMMA_ENABLED_PROPERTY_NAME;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.env.Environment;

/**
 * Synthetic config helper mirroring the accessor and enumeration shapes of the real Artemis helper.
 */
public class ArtemisConfigHelper {

    public boolean isAlphaEnabled(Environment environment) {
        return getPropertyOrExit(ALPHA_ENABLED_PROPERTY_NAME, environment);
    }

    public boolean isGammaEnabled(Environment environment) {
        return isAlphaEnabled(environment) && getPropertyOrExit(GAMMA_ENABLED_PROPERTY_NAME, environment);
    }

    public boolean isBetaEnabled(Environment environment) {
        return getPropertyOrExit(Constants.BETA_ENABLED_PROPERTY_NAME, environment);
    }

    public List<String> getEnabledFeatures(Environment environment) {
        List<String> enabledFeatures = new ArrayList<>();
        if (isAlphaEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_ALPHA);
        }
        if (isGammaEnabled(environment)) {
            enabledFeatures.add(Constants.MODULE_FEATURE_GAMMA);
        }
        if (isBetaEnabled(environment)) {
            enabledFeatures.add(Constants.FEATURE_BETA);
            enabledFeatures.add(Constants.FEATURE_BETA_EXTRA);
        }
        return enabledFeatures;
    }

    private boolean getPropertyOrExit(String key, Environment environment) {
        Boolean value = environment.getProperty(key, Boolean.class);
        if (value == null) {
            throw new RuntimeException("Property " + key + " not found in the configuration.");
        }
        return value;
    }
}
