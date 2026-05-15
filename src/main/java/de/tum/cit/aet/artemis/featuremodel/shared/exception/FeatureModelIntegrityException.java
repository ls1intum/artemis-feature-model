package de.tum.cit.aet.artemis.featuremodel.shared.exception;

public class FeatureModelIntegrityException extends RuntimeException {

    private final String code;

    public FeatureModelIntegrityException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
