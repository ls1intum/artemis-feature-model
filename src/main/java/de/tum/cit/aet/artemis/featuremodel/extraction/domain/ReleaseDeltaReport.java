package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * Informational comparison status against a previously deployed validated snapshot. The extraction run has no
 * configured baseline input, so it records a deterministic skipped result that can never change release eligibility.
 *
 * @param schemaVersion report schema version.
 * @param status {@code skipped} when no baseline is configured.
 * @param blocking always false; release delta is observability only.
 * @param baselineSnapshotId compared snapshot id, or null.
 * @param reason explanation when comparison was skipped.
 */
public record ReleaseDeltaReport(int schemaVersion, String status, boolean blocking, String baselineSnapshotId, String reason) {

    /** Current report schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Status used when no valid deployed baseline was supplied. */
    public static final String STATUS_SKIPPED = "skipped";

    /**
     * Creates the non-blocking result for a run without a baseline.
     *
     * @return deterministic skipped report.
     */
    public static ReleaseDeltaReport noBaseline() {
        return new ReleaseDeltaReport(CURRENT_SCHEMA_VERSION, STATUS_SKIPPED, false, null,
                "No previous validated snapshot baseline was configured.");
    }
}
