package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.RelationCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Orchestrates one extraction run: executes every anchor scan fail-soft, assembles candidates with evidence and
 * relation candidates, compares against the active curated model, and assembles the deterministic report. The service
 * itself produces no timestamps; scan metadata is the caller's concern so this outcome stays reproducible.
 */
public class FeatureExtractionService {

    /** Version of the extraction pipeline, recorded in the scan metadata. */
    public static final String EXTRACTOR_VERSION = "0.2.0";

    private static final Map<String, String> CODE_DOCUMENTATION = new TreeMap<>(Map.ofEntries(
            Map.entry(ReportItem.CODE_NEW_CANDIDATE_NOT_IN_MODEL, "A module or toggle candidate found in Artemis has no matching feature in the active curated model."),
            Map.entry(ReportItem.CODE_CURATED_ANCHOR_MISSING, "A curated feature references a config key, condition class, or frontend constant the scan did not find."),
            Map.entry(ReportItem.CODE_CURATED_EVIDENCE_STALE, "A curated file:line evidence reference no longer matches the scanned Artemis sources."),
            Map.entry(ReportItem.CODE_UNANCHORED_CURATED_FEATURE, "A curated feature has no config anchor; expected for conceptual aggregates and always-on modules."),
            Map.entry(ReportItem.CODE_FE_BE_MIRROR_MISMATCH, "Frontend and backend disagree about a module feature constant or runtime toggle enum member."),
            Map.entry(ReportItem.CODE_CONFIG_KEY_CATALOG_DRIFT, "The curated config key catalog disagrees with the scanned Artemis configuration keys or commit pin."),
            Map.entry(ReportItem.CODE_EXTRACTOR_ERROR, "One extractor failed to parse its source; the scan continued without its contribution."),
            Map.entry(ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY, "Backend enabled property constants and module feature constants are asymmetric."),
            Map.entry(ReportItem.CODE_PENDING_SCOPE_DECISION, "An extracted candidate is unlisted and awaits an explicit scope decision."),
            Map.entry(ReportItem.CODE_ANNOTATED_BUT_UNSCOPED, "A source annotation exists but the manifest does not include its candidate."),
            Map.entry(ReportItem.CODE_ANNOTATION_OVERRIDES_MANIFEST, "Source annotation semantics override the included manifest entry for the same anchor."),
            Map.entry(ReportItem.CODE_ANNOTATED_ANCHOR_NOT_EXTRACTED, "An annotated source anchor could not be joined to an extracted candidate."),
            Map.entry(ReportItem.CODE_MANIFEST_COMMIT_MISMATCH, "The scope manifest was verified against a different Artemis commit."),
            Map.entry(ReportItem.CODE_MANIFEST_ORPHAN_ANCHOR, "A manifest anchor matches no extraction candidate of this scan, or matches more than one."),
            Map.entry(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, "Manifest entries, annotations, or resolved semantics collide for this scan and need review.")));

    private final ObjectMapper objectMapper;

    /**
     * Creates the extraction service.
     *
     * @param objectMapper Jackson mapper shared with the scans that parse JSON resources.
     */
    public FeatureExtractionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Outcome of one extraction run.
     *
     * @param candidates feature candidates sorted by id.
     * @param evidence evidence items sorted by candidate id, file, line, kind, and symbol.
     * @param relationCandidates relation candidates sorted by id.
     * @param report assembled extraction report with drift and curation sections.
     * @param includedFeatures included candidates with resolved annotation-over-manifest semantics.
     */
    public record Outcome(List<FeatureCandidate> candidates, List<EvidenceItem> evidence, List<RelationCandidate> relationCandidates, ExtractionReport report,
            List<ResolvedFeatureScope> includedFeatures) {
    }

