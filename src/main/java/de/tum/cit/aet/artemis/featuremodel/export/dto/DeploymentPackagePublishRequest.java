package de.tum.cit.aet.artemis.featuremodel.export.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request to publish a remote-ansible deployment package. The generation request is the same one the download sends,
 * so publish and download stay byte-identical; the repository URL only decides where the package is pushed and
 * therefore stays outside the generation request shared by the artifact, preview, and download endpoints.
 *
 * @param request artifact generation request; an absent request is treated as an empty one and refused by the
 *            deployment mode check.
 * @param repositoryUrl deployment repository URL, or {@code null}/blank for the configured default.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeploymentPackagePublishRequest(ArtifactGenerationRequest request, String repositoryUrl) {

    /**
     * Normalizes an absent generation request to an empty one.
     *
     * @param request artifact generation request, or {@code null}.
     * @param repositoryUrl deployment repository URL, or {@code null}/blank for the configured default.
     */
    public DeploymentPackagePublishRequest {
        request = request == null ? new ArtifactGenerationRequest(List.of(), null, null) : request;
    }
}
