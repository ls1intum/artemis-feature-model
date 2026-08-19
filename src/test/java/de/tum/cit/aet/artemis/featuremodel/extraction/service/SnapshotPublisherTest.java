package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.TestFeatureModels;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotBundleContract;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotProvenance;
import tools.jackson.databind.ObjectMapper;

/** Covers fail-closed and atomic publication of generated snapshots. */
class SnapshotPublisherTest {

    private static final byte[] WORKFLOW_BYTES = "{}".getBytes(StandardCharsets.UTF_8);

    private static final String ARTEMIS_COMMIT = "0123456789abcdef0123456789abcdef01234567";

    private static final String MANIFEST_DIGEST = "sha256:abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd";

    private final SnapshotPublisher publisher = new SnapshotPublisher(new ObjectMapper());

    @TempDir
    Path outputRoot;

    @Test
    void ineligibleRunPublishesNothing() throws Exception {
        boolean published = publish(WORKFLOW_BYTES, false);

        assertThat(published).isFalse();
        assertThat(layout().snapshotDirectory()).doesNotExist();
    }

    @Test
    void ineligibleRerunRemovesPreviouslyPublishedSnapshot() throws Exception {
        assertThat(publish(WORKFLOW_BYTES, true)).isTrue();
        assertThat(layout().snapshotDirectory()).isDirectory();

        assertThat(publish(WORKFLOW_BYTES, false)).isFalse();

        assertThat(layout().snapshotDirectory()).doesNotExist();
    }

    @Test
    void validRerunAtomicallyReplacesPreviousSnapshotContents() throws Exception {
        publish(WORKFLOW_BYTES, true);
        Path staleFile = layout().snapshotDirectory().resolve("stale.txt");
        Files.writeString(staleFile, "stale");

        assertThat(publish(WORKFLOW_BYTES, true)).isTrue();

        Path snapshotDirectory = layout().snapshotDirectory();
        assertThat(snapshotDirectory.resolve(SnapshotBundleContract.SNAPSHOT_MODEL_FILE)).isRegularFile();
        assertThat(snapshotDirectory.resolve(SnapshotBundleContract.SNAPSHOT_WORKFLOW_FILE)).isRegularFile();
        assertThat(snapshotDirectory.resolve(SnapshotBundleContract.SNAPSHOT_METADATA_FILE)).isRegularFile();
        assertThat(snapshotDirectory.resolve(SnapshotBundleContract.SNAPSHOT_METADATA_FILE)).content().contains("\"sourceCommit\" : \"" + ARTEMIS_COMMIT + "\"")
                .contains("\"imageDigest\" : \"latest\"");
        assertThat(snapshotDirectory.resolve(SnapshotBundleContract.SNAPSHOT_CHECKSUM_FILE)).content().startsWith("sha256:")
                .contains("  config-key-catalog.json", "  provenance.json", "  generation-report.json");
        assertThat(Files.list(snapshotDirectory).map(path -> path.getFileName().toString()).sorted().toList())
                .containsExactly("checksums.txt", "config-key-catalog.json", "feature-model.json", "generation-report.json", "guided-workflow.json",
                        "metadata.json", "provenance.json");
        assertThat(staleFile).doesNotExist();
    }

    @Test
    void identicalInputsProduceByteIdenticalCompleteSnapshots() throws Exception {
        publish(WORKFLOW_BYTES, true);
        Map<String, byte[]> first = snapshotBytes();

        publish(WORKFLOW_BYTES, true);

        assertThat(snapshotBytes()).usingRecursiveComparison().isEqualTo(first);
        assertThat(first).containsOnlyKeys("checksums.txt", "config-key-catalog.json", "feature-model.json", "generation-report.json",
                "guided-workflow.json", "metadata.json", "provenance.json");
    }

    @Test
    void writeFailureLeavesNoPartiallyPublishedSnapshot() {
        assertThatThrownBy(() -> publish(null, true))
                .isInstanceOf(NullPointerException.class);

        assertThat(layout().snapshotDirectory()).doesNotExist();
        assertThat(layout().root().toFile().list((directory, name) -> name.startsWith(".snapshot-"))).isEmpty();
    }

    private ExtractionArtifactLayout layout() {
        return ExtractionArtifactLayout.forCommit(outputRoot, ARTEMIS_COMMIT);
    }

    private boolean publish(byte[] workflowBytes, boolean eligible) throws Exception {
        ArtemisConfigKeyCatalog catalog = new ArtemisConfigKeyCatalog("test", ARTEMIS_COMMIT, "generated", List.of());
        CurationReport curation = new CurationReport(2, ARTEMIS_COMMIT, Map.of(), Map.of(), List.of(), List.of());
        ExtractionReport report = new ExtractionReport(1, "pass", ARTEMIS_COMMIT, MANIFEST_DIGEST, curation, Map.of(), Map.of(), Map.of(), List.of());
        return publisher.publish(layout(), TestFeatureModels.baseModel(), workflowBytes, catalog, report, ARTEMIS_COMMIT, MANIFEST_DIGEST,
                "fedcba9876543210fedcba9876543210fedcba98", "sha256:profile", "latest", SnapshotProvenance.MANIFEST_SOURCE_REPOSITORY, eligible);
    }

    private Map<String, byte[]> snapshotBytes() throws Exception {
        Map<String, byte[]> bytes = new TreeMap<>();
        try (var paths = Files.list(layout().snapshotDirectory())) {
            for (Path file : paths.toList()) {
                bytes.put(file.getFileName().toString(), Files.readAllBytes(file));
            }
        }
        return bytes;
    }
}
