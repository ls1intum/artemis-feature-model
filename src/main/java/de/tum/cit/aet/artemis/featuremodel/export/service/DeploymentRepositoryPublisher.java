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
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportConfigCallback;
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
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentRepositoryPublishResult;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentRepositoryPublishException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes a generated deployment package as a commit to a deployment repository. The repository is the one named by
 * the publish request, or the configured default; its URL must pass {@link DeploymentRepositoryUrlPolicy} and the
 * repository must declare itself a compatible deployment repository through its marker file. One publish is a fresh
 * shallow single-branch clone into a temporary directory, a wholesale replacement of the target's package directory,
 * and an atomic push — never a force push, so no existing commit can be discarded by construction. A push the remote
 * rejects because the branch advanced concurrently is retried from a fresh clone of the new head, up to three
 * attempts; the retried commit lands on top of the concurrent winner's commit.
 *
 * <p>
 * HTTPS credentials come exclusively from the {@code FM_DEPLOYMENT_REPO_TOKEN} environment variable. SSH remotes use
 * an explicitly configured deploy key and known-hosts file. Credentials are never logged, serialized, or embedded in
 * a URL. {@code file://} remotes need no credential and are only reachable through an explicitly allowed
 * {@code file://} prefix, which keeps the full publish flow unit-testable offline.
 */
@Component
public class DeploymentRepositoryPublisher {

    /** Environment variable holding the deployment repository access token; never a configuration property. */
    static final String TOKEN_ENV_VAR = "FM_DEPLOYMENT_REPO_TOKEN";

    /** Directory under the target directory that the publisher replaces wholesale; nothing else is ever touched. */
    static final String PACKAGE_DIRECTORY_NAME = "package";

    /** Marker file at the repository root through which a repository declares itself a deployment repository. */
    static final String MARKER_FILE_NAME = "deployment-repository.json";

    /** Required {@code kind} of the marker file. */
    static final String MARKER_KIND = "artemis-feature-model-deployment-repository";

    /** Exact target name identifier: it becomes the package directory and the GitHub Environment name. */
    private static final Pattern TARGET_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}$");

    private static final String FILE_URL_PREFIX = "file://";

    private static final Logger log = LoggerFactory.getLogger(DeploymentRepositoryPublisher.class);

    private static final int MAX_PUSH_ATTEMPTS = 3;

    private final DeploymentRepositoryProperties properties;

    private final ObjectMapper objectMapper;

    private final UnaryOperator<String> environmentReader;

    /** Serializes publishes within this instance; cross-instance safety rests on the atomic push alone. */
    private final Object publishLock = new Object();

    /** Repository URLs whose github.com visibility expectation was verified in this process lifetime. */
    private final Set<String> visibilityVerifiedUrls = ConcurrentHashMap.newKeySet();

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
     * Checks whether publishing can be offered: it is enabled and at least one transport is usable — the HTTPS token
     * is present, both SSH files are readable, or a {@code file://} prefix is explicitly allowed. A repository URL is
     * not required, because the request can supply one.
     *
     * @return whether a publish can be attempted.
     */
    public boolean isConfigured() {
        if (!properties.enabled()) {
            return false;
        }
        boolean sshAvailable = DeploymentRepositorySshTransport.credentialGap(environmentReader) == null;
        return hasToken() || sshAvailable || fileTransportAllowed();
    }

    /**
     * Requires publishing to be enabled, resolves the effective repository URL through the URL policy, and requires the
     * credential of that URL's transport.
     *
     * @param requestedRepositoryUrl repository URL of the request, or {@code null}/blank for the configured default.
     * @return effective, trimmed repository URL.
     * @throws DeploymentRepositoryPublishException if publishing is disabled, no URL is available, the URL violates the
     *             policy, or the credential of its transport is missing.
     */
    public String requireConfiguredFor(String requestedRepositoryUrl) {
        if (!properties.enabled()) {
            throw DeploymentRepositoryPublishException.notConfigured("it is disabled (artemis.feature-model.deployment-repository.enabled=false).");
        }
        String repositoryUrl = DeploymentRepositoryUrlPolicy.effectiveUrl(requestedRepositoryUrl, properties);
        String gap = credentialGap(repositoryUrl);
        if (gap != null) {
            throw DeploymentRepositoryPublishException.notConfigured(gap);
        }
        return repositoryUrl;
    }

    /**
     * Validates a target name as an exact identifier; it is never sanitized, because the same name is the package
     * directory and the GitHub Environment an administrator creates.
     *
     * @param targetName raw target name.
     * @return the unchanged, valid target name.
     * @throws DeploymentRepositoryPublishException if the target name is absent or does not match
     *             {@code ^[a-z0-9][a-z0-9-]{0,62}$}.
     */
    public String validatedTargetName(String targetName) {
        if (targetName == null || targetName.isBlank()) {
            throw DeploymentRepositoryPublishException.requiresTargetName();
        }
        if (!TARGET_NAME_PATTERN.matcher(targetName).matches()) {
            throw DeploymentRepositoryPublishException.invalidTargetName(targetName);
        }
        return targetName;
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
     * Derives the repository-relative package directory of a target name: the configured root, the validated target
     * name, and the package directory.
     *
     * @param targetName raw target name.
     * @return repository-relative package directory.
     * @throws DeploymentRepositoryPublishException if the target name is absent or invalid.
     */
    public String targetDirectoryFor(String targetName) {
        return properties.targetDirectoryRoot() + "/" + validatedTargetName(targetName) + "/" + PACKAGE_DIRECTORY_NAME;
    }

    /**
     * Publishes the package files of one target as a commit on the configured deployment branch of the effective
     * repository. The target's package directory is replaced wholesale, so files that stop being generated are
     * deleted; an unchanged tree produces no commit and reports up to date.
     *
     * @param requestedRepositoryUrl repository URL of the request, or {@code null}/blank for the configured default.
     * @param targetName target name routing the package to its repository directory.
     * @param files generated package files, byte-identical to the download payload.
     * @param commitMessage commit message describing the published variant.
     * @return publish result with the effective repository URL and the carrying commit.
     * @throws DeploymentRepositoryPublishException if publishing is not configured for the URL, the URL is not
     *             allowed, the target name is missing or invalid, the repository lacks a compatible marker, the remote
     *             refuses the credential, or every push attempt is rejected.
     */
    public DeploymentRepositoryPublishResult publish(String requestedRepositoryUrl, String targetName, List<GeneratedArtifactFile> files,
            String commitMessage) {
        String repositoryUrl = requireConfiguredFor(requestedRepositoryUrl);
        String targetDirectory = targetDirectoryFor(targetName);
        DeploymentRepositorySshTransport sshTransport = new DeploymentRepositorySshTransport(repositoryUrl, environmentReader);
        verifyExpectedVisibility(repositoryUrl, sshTransport);
        synchronized (publishLock) {
            String lastRejection = "the push was rejected";
            for (int attempt = 1; attempt <= MAX_PUSH_ATTEMPTS; attempt++) {
                AttemptOutcome outcome = attemptPublish(repositoryUrl, sshTransport, targetDirectory, files, commitMessage, attempt);
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
     * Names the missing credential of the transport an effective repository URL selects.
     *
     * @param repositoryUrl effective repository URL.
     * @return human-readable gap naming the missing variable, or {@code null} when the transport is usable.
     */
    private String credentialGap(String repositoryUrl) {
        if (isHttpRemote(repositoryUrl) && !hasToken()) {
            return "the " + TOKEN_ENV_VAR + " environment variable is not set.";
        }
        return new DeploymentRepositorySshTransport(repositoryUrl, environmentReader).configurationGap();
    }

    /**
     * Checks whether an operator explicitly allowed a {@code file://} prefix, the transport that needs no credential.
     *
     * @return whether a {@code file://} prefix is allowed.
     */
    private boolean fileTransportAllowed() {
        for (String prefix : properties.allowedRepositoryUrlPrefixes()) {
            if (prefix.startsWith(FILE_URL_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs one publish attempt: fresh clone, marker check, wholesale directory replacement, commit when the tree
     * changed, push.
     *
     * @param repositoryUrl effective repository URL.
     * @param sshTransport SSH transport of the effective repository URL.
     * @param targetDirectory repository-relative package directory.
     * @param files generated package files.
     * @param commitMessage commit message.
     * @param attempt one-based attempt number.
     * @return the publish result, or the rejection message for a retry.
     * @throws DeploymentRepositoryPublishException if the remote refuses the credential, the transport fails, or the
     *             repository lacks a compatible marker.
     */
    private AttemptOutcome attemptPublish(String repositoryUrl, DeploymentRepositorySshTransport sshTransport, String targetDirectory,
            List<GeneratedArtifactFile> files, String commitMessage, int attempt) {
        Path tempDir = createTempDirectory();
        try (SshdSessionFactory sessionFactory = sshTransport.createSessionFactory(tempDir)) {
            TransportConfigCallback transportCallback = sshTransport.callback(sessionFactory);
            boolean branchExists = remoteBranchExists(repositoryUrl, transportCallback);
            try (Git git = cloneRepository(repositoryUrl, tempDir, branchExists, transportCallback)) {
                if (!branchExists) {
                    requireDefaultHead(git);
                }
                requireDeploymentRepositoryMarker(tempDir);
                if (!branchExists) {
                    git.checkout().setCreateBranch(true).setName(properties.branch()).call();
                }
                replacePackageDirectory(tempDir, targetDirectory, files);
                git.add().addFilepattern(targetDirectory).call();
                git.add().setUpdate(true).addFilepattern(targetDirectory).call();
                Status status = git.status().call();
                if (status.isClean() && branchExists) {
                    ObjectId head = git.getRepository().resolve(Constants.HEAD);
                    return AttemptOutcome.of(new DeploymentRepositoryPublishResult(repositoryUrl, properties.branch(), targetDirectory, head.name(), true));
                }
                String commitSha = git.getRepository().resolve(Constants.HEAD).name();
                if (!status.isClean()) {
                    PersonIdent author = new PersonIdent(properties.authorName(), properties.authorEmail());
                    RevCommit commit = git.commit().setMessage(commitMessage).setAuthor(author).setCommitter(author).setSign(false).call();
                    commitSha = commit.name();
                }
                beforePush(attempt);
                String rejection = push(git, repositoryUrl, transportCallback);
                if (rejection != null) {
                    return AttemptOutcome.retry(rejection);
                }
                return AttemptOutcome.of(new DeploymentRepositoryPublishResult(repositoryUrl, properties.branch(), targetDirectory, commitSha, false));
            }
        }
        catch (TransportException e) {
            throw asControlledTransportFailure(e, sshTransport);
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
     * Verifies the declared visibility of a {@code github.com} deployment repository once per repository URL and
     * process lifetime, so a publish never lands in a repository the operator does not think they are publishing to.
     * The expectation is an instance-wide policy. The check runs only for {@code github.com} HTTPS or SSH remotes with
     * a declared expectation; other remotes (including {@code file://} test remotes) skip it.
     *
     * @param repositoryUrl effective repository URL.
     * @param sshTransport SSH transport of the effective repository URL, which derives the GitHub repository path.
     * @throws DeploymentRepositoryPublishException if the actual visibility differs from the expectation or cannot be
     *             determined.
     */
    void verifyExpectedVisibility(String repositoryUrl, DeploymentRepositorySshTransport sshTransport) {
        if (visibilityVerifiedUrls.contains(repositoryUrl)) {
            return;
        }
        String expected = properties.expectedVisibility();
        if (sshTransport.gitHubRepositoryPath() != null && expected != null && !expected.isBlank()) {
            String actual = fetchGitHubVisibility(repositoryUrl);
            if (!expected.equalsIgnoreCase(actual)) {
                throw DeploymentRepositoryPublishException.visibilityMismatch(expected, actual);
            }
        }
        visibilityVerifiedUrls.add(repositoryUrl);
    }

    /**
     * Reads the actual visibility of a {@code github.com} repository from the GitHub API. The request uses the token
     * when present. GitHub hides private repositories behind an unauthenticated 404, which is therefore treated as
     * private when no token is configured. Failure messages never contain the token.
     *
     * @param repositoryUrl effective {@code github.com} repository URL.
     * @return actual repository visibility, for example {@code public} or {@code private}.
     * @throws DeploymentRepositoryPublishException if the visibility cannot be determined.
     */
    protected String fetchGitHubVisibility(String repositoryUrl) {
        String repositoryPath = new DeploymentRepositorySshTransport(repositoryUrl, environmentReader).gitHubRepositoryPath();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repositoryPath))
                .header("Accept", "application/vnd.github+json");
        String token = environmentReader.apply(TOKEN_ENV_VAR);
        if (token != null && !token.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }
        HttpRequest request = requestBuilder.GET().build();
        try {
            HttpResponse<String> response = sendGitHubVisibilityRequest(request);
            if (response.statusCode() == 404 && (token == null || token.isBlank())) {
                return "private";
            }
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
     * Sends the GitHub repository-visibility request.
     *
     * @param request prepared GitHub API request.
     * @return GitHub API response.
     * @throws IOException if the API cannot be reached.
     * @throws InterruptedException if the request is interrupted.
     */
    HttpResponse<String> sendGitHubVisibilityRequest(HttpRequest request) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Pushes the deployment branch without force.
     *
     * @param git open clone.
     * @param repositoryUrl effective repository URL, which selects the credential.
     * @param transportCallback per-attempt SSH transport callback, or {@code null} for other transports.
     * @return the remote's rejection message, or {@code null} on success.
     * @throws GitAPIException if the push transport fails.
     */
    private String push(Git git, String repositoryUrl, TransportConfigCallback transportCallback) throws GitAPIException {
        String branchRef = Constants.R_HEADS + properties.branch();
        Iterable<PushResult> results = git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(new RefSpec(branchRef + ":" + branchRef))
                .setCredentialsProvider(credentialsProvider(repositoryUrl)).setTransportConfigCallback(transportCallback).call();
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
     * @param repositoryUrl effective repository URL.
     * @param transportCallback per-attempt SSH transport callback, or {@code null} for other transports.
     * @return whether the branch exists.
     * @throws GitAPIException if the remote cannot be contacted.
     */
    private boolean remoteBranchExists(String repositoryUrl, TransportConfigCallback transportCallback) throws GitAPIException {
        String branchRef = Constants.R_HEADS + properties.branch();
        for (Ref ref : Git.lsRemoteRepository().setRemote(repositoryUrl).setHeads(true).setCredentialsProvider(credentialsProvider(repositoryUrl))
                .setTransportConfigCallback(transportCallback).call()) {
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
     * @param repositoryUrl effective repository URL.
     * @param tempDir clone target directory.
     * @param branchExists whether the deployment branch exists on the remote.
     * @param transportCallback per-attempt SSH transport callback, or {@code null} for other transports.
     * @return open clone.
     * @throws GitAPIException if the clone fails.
     */
    private Git cloneRepository(String repositoryUrl, Path tempDir, boolean branchExists, TransportConfigCallback transportCallback)
            throws GitAPIException {
        CloneCommand clone = Git.cloneRepository().setURI(repositoryUrl).setDirectory(tempDir.toFile()).setDepth(1)
                .setCredentialsProvider(credentialsProvider(repositoryUrl)).setTransportConfigCallback(transportCallback);
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
     * Requires the cloned work tree to declare itself a compatible deployment repository: a root marker file with the
     * expected kind and a package contract equal to the major version of the remote-ansible package format. The check
     * runs before any file is replaced, so a refused repository receives no commit and no push.
     *
     * @param workTree clone work tree root.
     * @throws IOException if the marker file cannot be read.
     * @throws DeploymentRepositoryPublishException if the marker is missing, unparsable, of the wrong kind, or declares
     *             another package contract.
     */
    private void requireDeploymentRepositoryMarker(Path workTree) throws IOException {
        String createFromTemplate = " Create the deployment repository from the template.";
        Path marker = workTree.resolve(MARKER_FILE_NAME);
        if (!Files.isRegularFile(marker)) {
            throw DeploymentRepositoryPublishException.notADeploymentRepository("it has no " + MARKER_FILE_NAME + " marker at its root." + createFromTemplate);
        }
        JsonNode markerContent;
        try {
            markerContent = objectMapper.readTree(Files.readString(marker));
        }
        catch (JacksonException e) {
            throw DeploymentRepositoryPublishException.notADeploymentRepository("its " + MARKER_FILE_NAME + " marker is not valid JSON." + createFromTemplate);
        }
        if (markerContent == null || !MARKER_KIND.equals(markerContent.path("kind").asString(null))) {
            throw DeploymentRepositoryPublishException
                    .notADeploymentRepository("its " + MARKER_FILE_NAME + " marker does not declare kind '" + MARKER_KIND + "'." + createFromTemplate);
        }
        int expectedContract = expectedPackageContract();
        JsonNode packageContract = markerContent.path("packageContract");
        if (!packageContract.isIntegralNumber() || packageContract.asInt() != expectedContract) {
            throw DeploymentRepositoryPublishException.notADeploymentRepository("its " + MARKER_FILE_NAME + " marker declares package contract "
                    + packageContract + " but this instance publishes package contract " + expectedContract
                    + ". Update the repository's workflow and marker from the template.");
        }
    }

    /**
     * Derives the package contract a deployment repository must declare: the major version of the remote-ansible
     * package format.
     *
     * @return expected package contract.
     */
    static int expectedPackageContract() {
        String packageVersion = RuntimePackageConstants.REMOTE_ANSIBLE_PACKAGE_VERSION;
        return Integer.parseInt(packageVersion.substring(0, packageVersion.indexOf('.')));
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
     * Maps a transport failure to its controlled publish exception, keeping the message credential-free.
     *
     * @param exception JGit transport failure.
     * @param sshTransport SSH transport of the effective repository URL.
     * @return controlled publish exception.
     */
    DeploymentRepositoryPublishException asControlledTransportFailure(TransportException exception, DeploymentRepositorySshTransport sshTransport) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        boolean httpAuthFailure = isHttpRemote(sshTransport.repositoryUrl())
                && (normalizedMessage.contains("not authorized") || normalizedMessage.contains("authentication"));
        boolean sshAuthFailure = sshTransport.isSshRemote()
                && (normalizedMessage.contains("auth fail") || normalizedMessage.contains("publickey"));
        if (sshAuthFailure) {
            String keyLoadFailure = sshTransport.privateKeyLoadFailure();
            if (keyLoadFailure != null) {
                return DeploymentRepositoryPublishException.rejected(keyLoadFailure);
            }
        }
        if (httpAuthFailure || sshAuthFailure) {
            return DeploymentRepositoryPublishException.authFailed(message);
        }
        return DeploymentRepositoryPublishException.rejected(message);
    }

    /**
     * Builds the HTTPS credentials provider from the token environment variable; {@code file://} remotes use none.
     *
     * @param repositoryUrl effective repository URL.
     * @return credentials provider, or {@code null} for non-HTTP remotes.
     */
    private CredentialsProvider credentialsProvider(String repositoryUrl) {
        if (!isHttpRemote(repositoryUrl)) {
            return null;
        }
        return new UsernamePasswordCredentialsProvider("x-access-token", environmentReader.apply(TOKEN_ENV_VAR));
    }

    /**
     * Checks whether a remote is an HTTP(S) URL.
     *
     * @param repositoryUrl repository URL.
     * @return whether the remote needs the token credential.
     */
    private static boolean isHttpRemote(String repositoryUrl) {
        return repositoryUrl != null && (repositoryUrl.startsWith("https://") || repositoryUrl.startsWith("http://"));
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
