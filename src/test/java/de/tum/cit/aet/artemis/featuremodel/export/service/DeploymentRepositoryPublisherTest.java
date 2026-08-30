package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentRepositoryPublishResult;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentRepositoryPublishException;

/**
 * Offline publish-flow tests against {@code file://} bare repositories: branch bootstrap from the default head with
 * exact package bytes, up-to-date idempotence, wholesale replacement deleting stale files, the concurrency race
 * (remote advances between clone and push, the retry lands on top with both commits intact), and the fail-closed
 * configuration paths.
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
        try (Git remote = Git.init().setBare(true).setInitialBranch("main").setDirectory(remoteDir.toFile()).call()) {
            assertThat(remote.getRepository().isBare()).isTrue();
        }
        remoteUrl = remoteDir.toUri().toString();
        Path seedDir = tempDir.resolve("seed");
        try (Git seed = Git.init().setInitialBranch("main").setDirectory(seedDir.toFile()).call()) {
            Files.writeString(seedDir.resolve("README.md"), "# deployment repository\n");
            seed.add().addFilepattern(".").call();
            PersonIdent seeder = new PersonIdent("seed", "seed@example.invalid");
            seed.commit().setMessage("seed default branch").setAuthor(seeder).setCommitter(seeder).setSign(false).call();
            seed.push().setRemote(remoteUrl).setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call();
        }
    }

    @Test
    void firstPublishCreatesTheBranchFromTheDefaultHeadAndWritesExactPackageBytes() throws Exception {
        DeploymentRepositoryPublisher publisher = publisher(enabledProperties());

        DeploymentRepositoryPublishResult result = publisher.publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);

        assertThat(result.upToDate()).isFalse();
        assertThat(result.branch()).isEqualTo("deployment");
        assertThat(result.targetDirectory()).isEqualTo("deployments/artemis-remote/package");
        try (Git verification = cloneBranch("deployment")) {
            Path workTree = verification.getRepository().getWorkTree().toPath();
            assertThat(workTree.resolve("README.md")).exists();
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

        DeploymentRepositoryPublishResult first = publisher.publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);
        DeploymentRepositoryPublishResult second = publisher.publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);

        assertThat(second.upToDate()).isTrue();
        assertThat(second.commitSha()).isEqualTo(first.commitSha());
        assertThat(remoteHead("deployment")).isEqualTo(first.commitSha());
    }

    @Test
    void changedSelectionReplacesTheDirectoryWholesaleAndDeletesStaleFiles() throws Exception {
        DeploymentRepositoryPublisher publisher = publisher(enabledProperties());
        publisher.publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);

        List<GeneratedArtifactFile> reducedVariant = List.of(new GeneratedArtifactFile("README.md", "text/markdown", "readme v2"),
                new GeneratedArtifactFile("inventory/group_vars/artemistests_without_exam.yml", "application/x-yaml", "---\nartemis_modules:\n  exam: false"));
        DeploymentRepositoryPublishResult result = publisher.publish("artemis-remote", reducedVariant, "deploy artemis-remote: reduced variant");

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
        publisher.publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);

        publisher.publish("Second Target", packageFiles("second"), "deploy secondtarget: variant");

        try (Git verification = cloneBranch("deployment")) {
            Path workTree = verification.getRepository().getWorkTree().toPath();
            assertThat(Files.readString(workTree.resolve("deployments/artemis-remote/package/README.md"))).isEqualTo("readme v1");
            assertThat(Files.readString(workTree.resolve("deployments/secondtarget/package/README.md"))).isEqualTo("readme second");
            assertThat(workTree.resolve("README.md")).exists();
        }
    }

    @Test
    void rejectedPushIsRetriedOnTopOfTheConcurrentCommitWithBothCommitsIntact() throws Exception {
        DeploymentRepositoryPublisher racingPublisher = new DeploymentRepositoryPublisher(enabledProperties(), name -> null) {

            private boolean advanced;

            @Override
            protected void beforePush(int attempt) {
                if (!advanced) {
                    advanced = true;
                    advanceRemoteConcurrently();
                }
            }
        };

        DeploymentRepositoryPublishResult result = racingPublisher.publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);

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
        DeploymentRepositoryPublisher disabled = publisher(new DeploymentRepositoryProperties(false, remoteUrl, "deployment", null, null, null, null));
        assertThatThrownBy(() -> disabled.publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_NOT_CONFIGURED"))
                .hasMessageContaining("disabled");
        assertThat(disabled.isConfigured()).isFalse();

        DeploymentRepositoryPublisher withoutUrl = publisher(new DeploymentRepositoryProperties(true, " ", "deployment", null, null, null, null));
        assertThatThrownBy(() -> withoutUrl.publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_NOT_CONFIGURED"))
                .hasMessageContaining("repository URL");

        DeploymentRepositoryPublisher withoutToken = publisher(
                new DeploymentRepositoryProperties(true, "https://github.com/example/deployments.git", "deployment", null, null, null, null));
        assertThatThrownBy(() -> withoutToken.publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_NOT_CONFIGURED"))
                .hasMessageContaining("FM_DEPLOYMENT_REPO_TOKEN");
        assertThat(withoutToken.isConfigured()).isFalse();
    }

    @Test
    void missingOrUnroutableTargetNameIsRefused() {
        DeploymentRepositoryPublisher publisher = publisher(enabledProperties());

        assertThatThrownBy(() -> publisher.publish(null, packageFiles("v1"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_REQUIRES_TARGET_NAME"));
        assertThatThrownBy(() -> publisher.publish("###", packageFiles("v1"), COMMIT_MESSAGE))
                .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_REQUIRES_TARGET_NAME"));
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

    private DeploymentRepositoryProperties enabledProperties() {
        return new DeploymentRepositoryProperties(true, remoteUrl, "deployment", null, null, null, null);
    }

    private DeploymentRepositoryPublisher publisher(DeploymentRepositoryProperties properties) {
        return new DeploymentRepositoryPublisher(properties, name -> null);
    }

    private List<GeneratedArtifactFile> packageFiles(String marker) {
        return List.of(new GeneratedArtifactFile("README.md", "text/markdown", "readme " + marker),
                new GeneratedArtifactFile("inventory/hosts", "text/plain", "[artemislocal]\n"),
                new GeneratedArtifactFile("inventory/group_vars/artemistests_iris.yml", "application/x-yaml", "---\niris " + marker));
    }

    private String remoteHead(String branch) throws IOException {
        try (Git remote = Git.open(remoteDir.toFile())) {
            return remote.getRepository().resolve(Constants.R_HEADS + branch).name();
        }
    }

    private Git cloneBranch(String branch) throws GitAPIException, IOException {
        Path verificationDir = Files.createTempDirectory(tempDir, "verification");
        return Git.cloneRepository().setURI(remoteUrl).setDirectory(verificationDir.toFile()).setBranch(branch).call();
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
