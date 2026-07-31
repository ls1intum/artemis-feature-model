package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanResult;
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
        ExtractionArtifactLayout firstRun = runAndWrite(temporaryDirectory.resolve("first"));
        ExtractionArtifactLayout secondRun = runAndWrite(temporaryDirectory.resolve("second"));

        for (String fileName : new String[] { ExtractionArtifactStore.SCAN_METADATA_FILE, ExtractionArtifactStore.FEATURE_CANDIDATES_FILE,
                ExtractionArtifactStore.EVIDENCE_FILE, ExtractionArtifactStore.RELATION_CANDIDATES_FILE, ExtractionArtifactStore.ANNOTATIONS_FILE,
                ExtractionArtifactStore.CONFIG_DEFAULTS_FILE, ExtractionArtifactStore.SCAN_DIAGNOSTICS_FILE, ExtractionArtifactStore.SCAN_RESULT_FILE }) {
            byte[] firstBytes = Files.readAllBytes(firstRun.scanDirectory().resolve(fileName));
            byte[] secondBytes = Files.readAllBytes(secondRun.scanDirectory().resolve(fileName));
            assertThat(secondBytes).as("output file %s must be byte-identical across runs", fileName).isEqualTo(firstBytes);
        }
    }

    /**
     * Runs one full scan over the fixture and writes all scan artifacts with fixed timestamps.
     *
     * @param outputRoot run output root.
     * @return the run layout.
     * @throws IOException if the outputs cannot be written.
     */
    private ExtractionArtifactLayout runAndWrite(Path outputRoot) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        LocalArtemisSourceRepository source = new LocalArtemisSourceRepository(FIXTURE_PATH);
        FeatureExtractionService.Outcome outcome = new FeatureExtractionService(objectMapper).scan(source, ExtractionTestModels.fixtureCuratedModel(),
                ExtractionTestModels.fixtureCatalog());
        ScanMetadata metadata = new ScanMetadata(ScanResult.EXTRACTOR_VERSION, source.root().toString(), source.commit(), source.workingTreeDirty(),
                FIXED_TIMESTAMP, FIXED_TIMESTAMP, outcome.candidates().size(), outcome.evidence().size(), outcome.relationCandidates().size(),
                outcome.items().size());
        ExtractionArtifactLayout layout = ExtractionArtifactLayout.forCommit(outputRoot, source.commit());
        new ExtractionArtifactStore(objectMapper).writeScan(layout, metadata, outcome);
        return layout;
    }
}
