package de.tum.cit.aet.artemis.featuremodel.export.dto;

/**
 * Describes where a deployment repository publish would go, so the review page can prefill the repository field, show
 * the destination, and hide the publish action on an unconfigured instance. Never carries a credential.
 *
 * @param configured whether publishing can be offered: enabled and at least one transport usable.
 * @param defaultRepositoryUrl configured default deployment repository URL, or {@code null}.
 * @param branch configured deployment branch.
 * @param targetDirectoryRoot repository directory under which each target's package directory lives.
 */
public record DeploymentPackagePublishTargetResponse(boolean configured, String defaultRepositoryUrl, String branch, String targetDirectoryRoot) {
}
