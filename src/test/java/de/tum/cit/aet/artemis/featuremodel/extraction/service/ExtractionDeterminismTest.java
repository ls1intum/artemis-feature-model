package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies the determinism contract: two extraction runs over the same fixture produce byte-identical output files
 * when the scan metadata timestamps are held constant. Timestamps only ever appear in {@code scan-metadata.json}.
 */
class ExtractionDeterminismTest {

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    private static final String FIXED_TIMESTAMP = "2026-01-01T00:00:00Z";

    @TempDir
    private Path temporaryDirectory;

    @Test
    void twoRunsProduceByteIdenticalOutputs() throws IOException {
        Path firstRun = runAndWrite(temporaryDirectory.resolve("first"));
        Path secondRun = runAndWrite(temporaryDirectory.resolve("second"));

        for (String fileName : new String[] { ExtractionOutputWriter.SCAN_METADATA_FILE, ExtractionOutputWriter.FEATURE_CANDIDATES_FILE,
                ExtractionOutputWriter.EVIDENCE_FILE, ExtractionOutputWriter.RELATION_CANDIDATES_FILE, ExtractionOutputWriter.EXTRACTION_REPORT_FILE }) {
            byte[] firstBytes = Files.readAllBytes(firstRun.resolve(fileName));
            byte[] secondBytes = Files.readAllBytes(secondRun.resolve(fileName));
            assertThat(secondBytes).as("output file %s must be byte-identical across runs", fileName).isEqualTo(firstBytes);
        }
    }

    /**
     * Runs one full extraction over the fixture and writes all outputs with fixed timestamps.
     *
     * @param outputDirectory run output directory.
     * @return the output directory.
     * @throws IOException if the outputs cannot be written.
     */
    private Path runAndWrite(Path outputDirectory) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        LocalArtemisSourceRepository source = new LocalArtemisSourceRepository(FIXTURE_PATH);
        FeatureExtractionService.Outcome outcome = new FeatureExtractionService(objectMapper).extract(source, ExtractionTestModels.fixtureCuratedModel(),
                ExtractionTestModels.fixtureCatalog());
        ScanMetadata metadata = new ScanMetadata(FeatureExtractionService.EXTRACTOR_VERSION, source.root().toString(), source.commit(), source.workingTreeDirty(),
                FIXED_TIMESTAMP, FIXED_TIMESTAMP, outcome.candidates().size(), outcome.evidence().size(), outcome.relationCandidates().size(),
                outcome.report().items().size());
        new ExtractionOutputWriter(objectMapper).writeAll(outputDirectory, metadata, outcome);
        return outputDirectory;
    }
}
