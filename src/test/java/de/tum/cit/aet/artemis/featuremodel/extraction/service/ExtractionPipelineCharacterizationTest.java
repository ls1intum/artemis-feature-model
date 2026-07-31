package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GuidedWorkflowValidationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ModelDiffReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecision;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedDecisionOption;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowMetadata;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflowStep;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.UseCaseTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Characterizes the complete extraction pipeline over the mini-Artemis fixture: scan, manifest curation, generated
 * model and catalog assembly, validation, and snapshot publication. The asserted values are the parity contract of the
 * orchestration refactor — the pipeline may be split into separate commands, but these observable outputs must not
 * change until a work package deliberately changes their semantics.
 */
class ExtractionPipelineCharacterizationTest {

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    private static final Path FIXTURE_MANIFEST_PATH = Path.of("src/test/resources/extraction/mini-artemis-manifest.yml");

    private static final String FIXED_TIMESTAMP = "2026-01-01T00:00:00Z";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static FeatureExtractionService.Outcome outcome;

    @TempDir
    private Path outputRoot;

    @BeforeAll
    static void runPipeline() throws Exception {
        FeatureScopeManifest manifest = new FeatureManifestLoader().load(FIXTURE_MANIFEST_PATH);
        outcome = new FeatureExtractionService(OBJECT_MAPPER).extract(new LocalArtemisSourceRepository(FIXTURE_PATH), ExtractionTestModels.fixtureCuratedModel(),
                ExtractionTestModels.fixtureCatalog(), manifest, fixtureWorkflow(), fixtureProfile());
    }

    @Test
    void classifiesEveryFixtureCandidate() {
        assertThat(outcome.candidates()).hasSize(18);
        assertThat(outcome.relationCandidates()).hasSize(2);
        assertThat(outcome.report().curation().stateCounts()).containsEntry("include", 1).containsEntry("exclude", 17).containsEntry("pending", 0);
        assertThat(outcome.includedFeatures()).extracting(ResolvedFeatureScope::id).containsExactly("alpha-feature");
    }

    @Test
    void assemblesGeneratedModelCatalogAndDiff() {
        assertThat(outcome.generatedModel().features()).extracting(FeatureNode::id).containsExactly("fixture-root", "alpha-feature");
        assertThat(outcome.generatedModel().relations()).extracting(relation -> relation.parentId() + "->" + relation.childId())
                .containsExactly("fixture-root->alpha-feature");
        assertThat(outcome.generatedModel().constraints()).isEmpty();
        assertThat(outcome.generatedCatalog().keys()).extracting(ArtemisConfigKeyCatalog.CatalogKey::key).containsExactly("artemis.alpha.enabled");
        assertThat(outcome.modelDiff().entries()).isNotEmpty();
        assertThat(outcome.modelDiff().classificationCounts()).containsKeys(ModelDiffReport.CLASS_INTENTIONAL_CURATION, ModelDiffReport.CLASS_ARTEMIS_DRIFT,
                ModelDiffReport.CLASS_MISSING_MANIFEST_ENTRY, ModelDiffReport.CLASS_EXTRACTOR_GAP);
        assertThat(outcome.artifactValidation().snapshotEligible()).isTrue();
        assertThat(outcome.guidedWorkflowValidation().status()).isEqualTo(GuidedWorkflowValidationReport.STATUS_PASS);
    }

    @Test
    void reportsDriftAndCurationDiagnosticsTogether() {
        assertThat(reportCodes()).contains(ReportItem.CODE_CURATED_ANCHOR_MISSING, ReportItem.CODE_CURATED_EVIDENCE_STALE,
                ReportItem.CODE_UNANCHORED_CURATED_FEATURE, ReportItem.CODE_FE_BE_MIRROR_MISMATCH, ReportItem.CODE_CONFIG_KEY_CATALOG_DRIFT,
                ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY);
        assertThat(reportCodes()).doesNotContain(ReportItem.CODE_EXTRACTOR_ERROR, ReportItem.CODE_NEW_CANDIDATE_NOT_IN_MODEL);
        assertThat(outcome.report().codes()).containsKey(ReportItem.CODE_EXTRACTOR_ERROR);
        assertThat(outcome.report().artemisCommit()).isEqualTo("unknown");
    }

