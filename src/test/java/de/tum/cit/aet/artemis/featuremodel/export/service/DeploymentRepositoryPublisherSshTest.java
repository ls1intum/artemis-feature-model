package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.ssh.SshTestGitServer;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentRepositoryPublishResult;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentRepositoryPublishException;
import tools.jackson.databind.ObjectMapper;

/** Offline end-to-end publish tests against JGit's in-process SSH Git server. */
class DeploymentRepositoryPublisherSshTest {

    private static final String TEST_USER = "test";

    private static final String COMMIT_MESSAGE = "deploy artemis-remote over ssh";

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @EnumSource(TestKeyAlgorithm.class)
    void publishesAndRepublishesWithOpenSshKeys(TestKeyAlgorithm algorithm) throws Exception {
        try (SshRepository repository = startRepository(algorithm)) {
            DeploymentRepositoryPublisher publisher = repository.publisher();

            DeploymentRepositoryPublishResult first = publisher.publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);
            DeploymentRepositoryPublishResult second = publisher.publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);

            assertThat(first.upToDate()).isFalse();
            assertThat(second.upToDate()).isTrue();
            assertThat(second.commitSha()).isEqualTo(first.commitSha());
            try (Git verification = repository.cloneBranch("deployment")) {
                Path packageDirectory = verification.getRepository().getWorkTree().toPath().resolve("deployments/artemis-remote/package");
                assertThat(Files.readString(packageDirectory.resolve("README.md"))).isEqualTo("readme v1");
                assertThat(Files.readString(packageDirectory.resolve("inventory/hosts"))).isEqualTo("[artemislocal]\n");
            }
        }
    }

    @Test
    void retriesFromTheConcurrentSshHeadWithoutLosingEitherTarget() throws Exception {
        try (SshRepository repository = startRepository(TestKeyAlgorithm.ED25519)) {
            DeploymentRepositoryPublisher concurrentPublisher = repository.publisher();
            DeploymentRepositoryPublisher racingPublisher = new DeploymentRepositoryPublisher(repository.properties(), new ObjectMapper(),
                    repository.environmentReader()) {

                private boolean advanced;

                @Override
                protected void beforePush(int attempt) {
                    if (!advanced) {
                        advanced = true;
                        concurrentPublisher.publish("other-target", packageFiles("concurrent"), "concurrent publish");
                    }
                }
            };

            DeploymentRepositoryPublishResult result = racingPublisher.publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE);

            assertThat(result.upToDate()).isFalse();
            try (Git verification = repository.cloneBranch("deployment")) {
                Path workTree = verification.getRepository().getWorkTree().toPath();
                assertThat(Files.readString(workTree.resolve("deployments/artemis-remote/package/README.md"))).isEqualTo("readme v1");
                assertThat(Files.readString(workTree.resolve("deployments/other-target/package/README.md"))).isEqualTo("readme concurrent");
            }
        }
    }

    @Test
    void rejectsAnUnknownHostWithoutWritingKnownHostsOrPushing() throws Exception {
        try (SshRepository repository = startRepository(TestKeyAlgorithm.ED25519)) {
            KeyPair unknownHost = TestKeyAlgorithm.ED25519.generate();
            writeKnownHosts(repository.knownHostsPath(), repository.port(), unknownHost);
            byte[] knownHostsBeforePublish = Files.readAllBytes(repository.knownHostsPath());

            assertThatThrownBy(() -> repository.publisher().publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE))
                    .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                            exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_REJECTED"));

            assertThat(Files.readAllBytes(repository.knownHostsPath())).isEqualTo(knownHostsBeforePublish);
            assertThat(repository.remote().getRepository().resolve(Constants.R_HEADS + "deployment")).isNull();
        }
    }

    @Test
    void mapsAnUnauthorizedClientKeyToAuthenticationFailed() throws Exception {
        try (SshRepository repository = startRepository(TestKeyAlgorithm.ED25519)) {
            writePrivateKey(repository.privateKeyPath(), TestKeyAlgorithm.ED25519.generate());

            assertThatThrownBy(() -> repository.publisher().publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE))
                    .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                            exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_AUTH_FAILED"));

            assertThat(repository.remote().getRepository().resolve(Constants.R_HEADS + "deployment")).isNull();
        }
    }

    @Test
    void mapsAnUnloadableClientKeyToRejected() throws Exception {
        try (SshRepository repository = startRepository(TestKeyAlgorithm.ED25519)) {
            Files.writeString(repository.privateKeyPath(), "not an OpenSSH private key");

            assertThatThrownBy(() -> repository.publisher().publish("artemis-remote", packageFiles("v1"), COMMIT_MESSAGE))
                    .isInstanceOfSatisfying(DeploymentRepositoryPublishException.class,
                            exception -> assertThat(exception.getCode()).isEqualTo("PUBLISH_REJECTED"));

            assertThat(repository.remote().getRepository().resolve(Constants.R_HEADS + "deployment")).isNull();
        }
    }

    private SshRepository startRepository(TestKeyAlgorithm algorithm) throws Exception {
        Path repositoryDirectory = Files.createTempDirectory(tempDir, "ssh-repository");
        Path remoteDirectory = repositoryDirectory.resolve("deployment-repo.git");
        Git remote = Git.init().setBare(true).setInitialBranch("main").setDirectory(remoteDirectory.toFile()).call();
        seedDefaultBranch(repositoryDirectory, remoteDirectory);

        KeyPair clientKey = algorithm.generate();
        KeyPair hostKey = algorithm.generate();
        Path privateKeyPath = repositoryDirectory.resolve("id_key");
        Path publicKeyPath = repositoryDirectory.resolve("id_key.pub");
        writePrivateKey(privateKeyPath, clientKey);
        writePublicKey(publicKeyPath, clientKey);

        SshTestGitServer server = new SshTestGitServer(TEST_USER, publicKeyPath, remote.getRepository(), hostKey);
        int port = server.start();
        Path knownHostsPath = repositoryDirectory.resolve("known_hosts");
        writeKnownHosts(knownHostsPath, port, hostKey);
        String remoteUrl = "ssh://" + TEST_USER + "@localhost:" + port + "/deployment-repo.git";
        return new SshRepository(remote, server, remoteDirectory, remoteUrl, privateKeyPath, knownHostsPath, port);
    }

    private void seedDefaultBranch(Path repositoryDirectory, Path remoteDirectory) throws IOException, GitAPIException {
        Path seedDirectory = repositoryDirectory.resolve("seed");
        try (Git seed = Git.init().setInitialBranch("main").setDirectory(seedDirectory.toFile()).call()) {
            Files.writeString(seedDirectory.resolve("README.md"), "# deployment repository\n");
            seed.add().addFilepattern(".").call();
            PersonIdent seeder = new PersonIdent("seed", "seed@example.invalid");
            seed.commit().setMessage("seed default branch").setAuthor(seeder).setCommitter(seeder).setSign(false).call();
            seed.push().setRemote(remoteDirectory.toUri().toString()).setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call();
        }
    }

    private void writePrivateKey(Path path, KeyPair keyPair) throws IOException, GeneralSecurityException {
        try (OutputStream output = Files.newOutputStream(path)) {
            OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(keyPair, "test key", null, output);
        }
    }

    private void writePublicKey(Path path, KeyPair keyPair) throws IOException, GeneralSecurityException {
        try (OutputStream output = Files.newOutputStream(path)) {
            OpenSSHKeyPairResourceWriter.INSTANCE.writePublicKey(keyPair.getPublic(), "test key", output);
        }
    }

    private void writeKnownHosts(Path path, int port, KeyPair hostKey) throws IOException {
        StringBuilder line = new StringBuilder("[localhost]:").append(port).append(' ');
        PublicKeyEntry.appendPublicKeyEntry(line, hostKey.getPublic());
        Files.writeString(path, line.append('\n'));
    }

    private List<GeneratedArtifactFile> packageFiles(String marker) {
        return List.of(new GeneratedArtifactFile("README.md", "text/markdown", "readme " + marker),
                new GeneratedArtifactFile("inventory/hosts", "text/plain", "[artemislocal]\n"));
    }

    private enum TestKeyAlgorithm {
        ED25519 {
            @Override
            KeyPair generate() throws GeneralSecurityException {
                return KeyUtils.generateKeyPair(KeyPairProvider.SSH_ED25519, 256);
            }
        },
        ECDSA {
            @Override
            KeyPair generate() throws GeneralSecurityException {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
                generator.initialize(new ECGenParameterSpec("secp256r1"));
                return generator.generateKeyPair();
            }
        };

        abstract KeyPair generate() throws GeneralSecurityException;
    }

    private record SshRepository(Git remote, SshTestGitServer server, Path remoteDirectory, String remoteUrl, Path privateKeyPath,
            Path knownHostsPath, int port) implements AutoCloseable {

        private DeploymentRepositoryPublisher publisher() {
            return new DeploymentRepositoryPublisher(properties(), new ObjectMapper(), environmentReader());
        }

        private DeploymentRepositoryProperties properties() {
            return new DeploymentRepositoryProperties(true, remoteUrl, "deployment", null, null, null, null);
        }

        private UnaryOperator<String> environmentReader() {
            Map<String, String> environment = Map.of(DeploymentRepositorySshTransport.SSH_KEY_PATH_ENV_VAR, privateKeyPath.toString(),
                    DeploymentRepositorySshTransport.KNOWN_HOSTS_PATH_ENV_VAR, knownHostsPath.toString());
            return environment::get;
        }

        private Git cloneBranch(String branch) throws GitAPIException, IOException {
            Path cloneDirectory = Files.createTempDirectory(remoteDirectory.getParent(), "verification");
            return Git.cloneRepository().setURI(remoteDirectory.toUri().toString()).setDirectory(cloneDirectory.toFile()).setBranch(branch).call();
        }

        @Override
        public void close() throws IOException {
            server.stop();
            remote.close();
        }
    }
}
