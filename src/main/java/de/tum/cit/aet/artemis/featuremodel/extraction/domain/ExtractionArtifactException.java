package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * Raised when an extraction stage cannot consume an upstream artifact: it is missing, was produced by another
 * extractor version or Artemis commit, or its content changed after the producing stage recorded its digest.
 */
public class ExtractionArtifactException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message description of the rejected artifact and why it cannot be consumed.
     */
    public ExtractionArtifactException(String message) {
        super(message);
    }
}
