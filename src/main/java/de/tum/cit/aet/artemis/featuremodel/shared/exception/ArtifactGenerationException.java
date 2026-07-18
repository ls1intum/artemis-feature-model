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
     * Creates a bad-request exception for a deployment mode id that is not a known mode.
     *
     * @param deploymentMode requested deployment mode id.
     * @return unknown-deployment-mode artifact generation exception.
     */
    public static ArtifactGenerationException unknownDeploymentMode(String deploymentMode) {
        return new ArtifactGenerationException("ARTIFACT_GENERATION_UNKNOWN_DEPLOYMENT_MODE", "Unknown deployment mode '" + deploymentMode + "'.",
                HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception for a known deployment mode the active profile does not support.
     *
     * @param deploymentMode requested deployment mode id.
     * @param profileId active deployment profile id.
     * @return unsupported-deployment-mode artifact generation exception.
     */
    public static ArtifactGenerationException unsupportedDeploymentMode(String deploymentMode, String profileId) {
        return new ArtifactGenerationException("ARTIFACT_GENERATION_UNSUPPORTED_DEPLOYMENT_MODE",
                "Deployment mode '" + deploymentMode + "' is not supported by deployment profile '" + profileId + "'.", HttpStatus.BAD_REQUEST);
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
