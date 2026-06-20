package de.tum.cit.aet.artemis.featuremodel.deployment.dto;

import java.util.List;

/**
 * Availability of a single feature under the active deployment profile.
 *
 * <p>
 * A feature is profile-dependent when it requires any capability, either directly or through a guided option that
 * selects it. {@code teacherReason} is a readable, non-technical message for the review page and never contains raw
 * capability ids; {@code requiredCapabilities} and {@code missingCapabilities} carry the technical detail for advanced
 * tree and debug views only.
 *
 * @param featureId feature id.
 * @param featureName feature display name, used to build the teacher-facing reason.
 * @param available whether the feature is available under the active profile.
 * @param profileDependent whether the feature requires any technical capability.
 * @param requiredCapabilities capabilities the feature requires, aggregated from the feature and guided options.
 * @param missingCapabilities required capabilities the active profile does not provide.
 * @param teacherReason teacher-facing reason when unavailable, otherwise {@code null}.
 */
public record FeatureAvailabilityDTO(String featureId, String featureName, boolean available, boolean profileDependent, List<String> requiredCapabilities,
        List<String> missingCapabilities, String teacherReason) {

    /**
     * Creates a feature availability DTO and normalizes nullable capability lists to immutable empty lists.
     *
     * @param featureId feature id.
     * @param featureName feature display name.
     * @param available whether the feature is available.
     * @param profileDependent whether the feature requires any capability.
     * @param requiredCapabilities capabilities the feature requires.
     * @param missingCapabilities required capabilities the active profile does not provide.
     * @param teacherReason teacher-facing reason when unavailable.
     */
    public FeatureAvailabilityDTO {
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
        missingCapabilities = missingCapabilities == null ? List.of() : List.copyOf(missingCapabilities);
    }
}
