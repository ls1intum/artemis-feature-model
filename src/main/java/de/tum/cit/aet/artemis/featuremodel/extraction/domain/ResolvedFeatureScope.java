package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Final semantics of one included candidate after annotation-over-manifest precedence is applied.
 *
 * @param candidateId namespaced extraction candidate id.
 * @param id curated model id.
 * @param group group placement, or null.
 * @param parent direct parent placement, or null.
 * @param kind model kind.
 * @param optionality {@code mandatory} or {@code optional}; drives the parent relation type of the generated model.
 * @param category {@code functional} or {@code technical}; null defaults by kind during assembly.
 * @param defaultState {@code enabled} or {@code disabled}, or null to defer to the scanned YAML default.
 * @param order relation order under the parent, or null to append after ordered siblings.
 * @param requiresCapabilities required deployment capabilities.
 * @param providesCapabilities provided deployment capabilities.
 * @param artifactMappings declared artifact mapping hints beyond the auto-derived enabled-key mapping.
 * @param name explicit name override, or null to use extracted i18n.
 * @param description explicit description override, or null to use extracted i18n.
 * @param documentationUrl explicit documentation link override, or null to use extracted admin-page data.
 * @param semanticSource {@code manifest} or {@code annotation}.
 */
public record ResolvedFeatureScope(String candidateId, String id, String group, String parent, String kind, String optionality, String category,
        String defaultState, Integer order, List<String> requiresCapabilities, List<String> providesCapabilities,
        List<FeatureScopeManifest.MappingHint> artifactMappings, String name, String description, String documentationUrl, String semanticSource) {

    /**
     * Normalizes capability and mapping collections to immutable lists.
     */
    public ResolvedFeatureScope {
        requiresCapabilities = requiresCapabilities == null ? List.of() : List.copyOf(requiresCapabilities);
        providesCapabilities = providesCapabilities == null ? List.of() : List.copyOf(providesCapabilities);
        artifactMappings = artifactMappings == null ? List.of() : List.copyOf(artifactMappings);
    }
}
