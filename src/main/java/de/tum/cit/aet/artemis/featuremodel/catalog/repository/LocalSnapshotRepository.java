package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.SnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.SnapshotException;
import tools.jackson.databind.ObjectMapper;

/**
 * File-based repository for local feature model snapshots stored under {@code <dataRoot>/imported-models/<snapshotId>/}.
 *
 * <p>
 * Each snapshot folder bundles a {@code feature-model.json} and a {@code guided-workflow.json} plus optional
 * {@code generation-report.json}, {@code checksum.txt}, and {@code metadata.json} files. This repository resolves the
 * configured active snapshot for the model and workflow stores, lists and reads imported snapshots, verifies checksums,
 * and copies or zips snapshot folders. It only deals in files and snapshot metadata; parsing of the feature model and
 * guided workflow domain types stays with their respective stores and services.
 */
@Repository
public class LocalSnapshotRepository {

    private static final Logger log = LoggerFactory.getLogger(LocalSnapshotRepository.class);

    private static final String IMPORTED_MODELS_DIR = "imported-models";

    private static final String METADATA_FILE = "metadata.json";

    private static final String CHECKSUM_PREFIX = "sha256:";

    private static final Pattern SNAPSHOT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    /** Fixed zip entry timestamp so exports are byte-for-byte deterministic. */
    private static final long FIXED_ENTRY_TIME = 0L;

    private final SnapshotProperties properties;

    private final ObjectMapper objectMapper;

    private volatile ResolvedSnapshot activeSnapshot;

    /**
     * Resolved active snapshot location and metadata.
     *
     * @param directory snapshot folder on disk.
     * @param metadata snapshot metadata.
     */
    private record ResolvedSnapshot(Path directory, SnapshotMetadata metadata) {
    }

    /**
     * Creates the local snapshot repository.
     *
     * @param properties snapshot configuration with the data root and active snapshot id.
     * @param objectMapper Jackson mapper used to read snapshot metadata.
     */
    public LocalSnapshotRepository(SnapshotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the configured active snapshot id, or {@code null} when the classpath fallback is used.
     *
     * @return active snapshot id or {@code null}.
     */
    public String activeSnapshotId() {
        return properties.activeSnapshotId();
    }

    /**
     * Resolves the active snapshot model file as a resource, or empty when no active snapshot is configured.
     *
     * @return active model resource, or empty for the classpath fallback.
     * @throws FeatureModelLoadException if the configured active snapshot cannot be resolved or fails verification.
     */
    public Optional<Resource> activeModelResource() {
        if (!properties.hasActiveSnapshot()) {
            return Optional.empty();
        }
        ResolvedSnapshot resolved = resolveActiveSnapshot();
        return Optional.of(new FileSystemResource(resolved.directory().resolve(resolved.metadata().modelFile())));
    }

    /**
     * Resolves the active snapshot guided workflow file as a resource, or empty when no active snapshot is configured.
     *
     * @return active guided workflow resource, or empty for the classpath fallback.
     * @throws FeatureModelLoadException if the configured active snapshot cannot be resolved or fails verification.
     */
    public Optional<Resource> activeWorkflowResource() {
        if (!properties.hasActiveSnapshot()) {
            return Optional.empty();
        }
        ResolvedSnapshot resolved = resolveActiveSnapshot();
        return Optional.of(new FileSystemResource(resolved.directory().resolve(resolved.metadata().workflowFile())));
    }

    /**
     * Lists imported snapshots with their metadata, sorted by snapshot id.
     *
     * @return imported snapshot metadata, empty when no snapshots are imported.
     * @throws FeatureModelLoadException if the imported models directory cannot be listed.
     */
    public List<SnapshotMetadata> listSnapshots() {
        Path root = importedModelsRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<SnapshotMetadata> snapshots = new ArrayList<>();
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(root)) {
            for (Path directory : directories) {
                if (Files.isDirectory(directory)) {
                    snapshots.add(readMetadata(directory));
                }
            }
        }
        catch (IOException e) {
            throw new FeatureModelLoadException("Could not list snapshots under " + root + ".", e);
        }
        snapshots.sort(Comparator.comparing(SnapshotMetadata::snapshotId));
        return List.copyOf(snapshots);
    }

    /**
     * Resolves and validates a snapshot folder by id, guarding against path traversal.
     *
     * @param snapshotId snapshot id.
     * @return snapshot folder on disk.
     * @throws SnapshotException if the id is invalid or the snapshot does not exist.
     */
    public Path requireSnapshotDirectory(String snapshotId) {
        validateSnapshotId(snapshotId);
        Path root = importedModelsRoot().normalize();
        Path directory = root.resolve(snapshotId).normalize();
        if (!directory.startsWith(root)) {
            throw SnapshotException.invalidId(snapshotId);
        }
        if (!Files.isDirectory(directory)) {
            throw SnapshotException.notFound(snapshotId);
        }
        return directory;
    }

