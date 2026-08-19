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
import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.Sha256Digest;
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
import tools.jackson.databind.node.ObjectNode;

/**
 * Characterizes the complete staged extraction pipeline over the mini-Artemis fixture: scan, model assembly, workflow
 * preparation, and snapshot packaging. The asserted values are the parity contract of the orchestration refactor —
 * the commands may move, but these observable outputs must not change until a work package deliberately changes their
 * semantics.
 */
class ExtractionPipelineCharacterizationTest {

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    private static final Path FIXTURE_INPUTS = Path.of("src/test/resources/extraction/fixture-inputs");

    private static final String DERIVED_COMMIT = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee";

    private static final String OTHER_COMMIT = "bbbbbbbbccccccccddddddddeeeeeeeeffffffff";

    private static final String PINNED_REPOSITORY_COMMIT = "fedcba9876543210fedcba9876543210fedcba98";

    /** Verdict badge the HTML report renders for a run that cannot be published. */
    private static final String FAILED_VERDICT_BADGE = "<span class=\"verdict bad\">FAIL</span>";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, String> RECORDED_STAGE_ONE_DIGESTS = Map.ofEntries(
            Map.entry("model/generated-config-key-catalog.json", "7a080f8415459765a83c5551347390d4252bbde63594786ded298dbaee93e5f5"),
            Map.entry("model/generated-feature-model.json", "bd84b5a2b7f142486fdfc9d57fa1319034a6df3c3149a3b535051b42999e55a4"),
            Map.entry("model/manifest-conformance-report.json", "c72f79baee5b0552bb16c7ef4bab16bbfdd9bae4c377f9d357ee2d13e241d7b8"),
            Map.entry("model/model-diagnostics.json", "25f881c3c71d326fd737fc9e76c6ce2f03de67a957d97a2cef3282ec2d0cc80f"),
            Map.entry("model/model-result.json", "3c198c42378a43e02ea5af028b12df4025be33826b07744b069d8fa69b9a01ec"),
            Map.entry("report/extraction-report.json", "3260f4aba66d6ef2088d732cea41d8a94b22188eeb7d7e6da823fee1d72cbea9"),
            Map.entry("report/index.html", "aeda17a128abde99bc6f146b706e56eee9437cc040dc7dcbd3e5c7318c35a2d5"),
            Map.entry("report/release-delta-report.json", "4581d5b3b95165376a5be075aebfca9e012a82498cb6f8dc592c687d31f3ebb9"),
            Map.entry("scan/annotations.json", "25f881c3c71d326fd737fc9e76c6ce2f03de67a957d97a2cef3282ec2d0cc80f"),
            Map.entry("scan/config-defaults.json", "f9ef321499b67c416f3b4bdcaeb67862a735560ec5c5ef894f27a4314b5b4cc0"),
            Map.entry("scan/evidence.json", "e2a8098c07ff01667fdf26f4379752187adf29fe079caec341781bb6bebb5f36"),
            Map.entry("scan/feature-candidates.json", "a9dcac02f05af8308090f3de00ff52e58d285a2f42b311fda7937fb3516e7b58"),
            Map.entry("scan/relation-candidates.json", "c8b43e1cb073e315b10523e73423eaa4f84e9fed85af8ed1335b6a202522302a"),
            Map.entry("scan/scan-diagnostics.json", "4e3081f07bc10b1c6f1f4cf14b6d14954fde697ba79805f3420885e7d2690319"),
            Map.entry("scan/scan-result.json", "fc66d48cef6815e5425718dbfb02b00c9ba08a0faecd5ec84b73f459c6db62f0"),
            Map.entry("snapshot/checksums.txt", "52ed44ace90f3de4ba6066e87233bfc7c3489e57394ea13d315788d38039900e"),
            Map.entry("snapshot/config-key-catalog.json", "7a080f8415459765a83c5551347390d4252bbde63594786ded298dbaee93e5f5"),
            Map.entry("snapshot/feature-model.json", "bd84b5a2b7f142486fdfc9d57fa1319034a6df3c3149a3b535051b42999e55a4"),
            Map.entry("snapshot/generation-report.json", "3260f4aba66d6ef2088d732cea41d8a94b22188eeb7d7e6da823fee1d72cbea9"),
            Map.entry("snapshot/guided-workflow.json", "692cf4c6cb29afcb6d30a76c0588dc00dea66e1b989f8d3835b7499a9dc3892d"),
            Map.entry("snapshot/metadata.json", "fcb059a6e620241630c5d0d2afc416357ccf15d98c320ecf12534833a5caaaf7"),
            Map.entry("snapshot/provenance.json", "9fbf242e4daf3e5ef2d0956da9adb0c887dda437a6a92fde02764fca41cd9546"),
            Map.entry("workflow/guided-workflow-validation.json", "d62007db411e48a6dde5ceb2dc8ee673ae5be15d89682a3f34ee4b1f96f9f40c"),
            Map.entry("workflow/guided-workflow.json", "692cf4c6cb29afcb6d30a76c0588dc00dea66e1b989f8d3835b7499a9dc3892d"),
            Map.entry("workflow/workflow-diagnostics.json", "25f881c3c71d326fd737fc9e76c6ce2f03de67a957d97a2cef3282ec2d0cc80f"),
            Map.entry("workflow/workflow-result.json", "8d88af5d3d4f8be0f156c6926eeca78ea7e15b40b4eaf3dc45600a10f8059adc"));

