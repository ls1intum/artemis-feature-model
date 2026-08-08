package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.SnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.SnapshotException;
import de.tum.cit.aet.artemis.featuremodel.snapshot.SnapshotTestFixtures;
import tools.jackson.databind.ObjectMapper;

class LocalSnapshotRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path dataRoot;

    private LocalSnapshotRepository repository(String activeSnapshotId) {
        SnapshotProperties properties = activeSnapshotId == null ? new SnapshotProperties(dataRoot.toString(), null)
                : new SnapshotProperties(FeatureModelSourceMode.SNAPSHOT, dataRoot.toString(), activeSnapshotId, false);
        return new LocalSnapshotRepository(properties, objectMapper);
    }

    private Path importedModels() {
        return dataRoot.resolve("imported-models");
    }

    @Test
    void listsImportedSnapshotsSortedById() {
        SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("b-snapshot"));
        SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("a-snapshot"));

        List<SnapshotMetadata> snapshots = repository(null).listSnapshots();

        assertThat(snapshots).extracting(SnapshotMetadata::snapshotId).containsExactly("a-snapshot", "b-snapshot");
        assertThat(snapshots).allSatisfy(metadata -> assertThat(metadata.modelId()).isEqualTo(SnapshotTestFixtures.MODEL_ID));
    }

    @Test
    void listsNoSnapshotsWhenDirectoryMissing() {
        assertThat(repository(null).listSnapshots()).isEmpty();
    }

    @Test
    void readsSynthesizedMetadataWhenMetadataFileIsAbsent() {
        Path directory = SnapshotTestFixtures.writeSnapshot(importedModels().resolve("no-metadata"), SnapshotTestFixtures.MODEL_ID, SnapshotTestFixtures.MODEL_VERSION,
                SnapshotTestFixtures.MODEL_ID, SnapshotTestFixtures.MODEL_VERSION, true, true, false);

        SnapshotMetadata metadata = repository(null).readMetadata(directory);

        assertThat(metadata.snapshotId()).isEqualTo("no-metadata");
        assertThat(metadata.modelId()).isNull();
        assertThat(metadata.modelFile()).isEqualTo(SnapshotTestFixtures.MODEL_FILE);
    }

    @Test
    void requireSnapshotDirectoryRejectsTraversalAndUnknownIds() {
        SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("present"));
        LocalSnapshotRepository repository = repository(null);

        assertThat(repository.requireSnapshotDirectory("present")).isEqualTo(importedModels().resolve("present"));
        assertThatThrownBy(() -> repository.requireSnapshotDirectory("../escape")).isInstanceOf(SnapshotException.class)
                .satisfies(error -> assertThat(((SnapshotException) error).getCode()).isEqualTo("SNAPSHOT_INVALID_ID"));
        assertThatThrownBy(() -> repository.requireSnapshotDirectory("missing")).isInstanceOf(SnapshotException.class)
                .satisfies(error -> assertThat(((SnapshotException) error).getCode()).isEqualTo("SNAPSHOT_NOT_FOUND"));
    }

    @Test
    void verifyChecksumPassesForMatchingChecksumAndIsSkippedWhenAbsent() {
        Path withChecksum = SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("with-checksum"));
        Path withoutChecksum = SnapshotTestFixtures.writeSnapshot(importedModels().resolve("without-checksum"), SnapshotTestFixtures.MODEL_ID,
                SnapshotTestFixtures.MODEL_VERSION, SnapshotTestFixtures.MODEL_ID, SnapshotTestFixtures.MODEL_VERSION, false, true, true);
        LocalSnapshotRepository repository = repository(null);

        repository.verifyChecksum(withChecksum, repository.readMetadata(withChecksum));
        repository.verifyChecksum(withoutChecksum, repository.readMetadata(withoutChecksum));
    }

    @Test
    void verifyChecksumFailsOnMismatch() {
        Path directory = SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("corrupt"));
        SnapshotTestFixtures.corruptChecksum(directory);
        LocalSnapshotRepository repository = repository(null);

        assertThatThrownBy(() -> repository.verifyChecksum(directory, repository.readMetadata(directory))).isInstanceOf(SnapshotException.class)
                .satisfies(error -> assertThat(((SnapshotException) error).getCode()).isEqualTo("SNAPSHOT_CHECKSUM_MISMATCH"));
    }

    @Test
    void resolvesActiveResourcesWhenSnapshotConfigured() {
        SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("active"));
        LocalSnapshotRepository repository = repository("active");

        assertThat(repository.activeModelResource()).isPresent();
        assertThat(repository.activeWorkflowResource()).isPresent();
        assertThat(repository.activeModelResource().orElseThrow().getFilename()).isEqualTo(SnapshotTestFixtures.MODEL_FILE);
    }

    @Test
    void returnsEmptyActiveResourcesWhenNoSnapshotConfigured() {
        LocalSnapshotRepository repository = repository(null);

        assertThat(repository.activeModelResource()).isEmpty();
        assertThat(repository.activeWorkflowResource()).isEmpty();
    }

    @Test
    void failsActiveResolutionWhenConfiguredSnapshotIsMissing() {
        LocalSnapshotRepository repository = repository("does-not-exist");

        assertThatThrownBy(repository::activeModelResource).isInstanceOf(FeatureModelLoadException.class)
                .hasMessageContaining("does-not-exist");
    }

    @Test
    void copiesSnapshotAndRejectsExistingWithoutOverwrite() {
        Path source = SnapshotTestFixtures.writeValidSnapshot(dataRoot.resolve("source"));
        LocalSnapshotRepository repository = repository(null);

        Path destination = repository.copySnapshot(source, "imported", false);

        assertThat(Files.isRegularFile(destination.resolve(SnapshotTestFixtures.MODEL_FILE))).isTrue();
        assertThat(Files.isRegularFile(destination.resolve(SnapshotTestFixtures.WORKFLOW_FILE))).isTrue();
        assertThat(Files.isRegularFile(destination.resolve(SnapshotTestFixtures.CHECKSUM_FILE))).isTrue();
        assertThatThrownBy(() -> repository.copySnapshot(source, "imported", false)).isInstanceOf(SnapshotException.class)
                .satisfies(error -> assertThat(((SnapshotException) error).getCode()).isEqualTo("SNAPSHOT_ALREADY_EXISTS"));

        Path overwritten = repository.copySnapshot(source, "imported", true);
        assertThat(Files.isRegularFile(overwritten.resolve(SnapshotTestFixtures.MODEL_FILE))).isTrue();
    }

    @Test
    void zipsSnapshotWithSortedDeterministicEntries() throws Exception {
        Path directory = SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("export"));
        LocalSnapshotRepository repository = repository(null);

        byte[] firstArchive = repository.zipSnapshot(directory, repository.readMetadata(directory));
        byte[] secondArchive = repository.zipSnapshot(directory, repository.readMetadata(directory));

        assertThat(firstArchive).isEqualTo(secondArchive);
        assertThat(entryNames(firstArchive)).containsExactly(SnapshotTestFixtures.CHECKSUM_FILE, SnapshotTestFixtures.MODEL_FILE, SnapshotTestFixtures.REPORT_FILE,
                SnapshotTestFixtures.WORKFLOW_FILE, SnapshotTestFixtures.METADATA_FILE).isSorted();
    }

    private List<String> entryNames(byte[] archive) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
