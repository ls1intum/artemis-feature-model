package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.SnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanResult;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes the importable snapshot folder of an eligible run: the generated model, a byte copy of the prepared lean
 * guided workflow, traceability metadata, and the model checksum — the exact layout
 * {@code POST /api/feature-model/snapshots/import} validates. Publication is atomic and fail-closed: an ineligible run
 * publishes nothing and removes a snapshot a previous run left behind.
 */
class SnapshotPublisher {

    static final String SNAPSHOT_MODEL_FILE = "feature-model.json";

    static final String SNAPSHOT_WORKFLOW_FILE = "guided-workflow.json";

    static final String SNAPSHOT_METADATA_FILE = "metadata.json";

    static final String SNAPSHOT_CHECKSUM_FILE = "checksum.txt";

    private static final int SHORT_COMMIT_LENGTH = 12;

    private static final String LINE_FEED = "\n";

    private final ObjectMapper objectMapper;

    private final DefaultPrettyPrinter prettyPrinter;

    /**
     * Creates the publisher with the shared Jackson mapper.
     *
     * @param objectMapper Jackson mapper used for serialization.
     */
    SnapshotPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        DefaultIndenter indenter = new DefaultIndenter("  ", LINE_FEED);
        this.prettyPrinter = new DefaultPrettyPrinter().withObjectIndenter(indenter).withArrayIndenter(indenter);
    }

    /**
     * Publishes the snapshot of an eligible run.
     *
     * @param layout output layout of this run.
     * @param generatedModel generated feature model.
     * @param workflowBytes bytes of the prepared lean guided workflow.
     * @param artemisPath scanned checkout path recorded as the snapshot source repository.
     * @param artemisCommit resolved commit of the scanned checkout.
     * @param imageDigest remote Artemis image digest from the extraction manifest.
     * @param eligible whether the run passed every gate that guards publication.
     * @return true when a snapshot was published.
     * @throws IOException if a file cannot be written.
     */
    boolean publish(ExtractionArtifactLayout layout, FeatureModel generatedModel, byte[] workflowBytes, String artemisPath, String artemisCommit,
            String imageDigest, boolean eligible) throws IOException {
        Path snapshotDirectory = layout.snapshotDirectory();
        if (!eligible) {
            removePublishedSnapshot(snapshotDirectory);
            return false;
        }

        Path runDirectory = Files.createDirectories(layout.root());
        Path temporaryDirectory = Files.createTempDirectory(runDirectory, ".snapshot-");
        try {
            writeSnapshotContents(temporaryDirectory, generatedModel, workflowBytes, artemisPath, artemisCommit, imageDigest);
            publishSnapshot(temporaryDirectory, snapshotDirectory);
            return true;
        }
        finally {
            ExtractionArtifactStore.deleteRecursively(temporaryDirectory);
        }
    }

    /**
     * Writes every file of a snapshot into an unpublished temporary directory.
     *
     * @param snapshotDirectory temporary snapshot directory.
     * @param generatedModel generated feature model.
     * @param workflowBytes bytes of the prepared lean guided workflow.
     * @param artemisPath scanned checkout path.
     * @param artemisCommit resolved scanned commit.
     * @param imageDigest remote Artemis image digest from the extraction manifest.
     * @throws IOException if a snapshot file cannot be written.
     */
    private void writeSnapshotContents(Path snapshotDirectory, FeatureModel generatedModel, byte[] workflowBytes, String artemisPath, String artemisCommit,
            String imageDigest) throws IOException {
        Path modelFile = snapshotDirectory.resolve(SNAPSHOT_MODEL_FILE);
        writeJson(modelFile, generatedModel);
        Files.write(snapshotDirectory.resolve(SNAPSHOT_WORKFLOW_FILE), workflowBytes);
        String version = generatedModel.model().version();
        String snapshotId = "generated-" + (artemisCommit == null ? "unknown" : artemisCommit.substring(0, Math.min(SHORT_COMMIT_LENGTH, artemisCommit.length())));
        SnapshotMetadata snapshotMetadata = new SnapshotMetadata(generatedModel.model().id(), snapshotId, version, "generated", artemisPath, null, artemisCommit,
                imageDigest, "feature-model-extractor@" + ScanResult.EXTRACTOR_VERSION, null, null, null, null);
        writeJson(snapshotDirectory.resolve(SNAPSHOT_METADATA_FILE), snapshotMetadata);
        Files.write(snapshotDirectory.resolve(SNAPSHOT_CHECKSUM_FILE),
                (ExtractionArtifactStore.digestOf(modelFile) + "  " + SNAPSHOT_MODEL_FILE + LINE_FEED).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Atomically switches a complete temporary snapshot into the public snapshot path. An existing snapshot is first
     * moved aside and restored if publication fails.
     *
     * @param temporaryDirectory complete unpublished snapshot directory.
     * @param snapshotDirectory public snapshot directory.
     * @throws IOException if the atomic directory moves fail.
     */
    private void publishSnapshot(Path temporaryDirectory, Path snapshotDirectory) throws IOException {
        Path previousSnapshot = snapshotDirectory.resolveSibling(".snapshot-previous-" + UUID.randomUUID());
        boolean previousMoved = false;
        if (Files.exists(snapshotDirectory)) {
            Files.move(snapshotDirectory, previousSnapshot, StandardCopyOption.ATOMIC_MOVE);
            previousMoved = true;
        }
        try {
            Files.move(temporaryDirectory, snapshotDirectory, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException e) {
            if (previousMoved) {
                Files.move(previousSnapshot, snapshotDirectory, StandardCopyOption.ATOMIC_MOVE);
            }
            throw e;
        }
        if (previousMoved) {
            ExtractionArtifactStore.deleteRecursively(previousSnapshot);
        }
    }

    /**
     * Removes a stale published snapshot after an ineligible rerun. The directory is first moved out of the public
     * path so deletion cannot expose a partially removed snapshot.
     *
     * @param snapshotDirectory public snapshot directory.
     * @throws IOException if the snapshot cannot be invalidated or removed.
     */
    private void removePublishedSnapshot(Path snapshotDirectory) throws IOException {
        if (!Files.exists(snapshotDirectory)) {
            return;
        }
        Path invalidSnapshot = snapshotDirectory.resolveSibling(".snapshot-ineligible-" + UUID.randomUUID());
        Files.move(snapshotDirectory, invalidSnapshot, StandardCopyOption.ATOMIC_MOVE);
        ExtractionArtifactStore.deleteRecursively(invalidSnapshot);
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
