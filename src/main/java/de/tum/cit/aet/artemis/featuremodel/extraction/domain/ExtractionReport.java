package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;
import java.util.Map;

/**
 * Payload of {@code extraction-report.json}: all diagnostics of one scan, including the drift section against the
 * active curated model and the manifest curation section. The report documents its own code contract so that later
 * automation phases can key on stable code strings without consulting external documentation.
 *
 * @param artemisCommit resolved git commit of the scanned checkout.
 * @param curatedModelId id of the curated model the drift section compared against.
 * @param curatedModelVersion version of the curated model the drift section compared against.
 * @param curation manifest membership and semantic-source section.
 * @param codes stable diagnostic codes with one-line meanings, sorted by code.
 * @param severityCounts item counts per severity, sorted by severity name.
 * @param codeCounts item counts per code, sorted by code.
 * @param items report items sorted by code, then subject, then message.
 */
public record ExtractionReport(String artemisCommit, String curatedModelId, String curatedModelVersion, CurationReport curation, Map<String, String> codes,
        Map<String, Integer> severityCounts, Map<String, Integer> codeCounts, List<ReportItem> items) {

    /**
     * Creates a report and normalizes the item list to an immutable copy.
     *
     * @param artemisCommit resolved git commit of the scanned checkout.
     * @param curatedModelId id of the curated model.
     * @param curatedModelVersion version of the curated model.
     * @param curation manifest curation section.
     * @param codes diagnostic code documentation.
     * @param severityCounts item counts per severity.
     * @param codeCounts item counts per code.
     * @param items sorted report items.
     */
    public ExtractionReport {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
