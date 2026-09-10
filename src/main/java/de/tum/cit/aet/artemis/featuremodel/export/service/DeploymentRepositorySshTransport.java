package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.List;
import java.util.function.UnaryOperator;

import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;

/**
 * Owns SSH URL recognition and the explicit, attempt-local SSH configuration used by the deployment repository
 * publisher. The configured key and known-hosts files are the only SSH inputs; the process home directory, SSH
 * config, other identities, and interactive authentication are not used.
 */
final class DeploymentRepositorySshTransport {

    static final String SSH_KEY_PATH_ENV_VAR = "FM_DEPLOYMENT_REPO_SSH_KEY_PATH";

    static final String KNOWN_HOSTS_PATH_ENV_VAR = "FM_DEPLOYMENT_REPO_KNOWN_HOSTS_PATH";

    private static final String SSH_URL_PREFIX = "ssh://";

    private static final String SCP_LIKE_URL_PREFIX = "git@";

    private static final String GITHUB_HTTPS_PREFIX = "https://github.com/";

    private static final String GITHUB_SSH_PREFIX = "ssh://git@github.com/";

    private static final String GITHUB_SCP_LIKE_PREFIX = "git@github.com:";

    private final String repositoryUrl;

    private final UnaryOperator<String> environmentReader;

    /**
     * Creates the SSH transport boundary for one configured repository URL.
     *
     * @param repositoryUrl configured deployment repository URL.
     * @param environmentReader environment variable reader.
     */
    DeploymentRepositorySshTransport(String repositoryUrl, UnaryOperator<String> environmentReader) {
        this.repositoryUrl = repositoryUrl;
        this.environmentReader = environmentReader;
    }

    /**
     * Checks whether the repository URL selects SSH transport.
     *
     * @return whether the URL is an {@code ssh://} or scp-like {@code git@host:path} URL.
     */
    boolean isSshRemote() {
        if (repositoryUrl == null) {
            return false;
        }
        boolean sshUrl = repositoryUrl.startsWith(SSH_URL_PREFIX);
        boolean scpLikeUrl = repositoryUrl.startsWith(SCP_LIKE_URL_PREFIX)
                && repositoryUrl.indexOf(':') > SCP_LIKE_URL_PREFIX.length();
        return sshUrl || scpLikeUrl;
    }

    /**
     * Names the first missing SSH path variable or unreadable file.
     *
     * @return human-readable configuration gap, or {@code null} when SSH is not selected or is fully configured.
     */
    String configurationGap() {
        if (!isSshRemote()) {
            return null;
        }
        String keyPath = environmentReader.apply(SSH_KEY_PATH_ENV_VAR);
        if (keyPath == null || keyPath.isBlank()) {
            return "the " + SSH_KEY_PATH_ENV_VAR + " environment variable is not set.";
        }
        if (!isReadableFile(keyPath)) {
            return "the file named by " + SSH_KEY_PATH_ENV_VAR + " is not readable: " + keyPath;
        }
        String knownHostsPath = environmentReader.apply(KNOWN_HOSTS_PATH_ENV_VAR);
        if (knownHostsPath == null || knownHostsPath.isBlank()) {
            return "the " + KNOWN_HOSTS_PATH_ENV_VAR + " environment variable is not set.";
        }
        if (!isReadableFile(knownHostsPath)) {
            return "the file named by " + KNOWN_HOSTS_PATH_ENV_VAR + " is not readable: " + knownHostsPath;
        }
        return null;
    }

    /**
     * Builds the SSH session factory for one publish attempt. The caller closes the factory before deleting the
     * attempt directory.
     *
     * @param attemptDirectory attempt-local home directory.
     * @return configured SSH session factory, or {@code null} for a non-SSH remote.
     */
    SshdSessionFactory createSessionFactory(Path attemptDirectory) {
        if (!isSshRemote()) {
            return null;
        }
        Path keyPath = Path.of(environmentReader.apply(SSH_KEY_PATH_ENV_VAR));
        Path knownHostsPath = Path.of(environmentReader.apply(KNOWN_HOSTS_PATH_ENV_VAR));
        return new SshdSessionFactoryBuilder()
                .setHomeDirectory(attemptDirectory.toFile())
                .setSshDirectory(attemptDirectory.resolve(".ssh").toFile())
                .setConfigFile(directory -> null)
                .setDefaultIdentities(directory -> List.of(keyPath))
                .setDefaultKnownHostsFiles(directory -> List.of(knownHostsPath))
                .setPreferredAuthentications("publickey")
                .build(new JGitKeyCache());
    }

    /**
     * Builds the per-command callback that installs the attempt's SSH session factory.
     *
     * @param sessionFactory attempt SSH session factory, or {@code null} for non-SSH transport.
     * @return transport callback, or {@code null} for non-SSH transport.
     */
    TransportConfigCallback callback(SshdSessionFactory sessionFactory) {
        if (sessionFactory == null) {
            return null;
        }
        return transport -> ((SshTransport) transport).setSshSessionFactory(sessionFactory);
    }

    /**
     * Checks whether the configured private key can be loaded after public-key authentication failed. This separates
     * a malformed or unsupported key from a valid key the server did not authorize.
     *
     * @return key-load failure detail, or {@code null} when the key loads successfully.
     */
    String privateKeyLoadFailure() {
        Path keyPath = Path.of(environmentReader.apply(SSH_KEY_PATH_ENV_VAR));
        try (InputStream input = Files.newInputStream(keyPath)) {
            Iterable<KeyPair> keys = SecurityUtils.loadKeyPairIdentities(null, NamedResource.ofName(keyPath.toString()), input, null);
            if (keys == null) {
                return "the SSH private key at " + keyPath + " contains no loadable Ed25519, ECDSA, or RSA key.";
            }
            return null;
        }
        catch (IOException | GeneralSecurityException e) {
            return "could not load the SSH private key at " + keyPath + ": " + e.getMessage();
        }
    }

    /**
     * Extracts the owner/repository path used by the GitHub repository API.
     *
     * @return owner/repository path without a trailing {@code .git}, or {@code null} for a non-GitHub URL.
     */
    String gitHubRepositoryPath() {
        String repositoryPath;
        if (repositoryUrl.startsWith(GITHUB_HTTPS_PREFIX)) {
            repositoryPath = repositoryUrl.substring(GITHUB_HTTPS_PREFIX.length());
        }
        else if (repositoryUrl.startsWith(GITHUB_SSH_PREFIX)) {
            repositoryPath = repositoryUrl.substring(GITHUB_SSH_PREFIX.length());
        }
        else if (repositoryUrl.startsWith(GITHUB_SCP_LIKE_PREFIX)) {
            repositoryPath = repositoryUrl.substring(GITHUB_SCP_LIKE_PREFIX.length());
        }
        else {
            return null;
        }
        if (repositoryPath.endsWith(".git")) {
            return repositoryPath.substring(0, repositoryPath.length() - ".git".length());
        }
        return repositoryPath;
    }

    private static boolean isReadableFile(String path) {
        Path file = Path.of(path);
        return Files.isRegularFile(file) && Files.isReadable(file);
    }
}