    @Test
    void writesEveryPipelineArtifactAndPublishesTheSnapshot() throws Exception {
        Path outputDirectory = outputRoot.resolve("unknown");
        ExtractionOutputWriter writer = new ExtractionOutputWriter(OBJECT_MAPPER);
        writer.writeAll(outputDirectory, scanMetadata(), outcome);

        boolean published = writer.writeSnapshot(outputDirectory, outcome, workflowBytes(), FIXTURE_PATH.toString(), "unknown");

        assertThat(published).isTrue();
        for (String fileName : List.of(ExtractionOutputWriter.SCAN_METADATA_FILE, ExtractionOutputWriter.FEATURE_CANDIDATES_FILE,
                ExtractionOutputWriter.EVIDENCE_FILE, ExtractionOutputWriter.RELATION_CANDIDATES_FILE, ExtractionOutputWriter.EXTRACTION_REPORT_FILE,
                ExtractionOutputWriter.GENERATED_MODEL_FILE, ExtractionOutputWriter.GENERATED_CATALOG_FILE, ExtractionOutputWriter.MODEL_DIFF_FILE,
                ExtractionOutputWriter.GUIDED_VALIDATION_FILE)) {
            assertThat(outputDirectory.resolve(fileName)).as("pipeline artifact %s", fileName).isRegularFile();
        }
        Path snapshotDirectory = outputDirectory.resolve(ExtractionOutputWriter.SNAPSHOT_DIRECTORY);
        for (String fileName : List.of("feature-model.json", "guided-workflow.json", "metadata.json", "checksum.txt")) {
            assertThat(snapshotDirectory.resolve(fileName)).as("snapshot file %s", fileName).isRegularFile();
        }
    }

    /**
     * Collects the distinct diagnostic codes of the characterized run.
     *
     * @return report codes present in the run.
     */
    private List<String> reportCodes() {
        return outcome.report().items().stream().map(ReportItem::code).distinct().toList();
    }

    /**
     * Creates the scan metadata of the characterized run with fixed timestamps.
     *
     * @return scan metadata payload.
     */
    private ScanMetadata scanMetadata() {
        return new ScanMetadata(FeatureExtractionService.EXTRACTOR_VERSION, FIXTURE_PATH.toString(), "unknown", null, FIXED_TIMESTAMP, FIXED_TIMESTAMP,
                outcome.candidates().size(), outcome.evidence().size(), outcome.relationCandidates().size(), outcome.report().items().size());
    }

    /**
     * Serializes the fixture workflow as the payload the snapshot embeds.
     *
     * @return serialized fixture workflow bytes.
     */
    private byte[] workflowBytes() {
        return OBJECT_MAPPER.writeValueAsString(fixtureWorkflow()).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Creates a guided workflow that covers the single included fixture feature.
     *
     * @return fixture guided workflow.
     */
    private static GuidedWorkflow fixtureWorkflow() {
        GuidedDecisionOption option = new GuidedDecisionOption("enable-alpha", "Alpha", "Fixture option.", List.of("alpha-feature"), List.of(), null, null,
                List.of("Outcome."), List.of(), List.of(), List.of());
        GuidedWorkflowMetadata metadata = new GuidedWorkflowMetadata("fixture-workflow", "Fixture Workflow", "0.0.1", null, null, "custom");
        UseCaseTemplate template = new UseCaseTemplate("custom", "Custom", "Fixture template.", List.of(), List.of(), List.of(), List.of(), List.of());
        GuidedWorkflowStep step = new GuidedWorkflowStep("selection", "Selection", 1, "Fixture step.",
                List.of(new GuidedDecision("decision", "Question?", "Fixture decision.", "multiple", List.of(option))));
        return new GuidedWorkflow(metadata, List.of(template), List.of(step), List.of());
    }

    /**
     * Creates the deployment profile used for the capability cross-check.
     *
     * @return fixture deployment profile.
     */
    private static DeploymentProfile fixtureProfile() {
        return new DeploymentProfile("fixture-profile", "Fixture Profile", "1.0.0", "published", List.of("maintainer"), List.of(), null, null);
    }
}
