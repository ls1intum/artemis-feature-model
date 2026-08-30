package de.tum.cit.aet.artemis.featuremodel.export.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the deployment repository publish action. Publishing is disabled unless it is explicitly enabled
 * and a repository URL is configured; the committed defaults keep it off. The access token is deliberately not a
 * configuration property — it is read exclusively from the {@code FM_DEPLOYMENT_REPO_TOKEN} environment variable and
 * never appears in any config file, log line, or response.
 *
 * @param enabled whether the publish action is enabled.
 * @param repositoryUrl HTTPS or {@code file://} URL of the deployment repository, without any credential.
 * @param branch deployment branch receiving the machine commits.
 * @param targetDirectoryRoot repository directory under which each target's package directory lives.
 * @param expectedVisibility declared visibility of a {@code github.com} repository ({@code public} or
 *            {@code private}); the publisher refuses to push when the actual visibility differs.
 * @param authorName commit author and committer name.
 * @param authorEmail commit author and committer email address.
 */
@ConfigurationProperties(prefix = "artemis.feature-model.deployment-repository")
public record DeploymentRepositoryProperties(boolean enabled, String repositoryUrl, String branch, String targetDirectoryRoot,
        String expectedVisibility, String authorName, String authorEmail) {

    /**
     * Applies the committed defaults for absent values.
     *
     * @param enabled whether the publish action is enabled.
     * @param repositoryUrl deployment repository URL.
     * @param branch deployment branch.
     * @param targetDirectoryRoot target directory root.
     * @param expectedVisibility declared {@code github.com} repository visibility.
     * @param authorName commit author name.
     * @param authorEmail commit author email address.
     */
    public DeploymentRepositoryProperties {
        branch = hasText(branch) ? branch : "main";
        targetDirectoryRoot = hasText(targetDirectoryRoot) ? targetDirectoryRoot : "deployments";
        authorName = hasText(authorName) ? authorName : "artemis-feature-model composer";
        authorEmail = hasText(authorEmail) ? authorEmail : "feature-model@thesis.invalid";
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
