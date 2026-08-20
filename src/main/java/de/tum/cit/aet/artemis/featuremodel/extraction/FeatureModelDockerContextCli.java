package de.tum.cit.aet.artemis.featuremodel.extraction;

import java.nio.file.Path;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.DockerSnapshotContext;
import de.tum.cit.aet.artemis.featuremodel.extraction.snapshot.DockerSnapshotContextStager;
import tools.jackson.databind.ObjectMapper;

/** Command-line entry point for staging one validated snapshot as a Docker named context. */
public final class FeatureModelDockerContextCli {

    private FeatureModelDockerContextCli() {
    }

    /**
     * Stages the configured snapshot or exits non-zero with an actionable error.
     *
     * @param args {@code --snapshot-path=<path>} and {@code --output-path=<path>}.
     */
    public static void main(String[] args) {
        try {
            Path snapshot = requiredPath(args, "--snapshot-path=");
            Path output = requiredPath(args, "--output-path=");
            DockerSnapshotContext context = new DockerSnapshotContextStager(new ObjectMapper()).stage(snapshot, output);
            System.out.println("snapshotId=" + context.snapshotId());
            System.out.println("snapshotDigest=" + context.snapshotDigest());
            System.out.println("snapshotContext=" + context.snapshotDirectory());
            System.out.println("buildProperties=" + context.propertiesFile());
        }
        catch (Exception e) {
            System.err.println("Docker snapshot context staging failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static Path requiredPath(String[] args, String prefix) {
        for (String argument : args) {
            if (argument.startsWith(prefix) && argument.length() > prefix.length()) {
                return Path.of(argument.substring(prefix.length()));
            }
        }
        throw new IllegalArgumentException("Missing required option " + prefix + "<path>.");
    }
}
