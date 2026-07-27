package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.TestFeatureModels;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GeneratedArtifactValidation;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanMetadata;
import tools.jackson.databind.ObjectMapper;

/** Covers fail-closed and atomic publication of generated snapshots. */
class ExtractionOutputWriterTest {

    private final ExtractionOutputWriter writer = new ExtractionOutputWriter(new ObjectMapper());

    @TempDir
    Path outputDirectory;

    @Test
    void hardValidationFailureWritesDiagnosticsButNoSnapshot() throws Exception {
        ReportItem error = ReportItem.error(ReportItem.CODE_GENERATED_MODEL_INVALID, "NO_ROOT_FEATURE", "Synthetic hard failure.");
        FeatureExtractionService.Outcome outcome = outcome(new GeneratedArtifactValidation(false, true), error);

        writer.writeAll(outputDirectory, metadata(), outcome);
        boolean published = writer.writeSnapshot(outputDirectory, outcome, "{}".getBytes(StandardCharsets.UTF_8), "/artemis", "abc123");

        assertThat(published).isFalse();
        assertThat(outputDirectory.resolve(ExtractionOutputWriter.EXTRACTION_REPORT_FILE)).content().contains(ReportItem.CODE_GENERATED_MODEL_INVALID);
        assertThat(outputDirectory.resolve(ExtractionOutputWriter.GENERATED_MODEL_FILE)).isRegularFile();
        assertThat(outputDirectory.resolve(ExtractionOutputWriter.SNAPSHOT_DIRECTORY)).doesNotExist();
    }

    @Test
    void invalidRerunRemovesPreviouslyPublishedSnapshot() throws Exception {
        FeatureExtractionService.Outcome valid = outcome(new GeneratedArtifactValidation(true, true));
        assertThat(writer.writeSnapshot(outputDirectory, valid, "{}".getBytes(StandardCharsets.UTF_8), "/artemis", "abc123")).isTrue();
        assertThat(outputDirectory.resolve(ExtractionOutputWriter.SNAPSHOT_DIRECTORY)).isDirectory();

        FeatureExtractionService.Outcome invalid = outcome(new GeneratedArtifactValidation(true, false));
        assertThat(writer.writeSnapshot(outputDirectory, invalid, "{}".getBytes(StandardCharsets.UTF_8), "/artemis", "abc123")).isFalse();

        assertThat(outputDirectory.resolve(ExtractionOutputWriter.SNAPSHOT_DIRECTORY)).doesNotExist();
    }

    @Test
    void validRerunAtomicallyReplacesPreviousSnapshotContents() throws Exception {
        FeatureExtractionService.Outcome valid = outcome(new GeneratedArtifactValidation(true, true));
        writer.writeSnapshot(outputDirectory, valid, "{}".getBytes(StandardCharsets.UTF_8), "/artemis", "abc123");
        Path staleFile = outputDirectory.resolve(ExtractionOutputWriter.SNAPSHOT_DIRECTORY).resolve("stale.txt");
        Files.writeString(staleFile, "stale");

        assertThat(writer.writeSnapshot(outputDirectory, valid, "{}".getBytes(StandardCharsets.UTF_8), "/artemis", "abc123")).isTrue();

        Path snapshotDirectory = outputDirectory.resolve(ExtractionOutputWriter.SNAPSHOT_DIRECTORY);
        assertThat(snapshotDirectory.resolve("feature-model.json")).isRegularFile();
        assertThat(snapshotDirectory.resolve("guided-workflow.json")).isRegularFile();
        assertThat(snapshotDirectory.resolve("metadata.json")).isRegularFile();
        assertThat(snapshotDirectory.resolve("checksum.txt")).isRegularFile();
        assertThat(staleFile).doesNotExist();
    }

    @Test
    void writeFailureLeavesNoPartiallyPublishedSnapshot() {
        FeatureExtractionService.Outcome valid = outcome(new GeneratedArtifactValidation(true, true));

        assertThatThrownBy(() -> writer.writeSnapshot(outputDirectory, valid, null, "/artemis", "abc123")).isInstanceOf(NullPointerException.class);

        assertThat(outputDirectory.resolve(ExtractionOutputWriter.SNAPSHOT_DIRECTORY)).doesNotExist();
        assertThat(outputDirectory.toFile().list((directory, name) -> name.startsWith(".snapshot-"))).isEmpty();
    }

    private FeatureExtractionService.Outcome outcome(GeneratedArtifactValidation validation, ReportItem... items) {
        ExtractionReport report = new ExtractionReport("abc123", "curated", "1.0.0", null, Map.of(), Map.of(), Map.of(), List.of(items));
        return new FeatureExtractionService.Outcome(List.of(), List.of(), List.of(), report, List.of(), TestFeatureModels.baseModel(), null, null, null,
                validation);
    }

    private ScanMetadata metadata() {
        return new ScanMetadata(FeatureExtractionService.EXTRACTOR_VERSION, "/artemis", "abc123", false, "start", "end", 0, 0, 0, 1);
    }
}
