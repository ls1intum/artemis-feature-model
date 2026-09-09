package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Parsed {@code @ArtemisFeature} values of contract v2: the feature id that grants membership and the configuration
 * keys the Artemis developer declared for the feature.
 *
 * @param id required feature id.
 * @param configuration declared configuration keys in declaration order; empty when the annotation declares none.
 */
public record ExtractedAnnotationSemantics(String id, List<ConfigurationDeclaration> configuration) {

    /** Normalizes the configuration list to an immutable copy. */
    public ExtractedAnnotationSemantics {
        configuration = configuration == null ? List.of() : List.copyOf(configuration);
    }

    /**
     * One nested {@code @ArtemisFeatureConfig} declaration.
     *
     * @param key dotted configuration key.
     * @param secret whether the value is a secret.
     */
    public record ConfigurationDeclaration(String key, boolean secret) {
    }
}
