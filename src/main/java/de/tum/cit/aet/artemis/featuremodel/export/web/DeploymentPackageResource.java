package de.tum.cit.aet.artemis.featuremodel.export.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentModes;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationResponse;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactPackageService;
import de.tum.cit.aet.artemis.featuremodel.export.service.DeploymentPackageService;
import de.tum.cit.aet.artemis.featuremodel.export.service.RuntimePackageConstants;

/**
 * REST endpoints for the deployment package. The package reuses the generated configuration artifacts and enriches
 * them with runtime templates, helper scripts, and metadata; the same controlled errors as the configuration artifact
 * endpoints apply (invalid selection, unknown profile).
 */
@RestController
@RequestMapping("/api/feature-model/deployment-package")
public class DeploymentPackageResource {

    private static final Logger log = LoggerFactory.getLogger(DeploymentPackageResource.class);

    private static final MediaType ZIP_MEDIA_TYPE = MediaType.parseMediaType("application/zip");

    private final DeploymentPackageService deploymentPackageService;

    private final ArtifactPackageService artifactPackageService;

    /**
     * Creates the deployment package resource.
     *
     * @param deploymentPackageService service used to generate the runtime package.
     * @param artifactPackageService service used to assemble the downloadable ZIP.
     */
    public DeploymentPackageResource(DeploymentPackageService deploymentPackageService, ArtifactPackageService artifactPackageService) {
        this.deploymentPackageService = deploymentPackageService;
        this.artifactPackageService = artifactPackageService;
    }

    /**
     * Generates a preview of the local runtime deployment package for a feature selection and deployment profile.
     *
     * @param request artifact generation request.
     * @return preview response with generated file content and the generation report.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException if the selection is invalid.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException if the profile cannot be resolved.
     */
    @PostMapping("/preview")
    public ArtifactGenerationResponse preview(@RequestBody ArtifactGenerationRequest request) {
        log.debug("REST request to preview a deployment package for {} selected feature ids.", request.selectedFeatureIds().size());
        GeneratedArtifactPackage runtimePackage = deploymentPackageService.generate(request);
        log.info("REST response previews a deployment package with {} files and status {}.", runtimePackage.files().size(), runtimePackage.report().status());
        return ArtifactGenerationResponse.from(runtimePackage);
    }

    /**
     * Generates and downloads the local runtime deployment package as a ZIP archive.
     *
     * @param request artifact generation request.
     * @return ZIP archive download.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException if the selection is invalid.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException if the profile cannot be resolved.
     */
    @PostMapping("/download")
    public ResponseEntity<Resource> download(@RequestBody ArtifactGenerationRequest request) {
        log.debug("REST request to download a deployment package for {} selected feature ids.", request.selectedFeatureIds().size());
        GeneratedArtifactPackage runtimePackage = deploymentPackageService.generate(request);
        boolean remoteAnsible = DeploymentModes.REMOTE_ANSIBLE.equals(request.deploymentMode());
        String rootDir = remoteAnsible ? RuntimePackageConstants.REMOTE_ANSIBLE_PACKAGE_ROOT_DIR : RuntimePackageConstants.PACKAGE_ROOT_DIR;
        String zipName = remoteAnsible ? RuntimePackageConstants.REMOTE_ANSIBLE_PACKAGE_ZIP_NAME : RuntimePackageConstants.PACKAGE_ZIP_NAME;
        byte[] archive = artifactPackageService.zip(runtimePackage, rootDir);
        log.info("REST response returns a {}-byte deployment package ZIP.", archive.length);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"")
                .contentType(ZIP_MEDIA_TYPE).contentLength(archive.length).body(new ByteArrayResource(archive));
    }
}
