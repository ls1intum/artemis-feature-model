package de.tum.cit.aet.artemis.featuremodel.shared.exception;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FeatureModelExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FeatureModelExceptionHandler.class);

    /**
     * Converts feature model integrity exceptions to JSON error responses.
     *
     * @param exception integrity exception.
     * @return response entity with stable error code and message.
     */
    @ExceptionHandler(FeatureModelIntegrityException.class)
    public ResponseEntity<Map<String, String>> handleIntegrityException(FeatureModelIntegrityException exception) {
        log.error("Feature model integrity exception converted to HTTP {} response with code {}.", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                exception.getCode(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", exception.getCode(), "message", exception.getMessage()));
    }

    /**
     * Converts feature model loading exceptions to JSON error responses.
     *
     * @param exception loading exception.
     * @return response entity with stable error code and message.
     */
    @ExceptionHandler(FeatureModelLoadException.class)
    public ResponseEntity<Map<String, String>> handleLoadException(FeatureModelLoadException exception) {
        log.error("Feature model load exception converted to HTTP {} response.", HttpStatus.INTERNAL_SERVER_ERROR.value(), exception);
        Map<String, String> body = Map.of("code", "FEATURE_MODEL_LOAD_FAILED", "message", exception.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Converts snapshot management exceptions to JSON error responses using the status the exception carries.
     *
     * @param exception snapshot exception.
     * @return response entity with stable error code and message.
     */
    @ExceptionHandler(SnapshotException.class)
    public ResponseEntity<Map<String, String>> handleSnapshotException(SnapshotException exception) {
        log.warn("Snapshot exception converted to HTTP {} response with code {}.", exception.getStatus().value(), exception.getCode());
        return ResponseEntity.status(exception.getStatus()).body(Map.of("code", exception.getCode(), "message", exception.getMessage()));
    }

    /**
     * Converts deployment profile exceptions to JSON error responses using the status the exception carries.
     *
     * @param exception deployment profile exception.
     * @return response entity with stable error code and message.
     */
    @ExceptionHandler(DeploymentProfileException.class)
    public ResponseEntity<Map<String, String>> handleDeploymentProfileException(DeploymentProfileException exception) {
        log.warn("Deployment profile exception converted to HTTP {} response with code {}.", exception.getStatus().value(), exception.getCode());
        return ResponseEntity.status(exception.getStatus()).body(Map.of("code", exception.getCode(), "message", exception.getMessage()));
    }

    /**
     * Converts artifact generation exceptions to JSON error responses using the status the exception carries.
     *
     * @param exception artifact generation exception.
     * @return response entity with stable error code and message.
     */
    @ExceptionHandler(ArtifactGenerationException.class)
    public ResponseEntity<Map<String, String>> handleArtifactGenerationException(ArtifactGenerationException exception) {
        log.warn("Artifact generation exception converted to HTTP {} response with code {}.", exception.getStatus().value(), exception.getCode());
        return ResponseEntity.status(exception.getStatus()).body(Map.of("code", exception.getCode(), "message", exception.getMessage()));
    }

    /**
     * Converts deployment repository publish exceptions to JSON error responses using the status the exception
     * carries.
     *
     * @param exception deployment repository publish exception.
     * @return response entity with stable error code and message.
     */
    @ExceptionHandler(DeploymentRepositoryPublishException.class)
    public ResponseEntity<Map<String, String>> handleDeploymentRepositoryPublishException(DeploymentRepositoryPublishException exception) {
        log.warn("Deployment repository publish exception converted to HTTP {} response with code {}.", exception.getStatus().value(), exception.getCode());
        return ResponseEntity.status(exception.getStatus()).body(Map.of("code", exception.getCode(), "message", exception.getMessage()));
    }
}
