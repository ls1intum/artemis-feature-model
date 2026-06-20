package de.tum.cit.aet.artemis.featuremodel.deployment.dto;

import java.util.List;

/**
 * Availability of a single guided decision option under the active deployment profile.
 *
 * <p>
 * {@code teacherReason} is a readable, non-technical message suitable for guided decision cards and never contains raw
 * capability ids. {@code requiredCapabilities} and {@code missingCapabilities} carry the technical detail for advanced
 * tree and debug views only.
 *
 * @param optionId guided decision option id.
 * @param available whether the option can be selected under the active profile.
 * @param requiredCapabilities capabilities the option requires.
 * @param missingCapabilities required capabilities the active profile does not provide.
 * @param teacherReason teacher-facing reason when unavailable, otherwise {@code null}.
 */
public record OptionAvailabilityDTO(String optionId, boolean available, List<String> requiredCapabilities, List<String> missingCapabilities, String teacherReason) {

    /**
     * Creates an option availability DTO and normalizes nullable capability lists to immutable empty lists.
     *
     * @param optionId guided decision option id.
     * @param available whether the option can be selected.
     * @param requiredCapabilities capabilities the option requires.
     * @param missingCapabilities required capabilities the active profile does not provide.
     * @param teacherReason teacher-facing reason when unavailable.
     */
    public OptionAvailabilityDTO {
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
        missingCapabilities = missingCapabilities == null ? List.of() : List.copyOf(missingCapabilities);
    }
}
