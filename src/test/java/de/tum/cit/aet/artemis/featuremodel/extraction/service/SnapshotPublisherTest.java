package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.TestFeatureModels;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import tools.jackson.databind.ObjectMapper;

/** Covers fail-closed and atomic publication of generated snapshots. */
class SnapshotPublisherTest {

    private static final byte[] WORKFLOW_BYTES = "{}".getBytes(StandardCharsets.UTF_8);

    private final SnapshotPublisher publisher = new SnapshotPublisher(new ObjectMapper());

    @TempDir
    Path outputRoot;

    @Test
    void ineligibleRunPublishesNothing() throws Exception {
        boolean published = publisher.publish(layout(), TestFeatureModels.baseModel(), WORKFLOW_BYTES, "/artemis", "abc123", false);

        assertThat(published).isFalse();
        assertThat(layout().snapshotDirectory()).doesNotExist();
    }

    @Test
    void ineligibleRerunRemovesPreviouslyPublishedSnapshot() throws Exception {
        assertThat(publisher.publish(layout(), TestFeatureModels.baseModel(), WORKFLOW_BYTES, "/artemis", "abc123", true)).isTrue();
        assertThat(layout().snapshotDirectory()).isDirectory();

        assertThat(publisher.publish(layout(), TestFeatureModels.baseModel(), WORKFLOW_BYTES, "/artemis", "abc123", false)).isFalse();

        assertThat(layout().snapshotDirectory()).doesNotExist();
    }

    @Test
    void validRerunAtomicallyReplacesPreviousSnapshotContents() throws Exception {
        publisher.publish(layout(), TestFeatureModels.baseModel(), WORKFLOW_BYTES, "/artemis", "abc123", true);
        Path staleFile = layout().snapshotDirectory().resolve("stale.txt");
        Files.writeString(staleFile, "stale");

        assertThat(publisher.publish(layout(), TestFeatureModels.baseModel(), WORKFLOW_BYTES, "/artemis", "abc123", true)).isTrue();

        Path snapshotDirectory = layout().snapshotDirectory();
        assertThat(snapshotDirectory.resolve(SnapshotPublisher.SNAPSHOT_MODEL_FILE)).isRegularFile();
        assertThat(snapshotDirectory.resolve(SnapshotPublisher.SNAPSHOT_WORKFLOW_FILE)).isRegularFile();
        assertThat(snapshotDirectory.resolve(SnapshotPublisher.SNAPSHOT_METADATA_FILE)).isRegularFile();
        assertThat(snapshotDirectory.resolve(SnapshotPublisher.SNAPSHOT_CHECKSUM_FILE)).content().startsWith("sha256:");
        assertThat(staleFile).doesNotExist();
    }

    @Test
    void writeFailureLeavesNoPartiallyPublishedSnapshot() {
        assertThatThrownBy(() -> publisher.publish(layout(), TestFeatureModels.baseModel(), null, "/artemis", "abc123", true))
                .isInstanceOf(NullPointerException.class);

        assertThat(layout().snapshotDirectory()).doesNotExist();
        assertThat(layout().root().toFile().list((directory, name) -> name.startsWith(".snapshot-"))).isEmpty();
    }

    private ExtractionArtifactLayout layout() {
        return ExtractionArtifactLayout.forCommit(outputRoot, "abc123");
    }
}
