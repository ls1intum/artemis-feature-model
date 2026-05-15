package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;

public record ModelMetadataDTO(String id, String name, String version) {

    public static ModelMetadataDTO fromDomain(ModelMetadata metadata) {
        return new ModelMetadataDTO(metadata.id(), metadata.name(), metadata.version());
    }
}
