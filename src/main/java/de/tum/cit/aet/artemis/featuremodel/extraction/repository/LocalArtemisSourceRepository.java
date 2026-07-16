package de.tum.cit.aet.artemis.featuremodel.extraction.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * {@link ArtemisSourceRepository} over a local Artemis checkout directory. The checkout is read strictly read-only;
 * the git commit is resolved once at construction time via {@code git rev-parse HEAD}.
 */
public class LocalArtemisSourceRepository implements ArtemisSourceRepository {

    private static final String UNKNOWN_COMMIT = "unknown";

    private static final int GIT_COMMAND_TIMEOUT_SECONDS = 30;

    private final Path root;

    private final String commit;

    private final Boolean workingTreeDirty;

    /**
     * Creates a repository over a local checkout and resolves its git state.
     *
     * @param root absolute path of the Artemis checkout root.
     * @throws IllegalArgumentException if the path is not an existing directory.
     */
    public LocalArtemisSourceRepository(Path root) {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Artemis checkout path is not an existing directory: " + root);
        }
        this.root = root.toAbsolutePath().normalize();
        if (Files.exists(this.root.resolve(".git"))) {
            this.commit = runGitCommand(List.of("git", "rev-parse", "HEAD")).orElse(UNKNOWN_COMMIT);
            this.workingTreeDirty = runGitCommand(List.of("git", "status", "--porcelain")).map(output -> !output.isBlank()).orElse(null);
        }
        else {
            this.commit = UNKNOWN_COMMIT;
            this.workingTreeDirty = null;
        }
    }

    @Override
    public String commit() {
        return commit;
    }

    @Override
    public Boolean workingTreeDirty() {
        return workingTreeDirty;
    }

    @Override
    public Path root() {
        return root;
    }

    @Override
    public boolean fileExists(String relativePath) {
        return Files.isRegularFile(root.resolve(relativePath));
    }

    @Override
    public String readFile(String relativePath) throws IOException {
        return Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8);
    }

    @Override
    public List<String> readLines(String relativePath) throws IOException {
        return Files.readAllLines(root.resolve(relativePath), StandardCharsets.UTF_8);
    }

    @Override
    public List<String> findFiles(String relativeDirectory, String fileNameSuffix) throws IOException {
        return walkMatchingFiles(relativeDirectory, path -> path.getFileName().toString().endsWith(fileNameSuffix));
    }

    @Override
    public List<String> findFilesByName(String relativeDirectory, String fileName) throws IOException {
        return walkMatchingFiles(relativeDirectory, path -> path.getFileName().toString().equals(fileName));
    }

    @Override
    public Optional<String> firstExisting(List<String> relativePaths) {
        return relativePaths.stream().filter(this::fileExists).findFirst();
    }

    /**
     * Walks a checkout-relative directory and collects matching regular files as sorted checkout-relative paths.
     *
     * @param relativeDirectory directory relative to the checkout root.
     * @param fileNameMatcher predicate on the file path.
     * @return sorted checkout-relative paths; empty when the directory does not exist.
     * @throws IOException if the directory cannot be traversed.
     */
    private List<String> walkMatchingFiles(String relativeDirectory, Predicate<Path> fileNameMatcher) throws IOException {
        Path directory = root.resolve(relativeDirectory);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).filter(fileNameMatcher).map(path -> root.relativize(path).toString().replace('\\', '/')).sorted().toList();
        }
    }

    /**
     * Runs a git command in the checkout root and returns its trimmed standard output.
     *
     * @param command git command and arguments.
     * @return command output, or empty when the command fails or times out.
     */
    private Optional<String> runGitCommand(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(false).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(GIT_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                return Optional.empty();
            }
            return Optional.of(output.trim());
        }
        catch (IOException e) {
            return Optional.empty();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
