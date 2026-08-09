package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisConfigKeyCatalog;
import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.ArtifactDirectoryOperations;
import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.ExtractionJsonWriter;
import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.Sha256Digest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionArtifactLayout;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GeneratedSnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ScanResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotProvenance;
import tools.jackson.databind.ObjectMapper;

/** Publishes the complete deterministic snapshot of an eligible generated artifact bundle atomically. */
class SnapshotPublisher {

    static final String SNAPSHOT_MODEL_FILE = "feature-model.json";

    static final String SNAPSHOT_WORKFLOW_FILE = "guided-workflow.json";

    static final String SNAPSHOT_CATALOG_FILE = "config-key-catalog.json";

    static final String SNAPSHOT_REPORT_FILE = "generation-report.json";

    static final String SNAPSHOT_PROVENANCE_FILE = "provenance.json";

    static final String SNAPSHOT_METADATA_FILE = "metadata.json";

    static final String SNAPSHOT_CHECKSUM_FILE = "checksums.txt";

    static final List<String> PAYLOAD_FILES = List.of(SNAPSHOT_CATALOG_FILE, SNAPSHOT_MODEL_FILE, SNAPSHOT_REPORT_FILE, SNAPSHOT_METADATA_FILE,
            SNAPSHOT_PROVENANCE_FILE, SNAPSHOT_WORKFLOW_FILE);

    private static final int SHORT_ID_LENGTH = 12;

    private static final String LINE_FEED = "\n";

    private final ExtractionJsonWriter jsonWriter;

    private final ArtifactDirectoryOperations directoryOperations;

    /**
     * Creates the publisher.
     *
     * @param objectMapper mapper used for canonical JSON payloads.
     */
    SnapshotPublisher(ObjectMapper objectMapper) {
        jsonWriter = new ExtractionJsonWriter(objectMapper);
        directoryOperations = new ArtifactDirectoryOperations();
    }

    /**
     * Publishes one complete snapshot or invalidates the previous publication when ineligible.
     *
     * @param layout run artifact layout.
     * @param generatedModel canonical generated model.
     * @param workflowBytes prepared workflow bytes.
     * @param generatedCatalog generated catalog.
     * @param generationReport consolidated successful report.
     * @param artemisCommit pinned Artemis commit.
     * @param manifestDigest manifest digest.
     * @param featureModelRepositoryCommit generator repository commit.
     * @param deploymentProfileDigest validated profile digest.
     * @param imageDigest manifest runtime image identity.
     * @param eligible all delivery gates passed.
     * @return true when publication succeeded.
     * @throws IOException if publication fails.
     */
    boolean publish(ExtractionArtifactLayout layout, FeatureModel generatedModel, byte[] workflowBytes, ArtemisConfigKeyCatalog generatedCatalog,
            ExtractionReport generationReport, String artemisCommit, String manifestDigest, String featureModelRepositoryCommit,
            String deploymentProfileDigest, String imageDigest, boolean eligible) throws IOException {
        Path snapshotDirectory = layout.snapshotDirectory();
        if (!eligible) {
            removePublishedSnapshot(snapshotDirectory);
            return false;
        }

        Path temporaryDirectory = Files.createTempDirectory(Files.createDirectories(layout.root()), ".snapshot-");
        try {
            writeSnapshotContents(temporaryDirectory, generatedModel, workflowBytes, generatedCatalog, generationReport, artemisCommit, manifestDigest,
                    featureModelRepositoryCommit, deploymentProfileDigest, imageDigest);
            publishSnapshot(temporaryDirectory, snapshotDirectory);
            return true;
        }
        finally {
            directoryOperations.deleteRecursively(temporaryDirectory);
        }
    }

    /**
     * Removes a publication that failed complete post-write validation.
     *
     * @param layout run artifact layout.
     * @throws IOException if invalidation fails.
     */
    void invalidate(ExtractionArtifactLayout layout) throws IOException {
        removePublishedSnapshot(layout.snapshotDirectory());
    }

