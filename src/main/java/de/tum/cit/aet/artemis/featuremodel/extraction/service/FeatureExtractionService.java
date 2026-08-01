package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.RelationCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.ArtemisFeatureAnnotationScan.AnnotatedAnchor;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs the source discovery half of one extraction run: executes every anchor scan fail-soft, assembles candidates
 * with evidence and relation candidates, and compares the result against the active curated model. It records source
 * facts only — manifest membership and generated artifacts belong to the later stages. The service itself produces no
 * timestamps; scan metadata is the caller's concern so this outcome stays reproducible.
 */
class FeatureExtractionService {

    private final ObjectMapper objectMapper;

    /**
     * Creates the extraction service.
     *
     * @param objectMapper Jackson mapper shared with the scans that parse JSON resources.
     */
    FeatureExtractionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Source facts discovered in one scan.
     *
     * @param candidates feature candidates sorted by id.
     * @param evidence evidence items sorted by candidate id, file, line, kind, and symbol.
     * @param relationCandidates relation candidates sorted by id.
     * @param annotations parsed {@code @ArtemisFeature} anchors.
     * @param configDefaults scanned configuration defaults of the checkout.
     * @param items scan diagnostics, including drift against the curated model.
     */
    record Outcome(List<FeatureCandidate> candidates, List<EvidenceItem> evidence, List<RelationCandidate> relationCandidates, List<AnnotatedAnchor> annotations,
            YamlConfigScan.Result configDefaults, List<ReportItem> items) {
    }

    /**
     * Scans a checkout for feature candidates, evidence, relation candidates, and drift against the curated model.
     *
     * @param source Artemis source repository.
     * @param curatedModel active curated feature model for the drift comparison.
     * @param catalog curated config key catalog for the drift comparison.
     * @return deterministic source discovery outcome.
     */
    Outcome scan(ArtemisSourceRepository source, FeatureModel curatedModel, ArtemisConfigKeyCatalog catalog) {
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

        return new Outcome(assembly.candidates(), assembly.evidence(), assembly.relationCandidates(), annotationScan.annotations(), yamlScan, List.copyOf(items));
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
}
