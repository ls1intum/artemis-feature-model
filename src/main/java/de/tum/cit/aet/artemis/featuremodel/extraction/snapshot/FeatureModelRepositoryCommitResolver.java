package de.tum.cit.aet.artemis.featuremodel.extraction.snapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Resolves the immutable feature-model repository commit recorded in snapshot provenance. */
class FeatureModelRepositoryCommitResolver {

    /**
     * Resolves {@code HEAD} without consulting a network remote.
     *
     * @return full lowercase repository commit.
     * @throws IOException if Git cannot resolve the current repository.
     */
    String resolve() throws IOException {
        Process process = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        try {
            if (process.waitFor() != 0 || !output.matches("[0-9a-f]{40}")) {
                throw new IOException("Could not resolve the feature-model repository commit: " + output);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while resolving the feature-model repository commit.", e);
        }
        return output;
    }
}
