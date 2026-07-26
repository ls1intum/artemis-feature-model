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
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GuidedWorkflowValidationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ModelDiffReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.RelationCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import tools.jackson.databind.ObjectMapper;

/**
 * Orchestrates one extraction run: executes every anchor scan fail-soft, assembles candidates with evidence and
 * relation candidates, compares against the active curated model, and assembles the deterministic report. The service
 * itself produces no timestamps; scan metadata is the caller's concern so this outcome stays reproducible.
 */
public class FeatureExtractionService {

    /** Version of the extraction pipeline, recorded in the scan metadata. */
    public static final String EXTRACTOR_VERSION = "0.3.0";

    private static final Map<String, String> CODE_DOCUMENTATION = new TreeMap<>(Map.ofEntries(
            Map.entry(ReportItem.CODE_GENERATED_MODEL_INVALID, "The assembled generated model failed the shared structural integrity validation."),
            Map.entry(ReportItem.CODE_GENERATED_WORKFLOW_INVALID, "The bundled guided workflow failed its hard reference validation against the generated model."),
            Map.entry(ReportItem.CODE_TECHNICAL_FEATURE_ROLE_LEAK, "A technical feature of the generated model is visible or configurable for teachers."),
            Map.entry(ReportItem.CODE_PROFILE_CAPABILITY_MISMATCH, "An included technical feature provides a capability the bundled deployment profile does not list."),
            Map.entry(ReportItem.CODE_RELATION_CANDIDATE_UNDECLARED, "A directed relation candidate connects included features without a matching declared constraint."),
            Map.entry(ReportItem.CODE_GUIDED_WORKFLOW_FINDINGS, "The guided workflow validation against the generated model produced findings; see guided-workflow-validation.json."),
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
     * Outcome of one extraction run. The generated-model outputs are null when the run was invoked without the
     * generation inputs (bundled guided workflow and deployment profile).
     *
     * @param candidates feature candidates sorted by id.
     * @param evidence evidence items sorted by candidate id, file, line, kind, and symbol.
     * @param relationCandidates relation candidates sorted by id.
     * @param report assembled extraction report with drift, curation, and generation sections.
     * @param includedFeatures included candidates with resolved annotation-over-manifest semantics.
     * @param generatedModel assembled generated feature model, or null.
     * @param generatedCatalog regenerated config-key catalog, or null.
     * @param modelDiff classified generated-versus-curated diff report, or null.
     * @param guidedWorkflowValidation guided workflow validation against the generated model, or null.
     */
    public record Outcome(List<FeatureCandidate> candidates, List<EvidenceItem> evidence, List<RelationCandidate> relationCandidates, ExtractionReport report,
            List<ResolvedFeatureScope> includedFeatures, FeatureModel generatedModel, ArtemisConfigKeyCatalog generatedCatalog, ModelDiffReport modelDiff,
            GuidedWorkflowValidationReport guidedWorkflowValidation) {
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
        FeatureScopeManifest emptyManifest = new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, source.commit(), List.of(), List.of(), List.of(),
                List.of(), List.of());
        return extract(source, curatedModel, catalog, emptyManifest);
    }

    /**
     * Runs the full extraction and curation pipeline against a checkout, without the generated-model assembly.
     *
     * @param source Artemis source repository.
     * @param curatedModel active curated feature model for the drift comparison.
     * @param catalog curated config key catalog for the drift comparison.
     * @param manifest scope manifest that controls candidate membership.
     * @return deterministic extraction and curation outcome.
     */
    public Outcome extract(ArtemisSourceRepository source, FeatureModel curatedModel, ArtemisConfigKeyCatalog catalog, FeatureScopeManifest manifest) {
        return extract(source, curatedModel, catalog, manifest, null, null);
    }

    /**
     * Runs the full extraction, curation, and generation pipeline against a checkout. When both the bundled guided
     * workflow and the bundled deployment profile are given, the pipeline additionally assembles the generated model,
     * regenerates the config-key catalog, validates both through the shared code paths, and classifies every
     * difference against the curated model.
     *
     * @param source Artemis source repository.
     * @param curatedModel active curated feature model for the drift and diff comparison.
     * @param catalog curated config key catalog for the drift and diff comparison.
     * @param manifest scope manifest that controls candidate membership and generated-model semantics.
     * @param bundledWorkflow lean bundled guided workflow validated against the generated model, or null to skip generation.
     * @param bundledProfile bundled deployment profile for the capability cross-checks, or null to skip generation.
     * @return deterministic extraction, curation, and generation outcome.
     */
    public Outcome extract(ArtemisSourceRepository source, FeatureModel curatedModel, ArtemisConfigKeyCatalog catalog, FeatureScopeManifest manifest,
            GuidedWorkflow bundledWorkflow, DeploymentProfile bundledProfile) {
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

        Generation generation = generate(source, curatedModel, catalog, manifest, curation, assembly, yamlScan, bundledWorkflow, bundledProfile, items);
        ExtractionReport report = assembleReport(curatedModel, source.commit(), curation.report(), items);
        return new Outcome(assembly.candidates(), assembly.evidence(), assembly.relationCandidates(), report, curation.includedFeatures(),
                generation.generatedModel(), generation.generatedCatalog(), generation.modelDiff(), generation.guidedWorkflowValidation());
    }

    /**
     * Generated-model outputs of one run; all fields are null when generation was skipped.
     *
     * @param generatedModel assembled generated model.
     * @param generatedCatalog regenerated config-key catalog.
     * @param modelDiff classified diff report.
     * @param guidedWorkflowValidation guided workflow validation report.
     */
    private record Generation(FeatureModel generatedModel, ArtemisConfigKeyCatalog generatedCatalog, ModelDiffReport modelDiff,
            GuidedWorkflowValidationReport guidedWorkflowValidation) {

        private static Generation skipped() {
            return new Generation(null, null, null, null);
        }
    }

    /**
     * Runs the generation stage: assembles the generated model from the curation result, regenerates the config-key
     * catalog, validates model and workflow through the shared code paths, and classifies every difference against
     * the curated model.
     *
     * @param source Artemis source repository.
     * @param curatedModel active curated model.
     * @param catalog curated config-key catalog.
     * @param manifest loaded scope manifest.
     * @param curation curation result with resolved include semantics.
     * @param assembly candidate assembly result.
     * @param yamlScan scanned configuration defaults.
     * @param bundledWorkflow lean bundled guided workflow, or null to skip generation.
     * @param bundledProfile bundled deployment profile, or null to skip generation.
     * @param items report item sink.
     * @return generation outputs, or the skipped marker.
     */
    private Generation generate(ArtemisSourceRepository source, FeatureModel curatedModel, ArtemisConfigKeyCatalog catalog, FeatureScopeManifest manifest,
            ScopeCurationService.Result curation, CandidateAssembler.Result assembly, YamlConfigScan.Result yamlScan, GuidedWorkflow bundledWorkflow,
            DeploymentProfile bundledProfile, List<ReportItem> items) {
        if (bundledWorkflow == null || bundledProfile == null) {
            return Generation.skipped();
        }
        GeneratedModelAssembler.Result generated = new GeneratedModelAssembler(objectMapper).assemble(manifest, curation.includedFeatures(),
                assembly.candidates(), assembly.evidence(), assembly.relationCandidates(), source.commit());
        items.addAll(generated.items());
        GeneratedCatalogAssembler catalogAssembler = new GeneratedCatalogAssembler();
        GeneratedCatalogAssembler.Result generatedCatalog = catalogAssembler.assemble(generated.model(), yamlScan, source.commit());
        items.addAll(generatedCatalog.items());
        GeneratedModelValidator.Result validation = new GeneratedModelValidator().validate(generated.model(), curation.includedFeatures(), bundledWorkflow,
                bundledProfile);
        items.addAll(validation.items());
        ModelDiffReport modelDiff = new ModelDiffService().compare(curatedModel, generated.model(), catalogAssembler.diff(catalog, generatedCatalog.catalog()),
                source.commit());
        return new Generation(generated.model(), generatedCatalog.catalog(), modelDiff, validation.guidedValidation());
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
