package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ExtractionMetadata;

public record ExtractionMetadataDTO(String method, String confidence, String status) {

    /**
     * Converts domain extraction metadata to its REST DTO representation.
     *
     * @param extraction domain extraction metadata.
     * @return DTO containing the same extraction metadata, or null when absent.
     */
    public static ExtractionMetadataDTO fromDomain(ExtractionMetadata extraction) {
        if (extraction == null) {
            return null;
        }
        return new ExtractionMetadataDTO(extraction.method(), extraction.confidence(), extraction.status());
    }
}
