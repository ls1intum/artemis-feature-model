package de.tum.cit.aet.artemis.alpha.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition to check if both the Alpha module and Beta functionality are enabled.
 */
public class AlphaWithBetaEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper;

    public AlphaWithBetaEnabled() {
        this.artemisConfigHelper = new ArtemisConfigHelper();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return artemisConfigHelper.isAlphaEnabled(context.getEnvironment()) && artemisConfigHelper.isBetaEnabled(context.getEnvironment());
    }
}
