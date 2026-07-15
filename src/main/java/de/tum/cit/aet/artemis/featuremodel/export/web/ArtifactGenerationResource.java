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

import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactGenerationService;
import de.tum.cit.aet.artemis.featuremodel.export.service.ArtifactPackageService;

@RestController
@RequestMapping("/api/feature-model/artifacts")
public class ArtifactGenerationResource {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGenerationResource.class);

    private static final MediaType ZIP_MEDIA_TYPE = MediaType.parseMediaType("application/zip");

    private final ArtifactGenerationService artifactGenerationService;

    private final ArtifactPackageService artifactPackageService;

    /**
     * Creates the artifact generation resource.
     *
     * @param artifactGenerationService service used to generate the artifact package.
     * @param artifactPackageService service used to assemble the downloadable ZIP package.
     */
    public ArtifactGenerationResource(ArtifactGenerationService artifactGenerationService, ArtifactPackageService artifactPackageService) {
        this.artifactGenerationService = artifactGenerationService;
        this.artifactPackageService = artifactPackageService;
    }

    /**
     * Generates and downloads the artifact package as a ZIP archive.
     *
     * @param request artifact generation request.
     * @return ZIP archive download.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException if the selection is invalid.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException if the profile cannot be resolved.
     */
    @PostMapping("/download")
    public ResponseEntity<Resource> download(@RequestBody ArtifactGenerationRequest request) {
        log.debug("REST request to download artifacts for {} selected feature ids.", request.selectedFeatureIds().size());
        GeneratedArtifactPackage artifactPackage = artifactGenerationService.generate(request);
        byte[] archive = artifactPackageService.zip(artifactPackage);
        log.info("REST response returns a {}-byte artifact ZIP package.", archive.length);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + ArtifactPackageService.PACKAGE_FILE_NAME + "\"")
                .contentType(ZIP_MEDIA_TYPE).contentLength(archive.length).body(new ByteArrayResource(archive));
    }
}
