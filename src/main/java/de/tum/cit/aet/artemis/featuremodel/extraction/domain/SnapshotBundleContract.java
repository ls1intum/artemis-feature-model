package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Persisted layout contract of a complete generated snapshot bundle: the payload file names, the checksum manifest
 * name, and the content-addressed snapshot id rule. The publisher, the validator, the Docker context stager, and the
 * fixture refresh all observe this one contract, so a snapshot written by one of them is recognized by the others.
 */
public final class SnapshotBundleContract {

    /** Generated feature model payload name. */
    public static final String SNAPSHOT_MODEL_FILE = "feature-model.json";

    /** Prepared guided workflow payload name. */
    public static final String SNAPSHOT_WORKFLOW_FILE = "guided-workflow.json";

    /** Generated config-key catalog payload name. */
    public static final String SNAPSHOT_CATALOG_FILE = "config-key-catalog.json";

    /** Generation report payload name. */
    public static final String SNAPSHOT_REPORT_FILE = "generation-report.json";

    /** Snapshot provenance payload name. */
    public static final String SNAPSHOT_PROVENANCE_FILE = "provenance.json";

    /** Snapshot metadata payload name. */
    public static final String SNAPSHOT_METADATA_FILE = "metadata.json";

    /** Checksum manifest name; the only bundle file not listed in {@link #PAYLOAD_FILES}. */
    public static final String SNAPSHOT_CHECKSUM_FILE = "checksums.txt";

    /** Checksummed payload files in the exact order of the {@code checksums.txt} lines. */
    public static final List<String> PAYLOAD_FILES = List.of(SNAPSHOT_CATALOG_FILE, SNAPSHOT_MODEL_FILE, SNAPSHOT_REPORT_FILE, SNAPSHOT_METADATA_FILE,
            SNAPSHOT_PROVENANCE_FILE, SNAPSHOT_WORKFLOW_FILE);

    private static final int SHORT_ID_LENGTH = 12;

    private SnapshotBundleContract() {
    }

    /**
     * Derives the content-addressed snapshot id from the identity the snapshot was generated from.
     *
     * @param artemisCommit derived Artemis source revision.
     * @param manifestDigest scope manifest digest in {@code sha256:<hex>} form.
     * @return deterministic snapshot id.
     */
    public static String snapshotId(String artemisCommit, String manifestDigest) {
        String manifestHex = manifestDigest.substring(manifestDigest.indexOf(':') + 1);
        return "generated-" + artemisCommit.substring(0, SHORT_ID_LENGTH) + "-" + manifestHex.substring(0, SHORT_ID_LENGTH);
    }
}
