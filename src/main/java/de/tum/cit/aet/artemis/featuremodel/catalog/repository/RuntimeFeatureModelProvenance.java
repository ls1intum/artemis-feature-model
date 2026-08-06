package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

/**
 * Safe, process-stable identity of the complete runtime feature-model bundle.
 *
 * @param sourceMode configured complete-bundle source.
 * @param modelId active feature model id.
 * @param modelVersion active feature model version.
 * @param snapshotId validated snapshot id, or {@code null} in classpath mode.
 * @param snapshotDigest validated snapshot digest, or {@code null} in classpath mode.
 * @param artemisCommit pinned Artemis commit, or {@code null} in classpath mode.
 * @param manifestDigest scope-manifest digest, or {@code null} in classpath mode.
 * @param featureModelRepositoryCommit feature-model repository commit, or {@code null} in classpath mode.
 * @param extractorVersion extractor version, or {@code null} in classpath mode.
 */
public record RuntimeFeatureModelProvenance(FeatureModelSourceMode sourceMode, String modelId, String modelVersion, String snapshotId,
        String snapshotDigest, String artemisCommit, String manifestDigest, String featureModelRepositoryCommit, String extractorVersion) {
}
