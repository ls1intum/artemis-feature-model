package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.extraction.artifact.ArtifactDirectoryOperations;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.DockerSnapshotContext;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotBundleContract;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotProvenance;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotValidationResult;
import tools.jackson.databind.ObjectMapper;

/** Stages only one completely validated snapshot for use as a Docker BuildKit named context. */
public class DockerSnapshotContextStager {

    /** Name of the subdirectory supplied to {@code docker build --build-context}. */
    public static final String SNAPSHOT_CONTEXT_DIRECTORY = "snapshot";

    /** Deterministic build-argument file written beside, but not inside, the named context. */
    public static final String BUILD_PROPERTIES_FILE = "image-build.properties";

    private final ObjectMapper objectMapper;

    private final FeatureModelSnapshotValidator validator;

    private final ArtifactDirectoryOperations directoryOperations;

    /**
     * Creates a controlled Docker context stager.
     *
     * @param objectMapper mapper used for snapshot provenance.
     */
    public DockerSnapshotContextStager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.validator = new FeatureModelSnapshotValidator(objectMapper);
        this.directoryOperations = new ArtifactDirectoryOperations();
    }

    /**
     * Validates the source, copies its exact file set into a temporary sibling, revalidates the copy, and publishes it
     * atomically. An invalid source leaves an existing staged context untouched.
     *
     * @param sourceSnapshot complete generated snapshot.
     * @param contextRoot controlled output root.
     * @return published context and immutable image build arguments.
     * @throws IOException if validation, copying, or atomic publication fails.
     */
    public DockerSnapshotContext stage(Path sourceSnapshot, Path contextRoot) throws IOException {
        Path normalizedSource = sourceSnapshot.toAbsolutePath().normalize();
        Path normalizedContext = contextRoot.toAbsolutePath().normalize();
        if (normalizedContext.startsWith(normalizedSource) || normalizedSource.startsWith(normalizedContext)) {
            throw new IOException("Docker snapshot context must not contain or replace its source snapshot.");
        }
        SnapshotValidationResult sourceValidation = validator.validate(normalizedSource);
        SnapshotProvenance provenance = objectMapper.readValue(Files.readAllBytes(normalizedSource.resolve(SnapshotBundleContract.SNAPSHOT_PROVENANCE_FILE)), SnapshotProvenance.class);

        Path parent = normalizedContext.getParent();
        if (parent == null) {
            throw new IOException("Docker snapshot context requires a parent directory.");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempDirectory(parent, ".feature-model-docker-context-");
        try {
            Path stagedSnapshot = Files.createDirectory(temporary.resolve(SNAPSHOT_CONTEXT_DIRECTORY));
            copyRegularFiles(normalizedSource, stagedSnapshot);
            SnapshotValidationResult stagedValidation = validator.validate(stagedSnapshot);
            if (!sourceValidation.equals(stagedValidation)) {
                throw new IOException("Staged Docker snapshot identity differs from its validated source.");
            }
            Path propertiesFile = temporary.resolve(BUILD_PROPERTIES_FILE);
            Files.writeString(propertiesFile, buildProperties(sourceValidation, provenance), StandardCharsets.UTF_8);
            directoryOperations.deleteRecursively(normalizedContext);
            movePublished(temporary, normalizedContext);
            return context(normalizedContext, sourceValidation, provenance);
        }
        finally {
            directoryOperations.deleteRecursively(temporary);
        }
    }

    private void copyRegularFiles(Path source, Path destination) throws IOException {
        List<Path> files;
        try (var paths = Files.list(source)) {
            files = new ArrayList<>(paths.toList());
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        for (Path file : files) {
            Files.copy(file, destination.resolve(file.getFileName()), StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private String buildProperties(SnapshotValidationResult validation, SnapshotProvenance provenance) {
        return "ARTEMIS_COMMIT=" + validation.artemisCommit() + "\n" + "EXTRACTOR_VERSION=" + provenance.extractorVersion() + "\n"
                + "FEATURE_MODEL_REPOSITORY_COMMIT=" + provenance.featureModelRepositoryCommit() + "\n" + "MANIFEST_DIGEST="
                + validation.manifestDigest() + "\n" + "SNAPSHOT_DIGEST=" + validation.snapshotDigest() + "\n" + "SNAPSHOT_ID="
                + validation.snapshotId() + "\n";
    }

    private void movePublished(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target);
        }
    }

    private DockerSnapshotContext context(Path root, SnapshotValidationResult validation, SnapshotProvenance provenance) {
        return new DockerSnapshotContext(root, root.resolve(SNAPSHOT_CONTEXT_DIRECTORY), root.resolve(BUILD_PROPERTIES_FILE), validation.snapshotId(),
                validation.snapshotDigest(), validation.artemisCommit(), validation.manifestDigest(), provenance.featureModelRepositoryCommit(),
                provenance.extractorVersion());
    }
}
