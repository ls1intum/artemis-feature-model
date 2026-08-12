package de.tum.cit.aet.artemis.featuremodel.extraction.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureExtractionInputs;

/**
 * Test support that turns a checked-in fixture directory into a real temporary git repository, so {@code checkout}
 * mode can be exercised end to end: canonical manifest path resolution, HEAD derivation via {@code git rev-parse},
 * dirty-tree rejection, and revision-keyed layouts all run against genuine git state instead of a fixture stub.
 */
public final class SyntheticArtemisCheckout {

    private final Path root;

    private SyntheticArtemisCheckout(Path root) {
        this.root = root;
    }

    /**
     * Creates a synthetic checkout by copying a fixture tree into the given directory and committing it.
     *
     * @param directory empty directory that becomes the repository root.
     * @param fixtureSource fixture tree to copy, typically the mini-Artemis fixture.
     * @return initialized synthetic checkout at its first commit.
     * @throws IOException if copying or a git command fails.
     */
    public static SyntheticArtemisCheckout create(Path directory, Path fixtureSource) throws IOException {
        copyTree(fixtureSource, directory);
        SyntheticArtemisCheckout checkout = new SyntheticArtemisCheckout(directory);
        checkout.git("init", "-q");
        checkout.git("config", "user.name", "Synthetic Checkout");
        checkout.git("config", "user.email", "synthetic-checkout@example.invalid");
        checkout.commitAll("Initial synthetic checkout");
        return checkout;
    }

    /**
     * Returns the repository root.
     *
     * @return checkout root directory.
     */
    public Path root() {
        return root;
    }

    /**
     * Resolves the current HEAD commit.
     *
     * @return full commit hash of HEAD.
     * @throws IOException if the git command fails.
     */
    public String head() throws IOException {
        return git("rev-parse", "HEAD").trim();
    }

    /**
     * Writes manifest bytes to the canonical checkout manifest path and commits the change.
     *
     * @param manifestBytes exact manifest bytes to commit.
     * @return HEAD commit after the change.
     * @throws IOException if writing or committing fails.
     */
    public String commitCheckoutManifest(byte[] manifestBytes) throws IOException {
        Path manifest = root.resolve(FeatureExtractionInputs.CHECKOUT_MANIFEST_RELATIVE_PATH);
        Files.createDirectories(manifest.getParent());
        Files.write(manifest, manifestBytes);
        return commitAll("Add canonical feature manifest");
    }

    /**
     * Commits every pending change, creating a new revision.
     *
     * @param message commit message.
     * @return HEAD commit after the commit.
     * @throws IOException if a git command fails.
     */
    public String commitAll(String message) throws IOException {
        git("add", "-A");
        git("commit", "-q", "-m", message);
        return head();
    }

    /**
     * Commits an inert marker file, so the same tree content exists at a second revision.
     *
     * @return HEAD commit after the change.
     * @throws IOException if writing or committing fails.
     */
    public String commitMarkerRevision() throws IOException {
        Files.writeString(root.resolve("synthetic-revision-marker.txt"), "second revision\n", StandardCharsets.UTF_8);
        return commitAll("Second synthetic revision");
    }

    /**
     * Dirties the working tree with an uncommitted file.
     *
     * @throws IOException if the file cannot be written.
     */
    public void makeDirty() throws IOException {
        Files.writeString(root.resolve("uncommitted-change.txt"), "dirty\n", StandardCharsets.UTF_8);
    }

    /**
     * Runs one git command in the repository root.
     *
     * @param arguments git arguments.
     * @return trimmed standard output.
     * @throws IOException if the command cannot be started or exits non-zero.
     */
    private String git(String... arguments) throws IOException {
        List<String> command = new java.util.ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IOException("git " + String.join(" ", arguments) + " failed: " + output);
            }
            return output;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("git " + String.join(" ", arguments) + " was interrupted.", e);
        }
    }

    /**
     * Copies a fixture tree recursively.
     *
     * @param source fixture root.
     * @param destination target root.
     * @throws IOException if copying fails.
     */
    private static void copyTree(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
