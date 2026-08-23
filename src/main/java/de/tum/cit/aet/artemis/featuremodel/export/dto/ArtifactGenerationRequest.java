package de.tum.cit.aet.artemis.featuremodel.export.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request to generate deployment configuration artifacts from a feature selection and a deployment profile.
 *
 * @param selectedFeatureIds selected functional feature ids.
 * @param profileId deployment profile id to generate against, or {@code null}/blank for the default profile.
 * @param mode generation mode, or {@code null}/blank for the default {@code DEMO} mode.
 * @param deploymentMode deployment mode id for the deployment package, or {@code null}/blank for today's default
 *            behavior (the local Docker runtime package). Mode ids are stable strings, not enums.
 * @param remoteEnvironment optional admin-owned environment values of the remote-ansible mode, or {@code null} for a
 *            placeholder package; rejected on non-remote deployment modes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtifactGenerationRequest(List<String> selectedFeatureIds, String profileId, String mode, String deploymentMode,
        RemoteEnvironmentInput remoteEnvironment) {

    /**
     * Normalizes the selected feature ids to an immutable list.
     *
     * @param selectedFeatureIds selected functional feature ids.
     * @param profileId deployment profile id, or {@code null}/blank for the default profile.
     * @param mode generation mode, or {@code null}/blank for the default mode.
     * @param deploymentMode deployment mode id, or {@code null}/blank for the default deployment mode.
     * @param remoteEnvironment optional remote environment values, or {@code null}.
     */
    public ArtifactGenerationRequest {
        selectedFeatureIds = selectedFeatureIds == null ? List.of() : List.copyOf(selectedFeatureIds);
    }

    /**
     * Creates a request without a remote environment component.
     *
     * @param selectedFeatureIds selected functional feature ids.
     * @param profileId deployment profile id, or {@code null}/blank for the default profile.
     * @param mode generation mode, or {@code null}/blank for the default mode.
     * @param deploymentMode deployment mode id, or {@code null}/blank for the default deployment mode.
     */
    public ArtifactGenerationRequest(List<String> selectedFeatureIds, String profileId, String mode, String deploymentMode) {
        this(selectedFeatureIds, profileId, mode, deploymentMode, null);
    }

    /**
     * Creates a request without a deployment mode, keeping today's default behavior. Convenient for callers and tests
     * written before the deployment-mode axis existed.
     *
     * @param selectedFeatureIds selected functional feature ids.
     * @param profileId deployment profile id, or {@code null}/blank for the default profile.
     * @param mode generation mode, or {@code null}/blank for the default mode.
     */
    public ArtifactGenerationRequest(List<String> selectedFeatureIds, String profileId, String mode) {
        this(selectedFeatureIds, profileId, mode, null, null);
    }
}
