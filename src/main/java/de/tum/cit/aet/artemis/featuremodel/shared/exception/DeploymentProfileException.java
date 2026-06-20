package de.tum.cit.aet.artemis.featuremodel.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Controlled exception for deployment profile failures exposed through the deployment profile API. Carries a stable
 * code and the HTTP status the caller should receive, so an unknown profile id is reported as not-found and invalid or
 * duplicate profile data as a server-side configuration error rather than as an opaque server fault.
 */
public class DeploymentProfileException extends RuntimeException {

    private final String code;

    private final HttpStatus status;

    /**
     * Creates a deployment profile exception.
     *
     * @param code stable error code.
     * @param message human-readable message.
     * @param status HTTP status the caller should receive.
     */
    public DeploymentProfileException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /**
     * Creates a not-found exception for an unknown profile id.
     *
     * @param profileId requested profile id.
     * @return not-found deployment profile exception.
     */
    public static DeploymentProfileException notFound(String profileId) {
        return new DeploymentProfileException("DEPLOYMENT_PROFILE_NOT_FOUND", "Deployment profile '" + profileId + "' does not exist.", HttpStatus.NOT_FOUND);
    }

    /**
     * Creates a server-error exception for a duplicate profile id within one source.
     *
     * @param profileId duplicated profile id.
     * @param source source description, for example {@code classpath} or a data root path.
     * @return duplicate deployment profile exception.
     */
    public static DeploymentProfileException duplicate(String profileId, String source) {
        return new DeploymentProfileException("DEPLOYMENT_PROFILE_DUPLICATE",
                "Duplicate deployment profile id '" + profileId + "' found in " + source + ".", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Creates a server-error exception for profile content that cannot be read or parsed.
     *
     * @param source source description of the unreadable profile.
     * @return unreadable deployment profile exception.
     */
    public static DeploymentProfileException unreadable(String source) {
        return new DeploymentProfileException("DEPLOYMENT_PROFILE_UNREADABLE", "Deployment profile in " + source + " could not be read.",
                HttpStatus.INTERNAL_SERVER_ERROR);
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
