package de.tum.cit.aet.artemis.featuremodel.shared.exception;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Controlled exception for deployment repository publish failures exposed through the publish API. Carries a stable
 * code and the HTTP status the caller should receive; every fail-closed publish path answers one of these codes
 * instead of an opaque server fault. Messages never contain the access token.
 */
public class DeploymentRepositoryPublishException extends RuntimeException {

    private final String code;

    private final HttpStatus status;

    /**
     * Creates a deployment repository publish exception.
     *
     * @param code stable error code.
     * @param message human-readable message.
     * @param status HTTP status the caller should receive.
     */
    public DeploymentRepositoryPublishException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /**
     * Creates the exception for a publish request while publishing is disabled or incompletely configured.
     *
     * @param reason human-readable configuration gap.
     * @return not-configured publish exception.
     */
    public static DeploymentRepositoryPublishException notConfigured(String reason) {
        return new DeploymentRepositoryPublishException("PUBLISH_NOT_CONFIGURED", "Deployment repository publishing is not configured: " + reason,
                HttpStatus.CONFLICT);
    }

    /**
     * Creates the exception for a publish request without a target name; a package with no target identity cannot be
     * routed to a repository directory.
     *
     * @return missing-target-name publish exception.
     */
    public static DeploymentRepositoryPublishException requiresTargetName() {
        return new DeploymentRepositoryPublishException("PUBLISH_REQUIRES_TARGET_NAME",
                "Publishing requires a target name: it routes the package to its repository directory.", HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates the exception for a target name that is not an exact identifier; the name becomes the package directory
     * and the GitHub Environment name, so it is validated instead of sanitized.
     *
     * @param targetName rejected target name.
     * @return invalid-target-name publish exception.
     */
    public static DeploymentRepositoryPublishException invalidTargetName(String targetName) {
        String message = "The target name '" + targetName + "' is not valid: use 1 to 63 lowercase letters, digits, or hyphens, "
                + "starting with a letter or digit. It names the package directory and the GitHub Environment.";
        return new DeploymentRepositoryPublishException("PUBLISH_INVALID_TARGET_NAME", message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates the exception for a publish request without a repository URL while no default URL is configured.
     *
     * @return missing-repository-URL publish exception.
     */
    public static DeploymentRepositoryPublishException requiresRepositoryUrl() {
        return new DeploymentRepositoryPublishException("PUBLISH_REQUIRES_REPOSITORY_URL",
                "Publishing requires a deployment repository URL: the request has none and no default URL is configured.", HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates the exception for a repository URL that violates the URL policy. The message names the violation and the
     * allowed prefixes but never repeats the URL, which may carry a credential.
     *
     * @param violation human-readable policy violation.
     * @param allowedPrefixes allowed repository URL prefixes.
     * @return repository-not-allowed publish exception.
     */
    public static DeploymentRepositoryPublishException repositoryNotAllowed(String violation, List<String> allowedPrefixes) {
        return new DeploymentRepositoryPublishException("PUBLISH_REPOSITORY_NOT_ALLOWED",
                "The deployment repository URL is not allowed: " + violation + ". Allowed URL prefixes: " + String.join(", ", allowedPrefixes) + ".",
                HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates the exception for a repository that does not declare itself a compatible deployment repository through
     * its marker file; nothing was committed or pushed.
     *
     * @param detail what is wrong with the marker and how to fix it.
     * @return not-a-deployment-repository publish exception.
     */
    public static DeploymentRepositoryPublishException notADeploymentRepository(String detail) {
        return new DeploymentRepositoryPublishException("PUBLISH_NOT_A_DEPLOYMENT_REPOSITORY", "The repository is not a deployment repository: " + detail,
                HttpStatus.CONFLICT);
    }

    /**
     * Creates the exception for a publish request whose deployment mode is not the remote-ansible mode.
     *
     * @param deploymentMode requested deployment mode id, or {@code null} for a default request.
     * @return wrong-deployment-mode publish exception.
     */
    public static DeploymentRepositoryPublishException wrongDeploymentMode(String deploymentMode) {
        String described = deploymentMode == null ? "the default local Docker mode" : "'" + deploymentMode + "'";
        return new DeploymentRepositoryPublishException("PUBLISH_WRONG_DEPLOYMENT_MODE",
                "Publishing applies only to deployment mode 'remote-ansible', not to " + described + ".", HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates the exception for a push the remote refused after every retry; pushes are atomic, so nothing was
     * half-published.
     *
     * @param gitMessage git-level refusal message.
     * @return rejected publish exception.
     */
    public static DeploymentRepositoryPublishException rejected(String gitMessage) {
        return new DeploymentRepositoryPublishException("PUBLISH_REJECTED", "The deployment repository rejected the publish: " + gitMessage,
                HttpStatus.CONFLICT);
    }

    /**
     * Creates the exception for a remote that refused the configured credential.
     *
     * @param detail credential-free transport failure detail.
     * @return authentication-failed publish exception.
     */
    public static DeploymentRepositoryPublishException authFailed(String detail) {
        return new DeploymentRepositoryPublishException("PUBLISH_AUTH_FAILED",
                "The deployment repository refused the configured credential: " + detail, HttpStatus.BAD_GATEWAY);
    }

    /**
     * Creates the exception for a {@code github.com} repository whose actual visibility differs from the configured
     * expectation; the fail-closed check that the operator is publishing to the repository they think they are.
     *
     * @param expected configured expected visibility.
     * @param actual actual repository visibility reported by the GitHub API.
     * @return visibility-mismatch publish exception.
     */
    public static DeploymentRepositoryPublishException visibilityMismatch(String expected, String actual) {
        return new DeploymentRepositoryPublishException("PUBLISH_VISIBILITY_MISMATCH",
                "The deployment repository visibility is '" + actual + "' but the configuration expects '" + expected + "'; refusing to publish.",
                HttpStatus.CONFLICT);
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
