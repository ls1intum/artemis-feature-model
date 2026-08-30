package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentRepositoryPublishResult;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentRepositoryPublishException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes a generated deployment package as a commit to the configured deployment repository. One publish is a
 * fresh shallow single-branch clone into a temporary directory, a wholesale replacement of the target's package
 * directory, and an atomic push — never a force push, so no existing commit can be discarded by construction. A push
 * the remote rejects because the branch advanced concurrently is retried from a fresh clone of the new head, up to
 * three attempts; the retried commit lands on top of the concurrent winner's commit.
 *
 * <p>
 * The access token is read exclusively from the {@code FM_DEPLOYMENT_REPO_TOKEN} environment variable, applied as
 * JGit HTTPS credentials, and never logged, serialized, or embedded in a URL. {@code file://} remotes need no
 * credential, which keeps the full publish flow unit-testable offline.
 */
@Component
public class DeploymentRepositoryPublisher {

    /** Environment variable holding the deployment repository access token; never a configuration property. */
    static final String TOKEN_ENV_VAR = "FM_DEPLOYMENT_REPO_TOKEN";

    /** Directory under the target directory that the publisher replaces wholesale; nothing else is ever touched. */
    static final String PACKAGE_DIRECTORY_NAME = "package";

    private static final Logger log = LoggerFactory.getLogger(DeploymentRepositoryPublisher.class);

    private static final int MAX_PUSH_ATTEMPTS = 3;

    private static final String GITHUB_REMOTE_PREFIX = "https://github.com/";

    private final DeploymentRepositoryProperties properties;

    private final ObjectMapper objectMapper;

    private final UnaryOperator<String> environmentReader;

    /** Serializes publishes within this instance; cross-instance safety rests on the atomic push alone. */
    private final Object publishLock = new Object();

    /** Whether the github.com visibility expectation was verified in this process lifetime. */
    private volatile boolean visibilityVerified;

    /**
     * Creates the publisher against the process environment.
     *
     * @param properties deployment repository configuration.
     * @param objectMapper Jackson mapper used to parse the GitHub repository-visibility response.
     */
    @Autowired
    public DeploymentRepositoryPublisher(DeploymentRepositoryProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, System::getenv);
    }

    /**
     * Creates the publisher with a caller-provided environment reader; used by tests.
     *
     * @param properties deployment repository configuration.
     * @param objectMapper Jackson mapper used to parse the GitHub repository-visibility response.
     * @param environmentReader environment variable reader.
     */
    DeploymentRepositoryPublisher(DeploymentRepositoryProperties properties, ObjectMapper objectMapper, UnaryOperator<String> environmentReader) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.environmentReader = environmentReader;
    }

    /**
     * Checks whether publishing is fully configured: enabled, a repository URL present, and — for an HTTP(S) remote —
     * the token environment variable set.
     *
     * @return whether a publish can be attempted.
     */
    public boolean isConfigured() {
        return configurationGap() == null;
    }

    /**
     * Requires publishing to be fully configured.
     *
     * @throws DeploymentRepositoryPublishException if publishing is disabled or incompletely configured.
     */
    public void requireConfigured() {
        String gap = configurationGap();
        if (gap != null) {
            throw DeploymentRepositoryPublishException.notConfigured(gap);
        }
    }

    /**
     * Derives the sanitized target directory name of a target name: lowercase with every character outside
     * {@code [a-z0-9-]} removed.
     *
     * @param targetName raw target name.
     * @return sanitized target directory name.
     * @throws DeploymentRepositoryPublishException if the target name is absent or sanitizes to nothing.
     */
    public String sanitizedTargetName(String targetName) {
        String sanitized = targetName == null ? "" : targetName.strip().toLowerCase().replaceAll("[^a-z0-9-]", "");
        if (sanitized.isEmpty()) {
            throw DeploymentRepositoryPublishException.requiresTargetName();
        }
        return sanitized;
    }

    /**
     * Returns the configured, credential-free deployment repository properties for response building.
     *
     * @return deployment repository configuration.
     */
    public DeploymentRepositoryProperties properties() {
        return properties;
    }

    /**
     * Derives the repository-relative package directory of a target name: the configured root, the sanitized target
     * directory name, and the package directory.
     *
     * @param targetName raw target name.
     * @return repository-relative package directory.
     * @throws DeploymentRepositoryPublishException if the target name is absent or sanitizes to nothing.
     */
    public String targetDirectoryFor(String targetName) {
        return properties.targetDirectoryRoot() + "/" + sanitizedTargetName(targetName) + "/" + PACKAGE_DIRECTORY_NAME;
    }

    /**
     * Publishes the package files of one target as a commit on the configured deployment branch. The target's package
     * directory is replaced wholesale, so files that stop being generated are deleted; an unchanged tree produces no
     * commit and reports up to date.
     *
     * @param targetName raw target name routing the package to its repository directory.
     * @param files generated package files, byte-identical to the download payload.
     * @param commitMessage commit message describing the published variant.
     * @return publish result with the carrying commit.
     * @throws DeploymentRepositoryPublishException if publishing is not configured, the target name is missing, the
     *             remote refuses the credential, or every push attempt is rejected.
     */
    public DeploymentRepositoryPublishResult publish(String targetName, List<GeneratedArtifactFile> files, String commitMessage) {
        requireConfigured();
        String targetDirectory = targetDirectoryFor(targetName);
        verifyExpectedVisibility();
        synchronized (publishLock) {
            String lastRejection = "the push was rejected";
            for (int attempt = 1; attempt <= MAX_PUSH_ATTEMPTS; attempt++) {
                AttemptOutcome outcome = attemptPublish(targetDirectory, files, commitMessage, attempt);
                if (outcome.result() != null) {
                    return outcome.result();
                }
                lastRejection = outcome.rejectionMessage();
                log.info("Deployment repository push attempt {} of {} was rejected; retrying from a fresh clone.", attempt, MAX_PUSH_ATTEMPTS);
            }
            throw DeploymentRepositoryPublishException.rejected(lastRejection);
        }
    }

    /**
     * Names the first configuration gap that prevents publishing.
     *
     * @return human-readable gap, or {@code null} when publishing is fully configured.
     */
    private String configurationGap() {
        if (!properties.enabled()) {
            return "it is disabled (artemis.feature-model.deployment-repository.enabled=false).";
        }
        if (properties.repositoryUrl() == null || properties.repositoryUrl().isBlank()) {
            return "no repository URL is configured.";
        }
        if (isHttpRemote() && !hasToken()) {
            return "the " + TOKEN_ENV_VAR + " environment variable is not set.";
        }
        return null;
    }

    /**
     * Runs one publish attempt: fresh clone, wholesale directory replacement, commit when the tree changed, push.
     *
     * @param targetDirectory repository-relative package directory.
     * @param files generated package files.
     * @param commitMessage commit message.
     * @param attempt one-based attempt number.
     * @return the publish result, or the rejection message for a retry.
     * @throws DeploymentRepositoryPublishException if the remote refuses the credential or the transport fails.
     */
    private AttemptOutcome attemptPublish(String targetDirectory, List<GeneratedArtifactFile> files, String commitMessage, int attempt) {
        Path tempDir = createTempDirectory();
        try {
            boolean branchExists = remoteBranchExists();
            try (Git git = cloneRepository(tempDir, branchExists)) {
                if (!branchExists) {
                    requireDefaultHead(git);
                    git.checkout().setCreateBranch(true).setName(properties.branch()).call();
                }
                replacePackageDirectory(tempDir, targetDirectory, files);
                git.add().addFilepattern(targetDirectory).call();
                git.add().setUpdate(true).addFilepattern(targetDirectory).call();
                Status status = git.status().call();
                if (status.isClean() && branchExists) {
                    ObjectId head = git.getRepository().resolve(Constants.HEAD);
                    return AttemptOutcome.of(new DeploymentRepositoryPublishResult(properties.branch(), targetDirectory, head.name(), true));
                }
                String commitSha = git.getRepository().resolve(Constants.HEAD).name();
                if (!status.isClean()) {
                    PersonIdent author = new PersonIdent(properties.authorName(), properties.authorEmail());
                    RevCommit commit = git.commit().setMessage(commitMessage).setAuthor(author).setCommitter(author).setSign(false).call();
                    commitSha = commit.name();
                }
                beforePush(attempt);
                String rejection = push(git);
                if (rejection != null) {
                    return AttemptOutcome.retry(rejection);
                }
                return AttemptOutcome.of(new DeploymentRepositoryPublishResult(properties.branch(), targetDirectory, commitSha, false));
            }
        }
        catch (TransportException e) {
            throw asControlledTransportFailure(e);
        }
        catch (GitAPIException | IOException e) {
            throw DeploymentRepositoryPublishException.rejected(e.getMessage());
        }
        finally {
            deleteRecursively(tempDir);
        }
    }

    /**
     * Test hook invoked between commit and push; the race tests advance the remote here.
     *
     * @param attempt one-based attempt number.
     */
    protected void beforePush(int attempt) {
        // Intentionally empty: the production publisher pushes immediately.
    }

    /**
     * Verifies the declared visibility of a {@code github.com} deployment repository once per process lifetime, so a
     * publish never lands in a repository the operator does not think they are publishing to. The check runs only for
     * {@code github.com} remotes with a declared expectation; other remotes (including {@code file://} test remotes)
     * skip it.
     *
     * @throws DeploymentRepositoryPublishException if the actual visibility differs from the expectation or cannot be
     *             determined.
     */
    private void verifyExpectedVisibility() {
        if (visibilityVerified) {
            return;
        }
        String expected = properties.expectedVisibility();
        if (!properties.repositoryUrl().startsWith(GITHUB_REMOTE_PREFIX) || expected == null || expected.isBlank()) {
            visibilityVerified = true;
            return;
        }
        String actual = fetchGitHubVisibility();
        if (!expected.equalsIgnoreCase(actual)) {
            throw DeploymentRepositoryPublishException.visibilityMismatch(expected, actual);
        }
        visibilityVerified = true;
    }

    /**
     * Reads the actual visibility of the configured {@code github.com} repository from the GitHub API. The request
     * authenticates with the token; failure messages never contain it.
     *
     * @return actual repository visibility, for example {@code public} or {@code private}.
     * @throws DeploymentRepositoryPublishException if the visibility cannot be determined.
     */
    protected String fetchGitHubVisibility() {
        String repositoryPath = properties.repositoryUrl().substring(GITHUB_REMOTE_PREFIX.length());
        if (repositoryPath.endsWith(".git")) {
            repositoryPath = repositoryPath.substring(0, repositoryPath.length() - ".git".length());
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repositoryPath))
                .header("Accept", "application/vnd.github+json").header("Authorization", "Bearer " + environmentReader.apply(TOKEN_ENV_VAR)).GET().build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw DeploymentRepositoryPublishException.visibilityMismatch(properties.expectedVisibility(),
                        "unknown; the GitHub API answered HTTP " + response.statusCode());
            }
            JsonNode repository = objectMapper.readTree(response.body());
            JsonNode visibility = repository.get("visibility");
            if (visibility == null) {
                return repository.path("private").asBoolean(false) ? "private" : "public";
            }
            return visibility.asString();
        }
        catch (IOException e) {
            throw DeploymentRepositoryPublishException.visibilityMismatch(properties.expectedVisibility(),
                    "unknown; the GitHub API could not be reached");
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw DeploymentRepositoryPublishException.visibilityMismatch(properties.expectedVisibility(),
                    "unknown; the GitHub API request was interrupted");
        }
    }

    /**
     * Pushes the deployment branch without force.
     *
     * @param git open clone.
     * @return the remote's rejection message, or {@code null} on success.
     * @throws GitAPIException if the push transport fails.
     */
    private String push(Git git) throws GitAPIException {
        String branchRef = Constants.R_HEADS + properties.branch();
        Iterable<PushResult> results = git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(new RefSpec(branchRef + ":" + branchRef))
                .setCredentialsProvider(credentialsProvider()).call();
        for (PushResult result : results) {
            for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                if (update.getStatus() != RemoteRefUpdate.Status.OK && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
                    String message = update.getMessage() == null ? update.getStatus().name() : update.getMessage();
                    return "the remote refused ref " + update.getRemoteName() + ": " + message;
                }
            }
        }
        return null;
    }

    /**
     * Checks whether the configured deployment branch exists on the remote.
     *
     * @return whether the branch exists.
     * @throws GitAPIException if the remote cannot be contacted.
     */
    private boolean remoteBranchExists() throws GitAPIException {
        String branchRef = Constants.R_HEADS + properties.branch();
        for (Ref ref : Git.lsRemoteRepository().setRemote(properties.repositoryUrl()).setHeads(true).setCredentialsProvider(credentialsProvider()).call()) {
            if (branchRef.equals(ref.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clones the deployment repository into a temporary directory: shallow and single-branch when the deployment
     * branch exists, the remote default head otherwise (the one-time branch bootstrap).
     *
     * @param tempDir clone target directory.
     * @param branchExists whether the deployment branch exists on the remote.
     * @return open clone.
     * @throws GitAPIException if the clone fails.
     */
    private Git cloneRepository(Path tempDir, boolean branchExists) throws GitAPIException {
        CloneCommand clone = Git.cloneRepository().setURI(properties.repositoryUrl()).setDirectory(tempDir.toFile()).setDepth(1)
                .setCredentialsProvider(credentialsProvider());
        if (branchExists) {
            clone.setBranchesToClone(List.of(Constants.R_HEADS + properties.branch())).setBranch(properties.branch());
        }
        return clone.call();
    }

    /**
     * Requires the cloned repository to have a default branch head to bootstrap the deployment branch from.
     *
     * @param git open clone.
     * @throws IOException if the repository cannot be read.
     * @throws DeploymentRepositoryPublishException if the remote has no commit on its default branch.
     */
    private void requireDefaultHead(Git git) throws IOException {
        if (git.getRepository().resolve(Constants.HEAD) == null) {
            throw DeploymentRepositoryPublishException
                    .rejected("the deployment repository has no default branch head to create branch '" + properties.branch() + "' from.");
        }
    }

    /**
     * Replaces the target's package directory wholesale with the generated files, so files that stop being generated
     * disappear from the tree.
     *
     * @param workTree clone work tree root.
     * @param targetDirectory repository-relative package directory.
     * @param files generated package files.
     * @throws IOException if a file cannot be written.
     */
    private void replacePackageDirectory(Path workTree, String targetDirectory, List<GeneratedArtifactFile> files) throws IOException {
        Path packageDir = workTree.resolve(targetDirectory);
        deleteRecursively(packageDir);
        for (GeneratedArtifactFile file : files) {
            Path target = packageDir.resolve(file.path());
            Files.createDirectories(target.getParent());
            Files.write(target, file.content().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Maps a transport failure to its controlled publish exception, keeping the message token-free.
     *
     * @param exception JGit transport failure.
     * @return controlled publish exception.
     */
    private DeploymentRepositoryPublishException asControlledTransportFailure(TransportException exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        boolean authFailure = message.contains("not authorized") || message.contains("authentication") || message.contains("Authentication");
        if (isHttpRemote() && authFailure) {
            return DeploymentRepositoryPublishException.authFailed(message);
        }
        return DeploymentRepositoryPublishException.rejected(message);
    }

    /**
     * Builds the HTTPS credentials provider from the token environment variable; {@code file://} remotes use none.
     *
     * @return credentials provider, or {@code null} for non-HTTP remotes.
     */
    private CredentialsProvider credentialsProvider() {
        if (!isHttpRemote()) {
            return null;
        }
        return new UsernamePasswordCredentialsProvider("x-access-token", environmentReader.apply(TOKEN_ENV_VAR));
    }

    /**
     * Checks whether the configured remote is an HTTP(S) URL.
     *
     * @return whether the remote needs the token credential.
     */
    private boolean isHttpRemote() {
        String url = properties.repositoryUrl();
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    /**
     * Checks whether the token environment variable carries a value.
     *
     * @return whether the token is present.
     */
    private boolean hasToken() {
        String token = environmentReader.apply(TOKEN_ENV_VAR);
        return token != null && !token.isBlank();
    }

    /**
     * Creates the temporary clone directory.
     *
     * @return temporary directory path.
     */
    private Path createTempDirectory() {
        try {
            return Files.createTempDirectory("deployment-repository-publish");
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not create the temporary publish directory.", e);
        }
    }

    /**
     * Deletes a directory tree; the temporary clone must never outlive the publish attempt.
     *
     * @param root directory to delete; absent directories are ignored.
     */
    private void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                }
                catch (IOException e) {
                    throw new UncheckedIOException("Could not delete " + path, e);
                }
            });
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not delete the directory tree " + root, e);
        }
    }

    /**
     * Outcome of one publish attempt: a result, or a rejection message for the next attempt.
     *
     * @param result successful publish result, or {@code null}.
     * @param rejectionMessage rejection message of a retryable push, or {@code null}.
     */
    private record AttemptOutcome(DeploymentRepositoryPublishResult result, String rejectionMessage) {

        private static AttemptOutcome of(DeploymentRepositoryPublishResult result) {
            return new AttemptOutcome(result, null);
        }

        private static AttemptOutcome retry(String rejectionMessage) {
            return new AttemptOutcome(null, rejectionMessage);
        }
    }
}