    private void writeSnapshotContents(Path directory, FeatureModel model, byte[] workflowBytes, ArtemisConfigKeyCatalog catalog, ExtractionReport report,
            String artemisCommit, String manifestDigest, String repositoryCommit, String profileDigest, String imageDigest) throws IOException {
        Path modelFile = directory.resolve(SNAPSHOT_MODEL_FILE);
        Path workflowFile = directory.resolve(SNAPSHOT_WORKFLOW_FILE);
        Path catalogFile = directory.resolve(SNAPSHOT_CATALOG_FILE);
        Path reportFile = directory.resolve(SNAPSHOT_REPORT_FILE);
        jsonWriter.write(modelFile, model);
        Files.write(workflowFile, workflowBytes);
        jsonWriter.write(catalogFile, catalog);
        jsonWriter.write(reportFile, report);

        SnapshotProvenance provenance = new SnapshotProvenance(SnapshotProvenance.CURRENT_FORMAT_VERSION, artemisCommit, manifestDigest, repositoryCommit,
                ScanResult.EXTRACTOR_VERSION, Sha256Digest.of(modelFile), Sha256Digest.of(workflowFile), Sha256Digest.of(catalogFile),
                Sha256Digest.of(reportFile), profileDigest, SnapshotProvenance.MANIFEST_SOURCE_REPOSITORY);
        jsonWriter.write(directory.resolve(SNAPSHOT_PROVENANCE_FILE), provenance);

        String snapshotId = snapshotId(artemisCommit, manifestDigest);
        GeneratedSnapshotMetadata metadata = new GeneratedSnapshotMetadata(GeneratedSnapshotMetadata.CURRENT_SCHEMA_VERSION,
                SnapshotProvenance.CURRENT_FORMAT_VERSION, model.model().id(), snapshotId, model.model().version(), GeneratedSnapshotMetadata.STATUS_GENERATED,
                artemisCommit, imageDigest, GeneratedSnapshotMetadata.EXTRACTOR_ID_PREFIX + ScanResult.EXTRACTOR_VERSION, SNAPSHOT_MODEL_FILE,
                SNAPSHOT_WORKFLOW_FILE, SNAPSHOT_CATALOG_FILE, SNAPSHOT_REPORT_FILE, SNAPSHOT_PROVENANCE_FILE, SNAPSHOT_CHECKSUM_FILE);
        jsonWriter.write(directory.resolve(SNAPSHOT_METADATA_FILE), metadata);
        writeChecksums(directory);
    }

    private String snapshotId(String artemisCommit, String manifestDigest) {
        String manifestHex = manifestDigest.substring(manifestDigest.indexOf(':') + 1);
        return "generated-" + artemisCommit.substring(0, SHORT_ID_LENGTH) + "-" + manifestHex.substring(0, SHORT_ID_LENGTH);
    }

    private void writeChecksums(Path directory) throws IOException {
        StringBuilder checksums = new StringBuilder();
        for (String fileName : PAYLOAD_FILES) {
            checksums.append(Sha256Digest.of(directory.resolve(fileName))).append("  ").append(fileName).append(LINE_FEED);
        }
        Files.write(directory.resolve(SNAPSHOT_CHECKSUM_FILE), checksums.toString().getBytes(StandardCharsets.UTF_8));
    }

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
            directoryOperations.deleteRecursively(previousSnapshot);
        }
    }

    private void removePublishedSnapshot(Path snapshotDirectory) throws IOException {
        if (!Files.exists(snapshotDirectory)) {
            return;
        }
        Path invalidSnapshot = snapshotDirectory.resolveSibling(".snapshot-ineligible-" + UUID.randomUUID());
        Files.move(snapshotDirectory, invalidSnapshot, StandardCopyOption.ATOMIC_MOVE);
        directoryOperations.deleteRecursively(invalidSnapshot);
    }
}
