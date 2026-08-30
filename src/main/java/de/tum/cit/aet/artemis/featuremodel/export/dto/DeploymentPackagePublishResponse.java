package de.tum.cit.aet.artemis.featuremodel.export.dto;

import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentRepositoryPublishResult;

/**
 * Response of a deployment repository publish: where the package landed and the commit carrying it. The repository
 * URL is the configured, credential-free URL; the commit URL is derived for {@code github.com} remotes only.
 *
 * @param repositoryUrl configured deployment repository URL, never carrying a credential.
 * @param branch deployment branch the publish targeted.
 * @param targetDirectory repository-relative directory holding the published package.
 * @param commitSha commit carrying the published package.
 * @param commitUrl browsable commit URL for {@code github.com} remotes, or {@code null}.
 * @param upToDate whether the branch head already contained the published bytes, so no commit was created.
 */
public record DeploymentPackagePublishResponse(String repositoryUrl, String branch, String targetDirectory, String commitSha, String commitUrl,
        boolean upToDate) {

    private static final String GITHUB_REMOTE_PREFIX = "https://github.com/";

    /**
     * Builds the response from a publish result and the configured repository URL.
     *
     * @param result publish result.
     * @param repositoryUrl configured deployment repository URL.
     * @return publish response.
     */
    public static DeploymentPackagePublishResponse from(DeploymentRepositoryPublishResult result, String repositoryUrl) {
        return new DeploymentPackagePublishResponse(repositoryUrl, result.branch(), result.targetDirectory(), result.commitSha(),
                commitUrlFor(repositoryUrl, result.commitSha()), result.upToDate());
    }

    /**
     * Derives the browsable commit URL of a {@code github.com} remote.
     *
     * @param repositoryUrl configured deployment repository URL.
     * @param commitSha commit carrying the published package.
     * @return commit URL, or {@code null} for a non-GitHub remote.
     */
    private static String commitUrlFor(String repositoryUrl, String commitSha) {
        if (repositoryUrl == null || !repositoryUrl.startsWith(GITHUB_REMOTE_PREFIX)) {
            return null;
        }
        String base = repositoryUrl.endsWith(".git") ? repositoryUrl.substring(0, repositoryUrl.length() - ".git".length()) : repositoryUrl;
        return base + "/commit/" + commitSha;
    }
}
