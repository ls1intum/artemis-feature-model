package de.tum.cit.aet.artemis.featuremodel.extraction;

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
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotBundleContract;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SourcePreflightException;
import de.tum.cit.aet.artemis.featuremodel.extraction.model.ModelStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.pipeline.ExtractionArtifactStore;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.FixtureArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.scan.ScanStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.snapshot.PackageStageService;
import de.tum.cit.aet.artemis.featuremodel.extraction.workflow.WorkflowStageService;
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
            Map.entry("model/config-derivation.json", "2920ffb35162daea7aedc2b99947c536711bfde7bef9c4d67b8b8ca02a819b54"),
            Map.entry("model/generated-config-key-catalog.json", "001cc8f7ec8818c84a54be67ff164697e7386558f3aa6f4fe24c375d5d630aca"),
            Map.entry("model/generated-feature-model.json", "0c89c2bb7805a6c8c918543383d2644f51142e4d50019fdeb09cd573834e529f"),
            Map.entry("model/manifest-conformance-report.json", "d5bee8867f9807e2f99f108a52d5a7df92fdc18efac749934128759d7c1b63b5"),
            Map.entry("model/model-diagnostics.json", "7602ab0970c517ea435328cc8d0a86aafeca21d753925041823dd9a54769a2da"),
            Map.entry("model/model-result.json", "fc76c4f0a7333bf3f6ca6efdb2fadb3c894b64d6351df0fd28bb7b30293e6dfa"),
            Map.entry("report/extraction-report.json", "d67421459485650aec8a1fa17a6289f0ee5844653ac6993946a17191d14e095a"),
            Map.entry("report/index.html", "d9d3ab3e162ee722434dd5e08f912ab6c787bb343678d3288254180f04bb109f"),
            Map.entry("report/release-delta-report.json", "4581d5b3b95165376a5be075aebfca9e012a82498cb6f8dc592c687d31f3ebb9"),
            Map.entry("scan/feature-usages.json", "4639391c8ae623fb371c45532322fd8c88e02dad5245a5297ed7adb3adf8fc82"),
            Map.entry("scan/config-defaults.json", "4973f5af6b899ac2816f8fe6d78a0f1ae8ec284db6df0dc9de02104e6329d1da"),
            Map.entry("scan/config-injections.json", "4da4d0cc8c3193ae0e3a4131d41b4f491825692d4145cb1e61c9593782533998"),
            Map.entry("scan/evidence.json", "2151a412cbfe552fea4f653d1c29f31e863f5de4500c1b55fe14c1bf203c0694"),
            Map.entry("scan/feature-candidates.json", "a9dcac02f05af8308090f3de00ff52e58d285a2f42b311fda7937fb3516e7b58"),
            Map.entry("scan/relation-candidates.json", "c8b43e1cb073e315b10523e73423eaa4f84e9fed85af8ed1335b6a202522302a"),
            Map.entry("scan/scan-diagnostics.json", "4e3081f07bc10b1c6f1f4cf14b6d14954fde697ba79805f3420885e7d2690319"),
            Map.entry("scan/scan-result.json", "62bfae9064bafce26582a046a4bdd1ae811d0ed167bc98a3dd9a36f2ba99d2fa"),
            Map.entry("snapshot/checksums.txt", "7df713ee6ee9f6e5c4ea7588b583e9270604bb85e123a1fca38cb00eee24742a"),
            Map.entry("snapshot/config-key-catalog.json", "001cc8f7ec8818c84a54be67ff164697e7386558f3aa6f4fe24c375d5d630aca"),
            Map.entry("snapshot/feature-model.json", "0c89c2bb7805a6c8c918543383d2644f51142e4d50019fdeb09cd573834e529f"),
            Map.entry("snapshot/generation-report.json", "d67421459485650aec8a1fa17a6289f0ee5844653ac6993946a17191d14e095a"),
            Map.entry("snapshot/guided-workflow.json", "47b79c65009f1c9f9bfa53d810e153ed3810ecc0f2a0e1bf3fc6d9b2c3a660c3"),
            Map.entry("snapshot/metadata.json", "f60e801dfdfb90d96e90886156382d77c34688bf5b2a797edd04acd5be945ee8"),
            Map.entry("snapshot/provenance.json", "91ce33851008cb614a375bc3c010bca32b1436ff810aefb5bb3b9cdb7c98f2d3"),
            Map.entry("workflow/guided-workflow-validation.json", "d62007db411e48a6dde5ceb2dc8ee673ae5be15d89682a3f34ee4b1f96f9f40c"),
            Map.entry("workflow/guided-workflow.json", "47b79c65009f1c9f9bfa53d810e153ed3810ecc0f2a0e1bf3fc6d9b2c3a660c3"),
            Map.entry("workflow/workflow-diagnostics.json", "25f881c3c71d326fd737fc9e76c6ce2f03de67a957d97a2cef3282ec2d0cc80f"),
            Map.entry("workflow/workflow-result.json", "277bc74d927b30b0ca279b58fa7b07ab629d95e31494cc1a41e589a2461f612b"));

    @TempDir
    private Path outputRoot;

    private FeatureExtractionInputs inputs;

    private ExtractionArtifactLayout layout;

    @BeforeEach
    void resolveInputs() {
        inputs = new FeatureExtractionInputs(FIXTURE_PATH, Path.of("src/test/resources/extraction/mini-artemis-manifest.yml"),
                FIXTURE_INPUTS.resolve("guided-workflow.json"), FIXTURE_INPUTS.resolve("artemis-runtime-image.json"), outputRoot);
        layout = ExtractionArtifactLayout.forCommit(outputRoot, DERIVED_COMMIT);
    }

    @Test
    void scanWritesOnlyTheRawSourceDiscoveryArtifacts() throws Exception {
        ScanStageService.Summary summary = runScan();

        assertThat(summary.candidateCount()).isEqualTo(15);
        assertThat(summary.relationCandidateCount()).isEqualTo(2);
        assertThat(summary.artemisCommit()).isEqualTo(DERIVED_COMMIT);
        for (String fileName : List.of(ExtractionArtifactStore.SCAN_METADATA_FILE, ExtractionArtifactStore.FEATURE_CANDIDATES_FILE,
                ExtractionArtifactStore.EVIDENCE_FILE, ExtractionArtifactStore.RELATION_CANDIDATES_FILE, ExtractionArtifactStore.FEATURE_USAGES_FILE,
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
        assertThat(summary.featureCount()).as("root, alpha, and alpha's one sub-feature").isEqualTo(3);
        assertThat(summary.relationCount()).isEqualTo(2);
        assertThat(summary.constraintCount()).isZero();
        assertThat(summary.catalogKeyCount()).isEqualTo(4);
        assertThat(summary.modelIntegrityValid()).isTrue();

        FeatureModel generatedModel = OBJECT_MAPPER.readValue(Files.readAllBytes(layout.modelDirectory().resolve(ExtractionArtifactStore.GENERATED_MODEL_FILE)),
                FeatureModel.class);
        assertThat(generatedModel.features()).extracting(FeatureNode::id).containsExactly("fixture-root", "alpha", "alpha/authoring/alpha-items");
        FeatureNode alphaFeature = generatedModel.features().get(1);
        FeatureNode alphaItems = generatedModel.features().getLast();
        assertThat(alphaItems.kind()).isEqualTo("sub-feature");
        assertThat(alphaItems.selectable()).isFalse();
        assertThat(alphaItems.source().usageLabel()).isEqualTo("authoring/alpha-items");
        assertThat(alphaItems.source().evidence()).containsExactly("AlphaResource.java:19");
        assertThat(generatedModel.relations()).anySatisfy(relation -> {
            assertThat(relation.parentId()).isEqualTo("alpha");
            assertThat(relation.childId()).isEqualTo("alpha/authoring/alpha-items");
            assertThat(relation.relationType()).isEqualTo("mandatory");
        });
        assertThat(alphaFeature.artifactMappings()).extracting(mapping -> mapping.path())
                .as("enabled-key toggle mapping first, then derived non-secret inputs sorted, then derived secret inputs")
                .containsExactly("artemis.alpha.enabled", "artemis.alpha.connector.endpoint", "artemis.alpha.url", "artemis.alpha.token");
        assertThat(alphaFeature.artifactMappings().getLast().secret()).isTrue();
        ArtemisConfigKeyCatalog generatedCatalog = OBJECT_MAPPER
                .readValue(Files.readAllBytes(layout.modelDirectory().resolve(ExtractionArtifactStore.GENERATED_CATALOG_FILE)), ArtemisConfigKeyCatalog.class);
        assertThat(generatedCatalog.keys()).extracting(ArtemisConfigKeyCatalog.CatalogKey::key).containsExactly("artemis.alpha.connector.endpoint",
                "artemis.alpha.enabled", "artemis.alpha.token", "artemis.alpha.url");
        assertThat(layout.modelDirectory().resolve(ExtractionArtifactStore.CONFIG_DERIVATION_FILE)).isRegularFile();
        ManifestConformanceReport conformance = OBJECT_MAPPER.readValue(
                Files.readAllBytes(layout.modelDirectory().resolve(ExtractionArtifactStore.MANIFEST_CONFORMANCE_FILE)), ManifestConformanceReport.class);
        assertThat(conformance.status()).isEqualTo(ManifestConformanceReport.STATUS_PASS);
        assertThat(conformance.generatedFeatureIds()).containsExactly("fixture-root", "alpha", "alpha/authoring/alpha-items");
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

        for (String fileName : List.of(SnapshotBundleContract.SNAPSHOT_MODEL_FILE, SnapshotBundleContract.SNAPSHOT_WORKFLOW_FILE,
                SnapshotBundleContract.SNAPSHOT_CATALOG_FILE, SnapshotBundleContract.SNAPSHOT_REPORT_FILE, SnapshotBundleContract.SNAPSHOT_PROVENANCE_FILE,
                SnapshotBundleContract.SNAPSHOT_METADATA_FILE, SnapshotBundleContract.SNAPSHOT_CHECKSUM_FILE)) {
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
                FeatureExtractionInputs.MANIFEST_SOURCE_REPOSITORY, inputs.authoredWorkflowFile(), inputs.runtimeImageFile(), outputRoot, DERIVED_COMMIT);

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
    void anUnmodeledAnchorOnlyInformsAndTheRunStillPublishes() throws Exception {
        FeatureExtractionInputs unmodeledManifest = withManifest(FIXTURE_INPUTS.resolve("manifest-with-unmodeled-anchor.yml"));

        runScan(unmodeledManifest);
        ModelStageService.Summary summary = new ModelStageService(OBJECT_MAPPER).run(unmodeledManifest, this::fixtureSource);
        new WorkflowStageService(OBJECT_MAPPER).run(unmodeledManifest, this::fixtureSource);
        new PackageStageService(OBJECT_MAPPER, PINNED_REPOSITORY_COMMIT).run(unmodeledManifest, this::fixtureSource);

        assertThat(summary.curationCounts()).containsEntry("include", 1).containsEntry("exclude", 13).containsEntry("undeclared", 0).containsEntry("unmodeled", 1);
        ExtractionReport report = OBJECT_MAPPER.readValue(Files.readAllBytes(layout.reportDirectory().resolve(ExtractionArtifactStore.EXTRACTION_REPORT_FILE)),
                ExtractionReport.class);
        assertThat(report.status()).isEqualTo(ExtractionReport.STATUS_PASS);
        assertThat(report.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_UNMODELED_ANCHOR);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_INFO);
            assertThat(item.subject()).isEqualTo("module:delta");
        });
        assertThat(layout.snapshotDirectory()).isDirectory();
        assertThat(Files.readString(layout.reportDirectory().resolve(ExtractionArtifactStore.HTML_REPORT_FILE))).contains("Unmodeled anchors", "module:delta");
    }

    @Test
    void aRedundantDeclaredExclusionWarnsAndTheAlternativeGroupDerivesItsExclusionAndComposeMappings() throws Exception {
        FeatureExtractionInputs xorManifest = withManifest(FIXTURE_INPUTS.resolve("manifest-with-redundant-xor-constraint.yml"));

        runScan(xorManifest);
        ModelStageService.Summary summary = new ModelStageService(OBJECT_MAPPER).run(xorManifest, this::fixtureSource);

        assertThat(summary.constraintCount()).isEqualTo(1);
        FeatureModel generatedModel = OBJECT_MAPPER.readValue(Files.readAllBytes(layout.modelDirectory().resolve(ExtractionArtifactStore.GENERATED_MODEL_FILE)),
                FeatureModel.class);
        assertThat(generatedModel.constraints()).singleElement().satisfies(constraint -> {
            assertThat(constraint.id()).isEqualTo("mysql-excludes-postgresql");
            assertThat(constraint.type()).isEqualTo("excludes");
            assertThat(constraint.description()).isEqualTo("A deployment selects exactly one option of Database; MySQL and PostgreSQL are mutually exclusive.");
        });
        FeatureNode mysql = generatedModel.features().stream().filter(feature -> feature.id().equals("mysql")).findFirst().orElseThrow();
        assertThat(mysql.category()).isEqualTo("technical");
        assertThat(mysql.kind()).isEqualTo("feature");
        assertThat(mysql.artifactMappings()).singleElement().satisfies(mapping -> {
            assertThat(mapping.target()).isEqualTo("docker-compose.override.yml");
            assertThat(mapping.path()).isEqualTo("db-group.composeFile");
            assertThat(mapping.valueWhenSelected().asString()).isEqualTo("docker/mysql.yml");
        });
        List<ReportItem> diagnostics = List.of(OBJECT_MAPPER.readValue(Files.readAllBytes(layout.modelDirectory().resolve(ExtractionArtifactStore.MODEL_DIAGNOSTICS_FILE)),
                ReportItem[].class));
        assertThat(diagnostics).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_MANIFEST_CONSTRAINT_REDUNDANT);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_WARNING);
            assertThat(item.subject()).isEqualTo("mysql-excludes-postgresql");
        });
        assertThat(summary.modelIntegrityValid()).isTrue();
    }

    @Test
    void aTechnicalProfileMemberDerivesItsProfileTokensAndEmitsItsConfirmedKeys() throws Exception {
        FeatureExtractionInputs technicalManifest = withManifest(FIXTURE_INPUTS.resolve("manifest-with-technical-confirmation.yml"));

        runScan(technicalManifest);
        new ModelStageService(OBJECT_MAPPER).run(technicalManifest, this::fixtureSource);

        FeatureModel generatedModel = OBJECT_MAPPER.readValue(Files.readAllBytes(layout.modelDirectory().resolve(ExtractionArtifactStore.GENERATED_MODEL_FILE)),
                FeatureModel.class);
        FeatureNode ciOne = generatedModel.features().stream().filter(feature -> feature.id().equals("ci-one")).findFirst().orElseThrow();
        assertThat(ciOne.requiresCapabilities()).as("technical members carry no capabilities").isEmpty();
        assertThat(ciOne.artifactMappings()).extracting(mapping -> mapping.target() + ":" + mapping.path()).containsExactly(".env:SPRING_PROFILES_ACTIVE",
                "application-feature-model.yml:artemis.continuous-integration.concurrent-build-size", "application-feature-model.yml:artemis.continuous-integration.ci-token");
        assertThat(ciOne.artifactMappings().getFirst().valueWhenSelected().asString()).isEqualTo("cione,agentx");
        assertThat(ciOne.artifactMappings().getLast().secret()).isTrue();
        FeatureNode cioneStatus = generatedModel.features().stream().filter(feature -> feature.id().equals("ci-one/build/cione-status")).findFirst().orElseThrow();
        assertThat(cioneStatus.kind()).isEqualTo("sub-feature");
        assertThat(cioneStatus.visibleTo()).as("sub-features of technical owners are maintainer-only").containsExactly("maintainer");
        assertThat(cioneStatus.source().springProfile()).isEqualTo("cione");
        assertThat(cioneStatus.source().usageLabel()).isEqualTo("build/cione-status");
    }

    @Test
    void aMissingCheckoutConfigurationFailsBeforeAnyRunIdentityExists() throws Exception {
        runPipeline();
        assertThat(layout.snapshotDirectory()).isDirectory();
        FeatureExtractionInputs checkoutlessInputs = new FeatureExtractionInputs(null, inputs.manifestFile(), inputs.authoredWorkflowFile(),
                inputs.runtimeImageFile(), outputRoot);

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
    void aTamperedFeatureUsagePayloadIsRejectedByTheScanEnvelope() throws Exception {
        runPipeline();
        Files.writeString(layout.scanDirectory().resolve(ExtractionArtifactStore.FEATURE_USAGES_FILE), "[]\n");

        assertThatThrownBy(() -> new ModelStageService(OBJECT_MAPPER).run(inputs, this::fixtureSource)).isInstanceOf(ExtractionArtifactException.class)
                .hasMessageContaining(ExtractionArtifactStore.FEATURE_USAGES_FILE);

        assertThat(layout.modelDirectory()).doesNotExist();
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
        return new FeatureExtractionInputs(FIXTURE_PATH, manifestFile, inputs.authoredWorkflowFile(), inputs.runtimeImageFile(), inputs.outputRoot());
    }

    /**
     * Creates inputs that read another authored workflow.
     *
     * @param workflowFile authored workflow to prepare.
     * @return inputs pointing at the given workflow.
     */
    private FeatureExtractionInputs withWorkflow(Path workflowFile) {
        return new FeatureExtractionInputs(FIXTURE_PATH, inputs.manifestFile(), workflowFile, inputs.runtimeImageFile(), inputs.outputRoot());
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
        draft.withArrayProperty("selects").add("alpha");
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
