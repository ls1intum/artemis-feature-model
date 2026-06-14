package de.tum.cit.aet.artemis.featuremodel.snapshot.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.SnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.LocalSnapshotRepository;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.selection.domain.GuidedWorkflow;
import de.tum.cit.aet.artemis.featuremodel.selection.service.GuidedWorkflowIntegrityService;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelIntegrityException;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.SnapshotException;
import de.tum.cit.aet.artemis.featuremodel.snapshot.dto.ImportSnapshotRequest;
import de.tum.cit.aet.artemis.featuremodel.snapshot.dto.ImportSnapshotResultDTO;
import de.tum.cit.aet.artemis.featuremodel.snapshot.dto.SnapshotDetailDTO;
import de.tum.cit.aet.artemis.featuremodel.snapshot.dto.SnapshotSummaryDTO;
import tools.jackson.databind.ObjectMapper;

/**
 * Manages local feature model snapshots: listing, detail, validated import, and deterministic export.
 *
 * <p>
 * Import validation reuses the same integrity services as runtime loading so an imported snapshot is rejected for the
 * same reasons the active model and workflow would be. A snapshot folder is only copied into the data root after it has
 * passed validation.
 */
@Service
public class SnapshotService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);

    private final LocalSnapshotRepository repository;

    private final ObjectMapper objectMapper;

    private final FeatureModelIntegrityService featureModelIntegrityService;

    private final GuidedWorkflowIntegrityService guidedWorkflowIntegrityService;

    /**
     * Creates the snapshot service.
     *
     * @param repository local snapshot repository used for file operations.
     * @param objectMapper Jackson mapper used to parse the model and workflow during import validation.
     * @param featureModelIntegrityService service used to validate the imported feature model.
     * @param guidedWorkflowIntegrityService service used to validate the imported workflow against the imported model.
     */
    public SnapshotService(LocalSnapshotRepository repository, ObjectMapper objectMapper, FeatureModelIntegrityService featureModelIntegrityService,
            GuidedWorkflowIntegrityService guidedWorkflowIntegrityService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.featureModelIntegrityService = featureModelIntegrityService;
        this.guidedWorkflowIntegrityService = guidedWorkflowIntegrityService;
    }

    /**
     * Lists imported snapshots with the active snapshot flagged.
     *
     * @return imported snapshot summaries.
     */
    public List<SnapshotSummaryDTO> listSnapshots() {
        String activeSnapshotId = repository.activeSnapshotId();
        return repository.listSnapshots().stream().map(metadata -> SnapshotSummaryDTO.from(metadata, metadata.snapshotId().equals(activeSnapshotId))).toList();
    }

    /**
     * Returns the detail of an imported snapshot.
     *
     * @param snapshotId snapshot id.
     * @return snapshot detail with file availability.
     * @throws SnapshotException if the id is invalid or the snapshot does not exist.
     */
    public SnapshotDetailDTO getSnapshot(String snapshotId) {
        Path directory = repository.requireSnapshotDirectory(snapshotId);
        return detail(directory, repository.readMetadata(directory));
    }

    /**
     * Imports a snapshot folder after validating its required files, integrity, checksum, and metadata, then registers
     * it under the data root.
     *
     * @param request import request with the source path and target id.
     * @return import result with the registered snapshot detail.
     * @throws SnapshotException if the source is missing or any validation fails.
     */
    public ImportSnapshotResultDTO importSnapshot(ImportSnapshotRequest request) {
        Path source = resolveSourceDirectory(request.sourcePath());
        SnapshotMetadata sourceMetadata = repository.readMetadata(source);
        String snapshotId = resolveTargetSnapshotId(request.snapshotId(), sourceMetadata);

        validateRequiredFiles(source, sourceMetadata);
        FeatureModel model = parseModel(source, sourceMetadata);
        GuidedWorkflow workflow = parseWorkflow(source, sourceMetadata);
        runIntegrityChecks(model, workflow);
        repository.verifyChecksum(source, sourceMetadata);
        validateMetadataMatchesModel(sourceMetadata, model);

        Path destination = repository.copySnapshot(source, snapshotId, request.overwrite());
        SnapshotMetadata storedMetadata = repository.readMetadata(destination);
        log.info("Imported feature model snapshot '{}' from {}.", snapshotId, source);
        return new ImportSnapshotResultDTO(snapshotId, "Snapshot '" + snapshotId + "' imported successfully.", detail(destination, storedMetadata));
    }

    /**
     * Exports an imported snapshot as a deterministic zip archive.
     *
     * @param snapshotId snapshot id.
     * @return zip archive bytes.
     * @throws SnapshotException if the id is invalid or the snapshot does not exist.
     */
    public byte[] exportSnapshot(String snapshotId) {
        Path directory = repository.requireSnapshotDirectory(snapshotId);
        return repository.zipSnapshot(directory, repository.readMetadata(directory));
    }

    /**
     * Builds the detail DTO for a snapshot folder.
     *
     * @param directory snapshot folder.
     * @param metadata snapshot metadata.
     * @return snapshot detail DTO.
     */
    private SnapshotDetailDTO detail(Path directory, SnapshotMetadata metadata) {
        boolean active = metadata.snapshotId().equals(repository.activeSnapshotId());
        return SnapshotDetailDTO.from(metadata, active, repository.hasFile(directory, metadata.modelFile()), repository.hasFile(directory, metadata.workflowFile()),
                repository.hasFile(directory, metadata.reportFile()), repository.hasFile(directory, metadata.checksumFile()));
    }

    /**
     * Resolves and validates the import source directory.
     *
     * @param sourcePath caller-provided source path.
     * @return normalized source directory.
     * @throws SnapshotException if the path is blank or not an existing directory.
     */
    private Path resolveSourceDirectory(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw SnapshotException.invalidImport("SNAPSHOT_SOURCE_REQUIRED", "An import source path is required.");
        }
        Path source = Path.of(sourcePath).normalize();
        if (!Files.isDirectory(source)) {
            throw SnapshotException.invalidImport("SNAPSHOT_SOURCE_NOT_FOUND", "Import source '" + sourcePath + "' is not an existing directory.");
        }
        return source;
    }

    /**
     * Determines the target snapshot id, preferring the explicit request id over the source folder name.
     *
     * @param requestedId requested snapshot id, may be blank.
     * @param sourceMetadata source metadata whose snapshot id is the source folder name.
     * @return target snapshot id.
     */
    private String resolveTargetSnapshotId(String requestedId, SnapshotMetadata sourceMetadata) {
        if (requestedId != null && !requestedId.isBlank()) {
            return requestedId;
        }
        return sourceMetadata.snapshotId();
    }

    /**
     * Ensures the source folder contains the required model and workflow files.
     *
     * @param source source folder.
     * @param metadata source metadata describing the file names.
     * @throws SnapshotException if a required file is missing.
     */
    private void validateRequiredFiles(Path source, SnapshotMetadata metadata) {
        requireFile(source, metadata.modelFile());
        requireFile(source, metadata.workflowFile());
    }

    /**
     * Requires a named file to exist in the source folder.
     *
     * @param source source folder.
     * @param fileName required file name.
     * @throws SnapshotException if the file is missing.
     */
    private void requireFile(Path source, String fileName) {
        if (!repository.hasFile(source, fileName)) {
            throw SnapshotException.invalidImport("SNAPSHOT_MISSING_FILE", "Import source is missing required file '" + fileName + "'.");
        }
    }

    /**
     * Parses the feature model from the source folder.
     *
     * @param source source folder.
     * @param metadata source metadata describing the model file.
     * @return parsed feature model.
     * @throws SnapshotException if the model file cannot be parsed.
     */
    private FeatureModel parseModel(Path source, SnapshotMetadata metadata) {
        try (InputStream inputStream = Files.newInputStream(source.resolve(metadata.modelFile()))) {
            return objectMapper.readValue(inputStream, FeatureModel.class);
        }
        catch (IOException | RuntimeException e) {
            throw SnapshotException.invalidImport("SNAPSHOT_MODEL_UNREADABLE", "Snapshot feature model '" + metadata.modelFile() + "' could not be parsed.");
        }
    }

    /**
     * Parses the guided workflow from the source folder.
     *
     * @param source source folder.
     * @param metadata source metadata describing the workflow file.
     * @return parsed guided workflow.
     * @throws SnapshotException if the workflow file cannot be parsed.
     */
    private GuidedWorkflow parseWorkflow(Path source, SnapshotMetadata metadata) {
        try (InputStream inputStream = Files.newInputStream(source.resolve(metadata.workflowFile()))) {
            return objectMapper.readValue(inputStream, GuidedWorkflow.class);
        }
        catch (IOException | RuntimeException e) {
            throw SnapshotException.invalidImport("SNAPSHOT_WORKFLOW_UNREADABLE", "Snapshot guided workflow '" + metadata.workflowFile() + "' could not be parsed.");
        }
    }

    /**
     * Runs the feature model and guided workflow integrity checks, translating integrity failures into controlled
     * import errors.
     *
     * @param model parsed feature model.
     * @param workflow parsed guided workflow.
     * @throws SnapshotException if the model or workflow fails integrity validation.
     */
    private void runIntegrityChecks(FeatureModel model, GuidedWorkflow workflow) {
        try {
            featureModelIntegrityService.validate(model);
            guidedWorkflowIntegrityService.validate(workflow, model);
        }
        catch (FeatureModelIntegrityException e) {
            throw SnapshotException.invalidImport(e.getCode(), e.getMessage());
        }
    }

    /**
     * Validates that the snapshot metadata model id and version, when present, match the imported feature model.
     *
     * @param metadata source metadata.
     * @param model imported feature model.
     * @throws SnapshotException if the metadata contradicts the feature model.
     */
    private void validateMetadataMatchesModel(SnapshotMetadata metadata, FeatureModel model) {
        String metadataModelId = metadata.modelId();
        String modelId = model.model().id();
        if (metadataModelId != null && modelId != null && !metadataModelId.equals(modelId)) {
            throw SnapshotException.invalidImport("SNAPSHOT_METADATA_MODEL_ID_MISMATCH",
                    "Snapshot metadata model id '" + metadataModelId + "' does not match the feature model id '" + modelId + "'.");
        }
        String metadataVersion = metadata.version();
        String modelVersion = model.model().version();
        if (metadataVersion != null && modelVersion != null && !metadataVersion.equals(modelVersion)) {
            throw SnapshotException.invalidImport("SNAPSHOT_METADATA_VERSION_MISMATCH",
                    "Snapshot metadata version '" + metadataVersion + "' does not match the feature model version '" + modelVersion + "'.");
        }
    }
}
