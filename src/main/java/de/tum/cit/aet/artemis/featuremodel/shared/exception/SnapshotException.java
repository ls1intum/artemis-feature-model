package de.tum.cit.aet.artemis.featuremodel.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Controlled exception for snapshot management failures exposed through the snapshot API. Carries a stable code and the
 * HTTP status the caller should receive, so invalid import requests are reported as client errors and unknown snapshots
 * as not-found rather than as opaque server faults.
 */
public class SnapshotException extends RuntimeException {

    private final String code;

    private final HttpStatus status;

    /**
     * Creates a snapshot exception.
     *
     * @param code stable error code.
     * @param message human-readable message.
     * @param status HTTP status the caller should receive.
     */
    public SnapshotException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /**
     * Creates a not-found exception for an unknown snapshot id.
     *
     * @param snapshotId requested snapshot id.
     * @return not-found snapshot exception.
     */
    public static SnapshotException notFound(String snapshotId) {
        return new SnapshotException("SNAPSHOT_NOT_FOUND", "Snapshot '" + snapshotId + "' does not exist.", HttpStatus.NOT_FOUND);
    }

    /**
     * Creates a bad-request exception for an invalid snapshot id.
     *
     * @param snapshotId rejected snapshot id.
     * @return invalid-id snapshot exception.
     */
    public static SnapshotException invalidId(String snapshotId) {
        return new SnapshotException("SNAPSHOT_INVALID_ID", "Snapshot id '" + snapshotId + "' is not a valid snapshot identifier.", HttpStatus.BAD_REQUEST);
    }

    /**
     * Creates a bad-request exception for an invalid import.
     *
     * @param code stable error code.
     * @param message human-readable message.
     * @return invalid-import snapshot exception.
     */
    public static SnapshotException invalidImport(String code, String message) {
        return new SnapshotException(code, message, HttpStatus.BAD_REQUEST);
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
