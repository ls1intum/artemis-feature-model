package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the deployment repository publish action. Publishing only runs when it is explicitly enabled; the
 * repository URL of a publish comes from the request or, when the request has none, from the configured default. HTTPS
 * tokens and SSH key paths are deliberately not configuration properties — they are read exclusively from environment
 * variables and never expose credential contents in a config file, log line, or response.
 *
 * @param enabled whether the publish action is enabled.
 * @param repositoryUrl default URL shown in the review page field and used when a request has none; HTTPS, SSH, or
 *            {@code file://}, without any credential.
 * @param allowedRepositoryUrlPrefixes prefixes every effective repository URL must start with; absent or empty means
 *            the whole of GitHub over HTTPS and SSH.
 * @param branch deployment branch receiving the machine commits.
 * @param targetDirectoryRoot repository directory under which each target's package directory lives.
 * @param expectedVisibility declared visibility of a {@code github.com} repository ({@code public} or
 *            {@code private}); the publisher refuses to push when the actual visibility differs.
 * @param authorName commit author and committer name.
 * @param authorEmail commit author and committer email address.
 */
@ConfigurationProperties(prefix = "artemis.feature-model.deployment-repository")
public record DeploymentRepositoryProperties(boolean enabled, String repositoryUrl, List<String> allowedRepositoryUrlPrefixes, String branch,
        String targetDirectoryRoot, String expectedVisibility, String authorName, String authorEmail) {

    /** Allowed prefixes when none are configured: the whole of GitHub over HTTPS and both SSH URL forms. */
    static final List<String> DEFAULT_ALLOWED_REPOSITORY_URL_PREFIXES = List.of("https://github.com/", "git@github.com:", "ssh://git@github.com/");

    /**
     * Applies the committed defaults for absent values. Blank prefix entries are dropped, because an empty prefix
     * would allow every URL.
     *
     * @param enabled whether the publish action is enabled.
     * @param repositoryUrl default deployment repository URL.
     * @param allowedRepositoryUrlPrefixes allowed repository URL prefixes.
     * @param branch deployment branch.
     * @param targetDirectoryRoot target directory root.
     * @param expectedVisibility declared {@code github.com} repository visibility.
     * @param authorName commit author name.
     * @param authorEmail commit author email address.
     */
    public DeploymentRepositoryProperties {
        allowedRepositoryUrlPrefixes = nonBlankOrDefault(allowedRepositoryUrlPrefixes);
        branch = hasText(branch) ? branch : "main";
        targetDirectoryRoot = hasText(targetDirectoryRoot) ? targetDirectoryRoot : "deployments";
        authorName = hasText(authorName) ? authorName : "artemis-feature-model composer";
        authorEmail = hasText(authorEmail) ? authorEmail : "feature-model@thesis.invalid";
    }

    /**
     * Keeps the non-blank prefixes, or falls back to the GitHub defaults when none remain.
     *
     * @param prefixes configured prefixes, possibly {@code null}.
     * @return immutable non-empty prefix list.
     */
    private static List<String> nonBlankOrDefault(List<String> prefixes) {
        List<String> nonBlank = new ArrayList<>();
        if (prefixes != null) {
            for (String prefix : prefixes) {
                if (hasText(prefix)) {
                    nonBlank.add(prefix.strip());
                }
            }
        }
        return nonBlank.isEmpty() ? DEFAULT_ALLOWED_REPOSITORY_URL_PREFIXES : List.copyOf(nonBlank);
    }

    /**
     * Checks whether a nullable value carries text.
     *
     * @param value nullable value.
     * @return whether the value is non-blank.
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