    @TempDir
    private Path outputRoot;

    private FeatureExtractionInputs inputs;

    private ExtractionArtifactLayout layout;

    @BeforeEach
    void resolveInputs() {
        inputs = new FeatureExtractionInputs(FIXTURE_PATH, Path.of("src/test/resources/extraction/mini-artemis-manifest.yml"),
                FIXTURE_INPUTS.resolve("guided-workflow.json"), FIXTURE_INPUTS.resolve("deployment-profile.json"),
                FIXTURE_INPUTS.resolve("artemis-runtime-image.json"), outputRoot);
        layout = ExtractionArtifactLayout.forCommit(outputRoot, DERIVED_COMMIT);
    }

    @Test
    void scanWritesOnlyTheRawSourceDiscoveryArtifacts() throws Exception {
        ScanStageService.Summary summary = runScan();

        assertThat(summary.candidateCount()).isEqualTo(15);
        assertThat(summary.relationCandidateCount()).isEqualTo(2);
        assertThat(summary.artemisCommit()).isEqualTo(DERIVED_COMMIT);
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
    void modelAssemblyConsumesTheScanWithoutRescanningArtemis() throws Exception {
        runScan();

        ModelStageService.Summary summary = new ModelStageService(OBJECT_MAPPER).run(inputs, this::fixtureSource);

        assertThat(summary.curationCounts()).containsEntry("include", 1).containsEntry("exclude", 14).containsEntry("undeclared", 0);
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
        assertThat(conformance.generatedOutputFindings()).isEmpty();
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
        assertThat(report.artemisCommit()).isEqualTo(DERIVED_COMMIT);
        assertThat(report.status()).isEqualTo(ExtractionReport.STATUS_PASS);
        assertThat(report.curation().stateCounts()).containsEntry("include", 1).containsEntry("exclude", 14);
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
    void warningOnlyGuidedFindingsPublishAFreshSnapshot() throws Exception {
        FeatureExtractionInputs draftInputs = withWorkflow(workflowWithDraftOption());
        runScan(draftInputs);
        new ModelStageService(OBJECT_MAPPER).run(draftInputs, this::fixtureSource);

        WorkflowStageService.Summary workflowSummary = new WorkflowStageService(OBJECT_MAPPER).run(draftInputs, this::fixtureSource);
        PackageStageService.Summary packageSummary = new PackageStageService(OBJECT_MAPPER, PINNED_REPOSITORY_COMMIT).run(draftInputs, this::fixtureSource);

        assertThat(workflowSummary.validationStatus()).isEqualTo(GuidedWorkflowValidationReport.STATUS_FINDINGS);
        assertThat(workflowSummary.deliveryEligible()).isTrue();
        assertThat(workflowSummary.severityCounts()).containsKey(ReportItem.SEVERITY_WARNING).doesNotContainKey(ReportItem.SEVERITY_ERROR);
        assertThat(workflowSummary.codeCounts()).containsKey("GUIDED_WORKFLOW_DRAFT_OPTION");
        assertThat(packageSummary.snapshotDirectory()).isNotNull();
        assertThat(layout.snapshotDirectory()).isDirectory();
        ExtractionReport report = OBJECT_MAPPER.readValue(Files.readAllBytes(layout.reportDirectory().resolve(ExtractionArtifactStore.EXTRACTION_REPORT_FILE)),
                ExtractionReport.class);
        assertThat(report.status()).isEqualTo(ExtractionReport.STATUS_PASS);
    }

    @Test
    void publishedOptionWithIncompleteProseBlocksPublication() throws Exception {
        FeatureExtractionInputs todoInputs = withWorkflow(workflowWithTodoPublishedOption());
        runScan(todoInputs);
        new ModelStageService(OBJECT_MAPPER).run(todoInputs, this::fixtureSource);

        WorkflowStageService.Summary workflowSummary = new WorkflowStageService(OBJECT_MAPPER).run(todoInputs, this::fixtureSource);

        assertThat(workflowSummary.deliveryEligible()).isFalse();
        assertThat(workflowSummary.severityCounts()).containsKey(ReportItem.SEVERITY_ERROR);
        assertThatThrownBy(() -> new PackageStageService(OBJECT_MAPPER, PINNED_REPOSITORY_COMMIT).run(todoInputs, this::fixtureSource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no snapshot was published");
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void rejectsAModelAssembledFromAScanThatChangedAfterwards() throws Exception {
        runScan();
        new ModelStageService(OBJECT_MAPPER).run(inputs, this::fixtureSource);
        Files.writeString(layout.scanDirectory().resolve(ExtractionArtifactStore.RELATION_CANDIDATES_FILE), "[]\n");

        assertThatThrownBy(() -> new WorkflowStageService(OBJECT_MAPPER).run(inputs, this::fixtureSource)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining(ExtractionArtifactStore.RELATION_CANDIDATES_FILE);
        assertThat(layout.workflowDirectory()).doesNotExist();
        assertFailureReportExists();
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void rejectsAWorkflowPreparedBeforeTheModelWasReassembled() throws Exception {
        runPipeline();
        Files.writeString(layout.modelDirectory().resolve(ExtractionArtifactStore.GENERATED_MODEL_FILE), "{}\n");

        assertThatThrownBy(() -> new PackageStageService(OBJECT_MAPPER, PINNED_REPOSITORY_COMMIT).run(inputs, this::fixtureSource))
                .isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("generated model digest");
        assertFailureReportExists();
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void rejectsATamperedGeneratedCatalogBeforePackaging() throws Exception {
        runPipeline();
        Path catalogFile = layout.modelDirectory().resolve(ExtractionArtifactStore.GENERATED_CATALOG_FILE);
        Files.writeString(catalogFile, Files.readString(catalogFile).replace(DERIVED_COMMIT, OTHER_COMMIT));

        assertThatThrownBy(() -> new PackageStageService(OBJECT_MAPPER, PINNED_REPOSITORY_COMMIT).run(inputs, this::fixtureSource))
                .isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("generated catalog digest");

        assertFailureReportExists();
        assertFailureVerdictMentions("generated catalog digest");
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void catalogParseFailureOverwritesTheEarlierPassingReport() throws Exception {
        runPipeline();
        Files.writeString(layout.modelDirectory().resolve(ExtractionArtifactStore.GENERATED_CATALOG_FILE), "invalid json\n");
        ModelResult result = OBJECT_MAPPER.readValue(Files.readAllBytes(layout.modelDirectory().resolve(ExtractionArtifactStore.MODEL_RESULT_FILE)),
                ModelResult.class);
        ModelResult matchingDigest = new ModelResult(result.schemaVersion(), result.extractorVersion(), result.artemisCommit(), result.scanDigest(),
                result.manifestDigest(), result.generatedModelDigest(), Sha256Digest.of(
                        layout.modelDirectory().resolve(ExtractionArtifactStore.GENERATED_CATALOG_FILE)), result.generatedOutputConformant(),
                result.modelIntegrityValid(), result.deliveryEligible(), result.conformance(), result.curation());
        Files.writeString(layout.modelDirectory().resolve(ExtractionArtifactStore.MODEL_RESULT_FILE), OBJECT_MAPPER.writeValueAsString(matchingDigest));

        assertThatThrownBy(() -> new PackageStageService(OBJECT_MAPPER, PINNED_REPOSITORY_COMMIT).run(inputs, this::fixtureSource))
                .isInstanceOf(RuntimeException.class);

        assertFailureVerdictMentions("unrecognized token");
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void generatedSemanticConformanceFailureBlocksPublication() throws Exception {
        runPipeline();
        Path resultFile = layout.modelDirectory().resolve(ExtractionArtifactStore.MODEL_RESULT_FILE);
        ModelResult result = OBJECT_MAPPER.readValue(Files.readAllBytes(resultFile), ModelResult.class);
        ModelResult failedConformance = new ModelResult(result.schemaVersion(), result.extractorVersion(), result.artemisCommit(), result.scanDigest(),
                result.manifestDigest(), result.generatedModelDigest(), result.generatedCatalogDigest(), false, result.modelIntegrityValid(), false,
                result.conformance(), result.curation());
        Files.writeString(resultFile, OBJECT_MAPPER.writeValueAsString(failedConformance));

        assertThatThrownBy(() -> new PackageStageService(OBJECT_MAPPER, PINNED_REPOSITORY_COMMIT).run(inputs, this::fixtureSource))
                .isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("does not conform to the resolved manifest semantics");

        assertFailureVerdictMentions("does not conform to the resolved manifest semantics");
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

        assertThatThrownBy(() -> new ModelStageService(OBJECT_MAPPER).run(inputs, this::fixtureSource)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("Artemis commit");
    }

    @Test
    void anExpectedRevisionMismatchNeverStartsAScanAndTouchesNoArtifact() throws Exception {
        runPipeline();
        FeatureExtractionInputs expectingInputs = new FeatureExtractionInputs(FIXTURE_PATH, inputs.manifestFile(),
                FeatureExtractionInputs.MANIFEST_SOURCE_REPOSITORY, inputs.authoredWorkflowFile(), inputs.deploymentProfileFile(), inputs.runtimeImageFile(),
                outputRoot, DERIVED_COMMIT);

        assertThatThrownBy(
                () -> new ScanStageService(OBJECT_MAPPER).run(expectingInputs, checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, OTHER_COMMIT)))
                .isInstanceOf(SourcePreflightException.class).hasMessageContaining(DERIVED_COMMIT).hasMessageContaining(OTHER_COMMIT);
        assertThat(layout.snapshotDirectory()).isDirectory();
        assertThat(ExtractionArtifactLayout.forCommit(outputRoot, OTHER_COMMIT).root()).doesNotExist();
    }

    @Test
    void aCheckoutAtAnotherRevisionScansIntoItsOwnLayoutAndLeavesTheFirstRunIntact() throws Exception {
        runPipeline();

        ScanStageService.Summary otherScan = new ScanStageService(OBJECT_MAPPER).run(inputs,
                checkout -> FixtureArtemisSourceRepository.cleanAt(checkout, OTHER_COMMIT));

        ExtractionArtifactLayout otherLayout = ExtractionArtifactLayout.forCommit(outputRoot, OTHER_COMMIT);
        assertThat(otherScan.artemisCommit()).isEqualTo(OTHER_COMMIT);
        assertThat(otherScan.scanDirectory()).isEqualTo(otherLayout.scanDirectory());
        assertThat(otherLayout.scanDirectory().resolve(ExtractionArtifactStore.SCAN_RESULT_FILE)).isRegularFile();
        assertThat(layout.snapshotDirectory()).isDirectory();
        assertThat(layout.scanDirectory().resolve(ExtractionArtifactStore.SCAN_RESULT_FILE)).isRegularFile();
    }

    @Test
    void anUndeclaredCandidateStopsTheRunWithoutAModelOrSnapshot() throws Exception {
        runPipeline();
        FeatureExtractionInputs incompleteManifest = withManifest(FIXTURE_INPUTS.resolve("manifest-with-undeclared-candidate.yml"));

        runScan(incompleteManifest);
        assertThatThrownBy(() -> new ModelStageService(OBJECT_MAPPER).run(incompleteManifest, this::fixtureSource))
                .isInstanceOf(ManifestConformanceException.class)
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
        assertThat(html).contains(FAILED_VERDICT_BADGE, "module:gamma", "UNDECLARED_CANDIDATE");
        assertThat(layout.snapshotDirectory()).doesNotExist();
        assertThatThrownBy(() -> new WorkflowStageService(OBJECT_MAPPER).run(incompleteManifest, this::fixtureSource))
                .isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("manifest incomplete");
    }

    @Test
    void aMissingCheckoutConfigurationFailsBeforeAnyRunIdentityExists() throws Exception {
        runPipeline();
        assertThat(layout.snapshotDirectory()).isDirectory();
        FeatureExtractionInputs checkoutlessInputs = new FeatureExtractionInputs(null, inputs.manifestFile(), inputs.authoredWorkflowFile(),
                inputs.deploymentProfileFile(), inputs.runtimeImageFile(), outputRoot);

        assertThatThrownBy(() -> new ScanStageService(OBJECT_MAPPER).run(checkoutlessInputs, LocalArtemisSourceRepository::new))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(FeatureExtractionInputs.ARTEMIS_PATH_ENVIRONMENT_VARIABLE);

        // Without a checkout no revision can be derived, so no run directory can be attributed to the failed
        // invocation and the previous run's artifacts legitimately survive.
        assertThat(layout.snapshotDirectory()).isDirectory();
        assertThat(layout.scanDirectory().resolve(ExtractionArtifactStore.SCAN_RESULT_FILE)).isRegularFile();
    }

    @Test
    void aTamperedScanRemovesThePreviouslyPublishedSnapshot() throws Exception {
        runPipeline();
        Files.writeString(layout.scanDirectory().resolve(ExtractionArtifactStore.EVIDENCE_FILE), "[]\n");

        assertThatThrownBy(() -> new ModelStageService(OBJECT_MAPPER).run(inputs, this::fixtureSource)).isInstanceOf(ExtractionArtifactException.class);

        assertThat(layout.modelDirectory()).doesNotExist();
        assertThat(layout.workflowDirectory()).doesNotExist();
        assertFailureReportExists();
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void aTamperedWorkflowRemovesThePreviouslyPublishedSnapshot() throws Exception {
        runPipeline();
        Files.writeString(layout.workflowDirectory().resolve(ExtractionArtifactStore.PREPARED_WORKFLOW_FILE), "{}\n");

        assertThatThrownBy(() -> new PackageStageService(OBJECT_MAPPER, PINNED_REPOSITORY_COMMIT).run(inputs, this::fixtureSource))
                .isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining("prepared workflow digest");

        assertFailureReportExists();
        assertThat(layout.snapshotDirectory()).doesNotExist();
    }

    @Test
    void modelAssemblyFailsWithoutAPriorScan() {
        assertThatThrownBy(() -> new ModelStageService(OBJECT_MAPPER).run(inputs, this::fixtureSource)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining(ExtractionArtifactStore.SCAN_RESULT_FILE);
    }

    private void assertFailureReportExists() {
        assertThat(layout.reportDirectory().resolve(ExtractionArtifactStore.EXTRACTION_REPORT_FILE)).isRegularFile();
        assertThat(layout.reportDirectory().resolve(ExtractionArtifactStore.HTML_REPORT_FILE)).isRegularFile();
        assertThat(layout.reportDirectory().resolve(ExtractionArtifactStore.RELEASE_DELTA_REPORT_FILE)).isRegularFile();
    }

    private void assertFailureVerdictMentions(String detail) throws Exception {
        ExtractionReport report = OBJECT_MAPPER.readValue(
                Files.readAllBytes(layout.reportDirectory().resolve(ExtractionArtifactStore.EXTRACTION_REPORT_FILE)), ExtractionReport.class);
        assertThat(report.status()).isEqualTo(ExtractionReport.STATUS_FAIL);
        assertThat(report.items()).extracting(ReportItem::message).anyMatch(message -> message.toLowerCase(Locale.ROOT).contains(detail.toLowerCase(Locale.ROOT)));
        assertThat(layout.reportDirectory().resolve(ExtractionArtifactStore.HTML_REPORT_FILE)).content().contains(FAILED_VERDICT_BADGE);
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
        return new ScanStageService(OBJECT_MAPPER).run(scanInputs, this::fixtureSource);
    }

    /**
     * Creates inputs that read another scope manifest.
     *
     * @param manifestFile manifest to curate with.
     * @return inputs pointing at the given manifest.
     */
    private FeatureExtractionInputs withManifest(Path manifestFile) {
        return new FeatureExtractionInputs(FIXTURE_PATH, manifestFile, inputs.authoredWorkflowFile(), inputs.deploymentProfileFile(),
                inputs.runtimeImageFile(), inputs.outputRoot());
    }

    /**
     * Creates inputs that read another authored workflow.
     *
     * @param workflowFile authored workflow to prepare.
     * @return inputs pointing at the given workflow.
     */
    private FeatureExtractionInputs withWorkflow(Path workflowFile) {
        return new FeatureExtractionInputs(FIXTURE_PATH, inputs.manifestFile(), workflowFile, inputs.deploymentProfileFile(),
                inputs.runtimeImageFile(), inputs.outputRoot());
    }

    /**
     * Writes a fixture workflow copy with one additional complete draft option.
     *
     * @return path of the augmented workflow.
     * @throws Exception if the fixture workflow cannot be read or written.
     */
    private Path workflowWithDraftOption() throws Exception {
        ObjectNode workflow = (ObjectNode) OBJECT_MAPPER.readTree(Files.readAllBytes(FIXTURE_INPUTS.resolve("guided-workflow.json")));
        ObjectNode draft = OBJECT_MAPPER.createObjectNode();
        draft.put("id", "enable-fixture-draft");
        draft.put("status", "draft");
        draft.put("label", "Fixture Draft");
        draft.put("description", "Complete draft description.");
        draft.withArrayProperty("selects").add("alpha-feature");
        draft.withArrayProperty("enabledOutcome").add("Outcome.");
        draft.withArrayProperty("recommendedWhen").add("Fits.");
        draft.withArrayProperty("thingsToKnow").add("Notes.");
        fixtureDecision(workflow).withArrayProperty("options").add(draft);
        return writeSyntheticWorkflow(workflow, "draft-guided-workflow.json");
    }

    /**
     * Writes a fixture workflow copy whose published option still carries TODO prose.
     *
     * @return path of the modified workflow.
     * @throws Exception if the fixture workflow cannot be read or written.
     */
    private Path workflowWithTodoPublishedOption() throws Exception {
        ObjectNode workflow = (ObjectNode) OBJECT_MAPPER.readTree(Files.readAllBytes(FIXTURE_INPUTS.resolve("guided-workflow.json")));
        ObjectNode option = (ObjectNode) fixtureDecision(workflow).withArrayProperty("options").get(0);
        option.put("description", "TODO: describe this option.");
        return writeSyntheticWorkflow(workflow, "todo-guided-workflow.json");
    }

    private ObjectNode fixtureDecision(ObjectNode workflow) {
        ObjectNode step = (ObjectNode) workflow.withArrayProperty("steps").get(0);
        return (ObjectNode) step.withArrayProperty("decisions").get(0);
    }

    private Path writeSyntheticWorkflow(ObjectNode workflow, String fileName) throws Exception {
        Path file = outputRoot.resolve(fileName);
        Files.writeString(file, OBJECT_MAPPER.writeValueAsString(workflow));
        return file;
    }

    /**
     * Runs the complete staged pipeline over the fixture checkout.
     *
     * @throws Exception if a command fails.
     */
    private void runPipeline() throws Exception {
        runScan();
        new ModelStageService(OBJECT_MAPPER).run(inputs, this::fixtureSource);
        new WorkflowStageService(OBJECT_MAPPER).run(inputs, this::fixtureSource);
        new PackageStageService(OBJECT_MAPPER, PINNED_REPOSITORY_COMMIT).run(inputs, this::fixtureSource);
    }

    /**
     * Creates the clean fixture source repository the pipeline derives its run identity from.
     *
     * @param checkout configured checkout path.
     * @return fixture repository reporting the derived commit and a clean working tree.
     */
    private FixtureArtemisSourceRepository fixtureSource(Path checkout) {
        return FixtureArtemisSourceRepository.cleanAt(checkout, DERIVED_COMMIT);
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
