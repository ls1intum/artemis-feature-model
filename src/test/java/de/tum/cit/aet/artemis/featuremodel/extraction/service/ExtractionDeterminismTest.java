package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedSourceFacts;
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

        for (String fileName : scanArtifactFileNames()) {
            byte[] firstBytes = Files.readAllBytes(firstRun.scanDirectory().resolve(fileName));
            byte[] secondBytes = Files.readAllBytes(secondRun.scanDirectory().resolve(fileName));
            assertThat(secondBytes).as("output file %s must be byte-identical across runs", fileName).isEqualTo(firstBytes);
        }
    }

    @Test
    void persistedScanFactsRoundTripWithoutChangingArtifactBytes() throws IOException {
        ExtractionArtifactLayout firstRun = runAndWrite(temporaryDirectory.resolve("round-trip-source"));
        ExtractionArtifactStore store = new ExtractionArtifactStore(new ObjectMapper());
        ExtractionArtifactStore.LoadedScan loaded = store.readScan(firstRun, new LocalArtemisSourceRepository(FIXTURE_PATH).commit());
        ExtractionArtifactLayout roundTrip = ExtractionArtifactLayout.forCommit(temporaryDirectory.resolve("round-trip-target"), loaded.result().artemisCommit());

        store.writeScan(roundTrip, loaded.metadata(), loaded.outcome());

        for (String fileName : scanArtifactFileNames()) {
            assertThat(Files.readAllBytes(roundTrip.scanDirectory().resolve(fileName))).as("round-tripped artifact %s", fileName)
                    .isEqualTo(Files.readAllBytes(firstRun.scanDirectory().resolve(fileName)));
        }
    }

    @Test
    void scanEnvelopeBytesAreStableAcrossSeparateJvmProcesses() throws Exception {
        String expected = null;
        for (int invocation = 0; invocation < 6; invocation++) {
            Process process = new ProcessBuilder(javaExecutable(), "-cp", System.getProperty("java.class.path"), ScanResultWriter.class.getName())
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            assertThat(process.waitFor()).as("child JVM output: %s", output).isZero();
            if (expected == null) {
                expected = output;
            }
            assertThat(output).isEqualTo(expected);
            assertThat(output.indexOf("a.json")).isLessThan(output.indexOf("b.json"));
        }
    }

    /** Writes a representative scan envelope in a fresh JVM for the parent-process byte assertion. */
    public static final class ScanResultWriter {

        private ScanResultWriter() {
        }

        /**
         * Serializes one ordered envelope.
         *
         * @param arguments unused command arguments.
         * @throws Exception if serialization fails.
         */
        public static void main(String[] arguments) throws Exception {
            Map<String, String> digests = new LinkedHashMap<>();
            digests.put("a.json", "sha256:aaaa");
            digests.put("b.json", "sha256:bbbb");
            ScanResult result = new ScanResult(1, "test", "0123456789abcdef0123456789abcdef01234567", digests, "sha256:combined");
            System.out.print(new ObjectMapper().writeValueAsString(result));
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
        ExtractedSourceFacts outcome = new FeatureExtractionService(objectMapper).scan(source);
        ScanMetadata metadata = new ScanMetadata(ScanResult.EXTRACTOR_VERSION, source.root().toString(), source.commit(), source.workingTreeDirty(),
                FIXED_TIMESTAMP, FIXED_TIMESTAMP, outcome.candidates().size(), outcome.evidence().size(), outcome.relationCandidates().size(),
                outcome.items().size());
        ExtractionArtifactLayout layout = ExtractionArtifactLayout.forCommit(outputRoot, source.commit());
        new ExtractionArtifactStore(objectMapper).writeScan(layout, metadata, outcome);
        return layout;
    }

    /**
     * Resolves the Java executable running the test suite.
     *
     * @return absolute Java executable path.
     */
    private String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    /**
     * Lists the complete persisted scan artifact contract in write order.
     *
     * @return scan artifact file names.
     */
    private List<String> scanArtifactFileNames() {
        return List.of(ExtractionArtifactStore.SCAN_METADATA_FILE, ExtractionArtifactStore.FEATURE_CANDIDATES_FILE, ExtractionArtifactStore.EVIDENCE_FILE,
                ExtractionArtifactStore.RELATION_CANDIDATES_FILE, ExtractionArtifactStore.ANNOTATIONS_FILE, ExtractionArtifactStore.CONFIG_DEFAULTS_FILE,
                ExtractionArtifactStore.SCAN_DIAGNOSTICS_FILE, ExtractionArtifactStore.SCAN_RESULT_FILE);
    }
}
