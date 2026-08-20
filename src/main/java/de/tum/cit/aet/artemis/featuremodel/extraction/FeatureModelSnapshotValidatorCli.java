package de.tum.cit.aet.artemis.featuremodel.extraction;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.SnapshotValidationResult;
import de.tum.cit.aet.artemis.featuremodel.extraction.snapshot.FeatureModelSnapshotValidator;
import tools.jackson.databind.ObjectMapper;

/** Command-line boundary of pure offline snapshot validation. */
public final class FeatureModelSnapshotValidatorCli {

    private static final String OPTION_SNAPSHOT_PATH = "snapshot-path";

    private FeatureModelSnapshotValidatorCli() {
    }

    /**
     * Validates a snapshot directory and prints its immutable identity.
     *
     * @param arguments one {@code --snapshot-path=<directory>} option.
     */
    public static void main(String[] arguments) {
        try {
            Map<String, String> options = ExtractionCommandOptions.parse(arguments, Set.of(OPTION_SNAPSHOT_PATH));
            String configuredPath = options.get(OPTION_SNAPSHOT_PATH);
            if (configuredPath == null || configuredPath.isBlank()) {
                throw new IllegalArgumentException("Missing required option --" + OPTION_SNAPSHOT_PATH + ".");
            }
            SnapshotValidationResult result = new FeatureModelSnapshotValidator(new ObjectMapper()).validate(Path.of(configuredPath));
            System.out.println("snapshotId=" + result.snapshotId());
            System.out.println("snapshotDigest=" + result.snapshotDigest());
            System.out.println("artemisCommit=" + result.artemisCommit());
            System.out.println("manifestDigest=" + result.manifestDigest());
            System.out.println("payloadCount=" + result.payloadCount());
        }
        catch (Exception e) {
            System.err.println("Snapshot validation failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
