package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GuidedWorkflowValidationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ManifestConformanceException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ModelDiffReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ModelResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SourcePreflightException;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.FixtureArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Characterizes the complete staged extraction pipeline over the mini-Artemis fixture: scan, model assembly, workflow
 * preparation, and snapshot packaging. The asserted values are the parity contract of the orchestration refactor —
 * the commands may move, but these observable outputs must not change until a work package deliberately changes their
 * semantics.
 */
class ExtractionPipelineCharacterizationTest {

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    private static final Path FIXTURE_INPUTS = Path.of("src/test/resources/extraction/fixture-inputs");

    private static final String PINNED_COMMIT = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee";

    private static final String OTHER_COMMIT = "bbbbbbbbccccccccddddddddeeeeeeeeffffffff";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    private Path outputRoot;

    private FeatureExtractionInputs inputs;

    private ExtractionArtifactLayout layout;

    @BeforeEach
    void resolveInputs() {
        inputs = new FeatureExtractionInputs(FIXTURE_PATH, Path.of("src/test/resources/extraction/mini-artemis-manifest.yml"),
                FIXTURE_INPUTS.resolve("guided-workflow.json"), FIXTURE_INPUTS.resolve("deployment-profile.json"),
                FIXTURE_INPUTS.resolve("curated-model.json"), FIXTURE_INPUTS.resolve("config-key-catalog.json"), outputRoot);
        layout = ExtractionArtifactLayout.forCommit(outputRoot, PINNED_COMMIT);
    }

