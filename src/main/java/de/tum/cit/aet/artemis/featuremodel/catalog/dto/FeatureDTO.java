package de.tum.cit.aet.artemis.featuremodel.catalog.dto;

import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;

public record FeatureDTO(String id, String name, String kind, boolean selectable, String description, String defaultState, FeatureSourceDTO source,
        String category, List<String> visibleTo, List<String> configurableBy, List<String> requiresCapabilities, List<ArtifactMappingDTO> artifactMappings,
        ExtractionMetadataDTO extraction) {

    /**
     * Creates a feature DTO and normalizes nullable collections to immutable empty lists.
     *
     * @param id stable feature id.
     * @param name display name.
     * @param kind feature kind.
     * @param selectable whether users can toggle this feature.
     * @param description optional description.
     * @param defaultState default selection state.
     * @param source source evidence metadata.
     * @param category feature category.
     * @param visibleTo roles that may see this feature, or an empty list for unrestricted visibility.
     * @param configurableBy roles that may configure this feature.
     * @param requiresCapabilities deployment capabilities required by this feature.
     * @param artifactMappings future artifact generation mappings.
     * @param extraction extraction and review metadata.
     */
    public FeatureDTO {
        visibleTo = visibleTo == null ? List.of() : List.copyOf(visibleTo);
        configurableBy = configurableBy == null ? List.of() : List.copyOf(configurableBy);
        requiresCapabilities = requiresCapabilities == null ? List.of() : List.copyOf(requiresCapabilities);
        artifactMappings = artifactMappings == null ? List.of() : List.copyOf(artifactMappings);
    }

    /**
     * Converts a domain feature node to its REST DTO representation.
     *
     * @param feature domain feature node.
     * @return DTO containing the same feature data.
     */
    public static FeatureDTO fromDomain(FeatureNode feature) {
        return new FeatureDTO(feature.id(), feature.name(), feature.kind(), feature.selectable(), feature.description(), feature.defaultState(),
                FeatureSourceDTO.fromDomain(feature.source()), feature.category(), feature.visibleTo(), feature.configurableBy(), feature.requiresCapabilities(),
                feature.artifactMappings().stream().map(ArtifactMappingDTO::fromDomain).toList(), ExtractionMetadataDTO.fromDomain(feature.extraction()));
    }
}
