package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.nio.file.Path;

/**
 * Validated Docker named-context identity and its local staged locations.
 *
 * @param contextRoot controlled context root.
 * @param snapshotDirectory directory passed as the Docker named context.
 * @param propertiesFile deterministic Docker build-argument file.
 * @param snapshotId validated snapshot id.
 * @param snapshotDigest validated snapshot digest.
 * @param artemisCommit pinned Artemis commit.
 * @param manifestDigest scope-manifest digest.
 * @param featureModelRepositoryCommit feature-model repository commit.
 * @param extractorVersion extractor version.
 */
public record DockerSnapshotContext(Path contextRoot, Path snapshotDirectory, Path propertiesFile, String snapshotId, String snapshotDigest,
        String artemisCommit, String manifestDigest, String featureModelRepositoryCommit, String extractorVersion) {
}
