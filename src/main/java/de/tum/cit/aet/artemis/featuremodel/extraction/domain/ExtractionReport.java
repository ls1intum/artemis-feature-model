package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;
import java.util.Map;

/**
 * Payload of {@code extraction-report.json}: all diagnostics and the manifest curation section. The report documents its own code contract so that later
 * automation phases can key on stable code strings without consulting external documentation.
 *
 * @param schemaVersion report schema version.
 * @param status overall deterministic delivery verdict, {@code pass} or {@code fail}.
 * @param artemisCommit resolved git commit of the scanned checkout.
 * @param manifestDigest digest of the manifest bytes used by this run.
 * @param curation manifest membership and semantic-source section.
 * @param codes stable diagnostic codes with one-line meanings, sorted by code.
 * @param severityCounts item counts per severity, sorted by severity name.
 * @param codeCounts item counts per code, sorted by code.
 * @param items report items sorted by code, then subject, then message.
 */
public record ExtractionReport(int schemaVersion, String status, String artemisCommit, String manifestDigest, CurationReport curation,
        Map<String, String> codes, Map<String, Integer> severityCounts, Map<String, Integer> codeCounts, List<ReportItem> items) {

    /** Current report schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Passing delivery verdict. */
    public static final String STATUS_PASS = "pass";

    /** Failing delivery verdict. */
    public static final String STATUS_FAIL = "fail";

    /**
     * Creates a report and normalizes the item list to an immutable copy.
     *
     * Normalizes report items to an immutable list.
     */
    public ExtractionReport {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
