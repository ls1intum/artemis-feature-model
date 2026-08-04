package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

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
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ManifestConformanceReport;
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

    private static final String PINNED_REPOSITORY_COMMIT = "fedcba9876543210fedcba9876543210fedcba98";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, String> RECORDED_STAGE_ONE_DIGESTS = Map.ofEntries(
            Map.entry("model/generated-config-key-catalog.json", "7a080f8415459765a83c5551347390d4252bbde63594786ded298dbaee93e5f5"),
            Map.entry("model/generated-feature-model.json", "df423ddc542889d09d863855b0bc0fd2c9bc810c945125915f37d3fd02fd305b"),
            Map.entry("model/manifest-conformance-report.json", "f4b1343cc2afbed9e064c5e939481a5390116eef90782b0801a5ef0948606c5d"),
            Map.entry("model/model-diagnostics.json", "25f881c3c71d326fd737fc9e76c6ce2f03de67a957d97a2cef3282ec2d0cc80f"),
            Map.entry("model/model-result.json", "e04ec465b8d99525561320c3bdcd78407f0dc618334258a8864110a8a0b38d9b"),
            Map.entry("report/extraction-report.json", "90c04105aae2037be436460a5a895eabbe8dfbf094c46411299c2e35bcd15963"),
            Map.entry("report/index.html", "e184f8ccb677509371407a9ebad122767e3abc46ea8969b1a61ff90aeda705aa"),
            Map.entry("report/release-delta-report.json", "4581d5b3b95165376a5be075aebfca9e012a82498cb6f8dc592c687d31f3ebb9"),
            Map.entry("scan/annotations.json", "25f881c3c71d326fd737fc9e76c6ce2f03de67a957d97a2cef3282ec2d0cc80f"),
            Map.entry("scan/config-defaults.json", "f9ef321499b67c416f3b4bdcaeb67862a735560ec5c5ef894f27a4314b5b4cc0"),
            Map.entry("scan/evidence.json", "beede58e239dd3f73a9754a955838de594ce5753be9a23c764e85fb0b0fc3567"),
            Map.entry("scan/feature-candidates.json", "e173b8730fc85d878bae0cbda90849b462039e008577f8fdda8644db012c7412"),
            Map.entry("scan/relation-candidates.json", "c8b43e1cb073e315b10523e73423eaa4f84e9fed85af8ed1335b6a202522302a"),
            Map.entry("scan/scan-diagnostics.json", "4e3081f07bc10b1c6f1f4cf14b6d14954fde697ba79805f3420885e7d2690319"),
            Map.entry("scan/scan-result.json", "5f1bd016aeac2a82ceb1786baa80e7975f930cd3c269240f976f5b7597a3ac9c"),
            Map.entry("snapshot/checksums.txt", "2deb095d235f058e9d8f8faddd90930b5a3d2f55467947265216a6ebe3fdffcd"),
            Map.entry("snapshot/config-key-catalog.json", "7a080f8415459765a83c5551347390d4252bbde63594786ded298dbaee93e5f5"),
            Map.entry("snapshot/feature-model.json", "df423ddc542889d09d863855b0bc0fd2c9bc810c945125915f37d3fd02fd305b"),
            Map.entry("snapshot/generation-report.json", "90c04105aae2037be436460a5a895eabbe8dfbf094c46411299c2e35bcd15963"),
            Map.entry("snapshot/guided-workflow.json", "acdd8024949b4b28b910358c721b2ec2067f596ce39944f740163226028577c8"),
            Map.entry("snapshot/metadata.json", "4221b0e6fee56b21091923326506c787a7419c03eaa23fc6556c5eda526ad9c3"),
            Map.entry("snapshot/provenance.json", "f83289526bc099a480c7272e6620a2ea23f5347bd12451b2366b8efd06da06b0"),
            Map.entry("workflow/guided-workflow-validation.json", "e0e8cd15f417efe76782691783bb7ae26f1216e13dcb71cc1a82275bff862f61"),
            Map.entry("workflow/guided-workflow.json", "acdd8024949b4b28b910358c721b2ec2067f596ce39944f740163226028577c8"),
            Map.entry("workflow/workflow-diagnostics.json", "25f881c3c71d326fd737fc9e76c6ce2f03de67a957d97a2cef3282ec2d0cc80f"),
            Map.entry("workflow/workflow-result.json", "f95da8ba6033a801520525f45250adea4a24df203f97967ee4f776ac6e083dfc"));

    @TempDir
    private Path outputRoot;

    private FeatureExtractionInputs inputs;

    private ExtractionArtifactLayout layout;

    @BeforeEach
    void resolveInputs() {
        inputs = new FeatureExtractionInputs(FIXTURE_PATH, Path.of("src/test/resources/extraction/mini-artemis-manifest.yml"),
                FIXTURE_INPUTS.resolve("guided-workflow.json"), FIXTURE_INPUTS.resolve("deployment-profile.json"), outputRoot);
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
        ManifestConformanceReport conformance = OBJECT_MAPPER.readValue(
                Files.readAllBytes(layout.modelDirectory().resolve(ExtractionArtifactStore.MANIFEST_CONFORMANCE_FILE)), ManifestConformanceReport.class);
        assertThat(conformance.status()).isEqualTo(ManifestConformanceReport.STATUS_PASS);
        assertThat(conformance.generatedFeatureIds()).containsExactly("fixture-root", "alpha-feature");
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
        assertThat(reportCodes(report)).contains(ReportItem.CODE_CLIENT_SERVER_MIRROR_MISMATCH, ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY);
        assertThat(reportCodes(report)).doesNotContain(ReportItem.CODE_EXTRACTOR_ERROR);
        assertThat(report.codes()).containsKey(ReportItem.CODE_EXTRACTOR_ERROR);
        assertThat(report.artemisCommit()).isEqualTo(PINNED_COMMIT);
        assertThat(report.status()).isEqualTo(ExtractionReport.STATUS_PASS);
        assertThat(report.curation().stateCounts()).containsEntry("include", 1).containsEntry("exclude", 17);
        assertThat(layout.reportDirectory().resolve(ExtractionArtifactStore.HTML_REPORT_FILE)).isRegularFile();
        assertThat(layout.reportDirectory().resolve(ExtractionArtifactStore.RELEASE_DELTA_REPORT_FILE)).content().contains("\"status\" : \"skipped\"")
                .contains("\"blocking\" : false");

        for (String fileName : List.of(SnapshotPublisher.SNAPSHOT_MODEL_FILE, SnapshotPublisher.SNAPSHOT_WORKFLOW_FILE,
                SnapshotPublisher.SNAPSHOT_CATALOG_FILE, SnapshotPublisher.SNAPSHOT_REPORT_FILE, SnapshotPublisher.SNAPSHOT_PROVENANCE_FILE,
                SnapshotPublisher.SNAPSHOT_METADATA_FILE, SnapshotPublisher.SNAPSHOT_CHECKSUM_FILE)) {
            assertThat(layout.snapshotDirectory().resolve(fileName)).as("snapshot file %s", fileName).isRegularFile();
        }
    }

    @Test
    void deterministicMiniArtemisArtifactsMatchTheRecordedStageOneBaseline() throws Exception {
        runPipeline();

        assertThat(deterministicArtifactDigests()).isEqualTo(RECORDED_STAGE_ONE_DIGESTS);
    }

    @Test
    void generatedArtifactsUseClientAndServerTerminology() throws Exception {
        runPipeline();
        List<String> retiredTerms = List.of("front" + "end", "back" + "end");

        try (var paths = Files.walk(layout.root())) {
            for (Path artifact : paths.filter(Files::isRegularFile).toList()) {
                String content = Files.readString(artifact).toLowerCase(Locale.ROOT);
                assertThat(retiredTerms).as("terminology in %s", artifact).noneMatch(content::contains);
            }
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
        assertFailureReportExists();
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void rejectsAWorkflowPreparedBeforeTheModelWasReassembled() throws Exception {
        runPipeline();
        Files.writeString(layout.modelDirectory().resolve(ExtractionArtifactStore.GENERATED_MODEL_FILE), "{}\n");

        assertThatThrownBy(() -> new PackageStageService(OBJECT_MAPPER, PINNED_REPOSITORY_COMMIT).run(inputsWithoutCheckout()))
                .isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("generated model digest");
        assertFailureReportExists();
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
        assertThat(layout.reportDirectory().resolve(ExtractionArtifactStore.EXTRACTION_REPORT_FILE)).isRegularFile();
        String html = Files.readString(layout.reportDirectory().resolve(ExtractionArtifactStore.HTML_REPORT_FILE));
        assertThat(html).contains("Overall verdict: FAIL", "module:gamma", "UNDECLARED_CANDIDATE");
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
        assertFailureReportExists();
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void aTamperedWorkflowRemovesThePreviouslyPublishedSnapshot() throws Exception {
        runPipeline();
        Files.writeString(layout.workflowDirectory().resolve(ExtractionArtifactStore.PREPARED_WORKFLOW_FILE), "{}\n");

        assertThatThrownBy(() -> new PackageStageService(OBJECT_MAPPER, PINNED_REPOSITORY_COMMIT).run(inputsWithoutCheckout()))
                .isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("prepared workflow digest");

        assertFailureReportExists();
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
        assertFailureReportExists();
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    private void assertFailureReportExists() {
        assertThat(layout.reportDirectory().resolve(ExtractionArtifactStore.EXTRACTION_REPORT_FILE)).isRegularFile();
        assertThat(layout.reportDirectory().resolve(ExtractionArtifactStore.HTML_REPORT_FILE)).isRegularFile();
        assertThat(layout.reportDirectory().resolve(ExtractionArtifactStore.RELEASE_DELTA_REPORT_FILE)).isRegularFile();
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
        return new FeatureExtractionInputs(FIXTURE_PATH, manifestFile, inputs.authoredWorkflowFile(), inputs.deploymentProfileFile(), inputs.outputRoot());
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
        new PackageStageService(OBJECT_MAPPER, PINNED_REPOSITORY_COMMIT).run(inputsWithoutCheckout());
    }

    /**
     * Creates inputs without an Artemis checkout, proving that the downstream commands never open one.
     *
     * @return inputs whose checkout is unset.
     */
    private FeatureExtractionInputs inputsWithoutCheckout() {
        return new FeatureExtractionInputs(null, inputs.manifestFile(), inputs.authoredWorkflowFile(), inputs.deploymentProfileFile(), inputs.outputRoot());
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

    /**
     * Hashes every deterministic mini-Artemis artifact. Scan metadata is excluded because it intentionally records
     * wall-clock timestamps and the temporary checkout path. The scan envelope is included because its digest map
     * now preserves canonical insertion order across processes.
     *
     * @return artifact digests keyed by run-relative path.
     * @throws Exception if an artifact cannot be listed, read, or hashed.
     */
    private Map<String, String> deterministicArtifactDigests() throws Exception {
        Map<String, String> digests = new TreeMap<>();
        try (var paths = Files.walk(layout.root())) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relativePath = layout.root().relativize(path).toString().replace('\\', '/');
                if (!relativePath.equals("scan/" + ExtractionArtifactStore.SCAN_METADATA_FILE)) {
                    digests.put(relativePath, sha256(Files.readAllBytes(path)));
                }
            }
        }
        return digests;
    }

    /**
     * Computes an unprefixed lowercase SHA-256 digest for a byte-parity assertion.
     *
     * @param bytes artifact bytes.
     * @return lowercase digest.
     */
    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }
}
