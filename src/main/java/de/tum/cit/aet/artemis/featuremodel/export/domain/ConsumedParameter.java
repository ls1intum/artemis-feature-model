package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * Record of a deployment profile parameter that was consumed by artifact generation.
 *
 * @param featureId feature that consumed the parameter.
 * @param profileKey deployment profile parameter key.
 * @param targetPath configuration path written into the overlay.
 * @param secret whether the value is a secret reference rendered as an environment placeholder.
 * @param source value source, one of {@link #SOURCE_LITERAL} or {@link #SOURCE_ENV}.
 */
public record ConsumedParameter(String featureId, String profileKey, String targetPath, boolean secret, String source) {

    /** A literal value copied directly from the profile. */
    public static final String SOURCE_LITERAL = "literal";

    /** An environment reference rendered as a {@code ${NAME}} placeholder. */
    public static final String SOURCE_ENV = "env";
}
