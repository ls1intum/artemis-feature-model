package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;

/**
 * Output of resolving a feature selection and deployment profile into overlay entries and report fragments.
 *
 * @param entries ordered overlay entries to write into the YAML overlay.
 * @param environmentVariables environment variable names referenced by the overlay, in encounter order.
 * @param consumedParameters profile parameters consumed by the generated overlay.
 * @param omittedMappings mappings that were skipped, with their reasons.
 * @param messages warnings and informational notes produced during resolution.
 */
public record ResolutionResult(List<OverlayEntry> entries, List<String> environmentVariables, List<ConsumedParameter> consumedParameters,
        List<OmittedMapping> omittedMappings, List<GenerationMessage> messages) {

    /**
     * Normalizes nullable collections to immutable empty collections.
     *
     * @param entries ordered overlay entries.
     * @param environmentVariables environment variable names referenced by the overlay.
     * @param consumedParameters profile parameters consumed by the overlay.
     * @param omittedMappings skipped mappings with reasons.
     * @param messages warnings and informational notes.
     */
    public ResolutionResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
        environmentVariables = environmentVariables == null ? List.of() : List.copyOf(environmentVariables);
        consumedParameters = consumedParameters == null ? List.of() : List.copyOf(consumedParameters);
        omittedMappings = omittedMappings == null ? List.of() : List.copyOf(omittedMappings);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
