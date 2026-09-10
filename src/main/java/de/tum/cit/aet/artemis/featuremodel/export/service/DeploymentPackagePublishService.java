package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentModes;
import de.tum.cit.aet.artemis.featuremodel.export.domain.AnsibleBindingCatalog;
import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentRepositoryPublishResult;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GenerationReport;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelectionMetadata;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentRepositoryPublishException;

/**
 * Orchestrates the deployment repository publish of a remote-ansible package: the package is generated through the
 * exact same service path as the download — byte parity between the publish tree and the download ZIP is a
 * determinism consequence, not a copy — and handed to the publisher with a commit message derived entirely from the
 * generated package. This thin service keeps the git concerns out of {@link DeploymentPackageService}.
 */
@Service
public class DeploymentPackagePublishService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentPackagePublishService.class);

    private static final String GROUP_VARS_PREFIX = "inventory/group_vars/";

    private static final String OFF_SWITCH_FILE_PREFIX = "artemistests_without_";

    private final DeploymentPackageService deploymentPackageService;

    private final DeploymentRepositoryPublisher publisher;

    private final AnsibleBindingCatalog catalog;

    /**
     * Creates the publish service.
     *
     * @param deploymentPackageService service generating the deployment package.
     * @param publisher deployment repository publisher.
     * @param catalogLoader fail-closed loader of the Ansible binding catalog, source of the commit-message identity.
     */
    public DeploymentPackagePublishService(DeploymentPackageService deploymentPackageService, DeploymentRepositoryPublisher publisher,
            AnsibleBindingCatalogLoader catalogLoader) {
        this.deploymentPackageService = deploymentPackageService;
        this.publisher = publisher;
        this.catalog = catalogLoader.catalog();
    }

    /**
     * Generates the remote-ansible package for a request and publishes it to the deployment repository.
     *
     * @param request artifact generation request; its deployment mode must be remote-ansible and its environment
     *            component must carry a target name.
     * @return publish result with the carrying commit.
     * @throws DeploymentRepositoryPublishException if publishing is not configured, the deployment mode is wrong, or
     *             the target name is missing.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException if the selection is
     *             invalid or cannot be expressed as a remote-ansible package.
     */
    public DeploymentRepositoryPublishResult publish(ArtifactGenerationRequest request) {
        publisher.requireConfigured();
        if (!DeploymentModes.REMOTE_ANSIBLE.equals(request.deploymentMode())) {
            throw DeploymentRepositoryPublishException.wrongDeploymentMode(request.deploymentMode());
        }
        String targetName = request.remoteEnvironment() == null ? null : request.remoteEnvironment().targetName();
        String sanitizedTargetName = publisher.sanitizedTargetName(targetName);
        GeneratedArtifactPackage generated = deploymentPackageService.generate(request);
        String commitMessage = commitMessage(sanitizedTargetName, generated.report(), generated.files());
        DeploymentRepositoryPublishResult result = publisher.publish(targetName, generated.files(), commitMessage);
        log.info("Published a remote-ansible package for target '{}' to branch '{}': commit {}{}.", sanitizedTargetName, result.branch(),
                result.commitSha(), result.upToDate() ? " (already up to date)" : "");
        return result;
    }

    /**
     * Returns the publisher for destination reporting.
     *
     * @return deployment repository publisher.
     */
    public DeploymentRepositoryPublisher publisher() {
        return publisher;
    }

    /**
     * Builds the commit message of a published variant. Every value comes from the generated package: model and
     * catalog identity, profile, technical choices, the emitted off-switch groups, and the emitted integration
     * groups.
     *
     * @param sanitizedTargetName sanitized target directory name.
     * @param report generation report of the package.
     * @param files generated package files.
     * @return commit message.
     */
    private String commitMessage(String sanitizedTargetName, GenerationReport report, List<GeneratedArtifactFile> files) {
        TechnicalSelectionMetadata technical = report.technicalSelection();
        String database = technical == null || technical.databaseId() == null ? "none" : technical.databaseId();
        String ciProvider = technical == null || technical.ciProviderId() == null ? "none" : technical.ciProviderId();
        Set<String> filePaths = new HashSet<>();
        for (GeneratedArtifactFile file : files) {
            filePaths.add(file.path());
        }
        return """
                deploy %s: model %s@%s, catalog v%s@%s

                profile: %s
                database: %s   ci: %s
                modules off: %s
                integrations: %s
                environment: env-channel""".formatted(sanitizedTargetName, report.modelId(), report.modelVersion(), catalog.catalogVersion(),
                catalog.collectionPin().substring(0, 7), report.profileId(), database, ciProvider, joinedOrNone(emittedOffSwitchKeys(filePaths)),
                joinedOrNone(emittedIntegrationIds(filePaths)));
    }

    /**
     * Derives the sorted Artemis keys of the emitted off-switch groups from the package files.
     *
     * @param filePaths generated package file paths.
     * @return sorted off-switch keys.
     */
    private Set<String> emittedOffSwitchKeys(Set<String> filePaths) {
        Set<String> keys = new TreeSet<>();
        for (String path : filePaths) {
            String fileName = path.startsWith(GROUP_VARS_PREFIX) ? path.substring(GROUP_VARS_PREFIX.length()) : "";
            if (fileName.startsWith(OFF_SWITCH_FILE_PREFIX) && fileName.endsWith(".yml")) {
                keys.add(fileName.substring(OFF_SWITCH_FILE_PREFIX.length(), fileName.length() - ".yml".length()));
            }
        }
        return keys;
    }

    /**
     * Derives the sorted feature ids of the emitted presence-gated integration groups from the package files.
     *
     * @param filePaths generated package file paths.
     * @return sorted integration feature ids.
     */
    private Set<String> emittedIntegrationIds(Set<String> filePaths) {
        Set<String> integrationIds = new TreeSet<>();
        for (Map.Entry<String, AnsibleBindingCatalog.FeatureBinding> entry : catalog.features().entrySet()) {
            AnsibleBindingCatalog.FeatureBinding binding = entry.getValue();
            boolean presenceGatedBound = AnsibleBindingCatalog.BINDING_BOUND.equals(binding.binding()) && !binding.emittedWhenDeselected();
            if (presenceGatedBound && filePaths.contains(GROUP_VARS_PREFIX + binding.groupVarsFile())) {
                integrationIds.add(entry.getKey());
            }
        }
        return integrationIds;
    }

    /**
     * Joins sorted values with commas, or names the empty set.
     *
     * @param values sorted values.
     * @return joined values, or {@code none}.
     */
    private String joinedOrNone(Set<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }
}
