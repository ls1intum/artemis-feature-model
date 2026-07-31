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
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GeneratedArtifactValidation;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanMetadata;
import tools.jackson.databind.ObjectMapper;

/** Covers fail-closed and atomic publication of generated snapshots. */
class ExtractionOutputWriterTest {

    private final ExtractionOutputWriter writer = new ExtractionOutputWriter(new ObjectMapper());

    @TempDir
    Path outputRoot;

    @Test
    void hardValidationFailureWritesDiagnosticsButNoSnapshot() throws Exception {
        ReportItem error = ReportItem.error(ReportItem.CODE_GENERATED_MODEL_INVALID, "NO_ROOT_FEATURE", "Synthetic hard failure.");
        FeatureExtractionService.Outcome outcome = outcome(new GeneratedArtifactValidation(false, true), error);

        writer.writeAll(layout(), metadata(), outcome);
        boolean published = writer.writeSnapshot(layout(), outcome, "{}".getBytes(StandardCharsets.UTF_8), "/artemis", "abc123");

        assertThat(published).isFalse();
        assertThat(layout().reportDirectory().resolve(ExtractionOutputWriter.EXTRACTION_REPORT_FILE)).content().contains(ReportItem.CODE_GENERATED_MODEL_INVALID);
        assertThat(layout().modelDirectory().resolve(ExtractionOutputWriter.GENERATED_MODEL_FILE)).isRegularFile();
        assertThat(layout().snapshotDirectory()).doesNotExist();
    }

    @Test
    void invalidRerunRemovesPreviouslyPublishedSnapshot() throws Exception {
        FeatureExtractionService.Outcome valid = outcome(new GeneratedArtifactValidation(true, true));
        assertThat(writer.writeSnapshot(layout(), valid, "{}".getBytes(StandardCharsets.UTF_8), "/artemis", "abc123")).isTrue();
        assertThat(layout().snapshotDirectory()).isDirectory();

        FeatureExtractionService.Outcome invalid = outcome(new GeneratedArtifactValidation(true, false));
        assertThat(writer.writeSnapshot(layout(), invalid, "{}".getBytes(StandardCharsets.UTF_8), "/artemis", "abc123")).isFalse();

        assertThat(layout().snapshotDirectory()).doesNotExist();
    }

    @Test
    void validRerunAtomicallyReplacesPreviousSnapshotContents() throws Exception {
        FeatureExtractionService.Outcome valid = outcome(new GeneratedArtifactValidation(true, true));
        writer.writeSnapshot(layout(), valid, "{}".getBytes(StandardCharsets.UTF_8), "/artemis", "abc123");
        Path staleFile = layout().snapshotDirectory().resolve("stale.txt");
        Files.writeString(staleFile, "stale");

        assertThat(writer.writeSnapshot(layout(), valid, "{}".getBytes(StandardCharsets.UTF_8), "/artemis", "abc123")).isTrue();

        Path snapshotDirectory = layout().snapshotDirectory();
        assertThat(snapshotDirectory.resolve("feature-model.json")).isRegularFile();
        assertThat(snapshotDirectory.resolve("guided-workflow.json")).isRegularFile();
        assertThat(snapshotDirectory.resolve("metadata.json")).isRegularFile();
        assertThat(snapshotDirectory.resolve("checksum.txt")).isRegularFile();
        assertThat(staleFile).doesNotExist();
    }

    @Test
    void writeFailureLeavesNoPartiallyPublishedSnapshot() {
        FeatureExtractionService.Outcome valid = outcome(new GeneratedArtifactValidation(true, true));

        assertThatThrownBy(() -> writer.writeSnapshot(layout(), valid, null, "/artemis", "abc123")).isInstanceOf(NullPointerException.class);

        assertThat(layout().snapshotDirectory()).doesNotExist();
        assertThat(layout().root().toFile().list((directory, name) -> name.startsWith(".snapshot-"))).isEmpty();
    }

    private ExtractionArtifactLayout layout() {
        return ExtractionArtifactLayout.forCommit(outputRoot, "abc123");
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
