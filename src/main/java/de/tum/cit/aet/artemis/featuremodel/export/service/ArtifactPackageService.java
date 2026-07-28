package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;

/**
 * Assembles a generated artifact package into a downloadable ZIP archive.
 *
 * <p>
 * Files are written under a single {@code artemis-feature-model-artifacts/} root directory in the package's file order,
 * with a fixed entry timestamp, so the archive is byte-for-byte deterministic for the same generation input.
 */
@Service
public class ArtifactPackageService {

    /** Download file name for the generated artifact package. */
    public static final String PACKAGE_FILE_NAME = "artemis-feature-model-artifacts.zip";

    private static final String ROOT_DIR = "artemis-feature-model-artifacts/";

    /** Fixed ZIP-local timestamp that preserves the archive bytes historically generated in Europe/Berlin. */
    private static final LocalDateTime FIXED_ENTRY_TIME = LocalDateTime.of(2020, 1, 1, 1, 0);

    /**
     * Builds the ZIP archive for a generated artifact package under the default Phase 5 root directory.
     *
     * @param artifactPackage generated artifact package.
     * @return ZIP archive bytes.
     */
    public byte[] zip(GeneratedArtifactPackage artifactPackage) {
        return zip(artifactPackage, ROOT_DIR);
    }

    /**
     * Builds the ZIP archive for a generated package under a caller-provided root directory. Reused by the Phase 6
     * deployment package, which uses a distinct root directory while keeping the same deterministic ordering and fixed
     * entry timestamp.
     *
     * @param artifactPackage generated package.
     * @param rootDir root directory prefix (must end with {@code /}) every entry is written under.
     * @return ZIP archive bytes.
     */
    public byte[] zip(GeneratedArtifactPackage artifactPackage, String rootDir) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zipStream = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (GeneratedArtifactFile file : artifactPackage.files()) {
                ZipEntry entry = new ZipEntry(rootDir + file.path());
                entry.setTimeLocal(FIXED_ENTRY_TIME);
                zipStream.putNextEntry(entry);
                zipStream.write(file.content().getBytes(StandardCharsets.UTF_8));
                zipStream.closeEntry();
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to assemble the artifact ZIP package.", exception);
        }
        return output.toByteArray();
    }
}
