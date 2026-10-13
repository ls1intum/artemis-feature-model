package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentRepositoryPublishResult;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentRepositoryPublishException;
import tools.jackson.databind.ObjectMapper;

/**
 * Offline publish-flow tests against {@code file://} bare repositories: branch bootstrap from the default head with
 * exact package bytes, up-to-date idempotence, wholesale replacement deleting stale files, the concurrency race
 * (remote advances between clone and push, the retry lands on top with both commits intact), routing to the requested
 * repository, the marker check, exact target names, and the fail-closed configuration paths.
 */
class DeploymentRepositoryPublisherTest {

    private static final String COMMIT_MESSAGE = "deploy artemis-remote: model functional-artemis@test, catalog v2@fce6ad1";

    @TempDir
    Path tempDir;

    private Path remoteDir;

    private String remoteUrl;

    @BeforeEach
    void setUp() throws IOException, GitAPIException {
        remoteDir = tempDir.resolve("deployment-repo.git");
        remoteUrl = seedRepository(remoteDir, DeploymentRepositoryMarkers.validMarker());
    }

    @Test
    void firstPublishCreatesTheBranchFromTheDefaultHeadAndWritesExactPackageBytes() throws Exception {
        DeploymentRepositoryPublisher publisher = publisher(enabledProperties());

        DeploymentRepositoryPublishResult result = publisher.publish(null, "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);

        assertThat(result.upToDate()).isFalse();
        assertThat(result.repositoryUrl()).isEqualTo(remoteUrl);
        assertThat(result.branch()).isEqualTo("deployment");
        assertThat(result.targetDirectory()).isEqualTo("deployments/artemis-remote/package");
        try (Git verification = cloneBranch("deployment")) {
            Path workTree = verification.getRepository().getWorkTree().toPath();
            assertThat(workTree.resolve("README.md")).exists();
            assertThat(workTree.resolve(DeploymentRepositoryMarkers.fileName())).exists();
            assertThat(Files.readString(workTree.resolve("deployments/artemis-remote/package/README.md"))).isEqualTo("readme v1");
            assertThat(Files.readString(workTree.resolve("deployments/artemis-remote/package/inventory/hosts"))).isEqualTo("[artemislocal]\n");
            RevCommit head = headCommit(verification);
            assertThat(head.name()).isEqualTo(result.commitSha());
            assertThat(head.getFullMessage()).isEqualTo(COMMIT_MESSAGE);
            assertThat(head.getParent(0).name()).isEqualTo(remoteHead("main"));
        }
    }

    @Test
    void identicalRepublishIsUpToDateWithoutANewCommit() throws Exception {
        DeploymentRepositoryPublisher publisher = publisher(enabledProperties());

        DeploymentRepositoryPublishResult first = publisher.publish(null, "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);
        DeploymentRepositoryPublishResult second = publisher.publish(null, "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);

        assertThat(second.upToDate()).isTrue();
        assertThat(second.commitSha()).isEqualTo(first.commitSha());
        assertThat(remoteHead("deployment")).isEqualTo(first.commitSha());
    }

    @Test
    void changedSelectionReplacesTheDirectoryWholesaleAndDeletesStaleFiles() throws Exception {
        DeploymentRepositoryPublisher publisher = publisher(enabledProperties());
        publisher.publish(null, "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);

        List<GeneratedArtifactFile> reducedVariant = List.of(new GeneratedArtifactFile("README.md", "text/markdown", "readme v2"),
                new GeneratedArtifactFile("inventory/group_vars/artemistests_without_exam.yml", "application/x-yaml", "---\nartemis_modules:\n  exam: false"));
        DeploymentRepositoryPublishResult result = publisher.publish(null, "artemis-remote", reducedVariant, "deploy artemis-remote: reduced variant");

        assertThat(result.upToDate()).isFalse();
        try (Git verification = cloneBranch("deployment")) {
            Path packageDir = verification.getRepository().getWorkTree().toPath().resolve("deployments/artemis-remote/package");
            assertThat(Files.readString(packageDir.resolve("README.md"))).isEqualTo("readme v2");
            assertThat(packageDir.resolve("inventory/group_vars/artemistests_without_exam.yml")).exists();
            assertThat(packageDir.resolve("inventory/hosts")).doesNotExist();
            assertThat(packageDir.resolve("inventory/group_vars/artemistests_iris.yml")).doesNotExist();
        }
    }

    @Test
    void publishTouchesOnlyItsOwnTargetDirectory() throws Exception {
        DeploymentRepositoryPublisher publisher = publisher(enabledProperties());
        publisher.publish(null, "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);

        publisher.publish(null, "second-target", packageFiles("second"), "deploy second-target: variant");

        try (Git verification = cloneBranch("deployment")) {
            Path workTree = verification.getRepository().getWorkTree().toPath();
            assertThat(Files.readString(workTree.resolve("deployments/artemis-remote/package/README.md"))).isEqualTo("readme v1");
            assertThat(Files.readString(workTree.resolve("deployments/second-target/package/README.md"))).isEqualTo("readme second");
            assertThat(workTree.resolve("README.md")).exists();
        }
    }

    @Test
    void rejectedPushIsRetriedOnTopOfTheConcurrentCommitWithBothCommitsIntact() throws Exception {
        DeploymentRepositoryPublisher racingPublisher = new DeploymentRepositoryPublisher(enabledProperties(), new ObjectMapper(), name -> null) {

            private boolean advanced;

            @Override
            protected void beforePush(int attempt) {
                if (!advanced) {
                    advanced = true;
                    advanceRemoteConcurrently();
                }
            }
        };

        DeploymentRepositoryPublishResult result = racingPublisher.publish(null, "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);

        assertThat(result.upToDate()).isFalse();
        try (Git verification = cloneBranch("deployment")) {
            RevCommit head = headCommit(verification);
            assertThat(head.name()).isEqualTo(result.commitSha());
            assertThat(head.getFullMessage()).isEqualTo(COMMIT_MESSAGE);
            assertThat(head.getParent(0).getFullMessage()).isEqualTo("concurrent commit");
            Path workTree = verification.getRepository().getWorkTree().toPath();
            assertThat(Files.readString(workTree.resolve("deployments/artemis-remote/package/README.md"))).isEqualTo("readme v1");
            assertThat(Files.readString(workTree.resolve("deployments/other-target/package/README.md"))).isEqualTo("concurrent content");
        }
    }

    @Test
    void disabledOrIncompleteConfigurationIsRefusedWithControlledErrors() {
        DeploymentRepositoryPublisher disabled = publisher(properties(false, remoteUrl, List.of(filePrefix()), null));
        assertThatThrownBy(() -> disabled.publish(null, "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_NOT_CONFIGURED"))
                .hasMessageContaining("disabled");
        assertThat(disabled.isConfigured()).isFalse();

        DeploymentRepositoryPublisher withoutUrl = publisher(properties(true, " ", List.of(filePrefix()), null));
        assertThatThrownBy(() -> withoutUrl.publish(" ", "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("PUBLISH_REQUIRES_REPOSITORY_URL");
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                });

        DeploymentRepositoryPublisher withoutToken = publisher(properties(true, "https://github.com/example/deployments.git", null, null));
        assertThatThrownBy(() -> withoutToken.publish(null, "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_NOT_CONFIGURED"))
                .hasMessageContaining("FM_DEPLOYMENT_REPO_TOKEN");
        assertThat(withoutToken.isConfigured()).isFalse();
    }

    @Test
    void configuredMeansEnabledWithAUsableTransportAndNeedsNoDefaultUrl() {
        DeploymentRepositoryProperties withoutUrl = properties(true, null, null, null);

        assertThat(new DeploymentRepositoryPublisher(withoutUrl, new ObjectMapper(), tokenEnvironment()).isConfigured()).isTrue();
        assertThat(publisher(withoutUrl).isConfigured()).isFalse();
        assertThat(publisher(properties(true, null, List.of(filePrefix()), null)).isConfigured()).isTrue();
        DeploymentRepositoryProperties disabled = properties(false, null, null, null);
        assertThat(new DeploymentRepositoryPublisher(disabled, new ObjectMapper(), tokenEnvironment()).isConfigured()).isFalse();
    }

    @Test
    void httpsRequestUrlWithoutTheTokenIsNotConfiguredEvenWhenTheDefaultIsAFileRemote() {
        DeploymentRepositoryPublisher publisher = publisher(properties(true, remoteUrl, List.of(filePrefix(), "https://github.com/"), null));

        assertThatThrownBy(() -> publisher.publish("https://github.com/example/deployments.git", "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_NOT_CONFIGURED"))
                .hasMessageContaining("FM_DEPLOYMENT_REPO_TOKEN");
    }

    @Test
    void requestedRepositoryOverridesTheDefaultAndEachPublishLandsInItsOwnRepository() throws Exception {
        Path secondRemoteDir = tempDir.resolve("second-repo.git");
        String secondRemoteUrl = seedRepository(secondRemoteDir, DeploymentRepositoryMarkers.validMarker());
        DeploymentRepositoryPublisher publisher = publisher(enabledProperties());

        DeploymentRepositoryPublishResult toDefault = publisher.publish(null, "artemis-remote", packageFiles("default"), COMMIT_MESSAGE);
        DeploymentRepositoryPublishResult toRequested = publisher.publish("  " + secondRemoteUrl + " ", "chair-vm", packageFiles("requested"),
                "deploy chair-vm: requested");

        assertThat(toDefault.repositoryUrl()).isEqualTo(remoteUrl);
        assertThat(toRequested.repositoryUrl()).isEqualTo(secondRemoteUrl);
        assertThat(remoteHead("deployment")).isEqualTo(toDefault.commitSha());
        assertThat(remoteHead(secondRemoteDir, "deployment")).isEqualTo(toRequested.commitSha());
        try (Git first = cloneBranch(remoteUrl, "deployment"); Git second = cloneBranch(secondRemoteUrl, "deployment")) {
            Path firstTree = first.getRepository().getWorkTree().toPath();
            Path secondTree = second.getRepository().getWorkTree().toPath();
            assertThat(Files.readString(firstTree.resolve("deployments/artemis-remote/package/README.md"))).isEqualTo("readme default");
            assertThat(firstTree.resolve("deployments/chair-vm")).doesNotExist();
            assertThat(Files.readString(secondTree.resolve("deployments/chair-vm/package/README.md"))).isEqualTo("readme requested");
            assertThat(secondTree.resolve("deployments/artemis-remote")).doesNotExist();
        }
    }

    @Test
    void urlOutsideTheAllowedPrefixesIsRefusedBeforeAnyGitCall() throws IOException {
        DeploymentRepositoryPublisher publisher = publisher(properties(true, remoteUrl, null, null));

        assertThatThrownBy(() -> publisher.publish(null, "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_REPOSITORY_NOT_ALLOWED"))
                .hasMessageContaining("https://github.com/");
        assertThat(remoteRef(remoteDir, "deployment")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "missing", "unparsable", "wrong-kind", "string-contract", "other-contract" })
    void firstPublishToARepositoryWithoutACompatibleMarkerIsRefusedWithoutCreatingTheBranch(String markerCase) throws Exception {
        Path refusingRemoteDir = tempDir.resolve("refusing-" + markerCase + ".git");
        String refusingRemoteUrl = seedRepository(refusingRemoteDir, markerContent(markerCase));
        String mainBefore = remoteHead(refusingRemoteDir, "main");
        DeploymentRepositoryPublisher publisher = publisher(enabledProperties());

        assertThatThrownBy(() -> publisher.publish(refusingRemoteUrl, "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("PUBLISH_NOT_A_DEPLOYMENT_REPOSITORY");
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                    assertThat(exception.getMessage()).contains("from the template");
                });

        assertThat(remoteHead(refusingRemoteDir, "main")).isEqualTo(mainBefore);
        assertThat(remoteRef(refusingRemoteDir, "deployment")).isNull();
    }

    @Test
    void existingDeploymentBranchWithAnOutdatedContractIsRefusedWithoutAPush() throws Exception {
        DeploymentRepositoryPublisher publisher = publisher(enabledProperties());
        publisher.publish(null, "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);
        commitMarkerOnDeploymentBranch(markerContent("other-contract"));
        String deploymentBefore = remoteHead("deployment");

        assertThatThrownBy(() -> publisher.publish(null, "artemis-remote", packageFiles("v2"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_NOT_A_DEPLOYMENT_REPOSITORY"))
                .hasMessageContaining("Update the repository's workflow and marker from the template");

        assertThat(remoteHead("deployment")).isEqualTo(deploymentBefore);
    }

    @Test
    void visibilityIsVerifiedOncePerRepositoryUrl() {
        Map<String, Integer> fetchesByUrl = new HashMap<>();
        DeploymentRepositoryPublisher publisher = new DeploymentRepositoryPublisher(properties(true, null, null, "public"), new ObjectMapper(),
                tokenEnvironment()) {

            @Override
            protected String fetchGitHubVisibility(String repositoryUrl) {
                fetchesByUrl.merge(repositoryUrl, 1, Integer::sum);
                return "public";
            }
        };
        String firstUrl = "https://github.com/example/first.git";
        String secondUrl = "git@github.com:example/second.git";

        for (int round = 0; round < 2; round++) {
            publisher.verifyExpectedVisibility(firstUrl, new DeploymentRepositorySshTransport(firstUrl, tokenEnvironment()));
            publisher.verifyExpectedVisibility(secondUrl, new DeploymentRepositorySshTransport(secondUrl, tokenEnvironment()));
        }

        assertThat(fetchesByUrl).containsExactlyInAnyOrderEntriesOf(Map.of(firstUrl, 1, secondUrl, 1));
    }

    @Test
    void gitHubVisibilityMismatchRefusesThePublishBeforeAnyPush() {
        DeploymentRepositoryProperties gitHubProperties = properties(true, "https://github.com/example/deployments.git", null, "public");
        DeploymentRepositoryPublisher publisher = new DeploymentRepositoryPublisher(gitHubProperties, new ObjectMapper(), name -> "token-present-marker") {

            @Override
            protected String fetchGitHubVisibility(String repositoryUrl) {
                return "private";
            }
        };

        assertThatThrownBy(() -> publisher.publish(null, "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_VISIBILITY_MISMATCH"))
                .hasMessageContaining("'private'").hasMessageContaining("'public'");
    }

    @Test
    void fileRemoteSkipsTheGitHubVisibilityCheck() throws Exception {
        DeploymentRepositoryProperties fileProperties = properties(true, remoteUrl, List.of(filePrefix()), "public");
        DeploymentRepositoryPublisher publisher = new DeploymentRepositoryPublisher(fileProperties, new ObjectMapper(), name -> null) {

            @Override
            protected String fetchGitHubVisibility(String repositoryUrl) {
                throw new IllegalStateException("The visibility check must not run for a file remote.");
            }
        };

        DeploymentRepositoryPublishResult result = publisher.publish(null, "artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);

        assertThat(result.upToDate()).isFalse();
        assertThat(remoteHead("deployment")).isEqualTo(result.commitSha());
    }

    @Test
    void missingTargetNameIsRefused() {
        DeploymentRepositoryPublisher publisher = publisher(enabledProperties());

        for (String blank : new String[] { null, "", "   " }) {
            assertThatThrownBy(() -> publisher.publish(null, blank, packageFiles("v1"), COMMIT_MESSAGE))
                    .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                            exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_REQUIRES_TARGET_NAME"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "a", "chair-vm", "artemis-remote", "0test", "abcdefghijklmnopqrstuvwxyz0123456789-abcdefghijklmnopqrstuvwxyz" })
    void exactTargetNamesAreAcceptedUnchanged(String targetName) {
        assertThat(targetName).hasSizeLessThanOrEqualTo(63);

        assertThat(publisher(enabledProperties()).validatedTargetName(targetName)).isEqualTo(targetName);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Chair-vm", "chair_vm", "-chair", "chair vm", " chair-vm", "###", "chair.vm",
            "abcdefghijklmnopqrstuvwxyz0123456789-abcdefghijklmnopqrstuvwxyz0" })
    void inexactTargetNamesAreRefusedInsteadOfSanitized(String targetName) throws IOException {
        DeploymentRepositoryPublisher publisher = publisher(enabledProperties());

        assertThatThrownBy(() -> publisher.publish(null, targetName, packageFiles("v1"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("PUBLISH_INVALID_TARGET_NAME");
                    assertThat(exception.getStatus().value()).isEqualTo(400);
                });
        assertThat(remoteRef(remoteDir, "deployment")).isNull();
    }

    /**
     * Advances the remote deployment branch with a commit to another target's directory, simulating a concurrent
     * publisher between this publisher's clone and push.
     */
    private void advanceRemoteConcurrently() {
        try {
            Path concurrentDir = Files.createTempDirectory(tempDir, "concurrent");
            try (Git git = Git.cloneRepository().setURI(remoteUrl).setDirectory(concurrentDir.toFile()).call()) {
                boolean branchExists = git.lsRemote().setHeads(true).call().stream().anyMatch(ref -> ref.getName().equals("refs/heads/deployment"));
                if (branchExists) {
                    git.checkout().setCreateBranch(true).setName("deployment").setStartPoint("origin/deployment").call();
                }
                else {
                    git.checkout().setCreateBranch(true).setName("deployment").call();
                }
                Path concurrentFile = concurrentDir.resolve("deployments/other-target/package/README.md");
                Files.createDirectories(concurrentFile.getParent());
                Files.writeString(concurrentFile, "concurrent content");
                git.add().addFilepattern(".").call();
                PersonIdent concurrentAuthor = new PersonIdent("concurrent", "concurrent@example.invalid");
                git.commit().setMessage("concurrent commit").setAuthor(concurrentAuthor).setCommitter(concurrentAuthor).setSign(false).call();
                git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(new RefSpec("refs/heads/deployment:refs/heads/deployment")).call();
            }
        }
        catch (IOException | GitAPIException e) {
            throw new IllegalStateException("Could not advance the remote concurrently.", e);
        }
    }

    /**
     * Commits a marker change directly on the remote deployment branch, as an outdated repository copy would carry.
     *
     * @param markerContent marker file content.
     */
    private void commitMarkerOnDeploymentBranch(String markerContent) throws IOException, GitAPIException {
        try (Git git = cloneBranch(remoteUrl, "deployment")) {
            Path workTree = git.getRepository().getWorkTree().toPath();
            Files.writeString(workTree.resolve(DeploymentRepositoryMarkers.fileName()), markerContent);
            git.add().addFilepattern(".").call();
            PersonIdent author = new PersonIdent("maintainer", "maintainer@example.invalid");
            git.commit().setMessage("change the marker").setAuthor(author).setCommitter(author).setSign(false).call();
            git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(new RefSpec("refs/heads/deployment:refs/heads/deployment")).call();
        }
    }

    /**
     * Seeds a bare repository with one default-branch commit holding a README and, unless {@code null}, a marker.
     *
     * @param bareDir bare repository directory.
     * @param markerContent marker file content, or {@code null} for no marker.
     * @return {@code file://} URL of the bare repository.
     */
    private String seedRepository(Path bareDir, String markerContent) throws IOException, GitAPIException {
        try (Git remote = Git.init().setBare(true).setInitialBranch("main").setDirectory(bareDir.toFile()).call()) {
            assertThat(remote.getRepository().isBare()).isTrue();
        }
        String url = bareDir.toUri().toString();
        Path seedDir = Files.createTempDirectory(tempDir, "seed");
        try (Git seed = Git.init().setInitialBranch("main").setDirectory(seedDir.toFile()).call()) {
            Files.writeString(seedDir.resolve("README.md"), "# deployment repository\n");
            if (markerContent != null) {
                Files.writeString(seedDir.resolve(DeploymentRepositoryMarkers.fileName()), markerContent);
            }
            seed.add().addFilepattern(".").call();
            PersonIdent seeder = new PersonIdent("seed", "seed@example.invalid");
            seed.commit().setMessage("seed default branch").setAuthor(seeder).setCommitter(seeder).setSign(false).call();
            seed.push().setRemote(url).setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call();
        }
        return url;
    }

    private String markerContent(String markerCase) {
        int expectedContract = DeploymentRepositoryPublisher.expectedPackageContract();
        return switch (markerCase) {
            case "missing" -> null;
            case "unparsable" -> "{ not json";
            case "wrong-kind" -> DeploymentRepositoryMarkers.marker("some-other-repository", String.valueOf(expectedContract));
            case "string-contract" -> DeploymentRepositoryMarkers.marker(DeploymentRepositoryPublisher.MARKER_KIND, "\"" + expectedContract + "\"");
            case "other-contract" -> DeploymentRepositoryMarkers.marker(DeploymentRepositoryPublisher.MARKER_KIND, String.valueOf(expectedContract + 1));
            default -> throw new IllegalArgumentException(markerCase);
        };
    }

    private String filePrefix() {
        return tempDir.toUri().toString();
    }

    private DeploymentRepositoryProperties enabledProperties() {
        return properties(true, remoteUrl, List.of(filePrefix()), null);
    }

    private DeploymentRepositoryProperties properties(boolean enabled, String repositoryUrl, List<String> allowedPrefixes, String expectedVisibility) {
        return new DeploymentRepositoryProperties(enabled, repositoryUrl, allowedPrefixes, "deployment", null, expectedVisibility, null, null);
    }

    private DeploymentRepositoryPublisher publisher(DeploymentRepositoryProperties properties) {
        return new DeploymentRepositoryPublisher(properties, new ObjectMapper(), name -> null);
    }

    private UnaryOperator<String> tokenEnvironment() {
        return name -> DeploymentRepositoryPublisher.TOKEN_ENV_VAR.equals(name) ? "token-present-marker" : null;
    }

    private List<GeneratedArtifactFile> packageFiles(String marker) {
        return List.of(new GeneratedArtifactFile("README.md", "text/markdown", "readme " + marker),
                new GeneratedArtifactFile("inventory/hosts", "text/plain", "[artemislocal]\n"),
                new GeneratedArtifactFile("inventory/group_vars/artemistests_iris.yml", "application/x-yaml", "---\niris " + marker));
    }

    private String remoteHead(String branch) throws IOException {
        return remoteHead(remoteDir, branch);
    }

    private String remoteHead(Path bareDir, String branch) throws IOException {
        return remoteRef(bareDir, branch).name();
    }

    private ObjectId remoteRef(Path bareDir, String branch) throws IOException {
        try (Git remote = Git.open(bareDir.toFile())) {
            return remote.getRepository().resolve(Constants.R_HEADS + branch);
        }
    }

    private Git cloneBranch(String branch) throws GitAPIException, IOException {
        return cloneBranch(remoteUrl, branch);
    }

    private Git cloneBranch(String url, String branch) throws GitAPIException, IOException {
        Path verificationDir = Files.createTempDirectory(tempDir, "verification");
        return Git.cloneRepository().setURI(url).setDirectory(verificationDir.toFile()).setBranch(branch).call();
    }

    private RevCommit headCommit(Git git) throws IOException {
        ObjectId head = git.getRepository().resolve(Constants.HEAD);
        try (RevWalk walk = new RevWalk(git.getRepository())) {
            RevCommit commit = walk.parseCommit(head);
            for (RevCommit parent : commit.getParents()) {
                walk.parseCommit(parent);
            }
            return commit;
        }
    }
}
