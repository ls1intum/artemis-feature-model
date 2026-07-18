package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.SnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanMetadata;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the five extraction outputs as deterministic pretty-printed JSON: fixed field order from the domain records,
 * two-space indentation, and line feed separators independent of the operating system. Apart from the timestamps in
 * {@code scan-metadata.json}, two runs on the same commit produce byte-identical files.
 */
public class ExtractionOutputWriter {

    public static final String SCAN_METADATA_FILE = "scan-metadata.json";

    public static final String FEATURE_CANDIDATES_FILE = "feature-candidates.json";

    public static final String EVIDENCE_FILE = "evidence.json";

    public static final String RELATION_CANDIDATES_FILE = "relation-candidates.json";

    public static final String EXTRACTION_REPORT_FILE = "extraction-report.json";

    public static final String GENERATED_MODEL_FILE = "generated-feature-model.json";

    public static final String GENERATED_CATALOG_FILE = "generated-config-key-catalog.json";

    public static final String MODEL_DIFF_FILE = "model-diff-report.json";

    public static final String GUIDED_VALIDATION_FILE = "guided-workflow-validation.json";

    public static final String SNAPSHOT_DIRECTORY = "snapshot";

    private static final String SNAPSHOT_MODEL_FILE = "feature-model.json";

    private static final String SNAPSHOT_WORKFLOW_FILE = "guided-workflow.json";

    private static final String SNAPSHOT_METADATA_FILE = "metadata.json";

    private static final String SNAPSHOT_CHECKSUM_FILE = "checksum.txt";

    private static final String LINE_FEED = "\n";

    private final ObjectMapper objectMapper;

    private final DefaultPrettyPrinter prettyPrinter;

    /**
     * Creates the writer with the shared Jackson mapper.
     *
     * @param objectMapper Jackson mapper used for serialization.
     */
    public ExtractionOutputWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        DefaultIndenter indenter = new DefaultIndenter("  ", LINE_FEED);
        this.prettyPrinter = new DefaultPrettyPrinter().withObjectIndenter(indenter).withArrayIndenter(indenter);
    }

    /**
     * Writes the outputs of one extraction run into the output directory, replacing previous outputs. The generated
     * model, catalog, diff, and guided validation files are written only when the run produced them.
     *
     * @param outputDirectory directory for this scan, typically {@code build/feature-extraction/<commit>}.
     * @param metadata scan metadata payload.
     * @param outcome extraction outcome with candidates, evidence, relations, the report, and generation outputs.
     * @throws IOException if a file cannot be written.
     */
    public void writeAll(Path outputDirectory, ScanMetadata metadata, FeatureExtractionService.Outcome outcome) throws IOException {
        Files.createDirectories(outputDirectory);
        writeJson(outputDirectory.resolve(SCAN_METADATA_FILE), metadata);
        writeJson(outputDirectory.resolve(FEATURE_CANDIDATES_FILE), outcome.candidates());
        writeJson(outputDirectory.resolve(EVIDENCE_FILE), outcome.evidence());
        writeJson(outputDirectory.resolve(RELATION_CANDIDATES_FILE), outcome.relationCandidates());
        writeJson(outputDirectory.resolve(EXTRACTION_REPORT_FILE), outcome.report());
        if (outcome.generatedModel() != null) {
            writeJson(outputDirectory.resolve(GENERATED_MODEL_FILE), outcome.generatedModel());
            writeJson(outputDirectory.resolve(GENERATED_CATALOG_FILE), outcome.generatedCatalog());
            writeJson(outputDirectory.resolve(MODEL_DIFF_FILE), outcome.modelDiff());
            writeJson(outputDirectory.resolve(GUIDED_VALIDATION_FILE), outcome.guidedWorkflowValidation());
        }
    }

    /**
     * Writes the importable snapshot folder for the generated model: the model, a byte copy of the bundled lean
     * guided workflow, traceability metadata, and the model checksum — the exact layout
     * {@code POST /api/feature-model/snapshots/import} validates.
     *
     * @param outputDirectory directory for this scan.
     * @param outcome extraction outcome carrying the generated model.
     * @param workflowResourceBytes bytes of the bundled lean guided workflow resource.
     * @param artemisPath scanned checkout path recorded as the snapshot source repository.
     * @param artemisCommit resolved commit of the scanned checkout.
     * @throws IOException if a file cannot be written.
     */
    public void writeSnapshot(Path outputDirectory, FeatureExtractionService.Outcome outcome, byte[] workflowResourceBytes, String artemisPath,
            String artemisCommit) throws IOException {
        if (outcome.generatedModel() == null) {
            return;
        }
        Path snapshotDirectory = outputDirectory.resolve(SNAPSHOT_DIRECTORY);
        Files.createDirectories(snapshotDirectory);
        Path modelFile = snapshotDirectory.resolve(SNAPSHOT_MODEL_FILE);
        writeJson(modelFile, outcome.generatedModel());
        Files.write(snapshotDirectory.resolve(SNAPSHOT_WORKFLOW_FILE), workflowResourceBytes);
        String version = outcome.generatedModel().model().version();
        String snapshotId = "generated-" + (artemisCommit == null ? "unknown" : artemisCommit.substring(0, Math.min(12, artemisCommit.length())));
        SnapshotMetadata snapshotMetadata = new SnapshotMetadata(outcome.generatedModel().model().id(), snapshotId, version, "generated", artemisPath, null,
                artemisCommit, "feature-model-extractor@" + FeatureExtractionService.EXTRACTOR_VERSION, null, null, null, null);
        writeJson(snapshotDirectory.resolve(SNAPSHOT_METADATA_FILE), snapshotMetadata);
        Files.write(snapshotDirectory.resolve(SNAPSHOT_CHECKSUM_FILE),
                ("sha256:" + sha256Hex(modelFile) + "  " + SNAPSHOT_MODEL_FILE + LINE_FEED).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes the lowercase SHA-256 hex digest of a file.
     *
     * @param file file to hash.
     * @return lowercase hex digest.
     * @throws IOException if the file cannot be read.
     */
    private String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }

    /**
     * Serializes one payload deterministically and writes it with a trailing line feed.
     *
     * @param file target file.
     * @param payload payload to serialize.
     * @throws IOException if the file cannot be written.
     */
    private void writeJson(Path file, Object payload) throws IOException {
        String json = objectMapper.writer().with(prettyPrinter).writeValueAsString(payload);
        Files.write(file, (json + LINE_FEED).getBytes(StandardCharsets.UTF_8));
    }
}