    /**
     * Reads snapshot metadata from a folder, using the folder name as the authoritative snapshot id and synthesizing
     * minimal metadata when {@code metadata.json} is absent.
     *
     * @param directory snapshot folder.
     * @return snapshot metadata.
     * @throws SnapshotException if {@code metadata.json} exists but cannot be parsed.
     */
    public SnapshotMetadata readMetadata(Path directory) {
        String snapshotId = directory.getFileName().toString();
        Path metadataFile = directory.resolve(METADATA_FILE);
        if (!Files.isRegularFile(metadataFile)) {
            return new SnapshotMetadata(null, snapshotId, null, null, null, null, null, null, null, null, null, null);
        }
        try (InputStream inputStream = Files.newInputStream(metadataFile)) {
            SnapshotMetadata metadata = objectMapper.readValue(inputStream, SnapshotMetadata.class);
            return metadata.withSnapshotId(snapshotId);
        }
        catch (IOException | RuntimeException e) {
            throw SnapshotException.invalidImport("SNAPSHOT_METADATA_UNREADABLE", "Snapshot metadata in '" + snapshotId + "' could not be read.");
        }
    }

    /**
     * Checks whether a named file exists inside a snapshot folder.
     *
     * @param directory snapshot folder.
     * @param fileName file name to check.
     * @return true if the file exists.
     */
    public boolean hasFile(Path directory, String fileName) {
        return Files.isRegularFile(directory.resolve(fileName));
    }

    /**
     * Verifies the snapshot model checksum when a checksum file is present. Snapshots without a checksum file are
     * treated as valid because the checksum file is optional.
     *
     * @param directory snapshot folder.
     * @param metadata snapshot metadata pointing to the model and checksum files.
     * @throws SnapshotException if the checksum file is present but does not match the model file.
     */
    public void verifyChecksum(Path directory, SnapshotMetadata metadata) {
        Path checksumFile = directory.resolve(metadata.checksumFile());
        if (!Files.isRegularFile(checksumFile)) {
            return;
        }
        String expected = readExpectedChecksum(checksumFile);
        String actual = sha256Hex(directory.resolve(metadata.modelFile()));
        if (!expected.equalsIgnoreCase(actual)) {
            throw SnapshotException.invalidImport("SNAPSHOT_CHECKSUM_MISMATCH",
                    "Checksum mismatch for snapshot '" + directory.getFileName() + "'. The model file does not match " + metadata.checksumFile() + ".");
        }
    }

    /**
     * Copies the recognized files of a source snapshot folder into {@code <dataRoot>/imported-models/<snapshotId>/}.
     *
     * @param sourceDirectory validated source snapshot folder.
     * @param snapshotId target snapshot id.
     * @param overwrite whether to replace an existing snapshot with the same id.
     * @return destination snapshot folder.
     * @throws SnapshotException if the target already exists and overwrite is false.
     */
    public Path copySnapshot(Path sourceDirectory, String snapshotId, boolean overwrite) {
        validateSnapshotId(snapshotId);
        Path root = importedModelsRoot().normalize();
        Path destination = root.resolve(snapshotId).normalize();
        if (!destination.startsWith(root)) {
            throw SnapshotException.invalidId(snapshotId);
        }
        try {
            if (Files.exists(destination)) {
                if (!overwrite) {
                    throw SnapshotException.invalidImport("SNAPSHOT_ALREADY_EXISTS",
                            "Snapshot '" + snapshotId + "' already exists. Use overwrite to replace it.");
                }
                deleteRecursively(destination);
            }
            Files.createDirectories(destination);
            for (String fileName : recognizedFiles(readMetadata(sourceDirectory))) {
                Path sourceFile = sourceDirectory.resolve(fileName);
                if (Files.isRegularFile(sourceFile)) {
                    Files.copy(sourceFile, destination.resolve(fileName));
                }
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not copy snapshot '" + snapshotId + "'.", e);
        }
        return destination;
    }

    /**
     * Builds a deterministic zip archive of the recognized files in a snapshot folder, with entries sorted by name.
     *
     * @param directory snapshot folder.
     * @param metadata snapshot metadata describing the recognized files.
     * @return zip archive bytes.
     */
    public byte[] zipSnapshot(Path directory, SnapshotMetadata metadata) {
        List<String> fileNames = new ArrayList<>(recognizedFiles(metadata));
        fileNames.sort(Comparator.naturalOrder());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zipStream = new ZipOutputStream(buffer)) {
            for (String fileName : fileNames) {
                Path file = directory.resolve(fileName);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                ZipEntry entry = new ZipEntry(fileName);
                entry.setTime(FIXED_ENTRY_TIME);
                zipStream.putNextEntry(entry);
                zipStream.write(Files.readAllBytes(file));
                zipStream.closeEntry();
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not export snapshot '" + directory.getFileName() + "'.", e);
        }
        return buffer.toByteArray();
    }

    /**
     * Resolves the imported models root under the configured data root.
     *
     * @return imported models root path.
     */
    public Path importedModelsRoot() {
        return Path.of(properties.dataRoot(), IMPORTED_MODELS_DIR);
    }

    /**
     * Resolves and caches the configured active snapshot, validating its files and checksum once.
     *
     * @return resolved active snapshot.
     * @throws FeatureModelLoadException if the active snapshot cannot be resolved or fails verification.
     */
    private ResolvedSnapshot resolveActiveSnapshot() {
        ResolvedSnapshot cached = activeSnapshot;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (activeSnapshot == null) {
                activeSnapshot = loadActiveSnapshot();
            }
            return activeSnapshot;
        }
    }

