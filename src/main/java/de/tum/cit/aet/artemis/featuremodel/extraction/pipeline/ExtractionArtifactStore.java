package de.tum.cit.aet.artemis.featuremodel.extraction.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.ArtifactDirectoryOperations;
import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.ExtractionJsonWriter;
import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.Sha256Digest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotation;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefaults;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedSourceFacts;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionStage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GuidedWorkflowValidationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ManifestConformanceReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ModelResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.RelationCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReleaseDeltaReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.WorkflowResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.report.ExtractionHtmlReportRenderer;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads and writes the intermediate artifacts and digest-verified envelopes of the extraction stages. Deterministic
 * JSON formatting, SHA-256 calculation, and recursive directory lifecycle mechanics are delegated to the narrowly
 * scoped {@code extraction.artifact} components. Apart from the timestamps in {@code scan-metadata.json}, two runs on
 * the same commit produce byte-identical payload files.
 *
 * <p>
 * Every stage writes an envelope next to its payload that records the extractor version, the Artemis commit, and the
 * digests of the inputs it consumed. A downstream stage loads its input only through this store, so a scan from a
 * different commit, a payload file edited after its stage ran, or a model assembled from another manifest is rejected
 * instead of silently composed into a snapshot.
 */
public class ExtractionArtifactStore {

    public static final String SCAN_METADATA_FILE = "scan-metadata.json";

    public static final String FEATURE_CANDIDATES_FILE = "feature-candidates.json";

    public static final String EVIDENCE_FILE = "evidence.json";

    public static final String RELATION_CANDIDATES_FILE = "relation-candidates.json";

    public static final String ANNOTATIONS_FILE = "annotations.json";

    public static final String CONFIG_DEFAULTS_FILE = "config-defaults.json";

    public static final String SCAN_DIAGNOSTICS_FILE = "scan-diagnostics.json";

    public static final String SCAN_RESULT_FILE = "scan-result.json";

    public static final String GENERATED_MODEL_FILE = "generated-feature-model.json";

    public static final String GENERATED_CATALOG_FILE = "generated-config-key-catalog.json";

    public static final String MANIFEST_CONFORMANCE_FILE = "manifest-conformance-report.json";

    public static final String MODEL_DIAGNOSTICS_FILE = "model-diagnostics.json";

    public static final String MODEL_RESULT_FILE = "model-result.json";

    public static final String PREPARED_WORKFLOW_FILE = "guided-workflow.json";

    public static final String GUIDED_VALIDATION_FILE = "guided-workflow-validation.json";

    public static final String WORKFLOW_DIAGNOSTICS_FILE = "workflow-diagnostics.json";

    public static final String WORKFLOW_RESULT_FILE = "workflow-result.json";

    public static final String EXTRACTION_REPORT_FILE = "extraction-report.json";

    public static final String HTML_REPORT_FILE = "index.html";

    public static final String RELEASE_DELTA_REPORT_FILE = "release-delta-report.json";

    private static final String LINE_FEED = "\n";

    private final ObjectMapper objectMapper;

    private final ExtractionJsonWriter jsonWriter;

    private final ArtifactDirectoryOperations directoryOperations;

