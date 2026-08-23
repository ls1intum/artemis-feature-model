package de.tum.cit.aet.artemis.featuremodel.shared.exception;

public class FeatureModelLoadException extends RuntimeException {

    /**
     * Creates an exception for feature model loading failures.
     *
     * @param message exception message.
     * @param cause original loading failure.
     */
    public FeatureModelLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception for loading failures detected by validation, without an underlying cause.
     *
     * @param message exception message.
     */
    public FeatureModelLoadException(String message) {
        super(message);
    }
}
