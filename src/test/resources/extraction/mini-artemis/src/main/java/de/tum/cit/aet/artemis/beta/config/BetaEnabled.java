package de.tum.cit.aet.artemis.beta.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if Beta functionality is enabled.
 */
public class BetaEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public BetaEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isBetaEnabled(context.getEnvironment());
    }
}
