package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ArtifactMapping;
import tools.jackson.databind.JsonNode;

public record ArtifactMappingDTO(String target, String path, String source, JsonNode valueWhenSelected, JsonNode valueWhenDeselected) {

    /**
     * Converts a domain artifact mapping to its REST DTO representation.
     *
     * @param mapping domain artifact mapping.
     * @return DTO containing the same mapping data.
     */
    public static ArtifactMappingDTO fromDomain(ArtifactMapping mapping) {
        return new ArtifactMappingDTO(mapping.target(), mapping.path(), mapping.source(), mapping.valueWhenSelected(), mapping.valueWhenDeselected());
    }
}
