package de.tum.cit.aet.artemis.featuremodel.shared.exception;

public class FeatureModelIntegrityException extends RuntimeException {

    private final String code;

    /**
     * Creates an exception for feature model integrity failures.
     *
     * @param code stable validation code.
     * @param message exception message.
     */
    public FeatureModelIntegrityException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Returns the stable validation code for this integrity failure.
     *
     * @return validation code.
     */
    public String getCode() {
        return code;
    }
}
