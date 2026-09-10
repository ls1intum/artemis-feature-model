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
import java.util.HashMap;
import java.util.Map;

import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentRepositoryPublishException;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for SSH URL recognition, configuration, GitHub routing, and controlled transport errors. */
class DeploymentRepositorySshTransportTest {

    @TempDir
    Path tempDir;

    @Test
    void recognizesSshAndLeavesOtherTransportsAlone() {
        assertThat(transport("ssh://git@example.com/owner/repository.git", Map.of()).isSshRemote()).isTrue();
        assertThat(transport("git@example.com:owner/repository.git", Map.of()).isSshRemote()).isTrue();
        assertThat(transport("https://example.com/owner/repository.git", Map.of()).isSshRemote()).isFalse();
        assertThat(transport("file:///tmp/repository.git", Map.of()).isSshRemote()).isFalse();
    }

    @Test
    void reportsEachMissingSshPathAndUnreadableFile() throws IOException {
        Map<String, String> environment = new HashMap<>();
        DeploymentRepositorySshTransport transport = transport("git@example.com:owner/repository.git", environment);

        assertNotConfigured(environment, DeploymentRepositorySshTransport.SSH_KEY_PATH_ENV_VAR);

        Path missingKey = tempDir.resolve("missing-key");
        environment.put(DeploymentRepositorySshTransport.SSH_KEY_PATH_ENV_VAR, missingKey.toString());
        assertNotConfigured(environment, DeploymentRepositorySshTransport.SSH_KEY_PATH_ENV_VAR, "not readable");

        Path key = Files.writeString(tempDir.resolve("key"), "key");
        environment.put(DeploymentRepositorySshTransport.SSH_KEY_PATH_ENV_VAR, key.toString());
        assertNotConfigured(environment, DeploymentRepositorySshTransport.KNOWN_HOSTS_PATH_ENV_VAR);

        Path missingKnownHosts = tempDir.resolve("missing-known-hosts");
        environment.put(DeploymentRepositorySshTransport.KNOWN_HOSTS_PATH_ENV_VAR, missingKnownHosts.toString());
        assertNotConfigured(environment, DeploymentRepositorySshTransport.KNOWN_HOSTS_PATH_ENV_VAR, "not readable");

        Path knownHosts = Files.writeString(tempDir.resolve("known_hosts"), "host key");
        environment.put(DeploymentRepositorySshTransport.KNOWN_HOSTS_PATH_ENV_VAR, knownHosts.toString());
        assertThat(transport.configurationGap()).isNull();
    }

    @Test
    void sshConfigurationDoesNotRequireTheHttpsToken() throws IOException {
        Path key = Files.writeString(tempDir.resolve("key"), "key");
        Path knownHosts = Files.writeString(tempDir.resolve("known_hosts"), "host key");
        Map<String, String> environment = Map.of(DeploymentRepositorySshTransport.SSH_KEY_PATH_ENV_VAR, key.toString(),
                DeploymentRepositorySshTransport.KNOWN_HOSTS_PATH_ENV_VAR, knownHosts.toString());
        DeploymentRepositoryProperties properties = properties("git@example.com:owner/repository.git");

        DeploymentRepositoryPublisher publisher = new DeploymentRepositoryPublisher(properties, new ObjectMapper(), environment::get);

        assertThat(publisher.isConfigured()).isTrue();
    }

    @Test
    void extractsGitHubRepositoryPathsFromHttpsAndBothSshForms() {
        assertThat(transport("https://github.com/example/deployments.git", Map.of()).gitHubRepositoryPath()).isEqualTo("example/deployments");
        assertThat(transport("ssh://git@github.com/example/deployments.git", Map.of()).gitHubRepositoryPath()).isEqualTo("example/deployments");
        assertThat(transport("git@github.com:example/deployments.git", Map.of()).gitHubRepositoryPath()).isEqualTo("example/deployments");
        assertThat(transport("git@example.com:example/deployments.git", Map.of()).gitHubRepositoryPath()).isNull();
    }

    @Test
    void mapsSshAuthenticationAndHostKeyFailuresToTheirControlledCodes() throws GeneralSecurityException, IOException {
        Path privateKey = tempDir.resolve("id_ecdsa");
        writeEcdsaPrivateKey(privateKey);
        Map<String, String> environment = Map.of(DeploymentRepositorySshTransport.SSH_KEY_PATH_ENV_VAR, privateKey.toString());
        DeploymentRepositoryProperties properties = properties("git@example.com:owner/repository.git");
        DeploymentRepositoryPublisher publisher = new DeploymentRepositoryPublisher(properties, new ObjectMapper(), environment::get);

        DeploymentRepositoryPublishException authentication = publisher
                .asControlledTransportFailure(new TransportException("Auth fail for methods 'publickey'"));
        DeploymentRepositoryPublishException hostKey = publisher
                .asControlledTransportFailure(new TransportException("Server key did not validate"));

        assertThat(authentication.getCode()).isEqualTo("PUBLISH_AUTH_FAILED");
        assertThat(hostKey.getCode()).isEqualTo("PUBLISH_REJECTED");
    }

    private void writeEcdsaPrivateKey(Path path) throws GeneralSecurityException, IOException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        try (OutputStream output = Files.newOutputStream(path)) {
            OpenSSHKeyPairResourceWriter.INSTANCE.writePrivateKey(keyPair, "test key", null, output);
        }
    }

    private DeploymentRepositorySshTransport transport(String url, Map<String, String> environment) {
        return new DeploymentRepositorySshTransport(url, environment::get);
    }

    private void assertNotConfigured(Map<String, String> environment, String... expectedMessageParts) {
        DeploymentRepositoryProperties properties = properties("git@example.com:owner/repository.git");
        DeploymentRepositoryPublisher publisher = new DeploymentRepositoryPublisher(properties, new ObjectMapper(), environment::get);

        assertThatThrownBy(publisher::requireConfigured).isInstanceOfSatisfying(DeploymentRepositoryPublishException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo("PUBLISH_NOT_CONFIGURED");
            assertThat(exception.getMessage()).contains(expectedMessageParts);
        });
    }

    private DeploymentRepositoryProperties properties(String url) {
        return new DeploymentRepositoryProperties(true, url, "deployment", null, null, null, null);
    }
}
