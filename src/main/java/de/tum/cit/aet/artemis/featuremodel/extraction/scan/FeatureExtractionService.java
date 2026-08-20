package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotation;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefaults;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedSourceFacts;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.ArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.SourceScanResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs the source discovery half of one extraction run: executes every anchor scan fail-soft, assembles candidates
 * with evidence and relation candidates. It records source facts only — manifest membership and generated artifacts belong to the later stages. The service itself produces no
 * timestamps; scan metadata is the caller's concern so this outcome stays reproducible.
 */
class FeatureExtractionService {

    private final ObjectMapper objectMapper;

    private final CandidateAssembler candidateAssembler;

    /**
     * Creates the extraction service.
     *
     * @param objectMapper Jackson mapper shared with the scans that parse JSON resources.
     */
    FeatureExtractionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        candidateAssembler = new CandidateAssembler();
    }

    /**
     * Scans a checkout for feature candidates, evidence, and relation candidates.
     *
     * @param source Artemis source repository.
     * @return deterministic source discovery outcome.
     */
    ExtractedSourceFacts scan(ArtemisSourceRepository source) {
        List<ReportItem> items = new ArrayList<>();

        SourceScanResult<ServerConstantScan.Result> constantScan = runScan("server constants",
                () -> SourceScanResult.success(new ServerConstantScan().scan(source)), ServerConstantScan.Result.empty());
        SourceScanResult<ConfigHelperScan.Result> configHelperScan = runScan("config helper",
                () -> SourceScanResult.success(new ConfigHelperScan().scan(source)), ConfigHelperScan.Result.empty());
        SourceScanResult<ConditionClassScan.Result> conditionScan = runScan("condition classes", () -> new ConditionClassScan().scan(source),
                ConditionClassScan.Result.empty());
        SourceScanResult<ServerFeatureEnumScan.Result> serverToggleScan = runScan("server feature enum",
                () -> SourceScanResult.success(new ServerFeatureEnumScan().scan(source)), ServerFeatureEnumScan.Result.empty());
        SourceScanResult<ClientConstantScan.Result> clientConstantScan = runScan("client constants",
                () -> SourceScanResult.success(new ClientConstantScan().scan(source)), ClientConstantScan.Result.empty());
        SourceScanResult<ClientToggleEnumScan.Result> clientToggleScan = runScan("client toggle enum",
                () -> SourceScanResult.success(new ClientToggleEnumScan().scan(source)), ClientToggleEnumScan.Result.empty());
        SourceScanResult<AdminPageScan.Result> adminPageScan = runScan("admin features page",
                () -> SourceScanResult.success(new AdminPageScan().scan(source)), AdminPageScan.Result.empty());
        SourceScanResult<FeatureI18nScan.Result> i18nScan = runScan("feature i18n",
                () -> SourceScanResult.success(new FeatureI18nScan(objectMapper).scan(source)), FeatureI18nScan.Result.empty());
        SourceScanResult<ExtractedConfigurationDefaults> yamlScan = runScan("configuration defaults", () -> new YamlConfigScan().scan(source),
                ExtractedConfigurationDefaults.empty());
        SourceScanResult<ComposeFileScan.Result> composeScan = runScan("compose files", () -> SourceScanResult.success(new ComposeFileScan().scan(source)),
                ComposeFileScan.Result.empty());
        SourceScanResult<UsageEvidenceScan.Result> usageScan = runScan("usage evidence",
                () -> SourceScanResult.success(new UsageEvidenceScan().scan(source)), UsageEvidenceScan.Result.empty());
        SourceScanResult<List<ExtractedAnnotation>> annotationScan = runScan("ArtemisFeature annotations", () -> new ArtemisFeatureAnnotationScan().scan(source),
                List.of());

        List<SourceScanResult<?>> scanResults = List.of(constantScan, configHelperScan, conditionScan, serverToggleScan, clientConstantScan,
                clientToggleScan, adminPageScan, i18nScan, yamlScan, composeScan, usageScan, annotationScan);
        appendWholeScannerDiagnostics(scanResults, items);
        appendIsolatedDiagnostics(List.of(conditionScan, yamlScan, annotationScan), items);

        CandidateAssemblyInput assemblyInput = new CandidateAssemblyInput(source, constantScan.facts(), configHelperScan.facts(), conditionScan.facts(),
                serverToggleScan.facts(), clientConstantScan.facts(), clientToggleScan.facts(), adminPageScan.facts(), i18nScan.facts(), yamlScan.facts(),
                composeScan.facts(), usageScan.facts());
        CandidateAssembler.Result assembly = candidateAssembler.assemble(assemblyInput);
        items.addAll(assembly.items());
        return new ExtractedSourceFacts(assembly.candidates(), assembly.evidence(), assembly.relationCandidates(), annotationScan.facts(), yamlScan.facts(),
                List.copyOf(items));
    }

    /**
     * Runs one scan fail-soft: a failing scan contributes an error report item and its empty fallback result instead
     * of aborting the run.
     *
     * @param <T> scan result type.
     * @param scanName human-readable scan name for the error item.
     * @param scan scan invocation.
     * @param fallback empty fallback result.
     * @return explicit source-scan result with discovered facts or the fallback and a controlled diagnostic.
     */
    private <T> SourceScanResult<T> runScan(String scanName, Callable<SourceScanResult<T>> scan, T fallback) {
        try {
            return scan.call();
        }
        catch (Exception e) {
            ReportItem diagnostic = ReportItem.error(ReportItem.CODE_EXTRACTOR_ERROR, scanName, "Scan '" + scanName + "' failed: " + e.getMessage());
            return SourceScanResult.failure(fallback, diagnostic);
        }
    }

    /**
     * Appends whole-scanner failures in scanner invocation order, preserving the persisted diagnostic order contract.
     *
     * @param scanResults source-scan results in invocation order.
     * @param items diagnostic sink.
     */
    private void appendWholeScannerDiagnostics(List<SourceScanResult<?>> scanResults, List<ReportItem> items) {
        scanResults.stream().filter(SourceScanResult::wholeScannerFailed).forEach(result -> items.addAll(result.diagnostics()));
    }

    /**
     * Appends isolated per-file failures after all whole-scanner failures, in the established condition, YAML, then
     * annotation order.
     *
     * @param scanResults scanners that can retain sibling facts after one file fails.
     * @param items diagnostic sink.
     */
    private void appendIsolatedDiagnostics(List<SourceScanResult<?>> scanResults, List<ReportItem> items) {
        scanResults.stream().filter(result -> !result.wholeScannerFailed()).forEach(result -> items.addAll(result.diagnostics()));
    }
}