    @Test
    void scanWritesOnlyTheRawSourceDiscoveryArtifacts() throws Exception {
        ScanStageService.Summary summary = runScan();

        assertThat(summary.candidateCount()).isEqualTo(18);
        assertThat(summary.relationCandidateCount()).isEqualTo(2);
        assertThat(summary.artemisCommit()).isEqualTo(PINNED_COMMIT);
        for (String fileName : List.of(ExtractionArtifactStore.SCAN_METADATA_FILE, ExtractionArtifactStore.FEATURE_CANDIDATES_FILE,
                ExtractionArtifactStore.EVIDENCE_FILE, ExtractionArtifactStore.RELATION_CANDIDATES_FILE, ExtractionArtifactStore.ANNOTATIONS_FILE,
                ExtractionArtifactStore.CONFIG_DEFAULTS_FILE, ExtractionArtifactStore.SCAN_DIAGNOSTICS_FILE, ExtractionArtifactStore.SCAN_RESULT_FILE)) {
            assertThat(layout.scanDirectory().resolve(fileName)).as("scan artifact %s", fileName).isRegularFile();
        }
        assertThat(layout.modelDirectory()).doesNotExist();
        assertThat(layout.workflowDirectory()).doesNotExist();
        assertThat(layout.reportDirectory()).doesNotExist();
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void modelAssemblyConsumesTheScanWithoutReopeningArtemis() throws Exception {
        runScan();

        ModelStageService.Summary summary = new ModelStageService(OBJECT_MAPPER).run(inputsWithoutCheckout());

        assertThat(summary.curationCounts()).containsEntry("include", 1).containsEntry("exclude", 17).containsEntry("undeclared", 0);
        assertThat(summary.featureCount()).isEqualTo(2);
        assertThat(summary.relationCount()).isEqualTo(1);
        assertThat(summary.constraintCount()).isZero();
        assertThat(summary.catalogKeyCount()).isEqualTo(1);
        assertThat(summary.modelIntegrityValid()).isTrue();

        FeatureModel generatedModel = OBJECT_MAPPER.readValue(Files.readAllBytes(layout.modelDirectory().resolve(ExtractionArtifactStore.GENERATED_MODEL_FILE)),
                FeatureModel.class);
        assertThat(generatedModel.features()).extracting(FeatureNode::id).containsExactly("fixture-root", "alpha-feature");
        ArtemisConfigKeyCatalog generatedCatalog = OBJECT_MAPPER
                .readValue(Files.readAllBytes(layout.modelDirectory().resolve(ExtractionArtifactStore.GENERATED_CATALOG_FILE)), ArtemisConfigKeyCatalog.class);
        assertThat(generatedCatalog.keys()).extracting(ArtemisConfigKeyCatalog.CatalogKey::key).containsExactly("artemis.alpha.enabled");
        ModelDiffReport modelDiff = OBJECT_MAPPER.readValue(Files.readAllBytes(layout.modelDirectory().resolve(ExtractionArtifactStore.MODEL_DIFF_FILE)),
                ModelDiffReport.class);
        assertThat(modelDiff.classificationCounts()).containsKeys(ModelDiffReport.CLASS_INTENTIONAL_CURATION, ModelDiffReport.CLASS_ARTEMIS_DRIFT,
                ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, ModelDiffReport.CLASS_EXTRACTOR_GAP);
    }

    @Test
    void aggregatePipelinePublishesTheSnapshotAndConsolidatesEveryStageDiagnostic() throws Exception {
        runPipeline();

        assertThat(layout.workflowDirectory().resolve(ExtractionArtifactStore.PREPARED_WORKFLOW_FILE)).isRegularFile();
        assertThat(layout.workflowDirectory().resolve(ExtractionArtifactStore.GUIDED_VALIDATION_FILE)).isRegularFile();
        GuidedWorkflowValidationReport guidedValidation = OBJECT_MAPPER
                .readValue(Files.readAllBytes(layout.workflowDirectory().resolve(ExtractionArtifactStore.GUIDED_VALIDATION_FILE)),
                        GuidedWorkflowValidationReport.class);
        assertThat(guidedValidation.status()).isEqualTo(GuidedWorkflowValidationReport.STATUS_PASS);

        ExtractionReport report = OBJECT_MAPPER.readValue(Files.readAllBytes(layout.reportDirectory().resolve(ExtractionArtifactStore.EXTRACTION_REPORT_FILE)),
                ExtractionReport.class);
        assertThat(reportCodes(report)).contains(ReportItem.CODE_CURATED_ANCHOR_MISSING, ReportItem.CODE_CURATED_EVIDENCE_STALE,
                ReportItem.CODE_UNANCHORED_CURATED_FEATURE, ReportItem.CODE_FE_BE_MIRROR_MISMATCH, ReportItem.CODE_CONFIG_KEY_CATALOG_DRIFT,
                ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY);
        assertThat(reportCodes(report)).doesNotContain(ReportItem.CODE_EXTRACTOR_ERROR, ReportItem.CODE_NEW_CANDIDATE_NOT_IN_MODEL);
        assertThat(report.codes()).containsKey(ReportItem.CODE_EXTRACTOR_ERROR);
        assertThat(report.artemisCommit()).isEqualTo(PINNED_COMMIT);
        assertThat(report.curatedModelId()).isEqualTo("fixture-model");
        assertThat(report.curation().stateCounts()).containsEntry("include", 1).containsEntry("exclude", 17);

        for (String fileName : List.of(SnapshotPublisher.SNAPSHOT_MODEL_FILE, SnapshotPublisher.SNAPSHOT_WORKFLOW_FILE, SnapshotPublisher.SNAPSHOT_METADATA_FILE,
                SnapshotPublisher.SNAPSHOT_CHECKSUM_FILE)) {
            assertThat(layout.snapshotDirectory().resolve(fileName)).as("snapshot file %s", fileName).isRegularFile();
        }
    }

    @Test
    void rejectsAModelAssembledFromAScanThatChangedAfterwards() throws Exception {
        runScan();
        new ModelStageService(OBJECT_MAPPER).run(inputsWithoutCheckout());
        Files.writeString(layout.scanDirectory().resolve(ExtractionArtifactStore.RELATION_CANDIDATES_FILE), "[]\n");

        assertThatThrownBy(() -> new WorkflowStageService(OBJECT_MAPPER).run(inputsWithoutCheckout())).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining(ExtractionArtifactStore.RELATION_CANDIDATES_FILE);
        assertThat(layout.workflowDirectory()).doesNotExist();
        assertThat(layout.reportDirectory()).doesNotExist();
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void rejectsAWorkflowPreparedBeforeTheModelWasReassembled() throws Exception {
        runPipeline();
        Files.writeString(layout.modelDirectory().resolve(ExtractionArtifactStore.GENERATED_MODEL_FILE), "{}\n");

        assertThatThrownBy(() -> new PackageStageService(OBJECT_MAPPER).run(inputsWithoutCheckout())).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("generated model digest");
        assertThat(layout.reportDirectory()).doesNotExist();
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void rejectsAScanTakenFromAnotherArtemisCommit() throws Exception {
        runScan();
        ScanResult scanResult = OBJECT_MAPPER.readValue(Files.readAllBytes(layout.scanDirectory().resolve(ExtractionArtifactStore.SCAN_RESULT_FILE)),
                ScanResult.class);
        ScanResult otherCommit = new ScanResult(scanResult.schemaVersion(), scanResult.extractorVersion(), "0123456789abcdef0123456789abcdef01234567",
                scanResult.payloadDigests(), scanResult.payloadDigest());
        Files.writeString(layout.scanDirectory().resolve(ExtractionArtifactStore.SCAN_RESULT_FILE), OBJECT_MAPPER.writeValueAsString(otherCommit));

        assertThatThrownBy(() -> new ModelStageService(OBJECT_MAPPER).run(inputsWithoutCheckout())).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("Artemis commit");
    }

    @Test
    void aCheckoutAtAnotherCommitNeverStartsAScan() throws Exception {
        runPipeline();

        assertThatThrownBy(() -> new ScanStageService(OBJECT_MAPPER).run(inputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, OTHER_COMMIT)))
                .isInstanceOf(SourcePreflightException.class).hasMessageContaining(PINNED_COMMIT);
        assertNoStageArtifactSurvives();
    }

    @Test
    void anUndeclaredCandidateStopsTheRunWithoutAModelOrSnapshot() throws Exception {
        runPipeline();
        FeatureExtractionInputs incompleteManifest = withManifest(FIXTURE_INPUTS.resolve("manifest-with-undeclared-candidate.yml"));

        runScan(incompleteManifest);
        assertThatThrownBy(() -> new ModelStageService(OBJECT_MAPPER).run(incompleteManifest)).isInstanceOf(ManifestConformanceException.class)
                .hasMessageContaining("module:gamma").hasMessageContaining("no feature model was assembled");

        ModelResult modelResult = OBJECT_MAPPER.readValue(Files.readAllBytes(layout.modelDirectory().resolve(ExtractionArtifactStore.MODEL_RESULT_FILE)),
                ModelResult.class);
        assertThat(modelResult.conformance().conformant()).isFalse();
        assertThat(modelResult.conformance().undeclaredCandidates()).containsExactly("module:gamma");
        assertThat(layout.modelDirectory().resolve(ExtractionArtifactStore.MODEL_DIAGNOSTICS_FILE)).isRegularFile();
        assertThat(layout.modelDirectory().resolve(ExtractionArtifactStore.GENERATED_MODEL_FILE)).doesNotExist();
        assertThat(layout.workflowDirectory()).doesNotExist();
        assertThat(layout.reportDirectory()).doesNotExist();
        assertThat(layout.snapshotDirectory()).doesNotExist();
        assertThatThrownBy(() -> new WorkflowStageService(OBJECT_MAPPER).run(incompleteManifest)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("manifest incomplete");
    }

    @Test
    void aMissingCheckoutConfigurationRemovesThePreviouslyPublishedSnapshot() throws Exception {
        runPipeline();
        assertThat(layout.snapshotDirectory()).isDirectory();

        assertThatThrownBy(() -> new ScanStageService(OBJECT_MAPPER).run(inputsWithoutCheckout(), LocalArtemisSourceRepository::new))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(FeatureExtractionInputs.ARTEMIS_PATH_ENVIRONMENT_VARIABLE);

        assertNoStageArtifactSurvives();
    }

    @Test
    void aTamperedScanRemovesThePreviouslyPublishedSnapshot() throws Exception {
        runPipeline();
        Files.writeString(layout.scanDirectory().resolve(ExtractionArtifactStore.EVIDENCE_FILE), "[]\n");

        assertThatThrownBy(() -> new ModelStageService(OBJECT_MAPPER).run(inputsWithoutCheckout())).isInstanceOf(ExtractionArtifactException.class);

        assertThat(layout.modelDirectory()).doesNotExist();
        assertThat(layout.workflowDirectory()).doesNotExist();
        assertThat(layout.reportDirectory()).doesNotExist();
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void aTamperedWorkflowRemovesThePreviouslyPublishedSnapshot() throws Exception {
        runPipeline();
        Files.writeString(layout.workflowDirectory().resolve(ExtractionArtifactStore.PREPARED_WORKFLOW_FILE), "{}\n");

        assertThatThrownBy(() -> new PackageStageService(OBJECT_MAPPER).run(inputsWithoutCheckout())).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("prepared workflow digest");

        assertThat(layout.reportDirectory()).doesNotExist();
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void modelAssemblyFailsWithoutAPriorScan() {
        assertThatThrownBy(() -> new ModelStageService(OBJECT_MAPPER).run(inputsWithoutCheckout())).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining(ExtractionArtifactStore.SCAN_RESULT_FILE);
    }

    /**
     * Asserts that no artifact of any stage survived, which is what every scan failure must leave behind.
     */
    private void assertNoStageArtifactSurvives() {
        assertThat(layout.scanDirectory()).doesNotExist();
        assertThat(layout.modelDirectory()).doesNotExist();
        assertThat(layout.workflowDirectory()).doesNotExist();
        assertThat(layout.reportDirectory()).doesNotExist();
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    /**
     * Runs the scan command over the fixture checkout.
     *
     * @return scan summary.
     * @throws Exception if the scan fails.
     */
    private ScanStageService.Summary runScan() throws Exception {
        return runScan(inputs);
    }

    /**
     * Runs the scan command over the fixture checkout with the given inputs.
     *
     * @param scanInputs command inputs to scan with.
     * @return scan summary.
     * @throws Exception if the scan fails.
     */
    private ScanStageService.Summary runScan(FeatureExtractionInputs scanInputs) throws Exception {
        return new ScanStageService(OBJECT_MAPPER).run(scanInputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, PINNED_COMMIT));
    }

    /**
     * Creates inputs that read another scope manifest.
     *
     * @param manifestFile manifest to curate with.
     * @return inputs pointing at the given manifest.
     */
    private FeatureExtractionInputs withManifest(Path manifestFile) {
        return new FeatureExtractionInputs(FIXTURE_PATH, manifestFile, inputs.authoredWorkflowFile(), inputs.deploymentProfileFile(), inputs.curatedModelFile(),
                inputs.bootstrapCatalogFile(), inputs.outputRoot());
    }

    /**
     * Runs the complete staged pipeline over the fixture checkout.
     *
     * @throws Exception if a command fails.
     */
    private void runPipeline() throws Exception {
        runScan();
        new ModelStageService(OBJECT_MAPPER).run(inputsWithoutCheckout());
        new WorkflowStageService(OBJECT_MAPPER).run(inputsWithoutCheckout());
        new PackageStageService(OBJECT_MAPPER).run(inputsWithoutCheckout());
    }

    /**
     * Creates inputs without an Artemis checkout, proving that the downstream commands never open one.
     *
     * @return inputs whose checkout is unset.
     */
    private FeatureExtractionInputs inputsWithoutCheckout() {
        return new FeatureExtractionInputs(null, inputs.manifestFile(), inputs.authoredWorkflowFile(), inputs.deploymentProfileFile(),
                inputs.curatedModelFile(), inputs.bootstrapCatalogFile(), inputs.outputRoot());
    }

    /**
     * Collects the distinct diagnostic codes of a consolidated report.
     *
     * @param report consolidated extraction report.
     * @return report codes present in the run.
     */
    private List<String> reportCodes(ExtractionReport report) {
        return report.items().stream().map(ReportItem::code).distinct().toList();
    }
}
