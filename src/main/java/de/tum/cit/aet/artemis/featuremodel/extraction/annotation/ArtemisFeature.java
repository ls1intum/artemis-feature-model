package de.tum.cit.aet.artemis.featuremodel.extraction.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares feature-model semantics on a canonical Artemis source anchor. The extractor reads this annotation from
 * source code and does not load or execute the annotated Artemis class. Membership remains exclusively controlled by
 * the feature scope manifest.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ ElementType.TYPE, ElementType.FIELD })
public @interface ArtemisFeature {

    /**
     * Curated feature id.
     *
     * @return stable feature-model id.
     */
    String id();

    /**
     * Curated group placement.
     *
     * @return group id, or an empty string when not declared.
     */
    String group() default "";

    /**
     * Direct parent placement, primarily for runtime-toggle child features.
     *
     * @return parent id, or an empty string when not declared.
     */
    String parent() default "";

    /**
     * Feature kind override.
     *
     * @return feature kind, or an empty string when inferred from the candidate.
     */
    String kind() default "";

    /**
     * Deployment capabilities required when the feature is selected.
     *
     * @return required capability ids.
     */
    String[] requiresCapabilities() default {};

    /**
     * Deployment capabilities supplied by a technical feature.
     *
     * @return provided capability ids.
     */
    String[] providesCapabilities() default {};

    /**
     * Explicit display-name override.
     *
     * @return display name, or an empty string to use extracted i18n.
     */
    String name() default "";

    /**
     * Explicit description override.
     *
     * @return description, or an empty string to use extracted i18n.
     */
    String description() default "";

    /**
     * Explicit documentation-link override.
     *
     * @return documentation URL, or an empty string to use extracted admin-page data.
     */
    String documentationUrl() default "";
}
