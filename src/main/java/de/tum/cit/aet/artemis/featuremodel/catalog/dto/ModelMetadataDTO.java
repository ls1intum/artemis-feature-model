package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;

public record ModelMetadataDTO(String id, String name, String version, String status, String sourceCommitSha) {

    /**
     * Converts domain model metadata to its REST DTO representation.
     *
     * @param metadata domain model metadata.
     * @return DTO containing the same model metadata.
     */
    public static ModelMetadataDTO fromDomain(ModelMetadata metadata) {
        return new ModelMetadataDTO(metadata.id(), metadata.name(), metadata.version(), metadata.status(), metadata.sourceCommitSha());
    }
}
