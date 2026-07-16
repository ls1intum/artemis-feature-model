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
 * @param requiresCapabilities required deployment capabilities.
 * @param providesCapabilities provided deployment capabilities.
 * @param name explicit name override, or null to use extracted i18n.
 * @param description explicit description override, or null to use extracted i18n.
 * @param documentationUrl explicit documentation link override, or null to use extracted admin-page data.
 * @param semanticSource {@code manifest} or {@code annotation}.
 */
public record ResolvedFeatureScope(String candidateId, String id, String group, String parent, String kind, List<String> requiresCapabilities,
        List<String> providesCapabilities, String name, String description, String documentationUrl, String semanticSource) {

    /**
     * Normalizes capability collections to immutable lists.
     */
    public ResolvedFeatureScope {
        requiresCapabilities = requiresCapabilities == null ? List.of() : List.copyOf(requiresCapabilities);
        providesCapabilities = providesCapabilities == null ? List.of() : List.copyOf(providesCapabilities);
    }
}