    /**
     * Creates the store with the shared Jackson mapper.
     *
     * @param objectMapper Jackson mapper used for serialization.
     */
    public ExtractionArtifactStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        jsonWriter = new ExtractionJsonWriter(objectMapper);
        directoryOperations = new ArtifactDirectoryOperations();
    }

    /**
     * Scan artifacts loaded from a previous scan command.
     *
     * @param result scan envelope.
     * @param metadata scan metadata.
     * @param outcome source facts of the scan.
     */
    public record LoadedScan(ScanResult result, ScanMetadata metadata, ExtractedSourceFacts outcome) {
    }

    /**
     * Model artifacts loaded from a previous model assembly command.
     *
     * @param result model envelope.
     * @param generatedModel generated feature model.
     * @param generatedCatalog generated config-key catalog.
     * @param items model assembly diagnostics.
     */
    public record LoadedModel(ModelResult result, FeatureModel generatedModel, ArtemisConfigKeyCatalog generatedCatalog, List<ReportItem> items) {
    }

    /**
     * Workflow artifacts loaded from a previous workflow preparation command.
     *
     * @param result workflow envelope.
     * @param preparedWorkflow bytes of the prepared guided workflow.
     * @param items workflow preparation diagnostics.
     */
    public record LoadedWorkflow(WorkflowResult result, byte[] preparedWorkflow, List<ReportItem> items) {
    }

    /**
     * Removes the output of a stage and of every stage derived from it, so a rerun cannot leave stale downstream
     * artifacts behind.
     *
     * @param layout output layout of this run.
     * @param stage stage that is about to write.
     * @throws IOException if a directory cannot be removed.
     */
    public void invalidateFrom(ExtractionArtifactLayout layout, ExtractionStage stage) throws IOException {
        directoryOperations.invalidateFrom(layout, stage);
    }

    /**
     * Writes the scan artifacts and their envelope.
     *
     * @param layout output layout of this run.
     * @param metadata scan metadata payload.
     * @param outcome source facts of the scan.
     * @return the written scan envelope.
     * @throws IOException if a file cannot be written.
     */
    public ScanResult writeScan(ExtractionArtifactLayout layout, ScanMetadata metadata, ExtractedSourceFacts outcome) throws IOException {
        Path directory = Files.createDirectories(layout.scanDirectory());
        jsonWriter.write(directory.resolve(SCAN_METADATA_FILE), metadata);
        jsonWriter.write(directory.resolve(FEATURE_CANDIDATES_FILE), outcome.candidates());
        jsonWriter.write(directory.resolve(EVIDENCE_FILE), outcome.evidence());
        jsonWriter.write(directory.resolve(RELATION_CANDIDATES_FILE), outcome.relationCandidates());
        jsonWriter.write(directory.resolve(ANNOTATIONS_FILE), outcome.annotations());
        jsonWriter.write(directory.resolve(CONFIG_DEFAULTS_FILE), outcome.configDefaults());
        jsonWriter.write(directory.resolve(SCAN_DIAGNOSTICS_FILE), outcome.items());

        Map<String, String> payloadDigests = digestsOf(directory,
                List.of(FEATURE_CANDIDATES_FILE, EVIDENCE_FILE, RELATION_CANDIDATES_FILE, ANNOTATIONS_FILE, CONFIG_DEFAULTS_FILE, SCAN_DIAGNOSTICS_FILE));
        ScanResult result = new ScanResult(ScanResult.CURRENT_SCHEMA_VERSION, ScanResult.EXTRACTOR_VERSION, metadata.artemisCommit(), payloadDigests,
                combinedDigest(payloadDigests));
        jsonWriter.write(directory.resolve(SCAN_RESULT_FILE), result);
        return result;
    }

    /**
     * Loads the scan artifacts of this run and verifies that they still match their envelope.
     *
     * @param layout output layout of this run.
     * @param expectedArtemisCommit commit the consuming stage expects.
     * @return loaded scan artifacts.
     * @throws IOException if a file cannot be read.
     * @throws ExtractionArtifactException if the scan is missing, stale, or from another commit or extractor version.
     */
    public LoadedScan readScan(ExtractionArtifactLayout layout, String expectedArtemisCommit) throws IOException {
        Path directory = layout.scanDirectory();
        ScanResult result = readJson(directory.resolve(SCAN_RESULT_FILE), ScanResult.class, "scan");
        requireSchemaVersion("scan", result.schemaVersion(), ScanResult.CURRENT_SCHEMA_VERSION);
        requireEqual("scan", "extractor version", result.extractorVersion(), ScanResult.EXTRACTOR_VERSION);
        requireEqual("scan", "Artemis commit", result.artemisCommit(), expectedArtemisCommit);
        verifyPayloadDigests(directory, result.payloadDigests());

        ExtractedSourceFacts outcome = new ExtractedSourceFacts(
                List.of(readJson(directory.resolve(FEATURE_CANDIDATES_FILE), FeatureCandidate[].class, "scan")),
                List.of(readJson(directory.resolve(EVIDENCE_FILE), EvidenceItem[].class, "scan")),
                List.of(readJson(directory.resolve(RELATION_CANDIDATES_FILE), RelationCandidate[].class, "scan")),
                List.of(readJson(directory.resolve(ANNOTATIONS_FILE), ExtractedAnnotation[].class, "scan")),
                readJson(directory.resolve(CONFIG_DEFAULTS_FILE), ExtractedConfigurationDefaults.class, "scan"),
                List.of(readJson(directory.resolve(SCAN_DIAGNOSTICS_FILE), ReportItem[].class, "scan")));
        return new LoadedScan(result, readJson(directory.resolve(SCAN_METADATA_FILE), ScanMetadata.class, "scan"), outcome);
    }

    /**
     * Writes the generated model artifacts and their envelope.
     *
     * @param layout output layout of this run.
     * @param outcome generated artifacts of the model assembly; a non-conformant outcome writes diagnostics only.
     * @param scanDigest payload digest of the consumed scan.
     * @param manifestDigest digest of the consumed scope manifest.
     * @param artemisCommit resolved commit of the run.
     * @return the written model envelope.
     * @throws IOException if a file cannot be written.
     */
    public ModelResult writeModel(ExtractionArtifactLayout layout, ModelAssemblyOutcome outcome, String scanDigest, String manifestDigest, String artemisCommit)
            throws IOException {
        Path directory = Files.createDirectories(layout.modelDirectory());
        jsonWriter.write(directory.resolve(MODEL_DIAGNOSTICS_FILE), outcome.items());
        String generatedModelDigest = null;
        String generatedCatalogDigest = null;
        if (outcome.conformance().conformant()) {
            jsonWriter.write(directory.resolve(GENERATED_MODEL_FILE), outcome.generatedModel());
            jsonWriter.write(directory.resolve(GENERATED_CATALOG_FILE), outcome.generatedCatalog());
            generatedModelDigest = Sha256Digest.of(directory.resolve(GENERATED_MODEL_FILE));
            generatedCatalogDigest = Sha256Digest.of(directory.resolve(GENERATED_CATALOG_FILE));
        }

        List<String> featureIds = outcome.generatedModel() == null ? List.of() : outcome.generatedModel().features().stream().map(feature -> feature.id()).toList();
        List<String> relationIds = outcome.generatedModel() == null ? List.of()
                : outcome.generatedModel().relations().stream().map(relation -> relation.parentId() + "->" + relation.childId()).toList();
        List<String> constraintIds = outcome.generatedModel() == null ? List.of()
                : outcome.generatedModel().constraints().stream().map(constraint -> constraint.id()).toList();
        List<ReportItem> generatedOutputFindings = outcome.items().stream()
                .filter(item -> ReportItem.CODE_GENERATED_MODEL_CONFORMANCE_MISMATCH.equals(item.code())).toList();
        String status = outcome.conformance().conformant() && outcome.generatedOutputConformant() ? ManifestConformanceReport.STATUS_PASS
                : ManifestConformanceReport.STATUS_FAIL;
        ManifestConformanceReport conformanceReport = new ManifestConformanceReport(ManifestConformanceReport.CURRENT_SCHEMA_VERSION, status, artemisCommit,
                manifestDigest, outcome.conformance(), outcome.curation(), featureIds, relationIds, constraintIds, generatedModelDigest,
                generatedOutputFindings);
        jsonWriter.write(directory.resolve(MANIFEST_CONFORMANCE_FILE), conformanceReport);

        ModelResult result = new ModelResult(ModelResult.CURRENT_SCHEMA_VERSION, ScanResult.EXTRACTOR_VERSION, artemisCommit, scanDigest, manifestDigest,
                generatedModelDigest, generatedCatalogDigest, outcome.generatedOutputConformant(), outcome.modelIntegrityValid(), outcome.deliveryEligible(),
                outcome.conformance(), outcome.curation());
        jsonWriter.write(directory.resolve(MODEL_RESULT_FILE), result);
        return result;
    }

    /**
     * Loads the generated model artifacts of this run and verifies that they still match their inputs.
     *
     * @param layout output layout of this run.
     * @param expectedArtemisCommit commit the consuming stage expects.
     * @param expectedScanDigest payload digest of the current scan.
     * @param expectedManifestDigest digest of the current scope manifest.
     * @return loaded model artifacts.
     * @throws IOException if a file cannot be read.
     * @throws ExtractionArtifactException if the model is missing or was assembled from other inputs.
     */
    public LoadedModel readModel(ExtractionArtifactLayout layout, String expectedArtemisCommit, String expectedScanDigest, String expectedManifestDigest)
            throws IOException {
        Path directory = layout.modelDirectory();
        ModelResult result = readJson(directory.resolve(MODEL_RESULT_FILE), ModelResult.class, "model");
        requireSchemaVersion("model", result.schemaVersion(), ModelResult.CURRENT_SCHEMA_VERSION);
        requireEqual("model", "extractor version", result.extractorVersion(), ScanResult.EXTRACTOR_VERSION);
        requireEqual("model", "Artemis commit", result.artemisCommit(), expectedArtemisCommit);
        requireEqual("model", "scan digest", result.scanDigest(), expectedScanDigest);
        requireEqual("model", "manifest digest", result.manifestDigest(), expectedManifestDigest);
        if (!result.conformance().conformant()) {
            throw new ExtractionArtifactException("The model stage found the manifest incomplete for this scan and produced no model: "
                    + result.conformance().describeFindings() + ".");
        }
        if (!result.generatedOutputConformant()) {
            throw new ExtractionArtifactException("The generated model does not conform to the resolved manifest semantics; rerun the model command.");
        }
        requireEqual("model", "generated model digest", Sha256Digest.of(directory.resolve(GENERATED_MODEL_FILE)), result.generatedModelDigest());
        requireEqual("model", "generated catalog digest", Sha256Digest.of(directory.resolve(GENERATED_CATALOG_FILE)), result.generatedCatalogDigest());

        FeatureModel generatedModel = readJson(directory.resolve(GENERATED_MODEL_FILE), FeatureModel.class, "model");
        ArtemisConfigKeyCatalog generatedCatalog = readJson(directory.resolve(GENERATED_CATALOG_FILE), ArtemisConfigKeyCatalog.class, "model");
        return new LoadedModel(result, generatedModel, generatedCatalog,
                List.of(readJson(directory.resolve(MODEL_DIAGNOSTICS_FILE), ReportItem[].class, "model")));
    }

    /**
     * Writes the prepared guided workflow, its validation report, and their envelope.
     *
     * @param layout output layout of this run.
     * @param validation workflow validation result.
     * @param authoredWorkflowBytes bytes of the authored guided workflow.
     * @param generatedModelDigest digest of the generated model the workflow was validated against.
     * @param artemisCommit resolved commit of the run.
     * @return the written workflow envelope.
     * @throws IOException if a file cannot be written.
     */
    public WorkflowResult writeWorkflow(ExtractionArtifactLayout layout, WorkflowValidationOutcome validation, byte[] authoredWorkflowBytes,
            String generatedModelDigest, String artemisCommit) throws IOException {
        Path directory = Files.createDirectories(layout.workflowDirectory());
        Files.write(directory.resolve(PREPARED_WORKFLOW_FILE), authoredWorkflowBytes);
        jsonWriter.write(directory.resolve(GUIDED_VALIDATION_FILE), validation.guidedValidation());
        jsonWriter.write(directory.resolve(WORKFLOW_DIAGNOSTICS_FILE), validation.items());

        WorkflowResult result = new WorkflowResult(WorkflowResult.CURRENT_SCHEMA_VERSION, ScanResult.EXTRACTOR_VERSION, artemisCommit, generatedModelDigest,
                Sha256Digest.of(authoredWorkflowBytes), Sha256Digest.of(directory.resolve(PREPARED_WORKFLOW_FILE)), validation.workflowIntegrityValid(),
                validation.deliveryEligible());
        jsonWriter.write(directory.resolve(WORKFLOW_RESULT_FILE), result);
        return result;
    }

    /**
     * Loads the prepared workflow artifacts of this run and verifies that they still match their inputs.
     *
     * @param layout output layout of this run.
     * @param expectedArtemisCommit commit the consuming stage expects.
     * @param expectedModelDigest digest of the current generated model.
     * @param expectedAuthoredWorkflowDigest digest of the current authored guided workflow.
     * @return loaded workflow artifacts.
     * @throws IOException if a file cannot be read.
     * @throws ExtractionArtifactException if the prepared workflow is missing or was prepared from other inputs.
     */
    public LoadedWorkflow readWorkflow(ExtractionArtifactLayout layout, String expectedArtemisCommit, String expectedModelDigest, String expectedAuthoredWorkflowDigest)
            throws IOException {
        Path directory = layout.workflowDirectory();
        WorkflowResult result = readJson(directory.resolve(WORKFLOW_RESULT_FILE), WorkflowResult.class, "workflow");
        requireSchemaVersion("workflow", result.schemaVersion(), WorkflowResult.CURRENT_SCHEMA_VERSION);
        requireEqual("workflow", "extractor version", result.extractorVersion(), ScanResult.EXTRACTOR_VERSION);
        requireEqual("workflow", "Artemis commit", result.artemisCommit(), expectedArtemisCommit);
        requireEqual("workflow", "generated model digest", result.generatedModelDigest(), expectedModelDigest);
        requireEqual("workflow", "authored workflow digest", result.authoredWorkflowDigest(), expectedAuthoredWorkflowDigest);
        Path preparedWorkflow = directory.resolve(PREPARED_WORKFLOW_FILE);
        requireEqual("workflow", "prepared workflow digest", Sha256Digest.of(preparedWorkflow), result.preparedWorkflowDigest());

        return new LoadedWorkflow(result, Files.readAllBytes(preparedWorkflow),
                List.of(readJson(directory.resolve(WORKFLOW_DIAGNOSTICS_FILE), ReportItem[].class, "workflow")));
    }

    /**
     * Writes the consolidated extraction report.
     *
     * @param layout output layout of this run.
     * @param report consolidated report.
     * @throws IOException if the report cannot be written.
     */
    public void writeReport(ExtractionArtifactLayout layout, ExtractionReport report) throws IOException {
        Path directory = Files.createDirectories(layout.reportDirectory());
        jsonWriter.write(directory.resolve(EXTRACTION_REPORT_FILE), report);
        jsonWriter.write(directory.resolve(RELEASE_DELTA_REPORT_FILE), ReleaseDeltaReport.noBaseline());
        Files.write(directory.resolve(HTML_REPORT_FILE), new ExtractionHtmlReportRenderer().render(report));
    }

    /**
     * Reads the guided workflow validation report of this run.
     *
     * @param layout output layout of this run.
     * @return guided workflow validation report.
     * @throws IOException if the report cannot be read.
     * @throws ExtractionArtifactException if the report is missing.
     */
    public GuidedWorkflowValidationReport readGuidedValidation(ExtractionArtifactLayout layout) throws IOException {
        return readJson(layout.workflowDirectory().resolve(GUIDED_VALIDATION_FILE), GuidedWorkflowValidationReport.class, "workflow");
    }

    /**
     * Reads and parses one artifact file.
     *
     * @param <T> payload type.
     * @param file artifact file.
     * @param type payload class.
     * @param stageName stage the artifact belongs to, used in failure messages.
     * @return parsed payload.
     * @throws IOException if the file cannot be read.
     * @throws ExtractionArtifactException if the file does not exist.
     */
    private <T> T readJson(Path file, Class<T> type, String stageName) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new ExtractionArtifactException(
                    "The " + stageName + " stage did not produce " + file.getFileName() + "; run the upstream command before this one.");
        }
        return objectMapper.readValue(Files.readAllBytes(file), type);
    }

    /**
     * Verifies that every recorded payload file still has the digest its stage wrote.
     *
     * @param directory stage directory.
     * @param payloadDigests recorded digests per file name.
     * @throws IOException if a payload file cannot be read.
     * @throws ExtractionArtifactException if a payload file is missing or changed.
     */
    private void verifyPayloadDigests(Path directory, Map<String, String> payloadDigests) throws IOException {
        for (Map.Entry<String, String> payload : payloadDigests.entrySet()) {
            Path file = directory.resolve(payload.getKey());
            if (!Files.isRegularFile(file)) {
                throw new ExtractionArtifactException("The scan payload file " + payload.getKey() + " is missing; rerun the scan command.");
            }
            requireEqual("scan", "digest of " + payload.getKey(), Sha256Digest.of(file), payload.getValue());
        }
    }

    /**
     * Computes the digests of a stage's payload files.
     *
     * @param directory stage directory.
     * @param fileNames payload file names.
     * @return digests sorted by file name.
     * @throws IOException if a payload file cannot be read.
     */
    private Map<String, String> digestsOf(Path directory, List<String> fileNames) throws IOException {
        Map<String, String> digests = new TreeMap<>();
        for (String fileName : fileNames) {
            digests.put(fileName, Sha256Digest.of(directory.resolve(fileName)));
        }
        return digests;
    }

    /**
     * Derives one digest identifying a complete set of payload digests.
     *
     * @param payloadDigests digests per file name.
     * @return digest over the sorted file name and digest pairs.
     */
    private String combinedDigest(Map<String, String> payloadDigests) {
        StringBuilder combined = new StringBuilder();
        payloadDigests.forEach((fileName, digest) -> combined.append(fileName).append('=').append(digest).append(LINE_FEED));
        return Sha256Digest.of(combined.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Rejects an artifact written by another schema version.
     *
     * @param stageName stage the artifact belongs to.
     * @param actual schema version found in the artifact.
     * @param expected schema version this code understands.
     * @throws ExtractionArtifactException if the versions differ.
     */
    private void requireSchemaVersion(String stageName, int actual, int expected) {
        if (actual != expected) {
            throw new ExtractionArtifactException(
                    "The " + stageName + " artifact uses schema version " + actual + " but this extractor expects " + expected + "; rerun the upstream command.");
        }
    }

    /**
     * Rejects an artifact whose recorded input no longer matches the current one.
     *
     * @param stageName stage the artifact belongs to.
     * @param aspect input that differs, used in the failure message.
     * @param actual value recorded in or computed from the artifact.
     * @param expected value the consuming stage requires.
     * @throws ExtractionArtifactException if the values differ.
     */
    private void requireEqual(String stageName, String aspect, String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new ExtractionArtifactException("The " + stageName + " artifact was produced with " + aspect + " '" + actual + "' but this run requires '"
                    + expected + "'; rerun the upstream command.");
        }
    }
}