    /**
     * Loads and verifies the configured active snapshot.
     *
     * @return resolved active snapshot.
     * @throws FeatureModelLoadException if the active snapshot is missing, malformed, or fails checksum verification.
     */
    private ResolvedSnapshot loadActiveSnapshot() {
        String snapshotId = properties.activeSnapshotId();
        try {
            Path directory = requireSnapshotDirectory(snapshotId);
            SnapshotMetadata metadata = readMetadata(directory);
            requireFile(directory, metadata.modelFile());
            requireFile(directory, metadata.workflowFile());
            verifyChecksum(directory, metadata);
            log.info("Resolved active local feature model snapshot '{}' from {}.", snapshotId, directory);
            return new ResolvedSnapshot(directory, metadata);
        }
        catch (SnapshotException e) {
            throw new FeatureModelLoadException("Active feature model snapshot '" + snapshotId + "' could not be loaded: " + e.getMessage(), e);
        }
    }

    /**
     * Ensures a required file exists inside the active snapshot folder.
     *
     * @param directory snapshot folder.
     * @param fileName required file name.
     * @throws SnapshotException if the file is missing.
     */
    private void requireFile(Path directory, String fileName) {
        if (!Files.isRegularFile(directory.resolve(fileName))) {
            throw SnapshotException.invalidImport("SNAPSHOT_MISSING_FILE", "Snapshot '" + directory.getFileName() + "' is missing required file '" + fileName + "'.");
        }
    }

    /**
     * Returns the recognized snapshot file names for copy and export, derived from the metadata.
     *
     * @param metadata snapshot metadata.
     * @return recognized file names.
     */
    private List<String> recognizedFiles(SnapshotMetadata metadata) {
        return List.of(metadata.modelFile(), metadata.workflowFile(), metadata.reportFile(), metadata.checksumFile(), METADATA_FILE);
    }

    /**
     * Validates a snapshot id against the allowed identifier pattern, rejecting path traversal and unsafe characters.
     *
     * @param snapshotId snapshot id.
     * @throws SnapshotException if the id is blank or invalid.
     */
    private void validateSnapshotId(String snapshotId) {
        if (snapshotId == null || !SNAPSHOT_ID_PATTERN.matcher(snapshotId).matches()) {
            throw SnapshotException.invalidId(String.valueOf(snapshotId));
        }
    }

    /**
     * Reads the expected checksum value from a checksum file, stripping an optional {@code sha256:} prefix and any
     * trailing file name or whitespace.
     *
     * @param checksumFile checksum file.
     * @return expected lowercase hex checksum.
     */
    private String readExpectedChecksum(Path checksumFile) {
        String content;
        try {
            content = Files.readString(checksumFile).strip();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not read checksum file " + checksumFile + ".", e);
        }
        String firstToken = content.split("\\s+", 2)[0];
        if (firstToken.regionMatches(true, 0, CHECKSUM_PREFIX, 0, CHECKSUM_PREFIX.length())) {
            firstToken = firstToken.substring(CHECKSUM_PREFIX.length());
        }
        return firstToken.toLowerCase();
    }

    /**
     * Computes the lowercase SHA-256 hex digest of a file.
     *
     * @param file file to hash.
     * @return lowercase hex digest.
     */
    private String sha256Hex(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(hash);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not read model file " + file + " for checksum verification.", e);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }

    /**
     * Recursively deletes a directory tree.
     *
     * @param directory directory to delete.
     * @throws IOException if deletion fails.
     */
    private void deleteRecursively(Path directory) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
