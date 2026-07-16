package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/** Controlled failure raised when the scope manifest is malformed or inconsistent with extraction candidates. */
public class FeatureManifestException extends IllegalArgumentException {

    /**
     * Creates a manifest validation failure.
     *
     * @param message actionable validation message.
     */
    public FeatureManifestException(String message) {
        super(message);
    }
}
