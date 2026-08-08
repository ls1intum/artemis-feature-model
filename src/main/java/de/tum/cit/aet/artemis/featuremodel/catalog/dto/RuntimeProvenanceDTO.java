package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelProvenance;

/**
 * Safe read-only identity of the active complete runtime feature-model bundle.
 *
 * @param sourceMode active source mode.
 * @param modelId active model id.
 * @param modelVersion active model version.
 * @param snapshotId validated snapshot id, omitted in classpath mode.
 * @param snapshotDigest validated snapshot digest, omitted in classpath mode.
 * @param artemisCommit pinned Artemis commit, omitted in classpath mode.
 * @param manifestDigest scope-manifest digest, omitted in classpath mode.
 * @param featureModelRepositoryCommit feature-model repository commit, omitted in classpath mode.
 * @param extractorVersion extractor version, omitted in classpath mode.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuntimeProvenanceDTO(String sourceMode, String modelId, String modelVersion, String snapshotId, String snapshotDigest, String artemisCommit,
        String manifestDigest, String featureModelRepositoryCommit, String extractorVersion) {

    /**
     * Converts internal runtime provenance to the stable public response.
     *
     * @param provenance internal process-stable identity.
     * @return safe public provenance response.
     */
    public static RuntimeProvenanceDTO from(RuntimeFeatureModelProvenance provenance) {
        return new RuntimeProvenanceDTO(provenance.sourceMode().value(), provenance.modelId(), provenance.modelVersion(), provenance.snapshotId(),
                provenance.snapshotDigest(), provenance.artemisCommit(), provenance.manifestDigest(), provenance.featureModelRepositoryCommit(),
                provenance.extractorVersion());
    }
}
