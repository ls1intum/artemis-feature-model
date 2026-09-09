package de.tum.cit.aet.artemis.featuremodel.extraction.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated Artemis source anchor is a feature of the feature model and names it. The feature-model
 * extractor reads this annotation from source code with JavaParser; it never loads the annotated class and never needs
 * the annotation on its own classpath. Every modeling judgment, such as placement, order, optionality, deployment
 * capabilities, and user-facing prose, lives in the feature scope manifest of the feature-model repository, keyed by the
 * declared id. Place it on the {@code *Enabled} Spring condition class of a module, or on a {@code MODULE_FEATURE_*}
 * constant or {@code Feature} enum constant when no condition class exists; at most one annotation per anchor.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.FIELD })
public @interface ArtemisFeature {

    /**
     * Stable kebab-case feature id, unique across all annotated anchors.
     *
     * @return feature id.
     */
    String id();

    /**
     * Configuration keys a deployment must supply when the feature is selected. A key declared here has the final word:
     * the manifest can neither alter nor remove it.
     *
     * @return declared configuration keys; empty when the feature declares none.
     */
    ArtemisFeatureConfig[] configuration() default {};
}
