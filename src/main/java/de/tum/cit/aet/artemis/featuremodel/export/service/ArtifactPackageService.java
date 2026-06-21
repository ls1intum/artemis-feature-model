package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    /** Fixed entry timestamp (2020-01-01T00:00:00Z) so archives are reproducible for the same input. */
    private static final long FIXED_ENTRY_TIME = 1_577_836_800_000L;

    /**
     * Builds the ZIP archive for a generated artifact package.
     *
     * @param artifactPackage generated artifact package.
     * @return ZIP archive bytes.
     */
    public byte[] zip(GeneratedArtifactPackage artifactPackage) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zipStream = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (GeneratedArtifactFile file : artifactPackage.files()) {
                ZipEntry entry = new ZipEntry(ROOT_DIR + file.path());
                entry.setTime(FIXED_ENTRY_TIME);
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
