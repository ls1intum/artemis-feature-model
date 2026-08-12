package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;

/**
 * Output of resolving a feature selection into overlay entries and report fragments.
 *
 * @param entries ordered overlay entries to write into the YAML overlay.
 * @param environmentRequirements structured environment requirements produced by selected environment mappings, in
 *            encounter order.
 * @param messages warnings and informational notes produced during resolution.
 */
public record ResolutionResult(List<OverlayEntry> entries, List<EnvironmentRequirement> environmentRequirements, List<GenerationMessage> messages) {

    /**
     * Normalizes nullable collections to immutable empty collections.
     *
     * @param entries ordered overlay entries.
     * @param environmentRequirements structured environment requirements.
     * @param messages warnings and informational notes.
     */
    public ResolutionResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
        environmentRequirements = environmentRequirements == null ? List.of() : List.copyOf(environmentRequirements);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
