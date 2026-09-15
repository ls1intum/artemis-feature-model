package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Final semantics of one member: the id its membership declaration carries and the modeling judgments of its manifest
 * entry.
 *
 * @param candidateId namespaced extraction candidate id.
 * @param id feature id.
 * @param group group placement, or null.
 * @param parent direct parent placement, or null.
 * @param kind model kind.
 * @param optionality {@code mandatory} or {@code optional}; drives the parent relation type of the generated model.
 * @param category {@code functional} or {@code technical}; null defaults by kind during assembly.
 * @param defaultState {@code enabled} or {@code disabled}, or null to defer to the scanned YAML default.
 * @param order relation order under the parent, or null to append after ordered siblings.
 * @param requiresCapabilities required deployment capabilities.
 * @param providesCapabilities provided deployment capabilities.
 * @param artifactMappings artifact mappings beyond the auto-derived enabled-key mapping: declared hints for
 *            technical members, the resolved configuration mappings for functional members once the deriver ran.
 * @param configuration manifest configuration-key confirmations and exceptions of the member.
 * @param name explicit name override, or null to use extracted i18n.
 * @param description explicit description override, or null to use extracted i18n.
 * @param documentationUrl explicit documentation link override, or null to use extracted admin-page data.
 * @param membershipSource {@code annotation}, {@code provisional}, or {@code technical}.
 */
public record ResolvedFeatureScope(String candidateId, String id, String group, String parent, String kind, String optionality, String category,
        String defaultState, Integer order, List<String> requiresCapabilities, List<String> providesCapabilities,
        List<FeatureScopeManifest.MappingHint> artifactMappings, List<FeatureScopeManifest.ConfigurationEntry> configuration, String name, String description,
        String documentationUrl, String membershipSource) {

    /**
     * Normalizes capability, mapping, and configuration collections to immutable lists.
     */
    public ResolvedFeatureScope {
        requiresCapabilities = requiresCapabilities == null ? List.of() : List.copyOf(requiresCapabilities);
        providesCapabilities = providesCapabilities == null ? List.of() : List.copyOf(providesCapabilities);
        artifactMappings = artifactMappings == null ? List.of() : List.copyOf(artifactMappings);
        configuration = configuration == null ? List.of() : List.copyOf(configuration);
    }

    /**
     * Returns a copy of this scope whose artifact mappings are the given resolved mappings.
     *
     * @param resolvedMappings resolved environment mappings in emission order.
     * @return copy with replaced mappings.
     */
    public ResolvedFeatureScope withArtifactMappings(List<FeatureScopeManifest.MappingHint> resolvedMappings) {
        return new ResolvedFeatureScope(candidateId, id, group, parent, kind, optionality, category, defaultState, order, requiresCapabilities,
                providesCapabilities, resolvedMappings, configuration, name, description, documentationUrl, membershipSource);
    }
}
