package de.tum.cit.aet.artemis.featuremodel.shared.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FeatureModelExceptionHandler {

    /**
     * Converts feature model integrity exceptions to JSON error responses.
     *
     * @param exception integrity exception.
     * @return response entity with stable error code and message.
     */
    @ExceptionHandler(FeatureModelIntegrityException.class)
    public ResponseEntity<Map<String, String>> handleIntegrityException(FeatureModelIntegrityException exception) {
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
        Map<String, String> body = Map.of("code", "FEATURE_MODEL_LOAD_FAILED", "message", exception.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
