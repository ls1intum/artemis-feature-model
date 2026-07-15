package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * Classification of a deployment profile parameter value for artifact generation.
 */
public enum ProfileValueKind {

    /** A literal value written directly into the overlay (for example a URL or a number). */
    LITERAL,

    /** An {@code env:NAME} reference written into the overlay as a {@code ${NAME}} placeholder. */
    ENV,

    /** A {@code vault:...} reference that is not resolved in this phase and is omitted from the overlay. */
    VAULT
}
