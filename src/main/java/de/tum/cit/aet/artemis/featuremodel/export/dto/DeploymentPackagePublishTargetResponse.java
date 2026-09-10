package de.tum.cit.aet.artemis.featuremodel.export.dto;

/**
 * Describes where a deployment repository publish would go, so the review page can show the destination and hide the
 * publish action on an unconfigured instance. Never carries a credential.
 *
 * @param configured whether publishing is fully configured (enabled, URL present, token available where needed).
 * @param repositoryUrl configured deployment repository URL, or {@code null}.
 * @param branch configured deployment branch.
 * @param targetDirectoryRoot repository directory under which each target's package directory lives.
 */
public record DeploymentPackagePublishTargetResponse(boolean configured, String repositoryUrl, String branch, String targetDirectoryRoot) {
}
