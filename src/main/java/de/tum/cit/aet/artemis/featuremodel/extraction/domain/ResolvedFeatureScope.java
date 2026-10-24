package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Final semantics of one member: the id its membership declaration carries, the modeling judgments of its manifest
 * entry, and — once the deriver ran — its derived mappings and capabilities.
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
 * @param requiresCapabilities derived required deployment capabilities; empty until the deriver ran.
 * @param profiles Spring profile tokens a technical member activates; empty defers to the anchor's profile name.
 * @param artifactMappings derived artifact mappings beyond the auto-derived enabled-key mapping; empty until the
 *            deriver ran.
 * @param configuration manifest configuration-key confirmations and exceptions of the member.
 * @param name explicit name override, or null to use extracted i18n.
 * @param description explicit description override, or null to use extracted i18n.
 * @param documentationUrl explicit documentation link override, or null to use extracted admin-page data.
 * @param membershipSource {@code features} or {@code technical}.
 */
public record ResolvedFeatureScope(String candidateId, String id, String group, String parent, String kind, String optionality, String category,
        String defaultState, Integer order, List<String> requiresCapabilities, List<String> profiles, List<FeatureScopeManifest.MappingHint> artifactMappings,
        List<FeatureScopeManifest.ConfigurationEntry> configuration, String name, String description, String documentationUrl, String membershipSource) {

    /**
     * Normalizes capability, profile, mapping, and configuration collections to immutable lists.
     */
    public ResolvedFeatureScope {
        requiresCapabilities = requiresCapabilities == null ? List.of() : List.copyOf(requiresCapabilities);
        profiles = profiles == null ? List.of() : List.copyOf(profiles);
        artifactMappings = artifactMappings == null ? List.of() : List.copyOf(artifactMappings);
        configuration = configuration == null ? List.of() : List.copyOf(configuration);
    }

    /**
     * Returns a copy of this scope carrying the derived mappings and capabilities.
     *
     * @param derivedMappings derived mappings in emission order.
     * @param derivedCapabilities derived required capabilities in emission order.
     * @return copy with the derived values.
     */
    public ResolvedFeatureScope withDerivation(List<FeatureScopeManifest.MappingHint> derivedMappings, List<String> derivedCapabilities) {
        return new ResolvedFeatureScope(candidateId, id, group, parent, kind, optionality, category, defaultState, order, derivedCapabilities, profiles,
                derivedMappings, configuration, name, description, documentationUrl, membershipSource);
    }
}
