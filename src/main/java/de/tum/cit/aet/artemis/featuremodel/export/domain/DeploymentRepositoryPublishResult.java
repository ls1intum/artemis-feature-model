package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * Result of one deployment repository publish: where the package landed and the commit that carries it. An
 * up-to-date result means the branch head already contained exactly the published bytes, so no commit was created.
 *
 * @param branch deployment branch the publish targeted.
 * @param targetDirectory repository-relative directory holding the published package.
 * @param commitSha commit carrying the published package: the new commit, or the unchanged branch head when the
 *            publish was up to date.
 * @param upToDate whether the branch head already contained the published bytes.
 */
public record DeploymentRepositoryPublishResult(String branch, String targetDirectory, String commitSha, boolean upToDate) {
}