    /**
     * Runs the full extraction pipeline against a checkout.
     *
     * @param source Artemis source repository.
     * @param curatedModel active curated feature model for the drift comparison.
     * @param catalog curated config key catalog for the drift comparison.
     * @return deterministic extraction outcome.
     */
    public Outcome extract(ArtemisSourceRepository source, FeatureModel curatedModel, ArtemisConfigKeyCatalog catalog) {
        FeatureScopeManifest emptyManifest = new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, source.commit(), List.of(), List.of(), List.of());
        return extract(source, curatedModel, catalog, emptyManifest);
    }

    /**
     * Runs the full extraction and curation pipeline against a checkout.
     *
     * @param source Artemis source repository.
     * @param curatedModel active curated feature model for the drift comparison.
     * @param catalog curated config key catalog for the drift comparison.
     * @param manifest scope manifest that controls candidate membership.
     * @return deterministic extraction and curation outcome.
     */
    public Outcome extract(ArtemisSourceRepository source, FeatureModel curatedModel, ArtemisConfigKeyCatalog catalog, FeatureScopeManifest manifest) {
        List<ReportItem> items = new ArrayList<>();

        BackendConstantScan.Result constantScan = runScan("backend constants", items, () -> new BackendConstantScan().scan(source), BackendConstantScan.Result.empty());
        ConfigHelperScan.Result configHelperScan = runScan("config helper", items, () -> new ConfigHelperScan().scan(source), ConfigHelperScan.Result.empty());
        ConditionClassScan.Result conditionScan = runScan("condition classes", items, () -> new ConditionClassScan().scan(source), ConditionClassScan.Result.empty());
        BackendFeatureEnumScan.Result backendToggleScan = runScan("backend feature enum", items, () -> new BackendFeatureEnumScan().scan(source),
                BackendFeatureEnumScan.Result.empty());
        FrontendConstantScan.Result frontendConstantScan = runScan("frontend constants", items, () -> new FrontendConstantScan().scan(source),
                FrontendConstantScan.Result.empty());
        FrontendToggleEnumScan.Result frontendToggleScan = runScan("frontend toggle enum", items, () -> new FrontendToggleEnumScan().scan(source),
                FrontendToggleEnumScan.Result.empty());
        AdminPageScan.Result adminPageScan = runScan("admin features page", items, () -> new AdminPageScan().scan(source), AdminPageScan.Result.empty());
        FeatureI18nScan.Result i18nScan = runScan("feature i18n", items, () -> new FeatureI18nScan(objectMapper).scan(source), FeatureI18nScan.Result.empty());
        YamlConfigScan.Result yamlScan = runScan("configuration defaults", items, () -> new YamlConfigScan().scan(source), YamlConfigScan.Result.empty());
        ComposeFileScan.Result composeScan = runScan("compose files", items, () -> new ComposeFileScan().scan(source), ComposeFileScan.Result.empty());
        UsageEvidenceScan.Result usageScan = runScan("usage evidence", items, () -> new UsageEvidenceScan().scan(source), UsageEvidenceScan.Result.empty());
        ArtemisFeatureAnnotationScan.Result annotationScan = runScan("ArtemisFeature annotations", items, () -> new ArtemisFeatureAnnotationScan().scan(source),
                ArtemisFeatureAnnotationScan.Result.empty());

        items.addAll(conditionScan.errors());
        items.addAll(yamlScan.errors());
        items.addAll(annotationScan.errors());

        CandidateAssembler.Result assembly = new CandidateAssembler().assemble(source, constantScan, configHelperScan, conditionScan, backendToggleScan,
                frontendConstantScan, frontendToggleScan, adminPageScan, i18nScan, yamlScan, composeScan, usageScan);
        items.addAll(assembly.items());
        items.addAll(new DriftComparator().compare(source, curatedModel, catalog, assembly.candidates(), yamlScan, source.commit()));
        ScopeCurationService.Result curation = new ScopeCurationService().curate(manifest, assembly.candidates(), annotationScan.annotations(), source.commit());
        suppressExplicitlyExcludedNewCandidateItems(items, curation);
        items.addAll(curation.items());

        ExtractionReport report = assembleReport(curatedModel, source.commit(), curation.report(), items);
        return new Outcome(assembly.candidates(), assembly.evidence(), assembly.relationCandidates(), report, curation.includedFeatures());
    }

    /**
     * Removes generic new-candidate drift warnings after the manifest has documented a permanent exclusion. Included
     * and pending candidates retain the drift warning until generated-model comparison supersedes it in E3.
     *
     * @param items collected drift diagnostics.
     * @param curation manifest curation result.
     */
    private void suppressExplicitlyExcludedNewCandidateItems(List<ReportItem> items, ScopeCurationService.Result curation) {
        Set<String> excludedCandidateIds = new HashSet<>();
        curation.report().decisions().stream().filter(decision -> ScopeCurationService.STATE_EXCLUDE.equals(decision.state()))
                .forEach(decision -> excludedCandidateIds.add(decision.candidateId()));
        items.removeIf(item -> ReportItem.CODE_NEW_CANDIDATE_NOT_IN_MODEL.equals(item.code()) && excludedCandidateIds.contains(item.subject()));
    }

    /**
     * Runs one scan fail-soft: a failing scan contributes an error report item and its empty fallback result instead
     * of aborting the run.
     *
     * @param <T> scan result type.
     * @param scanName human-readable scan name for the error item.
     * @param items report item sink.
     * @param scan scan invocation.
     * @param fallback empty fallback result.
     * @return scan result or fallback.
     */
    private <T> T runScan(String scanName, List<ReportItem> items, Callable<T> scan, T fallback) {
        try {
            return scan.call();
        }
        catch (Exception e) {
            items.add(ReportItem.error(ReportItem.CODE_EXTRACTOR_ERROR, scanName, "Scan '" + scanName + "' failed: " + e.getMessage()));
            return fallback;
        }
    }

    /**
     * Assembles the deterministic extraction report: documented codes, counts, and items sorted by code, subject,
     * message, and severity.
     *
     * @param curatedModel active curated model.
     * @param artemisCommit resolved commit of the scanned checkout.
     * @param curation manifest curation section.
     * @param items collected report items.
     * @return assembled report.
     */
    private ExtractionReport assembleReport(FeatureModel curatedModel, String artemisCommit, CurationReport curation, List<ReportItem> items) {
        List<ReportItem> sortedItems = new ArrayList<>(items);
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
}
