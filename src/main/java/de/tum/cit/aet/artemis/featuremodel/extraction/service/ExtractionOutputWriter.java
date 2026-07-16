package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
     * Writes all five outputs of one extraction run into the output directory, replacing previous outputs.
     *
     * @param outputDirectory directory for this scan, typically {@code build/feature-extraction/<commit>}.
     * @param metadata scan metadata payload.
     * @param outcome extraction outcome with candidates, evidence, relations, and the report.
     * @throws IOException if a file cannot be written.
     */
    public void writeAll(Path outputDirectory, ScanMetadata metadata, FeatureExtractionService.Outcome outcome) throws IOException {
        Files.createDirectories(outputDirectory);
        writeJson(outputDirectory.resolve(SCAN_METADATA_FILE), metadata);
        writeJson(outputDirectory.resolve(FEATURE_CANDIDATES_FILE), outcome.candidates());
        writeJson(outputDirectory.resolve(EVIDENCE_FILE), outcome.evidence());
        writeJson(outputDirectory.resolve(RELATION_CANDIDATES_FILE), outcome.relationCandidates());
        writeJson(outputDirectory.resolve(EXTRACTION_REPORT_FILE), outcome.report());
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
