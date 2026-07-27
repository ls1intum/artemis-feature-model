package de.tum.cit.aet.artemis.alpha.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if the Gamma submodule is enabled.
 */
public class GammaEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public GammaEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isGammaEnabled(context.getEnvironment());
    }
}
