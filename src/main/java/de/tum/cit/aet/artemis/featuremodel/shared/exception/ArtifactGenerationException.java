package de.tum.cit.aet.artemis.featuremodel.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Controlled exception for artifact generation failures exposed through the artifact generation API. Carries a stable
 * code and the HTTP status the caller should receive, so an invalid selection is reported as a bad request rather than
 * as an opaque server fault. Missing deployment profiles are reported through {@link DeploymentProfileException}.
 */
public class ArtifactGenerationException extends RuntimeException {

    private final String code;

    private final HttpStatus status;

    /**
     * Creates an artifact generation exception.
     *
     * @param code stable error code.
     * @param message human-readable message.
     * @param status HTTP status the caller should receive.
     */
    public ArtifactGenerationException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /**
     * Creates a bad-request exception for a selection that fails validation.
     *
     * @param violationCount number of validation violations.
     * @param firstViolationMessage message of the first violation, for a readable hint.
     * @return invalid-selection artifact generation exception.
     */
    public static ArtifactGenerationException invalidSelection(int violationCount, String firstViolationMessage) {
        String message = "Cannot generate artifacts: the selection is invalid (" + violationCount + " violation(s)). " + firstViolationMessage;
        return new ArtifactGenerationException("ARTIFACT_GENERATION_INVALID_SELECTION", message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Returns the stable error code for this failure.
     *
     * @return error code.
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the HTTP status the caller should receive.
     *
     * @return HTTP status.
     */
    public HttpStatus getStatus() {
        return status;
    }
}
