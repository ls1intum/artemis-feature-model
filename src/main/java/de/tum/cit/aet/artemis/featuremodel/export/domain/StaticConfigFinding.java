package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * A single static configuration validation finding for one overlay entry. Values are safe to echo because the
 * generator never writes plaintext secrets into the overlay; secret values appear only as {@code ${VARIABLE}}
 * placeholders.
 *
 * @param path dotted configuration key of the offending overlay entry.
 * @param value string rendering of the offending value.
 * @param issue finding kind, one of {@link #ISSUE_UNKNOWN_KEY} or {@link #ISSUE_TYPE_MISMATCH}.
 * @param detail human-readable explanation of the finding.
 */
public record StaticConfigFinding(String path, String value, String issue, String detail) {

    /** The key is not present in the verified Artemis configuration key catalog. */
    public static final String ISSUE_UNKNOWN_KEY = "UNKNOWN_KEY";

    /** The value does not have the type the catalog declares for the key. */
    public static final String ISSUE_TYPE_MISMATCH = "TYPE_MISMATCH";
}
