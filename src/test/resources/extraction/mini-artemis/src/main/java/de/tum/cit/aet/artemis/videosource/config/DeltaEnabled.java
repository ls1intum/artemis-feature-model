package de.tum.cit.aet.artemis.videosource.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition to check if the Delta integration is enabled.
 * The integration is considered enabled when the API base URL property is set to a non-blank value.
 */
public class DeltaEnabled implements Condition {

    private static final String DELTA_API_BASE_URL_PROPERTY = "artemis.delta.api-base-url";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String url = context.getEnvironment().getProperty(DELTA_API_BASE_URL_PROPERTY);
        return url != null && !url.isBlank();
    }
}
