package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;

/**
 * Consolidates the diagnostics of all extraction stages into the deterministic {@code extraction-report.json}. It is
 * the only place that sees scan, model, and workflow diagnostics together.
 */
class ExtractionReportAssembler {

    private static final Map<String, String> CODE_DOCUMENTATION = new TreeMap<>(Map.ofEntries(
            Map.entry(ReportItem.CODE_GENERATED_MODEL_INVALID, "The assembled generated model failed the shared structural integrity validation."),
            Map.entry(ReportItem.CODE_GENERATED_WORKFLOW_INVALID, "The bundled guided workflow failed its hard reference validation against the generated model."),
            Map.entry(ReportItem.CODE_TECHNICAL_FEATURE_ROLE_LEAK, "A technical feature of the generated model is visible or configurable for teachers."),
            Map.entry(ReportItem.CODE_PROFILE_CAPABILITY_MISMATCH, "An included technical feature provides a capability the bundled deployment profile does not list."),
            Map.entry(ReportItem.CODE_RELATION_CANDIDATE_UNDECLARED, "A relation candidate between included features has neither a declared constraint nor an entry in ignoredRelations."),
            Map.entry(ReportItem.CODE_DANGLING_GENERATED_CONSTRAINT,
                    "A manifest constraint references a feature that was not emitted into the generated model."),
            Map.entry(ReportItem.CODE_GUIDED_WORKFLOW_FINDINGS, "The guided workflow validation against the generated model produced findings; see guided-workflow-validation.json."),
            Map.entry(ReportItem.CODE_CLIENT_SERVER_MIRROR_MISMATCH, "Client and server disagree about a module feature constant or runtime toggle enum member."),
            Map.entry(ReportItem.CODE_EXTRACTOR_ERROR, "One extractor failed to parse its source; the scan continued without its contribution."),
            Map.entry(ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY, "Server enabled property constants and module feature constants are asymmetric."),
            Map.entry(ReportItem.CODE_UNDECLARED_CANDIDATE, "An extracted candidate has no manifest include or exclude decision, so the run cannot be published."),
            Map.entry(ReportItem.CODE_ANNOTATED_BUT_UNSCOPED, "A source annotation exists but the manifest does not include its candidate."),
            Map.entry(ReportItem.CODE_MANIFEST_OVERRIDES_ANNOTATION, "A source annotation contradicts the manifest entry for the same anchor; the manifest value is used."),
            Map.entry(ReportItem.CODE_ANNOTATED_ANCHOR_NOT_EXTRACTED, "An annotated source anchor could not be joined to an extracted candidate."),
            Map.entry(ReportItem.CODE_MANIFEST_ORPHAN_ANCHOR, "A manifest anchor matches no extraction candidate of this scan, or matches more than one."),
            Map.entry(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, "Manifest entries, annotations, or resolved semantics collide for this scan and need review.")));

    /**
     * Assembles the consolidated report: documented codes, counts, and items sorted by code, subject, message, and
     * severity.
     *
     * @param artemisCommit resolved commit of the scanned checkout.
     * @param manifestDigest digest of the manifest bytes used by the run.
     * @param curation manifest curation section.
     * @param stageItems diagnostics of every stage that ran, in stage order.
     * @param eligible whether all deterministic delivery gates passed.
     * @return assembled report.
     */
    ExtractionReport assemble(String artemisCommit, String manifestDigest, CurationReport curation, List<ReportItem> stageItems, boolean eligible) {
        List<ReportItem> sortedItems = new ArrayList<>(stageItems);
        sortedItems.sort(Comparator.comparing(ReportItem::code).thenComparing(ReportItem::subject).thenComparing(ReportItem::message).thenComparing(ReportItem::severity));
        Map<String, Integer> severityCounts = new TreeMap<>();
        Map<String, Integer> codeCounts = new TreeMap<>();
        for (ReportItem item : sortedItems) {
            severityCounts.merge(item.severity(), 1, Integer::sum);
            codeCounts.merge(item.code(), 1, Integer::sum);
        }
        String status = eligible ? ExtractionReport.STATUS_PASS : ExtractionReport.STATUS_FAIL;
        return new ExtractionReport(ExtractionReport.CURRENT_SCHEMA_VERSION, status, artemisCommit, manifestDigest, curation,
                new LinkedHashMap<>(CODE_DOCUMENTATION), severityCounts, codeCounts, List.copyOf(sortedItems));
    }
}
