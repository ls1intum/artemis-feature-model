package de.tum.cit.aet.artemis.featuremodel.extraction.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One configuration key a feature needs from its deployment, declared inside {@link ArtemisFeature#configuration()}.
 * The extractor emits the key as an environment-supplied placeholder of the generated configuration overlay and never
 * writes a value for it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface ArtemisFeatureConfig {

    /**
     * Dotted Spring configuration key, for example {@code artemis.iris.url}.
     *
     * @return configuration key.
     */
    String key();

    /**
     * Whether the value is a secret that must never appear in plaintext in any generated artifact.
     *
     * @return true for secrets; false by default.
     */
    boolean secret() default false;
}
