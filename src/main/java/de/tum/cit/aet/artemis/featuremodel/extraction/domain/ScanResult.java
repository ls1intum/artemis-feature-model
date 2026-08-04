package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contract of the scan stage, written as {@code scan/scan-result.json}. The model stage consumes the scan payload only
 * through this envelope, so a scan produced by another extractor version, from another Artemis commit, or with a
 * payload file that changed after the scan cannot be composed into a model.
 *
 * @param schemaVersion schema version of this envelope.
 * @param extractorVersion version of the extraction pipeline that produced the payload.
 * @param artemisCommit resolved git commit of the scanned checkout.
 * @param payloadDigests digest per payload file name, sorted by file name.
 * @param payloadDigest digest over all payload digests, identifying the scan as a whole.
 */
public record ScanResult(int schemaVersion, String extractorVersion, String artemisCommit, Map<String, String> payloadDigests, String payloadDigest) {

    /** Current schema version of the scan envelope. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Version of the extraction pipeline, recorded in the scan metadata and verified by every downstream stage. */
    public static final String EXTRACTOR_VERSION = "0.3.0";

    /**
     * Normalizes the digest map to an immutable copy.
     */
    public ScanResult {
        payloadDigests = payloadDigests == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payloadDigests));
    }
}
