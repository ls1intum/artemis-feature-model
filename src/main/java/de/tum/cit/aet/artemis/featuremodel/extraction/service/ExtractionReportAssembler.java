package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;

/**
 * Consolidates the diagnostics of all extraction stages into the deterministic {@code extraction-report.json}. It is
 * the only place that sees scan, model, and workflow diagnostics together, so it also resolves the one cross-stage
 * rule: a generic new-candidate drift warning is dropped once the manifest documents a permanent exclusion.
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
            Map.entry(ReportItem.CODE_NEW_CANDIDATE_NOT_IN_MODEL, "A module or toggle candidate found in Artemis has no matching feature in the active curated model."),
            Map.entry(ReportItem.CODE_CURATED_ANCHOR_MISSING, "A curated feature references a config key, condition class, or client constant the scan did not find."),
            Map.entry(ReportItem.CODE_CURATED_EVIDENCE_STALE, "A curated file:line evidence reference no longer matches the scanned Artemis sources."),
            Map.entry(ReportItem.CODE_UNANCHORED_CURATED_FEATURE, "A curated feature has no config anchor; expected for conceptual aggregates and always-on modules."),
            Map.entry(ReportItem.CODE_CLIENT_SERVER_MIRROR_MISMATCH, "Client and server disagree about a module feature constant or runtime toggle enum member."),
            Map.entry(ReportItem.CODE_CONFIG_KEY_CATALOG_DRIFT, "The curated config key catalog disagrees with the scanned Artemis configuration keys or commit pin."),
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
     * @param curatedModel active curated model the drift section compared against.
     * @param artemisCommit resolved commit of the scanned checkout.
     * @param curation manifest curation section.
     * @param stageItems diagnostics of every stage that ran, in stage order.
     * @return assembled report.
     */
    ExtractionReport assemble(FeatureModel curatedModel, String artemisCommit, CurationReport curation, List<ReportItem> stageItems) {
        List<ReportItem> sortedItems = new ArrayList<>(stageItems);
        suppressExplicitlyExcludedNewCandidateItems(sortedItems, curation);
        sortedItems.sort(Comparator.comparing(ReportItem::code).thenComparing(ReportItem::subject).thenComparing(ReportItem::message).thenComparing(ReportItem::severity));
        Map<String, Integer> severityCounts = new TreeMap<>();
        Map<String, Integer> codeCounts = new TreeMap<>();
        for (ReportItem item : sortedItems) {
            severityCounts.merge(item.severity(), 1, Integer::sum);
            codeCounts.merge(item.code(), 1, Integer::sum);
        }
        return new ExtractionReport(artemisCommit, curatedModel.model().id(), curatedModel.model().version(), curation, new LinkedHashMap<>(CODE_DOCUMENTATION),
                severityCounts, codeCounts, List.copyOf(sortedItems));
    }

    /**
     * Removes generic new-candidate drift warnings after the manifest has documented a permanent exclusion. Included
     * candidates retain the drift warning because the generated-versus-curated diff supersedes it.
     *
     * @param items collected diagnostics.
     * @param curation manifest curation section.
     */
    private void suppressExplicitlyExcludedNewCandidateItems(List<ReportItem> items, CurationReport curation) {
        Set<String> excludedCandidateIds = new HashSet<>();
        curation.decisions().stream().filter(decision -> ScopeCurationService.STATE_EXCLUDE.equals(decision.state()))
                .forEach(decision -> excludedCandidateIds.add(decision.candidateId()));
        items.removeIf(item -> ReportItem.CODE_NEW_CANDIDATE_NOT_IN_MODEL.equals(item.code()) && excludedCandidateIds.contains(item.subject()));
    }
}
