package de.tum.cit.aet.artemis.featuremodel.extraction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotBundleContract;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotValidationResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.service.FeatureModelSnapshotValidator;
import tools.jackson.databind.ObjectMapper;

/**
 * Refreshes the classpath fixture from a validated generated snapshot. The snapshot's feature model and config-key
 * catalog are copied wholesale over the classpath resources — their paths never move — and a provenance sidecar
 * records which snapshot the fixture is a copy of. All content is derived from the snapshot, so running the refresh
 * twice against the same snapshot is a byte-identical no-op.
 */
public final class FeatureModelFixtureRefreshCli {

    private static final String OPTION_SNAPSHOT_PATH = "snapshot-path";

    private static final String OPTION_RESOURCE_DIR = "resource-dir";

    private static final String FIXTURE_MODEL_FILE = "functional-feature-model.json";

    private static final String FIXTURE_CATALOG_FILE = "artemis-config-key-catalog.json";

    private static final String FIXTURE_PROVENANCE_FILE = "fixture-provenance.json";

    private FeatureModelFixtureRefreshCli() {
    }

    /**
     * Validates the snapshot, copies its model and catalog over the classpath fixture, and writes the provenance
     * sidecar.
     *
     * @param arguments {@code --snapshot-path=<directory>} and {@code --resource-dir=<feature-model resource dir>}.
     */
    public static void main(String[] arguments) {
        try {
            Map<String, String> options = ExtractionCommandOptions.parse(arguments, Set.of(OPTION_SNAPSHOT_PATH, OPTION_RESOURCE_DIR));
            Path snapshotPath = requiredPath(options, OPTION_SNAPSHOT_PATH);
            Path resourceDirectory = requiredPath(options, OPTION_RESOURCE_DIR);

            SnapshotValidationResult validation = new FeatureModelSnapshotValidator(new ObjectMapper()).validate(snapshotPath);
            boolean modelChanged = copyIfChanged(snapshotPath.resolve(SnapshotBundleContract.SNAPSHOT_MODEL_FILE), resourceDirectory.resolve(FIXTURE_MODEL_FILE));
            boolean catalogChanged = copyIfChanged(snapshotPath.resolve(SnapshotBundleContract.SNAPSHOT_CATALOG_FILE), resourceDirectory.resolve(FIXTURE_CATALOG_FILE));
            boolean provenanceChanged = writeProvenanceIfChanged(resourceDirectory.resolve(FIXTURE_PROVENANCE_FILE), validation);

            System.out.println("snapshotId=" + validation.snapshotId());
            System.out.println("artemisCommit=" + validation.artemisCommit());
            System.out.println("manifestDigest=" + validation.manifestDigest());
            System.out.println("changed=" + (modelChanged || catalogChanged || provenanceChanged));
        }
        catch (Exception e) {
            System.err.println("Fixture refresh failed: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Reads a required path option.
     *
     * @param options parsed options.
     * @param option option name.
     * @return configured path.
     * @throws IllegalArgumentException if the option is absent or blank.
     */
    private static Path requiredPath(Map<String, String> options, String option) {
        String value = options.get(option);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option --" + option + ".");
        }
        return Path.of(value);
    }

    /**
     * Copies the source bytes over the target only when they differ, keeping a repeated refresh byte-identical.
     *
     * @param source validated snapshot payload.
     * @param target classpath fixture file.
     * @return true when the target content changed.
     * @throws IOException if reading or writing fails.
     */
    private static boolean copyIfChanged(Path source, Path target) throws IOException {
        byte[] content = Files.readAllBytes(source);
        if (Files.exists(target) && Arrays.equals(content, Files.readAllBytes(target))) {
            return false;
        }
        Files.write(target, content);
        return true;
    }

    /**
     * Writes the fixture provenance sidecar naming the source snapshot, only when its content changed.
     *
     * @param target sidecar path.
     * @param validation validated snapshot identity.
     * @return true when the sidecar content changed.
     * @throws IOException if writing fails.
     */
    private static boolean writeProvenanceIfChanged(Path target, SnapshotValidationResult validation) throws IOException {
        String content = """
                {
                  "snapshotId" : "%s",
                  "artemisCommit" : "%s",
                  "manifestDigest" : "%s"
                }
                """.formatted(validation.snapshotId(), validation.artemisCommit(), validation.manifestDigest());
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (Files.exists(target) && Arrays.equals(bytes, Files.readAllBytes(target))) {
            return false;
        }
        Files.write(target, bytes);
        return true;
    }
}
