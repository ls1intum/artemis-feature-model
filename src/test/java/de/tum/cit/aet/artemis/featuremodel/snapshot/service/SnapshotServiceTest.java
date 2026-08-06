package de.tum.cit.aet.artemis.featuremodel.snapshot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.LocalSnapshotRepository;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.FeatureModelSourceMode;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.SnapshotException;
import de.tum.cit.aet.artemis.featuremodel.snapshot.SnapshotTestFixtures;
import de.tum.cit.aet.artemis.featuremodel.snapshot.dto.ImportSnapshotRequest;
import de.tum.cit.aet.artemis.featuremodel.snapshot.dto.ImportSnapshotResultDTO;
import de.tum.cit.aet.artemis.featuremodel.snapshot.dto.SnapshotDetailDTO;
import tools.jackson.databind.ObjectMapper;

class SnapshotServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path dataRoot;

    @TempDir
    Path sourceRoot;

    private SnapshotService service(String activeSnapshotId) {
        SnapshotProperties properties = activeSnapshotId == null ? new SnapshotProperties(dataRoot.toString(), null)
                : new SnapshotProperties(FeatureModelSourceMode.SNAPSHOT, dataRoot.toString(), activeSnapshotId, false);
        LocalSnapshotRepository repository = new LocalSnapshotRepository(properties, objectMapper);
        return new SnapshotService(repository, objectMapper, new FeatureModelIntegrityService(), new GuidedWorkflowIntegrityService());
    }

    private Path importedModels() {
        return dataRoot.resolve("imported-models");
    }

    @Test
    void listsImportedSnapshotsWithActiveFlag() {
        SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("develop-latest"));
        SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("release-1"));

        var snapshots = service("develop-latest").listSnapshots();

        assertThat(snapshots).extracting("snapshotId").containsExactly("develop-latest", "release-1");
        assertThat(snapshots).filteredOn("active", true).extracting("snapshotId").containsExactly("develop-latest");
    }

    @Test
    void getSnapshotReportsMetadataAndFileAvailability() {
        SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("develop-latest"));

        SnapshotDetailDTO detail = service("develop-latest").getSnapshot("develop-latest");

        assertThat(detail.snapshotId()).isEqualTo("develop-latest");
        assertThat(detail.modelId()).isEqualTo(SnapshotTestFixtures.MODEL_ID);
        assertThat(detail.active()).isTrue();
        assertThat(detail.modelFileAvailable()).isTrue();
        assertThat(detail.workflowFileAvailable()).isTrue();
        assertThat(detail.reportAvailable()).isTrue();
        assertThat(detail.checksumAvailable()).isTrue();
    }

    @Test
    void getSnapshotRejectsUnknownId() {
        assertThatThrownBy(() -> service(null).getSnapshot("missing")).isInstanceOf(SnapshotException.class)
                .satisfies(error -> assertThat(((SnapshotException) error).getCode()).isEqualTo("SNAPSHOT_NOT_FOUND"));
    }

    @Test
    void importsValidSnapshotFromSourceFolder() {
        Path source = SnapshotTestFixtures.writeValidSnapshot(sourceRoot.resolve("develop-latest"));

        ImportSnapshotResultDTO result = service(null).importSnapshot(new ImportSnapshotRequest(source.toString(), null, false));

        assertThat(result.snapshotId()).isEqualTo("develop-latest");
        assertThat(result.detail().modelFileAvailable()).isTrue();
        assertThat(Files.isRegularFile(importedModels().resolve("develop-latest").resolve(SnapshotTestFixtures.MODEL_FILE))).isTrue();
    }

    @Test
    void importUsesRequestedSnapshotId() {
        Path source = SnapshotTestFixtures.writeValidSnapshot(sourceRoot.resolve("raw"));

        ImportSnapshotResultDTO result = service(null).importSnapshot(new ImportSnapshotRequest(source.toString(), "renamed-snapshot", false));

        assertThat(result.snapshotId()).isEqualTo("renamed-snapshot");
        assertThat(Files.isDirectory(importedModels().resolve("renamed-snapshot"))).isTrue();
    }

    @Test
    void importRejectsMissingSource() {
        assertThatThrownBy(() -> service(null).importSnapshot(new ImportSnapshotRequest(sourceRoot.resolve("absent").toString(), null, false)))
                .isInstanceOf(SnapshotException.class).satisfies(error -> assertThat(((SnapshotException) error).getCode()).isEqualTo("SNAPSHOT_SOURCE_NOT_FOUND"));
    }

    @Test
    void importRejectsMissingWorkflowFile() throws Exception {
        Path source = SnapshotTestFixtures.writeValidSnapshot(sourceRoot.resolve("no-workflow"));
        Files.delete(source.resolve(SnapshotTestFixtures.WORKFLOW_FILE));

        assertThatThrownBy(() -> service(null).importSnapshot(new ImportSnapshotRequest(source.toString(), null, false))).isInstanceOf(SnapshotException.class)
                .satisfies(error -> assertThat(((SnapshotException) error).getCode()).isEqualTo("SNAPSHOT_MISSING_FILE"));
    }

    @Test
    void importRejectsWorkflowTargetingDifferentModel() {
        Path source = SnapshotTestFixtures.writeSnapshot(sourceRoot.resolve("mismatch"), SnapshotTestFixtures.MODEL_ID, SnapshotTestFixtures.MODEL_VERSION,
                "other-model", SnapshotTestFixtures.MODEL_VERSION, true, true, true);

        assertThatThrownBy(() -> service(null).importSnapshot(new ImportSnapshotRequest(source.toString(), null, false))).isInstanceOf(SnapshotException.class)
                .satisfies(error -> assertThat(((SnapshotException) error).getCode()).isEqualTo("GUIDED_WORKFLOW_MODEL_ID_MISMATCH"));
    }

    @Test
    void importRejectsChecksumMismatch() {
        Path source = SnapshotTestFixtures.writeValidSnapshot(sourceRoot.resolve("corrupt"));
        SnapshotTestFixtures.corruptChecksum(source);

        assertThatThrownBy(() -> service(null).importSnapshot(new ImportSnapshotRequest(source.toString(), null, false))).isInstanceOf(SnapshotException.class)
                .satisfies(error -> assertThat(((SnapshotException) error).getCode()).isEqualTo("SNAPSHOT_CHECKSUM_MISMATCH"));
    }

    @Test
    void importRejectsConstraintWithMissingTarget() throws Exception {
        Path source = SnapshotTestFixtures.writeValidSnapshot(sourceRoot.resolve("dangling-constraint"));
        Path modelFile = source.resolve(SnapshotTestFixtures.MODEL_FILE);
        String modelJson = Files.readString(modelFile).replace("\"constraints\": []", """
                "constraints": [
                    {
                      "id": "lecture-requires-ghost",
                      "type": "requires",
                      "source": "lecture",
                      "target": "ghost"
                    }
                  ]""");
        Files.writeString(modelFile, modelJson);
        Files.writeString(source.resolve(SnapshotTestFixtures.CHECKSUM_FILE), "sha256:" + SnapshotTestFixtures.sha256Hex(modelFile) + "\n");

        assertThatThrownBy(() -> service(null).importSnapshot(new ImportSnapshotRequest(source.toString(), null, false))).isInstanceOf(SnapshotException.class)
                .satisfies(error -> assertThat(((SnapshotException) error).getCode()).isEqualTo("MISSING_CONSTRAINT_TARGET"));
    }

    @Test
    void importRejectsMetadataModelMismatch() throws Exception {
        Path source = SnapshotTestFixtures.writeValidSnapshot(sourceRoot.resolve("bad-metadata"));
        Files.writeString(source.resolve(SnapshotTestFixtures.METADATA_FILE), "{ \"modelId\": \"other-model\", \"version\": \"1.0.0\" }");

        assertThatThrownBy(() -> service(null).importSnapshot(new ImportSnapshotRequest(source.toString(), null, false))).isInstanceOf(SnapshotException.class)
                .satisfies(error -> assertThat(((SnapshotException) error).getCode()).isEqualTo("SNAPSHOT_METADATA_MODEL_ID_MISMATCH"));
    }

    @Test
    void importRejectsExistingSnapshotWithoutOverwrite() {
        SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("develop-latest"));
        Path source = SnapshotTestFixtures.writeValidSnapshot(sourceRoot.resolve("develop-latest"));

        assertThatThrownBy(() -> service(null).importSnapshot(new ImportSnapshotRequest(source.toString(), "develop-latest", false)))
                .isInstanceOf(SnapshotException.class).satisfies(error -> assertThat(((SnapshotException) error).getCode()).isEqualTo("SNAPSHOT_ALREADY_EXISTS"));
    }

    @Test
    void exportsImportedSnapshotAsNonEmptyArchive() {
        SnapshotTestFixtures.writeValidSnapshot(importedModels().resolve("develop-latest"));

        byte[] archive = service(null).exportSnapshot("develop-latest");

        assertThat(archive).isNotEmpty();
    }
}
