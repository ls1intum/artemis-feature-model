package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentModes;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisRuntimeSource;
import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentPackageManifest;
import de.tum.cit.aet.artemis.featuremodel.export.domain.EnvironmentRequirement;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GenerationMessage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GenerationReport;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RuntimeCheck;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RuntimeChecksReport;
import de.tum.cit.aet.artemis.featuremodel.export.domain.StaticConfigValidationReport;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelection;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelectionMetadata;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds a deployment package for the requested deployment mode by enriching the Phase 5 configuration artifacts with
 * mode-specific files and package metadata.
 *
 * <p>
 * This service does not re-map features to parameters or re-render the YAML overlay: it delegates to
 * {@link ArtifactGenerationService}, reuses the generated Phase 5 files, and composes them per deployment mode with
 * shared metadata (package manifest, static config validation report) plus mode-specific files. The default mode is
 * {@link DeploymentModes#LOCAL_DOCKER} (Phase 6, Layer 1): package README, demo env file, env README, runtime checks,
 * the local-repo Compose override and its README, and the helper scripts. A default-mode request and an explicit
 * local-docker request produce the same package except for the deployment mode recorded in the manifest; a recorded
 * fixture test guards the package bytes against accidental drift, so deliberate content changes must re-baseline the
 * fixture. The result reuses {@link GeneratedArtifactPackage}: the file list is the full package and its Phase 5
 * report gains technical-selection recording only when the active model declares selected structural mappings.
 */
@Service
public class DeploymentPackageService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentPackageService.class);

    /** Phase 6 package README, which replaces the Phase 5 artifacts README at the package root. */
    static final String PACKAGE_README_FILE = "README.md";

    static final String ENV_DEMO_FILE = "env/.env.demo";

    static final String ENV_README_FILE = "env/README.md";

    static final String MANIFEST_FILE = "metadata/package-manifest.json";

    static final String RUNTIME_CHECKS_FILE = "metadata/runtime-checks.json";

    static final String STATIC_VALIDATION_FILE = "metadata/static-config-validation.json";

    static final String LOCAL_REPO_OVERRIDE_FILE = "deployment/local-repo/docker-compose.override.example.yml";

    static final String TECHNICAL_STACK_FILE = RuntimePackageConstants.TECHNICAL_STACK_PACKAGE_PATH;

    static final String REMOTE_IMAGE_STACK_FILE = RuntimePackageConstants.REMOTE_IMAGE_STACK_PACKAGE_PATH;

    static final String LOCAL_REPO_README_FILE = "deployment/local-repo/README.md";

    static final String PREPARE_ENV_SCRIPT_FILE = "scripts/prepare-env.sh";

    /** Single-command DEMO entry point chaining chmod, prepare-env --demo, and start-local-repo. */
    static final String START_DEMO_SCRIPT_FILE = "scripts/start-demo.sh";

    static final String VALIDATE_PACKAGE_SCRIPT_FILE = "scripts/validate-package.sh";

    static final String START_LOCAL_REPO_SCRIPT_FILE = "scripts/start-local-repo.sh";

    static final String START_REMOTE_IMAGE_SCRIPT_FILE = "scripts/start-remote-image.sh";

    static final String STOP_SCRIPT_FILE = "scripts/stop.sh";

    static final String STOP_LOCAL_REPO_SCRIPT_FILE = "scripts/stop-local-repo.sh";

    static final String PRINT_SUMMARY_SCRIPT_FILE = "scripts/print-runtime-summary.sh";

    /** IntelliJ run configuration of the dev-ide mode; the file name follows the IntelliJ naming convention. */
    static final String DEV_IDE_RUN_CONFIG_FILE = "intellij/runConfigurations/Artemis_Server__Feature_Model_Selection_.xml";

    /**
     * Demo defaults for the overlay's environment-variable placeholders, loaded via the {@code feature-model-demo}
     * profile; the dev-ide counterpart of {@code env/.env.demo}.
     */
    static final String DEV_IDE_DEMO_ENV_FILE = "config/application-feature-model-demo.yml";

    /** Package type recorded in the dev-ide manifest; the package is configuration-only and contains no runtime. */
    static final String DEV_IDE_PACKAGE_TYPE = "dev-ide-configuration-package";

    private static final String CONTENT_TYPE_YAML = "application/x-yaml";

    private static final String CONTENT_TYPE_JSON = "application/json";

    private static final String CONTENT_TYPE_TEXT = "text/plain";

    private static final String CONTENT_TYPE_MARKDOWN = "text/markdown";

    private static final String CONTENT_TYPE_SHELL = "text/x-shellscript";

    private static final String CONTENT_TYPE_XML = "application/xml";

    private static final Pattern ENV_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([A-Z0-9_]+)\\}");

    /** Matches a leaked {@code env:NAME} reference value that should have been rendered as {@code ${NAME}}. */
    private static final Pattern ENV_LEAK_PATTERN = Pattern.compile("env:[A-Za-z_]");

    private static final String JENKINS_LOCAL_DOCKER_WARNING = "Jenkins configuration was generated, but this package cannot DEMO-boot a Jenkins stack because "
            + "no Jenkins service is included.";

    private final ArtifactGenerationService artifactGenerationService;

    private final FeatureModelCatalogService featureModelCatalogService;

    private final DeploymentProfileService deploymentProfileService;

    private final TechnicalSelectionResolver technicalSelectionResolver;

    private final StaticConfigValidationService staticConfigValidationService;

    private final RuntimeTemplateWriter templateWriter;

    private final RuntimeStackWriter stackWriter;

    private final RemoteImageStackWriter remoteImageStackWriter;

    private final RuntimeScriptWriter scriptWriter;

    private final ActiveProfilesDeriver activeProfilesDeriver;

    private final DevIdeTemplateWriter devIdeTemplateWriter;

    private final EnvExampleWriter envExampleWriter;

    private final ArtemisRuntimeSourceResolver runtimeSourceResolver;

    private final ObjectMapper objectMapper;

    /**
     * Creates the deployment package service.
     *
     * @param artifactGenerationService Phase 5 service used to generate the base configuration artifacts.
     * @param featureModelCatalogService service used to re-read the active feature model for technical resolution.
     * @param deploymentProfileService service used to resolve the active profile for the deployment-mode support check.
     * @param technicalSelectionResolver resolver for selected structural technical mappings.
     * @param staticConfigValidationService validator for the generated overlay against the Artemis config key catalog.
     * @param templateWriter writer for the local-docker runtime template files.
     * @param stackWriter writer for selection-driven local-docker stacks.
     * @param remoteImageStackWriter writer for the self-contained remote-image stack.
     * @param scriptWriter writer for the local-docker helper scripts.
     * @param activeProfilesDeriver deriver of the dev-ide {@code ACTIVE_PROFILES} value from the selection.
     * @param devIdeTemplateWriter writer for the dev-ide run configuration XML and README.
     * @param envExampleWriter writer for local-docker environment declarations.
     * @param runtimeSourceResolver resolver for snapshot or classpath Artemis runtime provenance.
     * @param objectMapper Jackson mapper used to serialize the manifest and runtime checks.
     */
    public DeploymentPackageService(ArtifactGenerationService artifactGenerationService, FeatureModelCatalogService featureModelCatalogService,
            DeploymentProfileService deploymentProfileService, TechnicalSelectionResolver technicalSelectionResolver,
            StaticConfigValidationService staticConfigValidationService, RuntimeTemplateWriter templateWriter, RuntimeStackWriter stackWriter,
            RemoteImageStackWriter remoteImageStackWriter, RuntimeScriptWriter scriptWriter, ActiveProfilesDeriver activeProfilesDeriver,
            DevIdeTemplateWriter devIdeTemplateWriter, EnvExampleWriter envExampleWriter, ArtemisRuntimeSourceResolver runtimeSourceResolver,
            ObjectMapper objectMapper) {
        this.artifactGenerationService = artifactGenerationService;
        this.featureModelCatalogService = featureModelCatalogService;
        this.deploymentProfileService = deploymentProfileService;
        this.technicalSelectionResolver = technicalSelectionResolver;
        this.staticConfigValidationService = staticConfigValidationService;
        this.templateWriter = templateWriter;
        this.stackWriter = stackWriter;
        this.remoteImageStackWriter = remoteImageStackWriter;
        this.scriptWriter = scriptWriter;
        this.activeProfilesDeriver = activeProfilesDeriver;
        this.devIdeTemplateWriter = devIdeTemplateWriter;
        this.envExampleWriter = envExampleWriter;
        this.runtimeSourceResolver = runtimeSourceResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * Shared generation results every deployment mode composes its package from: the Phase 5 files by path, the Phase
     * 5 report, the parsed required environment variables, and the static overlay validation.
     *
     * @param report Phase 5 generation report.
     * @param baseByPath Phase 5 files keyed by package path.
     * @param overlay generated Spring configuration overlay file.
     * @param envExample generated {@code .env.example} file.
     * @param requiredEnvVars environment variable names the overlay references.
     * @param staticValidation static overlay validation result against the Artemis config key catalog.
     * @param technicalSelection resolved structural technical mappings.
     */
    private record SharedArtifacts(GenerationReport report, Map<String, GeneratedArtifactFile> baseByPath, GeneratedArtifactFile overlay,
            GeneratedArtifactFile envExample, List<String> requiredEnvVars, StaticConfigValidationReport staticValidation,
            TechnicalSelection technicalSelection) {
    }

    /**
     * Generates the in-memory deployment package for a request in the requested deployment mode. A request without a
     * deployment mode produces the default local Docker runtime package.
     *
     * @param request artifact generation request (selection, optional profile, optional deployment mode).
     * @return generated package for the requested mode.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException if the selection is invalid, the deployment mode is unknown, or the
     *             active profile does not support the requested mode.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException if the profile cannot be resolved.
     */
    public GeneratedArtifactPackage generate(ArtifactGenerationRequest request) {
        String requestedDeploymentMode = normalizedDeploymentMode(request);
        String deploymentMode = requestedDeploymentMode == null ? DeploymentModes.LOCAL_DOCKER : requestedDeploymentMode;
        if (!DeploymentModes.isKnown(deploymentMode)) {
            throw ArtifactGenerationException.unknownDeploymentMode(deploymentMode);
        }
        DeploymentProfile profile = deploymentProfileService.resolveProfileOrDefault(deploymentProfileService.loadProfiles(), request.profileId());
        if (!profile.supportsDeploymentMode(deploymentMode)) {
            throw ArtifactGenerationException.unsupportedDeploymentMode(deploymentMode, profile.id());
        }

        SharedArtifacts shared = generateSharedArtifacts(request);
        shared = applyModeMetadata(shared, deploymentMode);
        List<GeneratedArtifactFile> files = composeFilesForMode(shared, deploymentMode, requestedDeploymentMode);

        log.info("Generated a '{}' deployment package with {} files for profile '{}' with status {}.", deploymentMode, files.size(), shared.report().profileId(),
                shared.report().status());
        return new GeneratedArtifactPackage(files, shared.report());
    }

    /**
     * Composes package files for the resolved deployment mode.
     *
     * @param shared shared generation results.
     * @param deploymentMode resolved deployment mode.
     * @param requestedDeploymentMode explicitly requested deployment mode id, or {@code null} for a default request.
     * @return ordered package files for the resolved mode.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException if the deployment mode is unknown.
     */
    private List<GeneratedArtifactFile> composeFilesForMode(SharedArtifacts shared, String deploymentMode, String requestedDeploymentMode) {
        return switch (deploymentMode) {
            case DeploymentModes.LOCAL_DOCKER -> composeLocalDockerFiles(shared, requestedDeploymentMode);
            case DeploymentModes.DEV_IDE -> composeDevIdeFiles(shared);
            default -> throw ArtifactGenerationException.unknownDeploymentMode(deploymentMode);
        };
    }

    /**
     * Normalizes the requested deployment mode to {@code null} when absent or blank.
     *
     * @param request artifact generation request.
     * @return requested deployment mode id, or {@code null} for a default-mode request.
     */
    private String normalizedDeploymentMode(ArtifactGenerationRequest request) {
        String deploymentMode = request.deploymentMode();
        return deploymentMode == null || deploymentMode.isBlank() ? null : deploymentMode;
    }

    /**
     * Generates the mode-independent shared artifacts: the Phase 5 files, the required environment variables, and the
     * static overlay validation.
     *
     * @param request artifact generation request.
     * @return shared artifacts every mode composes its package from.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException if the selection is invalid.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException if the profile cannot be resolved.
     */
    private SharedArtifacts generateSharedArtifacts(ArtifactGenerationRequest request) {
        GeneratedArtifactPackage base = artifactGenerationService.generate(request);
        FeatureModel model = featureModelCatalogService.loadActiveModel();
        Set<String> selectedFeatureIds = new LinkedHashSet<>(base.report().selectedFeatureIds());
        TechnicalSelection technicalSelection = technicalSelectionResolver.resolve(model, selectedFeatureIds);
        Map<String, GeneratedArtifactFile> baseByPath = filesByPath(base.files());
        GeneratedArtifactFile overlay = baseByPath.get(ArtifactGenerationService.OVERLAY_FILE);
        GeneratedArtifactFile envExample = baseByPath.get(ArtifactGenerationService.ENV_FILE);
        List<String> requiredEnvVars = parseEnvNames(envExample.content());
        StaticConfigValidationReport staticValidation = staticConfigValidationService.validate(overlay.content());
        return new SharedArtifacts(base.report(), baseByPath, overlay, envExample, requiredEnvVars, staticValidation, technicalSelection);
    }

    /**
     * Applies mode-specific technical dispositions and rewrites the report only for a technical model.
     *
     * @param shared shared artifacts with the Phase 5 report.
     * @param deploymentMode resolved deployment mode.
     * @return shared artifacts carrying the mode-specific report.
     */
    private SharedArtifacts applyModeMetadata(SharedArtifacts shared, String deploymentMode) {
        TechnicalSelection selection = shared.technicalSelection();
        if (selection.isEmpty()) {
            return shared;
        }

        TechnicalSelectionMetadata metadata = technicalMetadata(selection, deploymentMode);
        GenerationReport report = reportWithTechnicalSelection(shared.report(), metadata, deploymentMode, selection);
        replaceGenerationReport(shared.baseByPath(), report);
        return new SharedArtifacts(report, shared.baseByPath(), shared.overlay(), shared.envExample(), shared.requiredEnvVars(),
                shared.staticValidation(), selection);
    }

    /**
     * Builds dispositions for the mode that owns each technical axis.
     *
     * @param selection resolved technical selection.
     * @param deploymentMode resolved deployment mode.
     * @return mode-specific technical metadata.
     */
    private TechnicalSelectionMetadata technicalMetadata(TechnicalSelection selection, String deploymentMode) {
        String databaseDisposition = switch (deploymentMode) {
            case DeploymentModes.LOCAL_DOCKER -> TechnicalSelectionMetadata.DISPOSITION_APPLIED;
            case DeploymentModes.DEV_IDE -> TechnicalSelectionMetadata.DISPOSITION_NOT_APPLICABLE_DEV_IDE;
            default -> throw ArtifactGenerationException.unknownDeploymentMode(deploymentMode);
        };
        return TechnicalSelectionMetadata.from(selection, databaseDisposition, TechnicalSelectionMetadata.DISPOSITION_APPLIED);
    }

    /**
     * Adds the controlled local-docker Jenkins warning when applicable.
     *
     * @param report Phase 5 report.
     * @param metadata technical metadata.
     * @param deploymentMode resolved deployment mode.
     * @param selection resolved technical selection.
     * @return augmented generation report.
     */
    private GenerationReport reportWithTechnicalSelection(GenerationReport report, TechnicalSelectionMetadata metadata, String deploymentMode,
            TechnicalSelection selection) {
        boolean localDocker = DeploymentModes.LOCAL_DOCKER.equals(deploymentMode);
        String ciProviderId = selection.ciProviderId().orElse(null);
        boolean jenkinsSelected = "jenkins".equals(ciProviderId);
        boolean localDockerJenkins = localDocker && jenkinsSelected;
        if (!localDockerJenkins) {
            return report.withTechnicalSelection(metadata);
        }
        GenerationMessage warning = GenerationMessage.warning("jenkins", null, JENKINS_LOCAL_DOCKER_WARNING);
        return report.withTechnicalSelectionAndWarning(metadata, warning);
    }

    /**
     * Indexes generated files by path without changing their content.
     *
     * @param files generated files.
     * @return files keyed by path.
     */
    private Map<String, GeneratedArtifactFile> filesByPath(List<GeneratedArtifactFile> files) {
        Map<String, GeneratedArtifactFile> filesByPath = new LinkedHashMap<>();
        for (GeneratedArtifactFile file : files) {
            filesByPath.put(file.path(), file);
        }
        return filesByPath;
    }

    /**
     * Replaces the package report file only when technical metadata is present. Curated-model bytes remain untouched.
     *
     * @param filesByPath Phase 5 files keyed by path.
     * @param report report to record.
     */
    private void replaceGenerationReport(Map<String, GeneratedArtifactFile> filesByPath, GenerationReport report) {
        GeneratedArtifactFile current = filesByPath.get(ArtifactGenerationService.REPORT_FILE);
        GeneratedArtifactFile replacement = new GeneratedArtifactFile(current.path(), current.contentType(), writeJson(report));
        filesByPath.put(replacement.path(), replacement);
    }

    /**
     * Composes the local Docker runtime package (Phase 6, Layer 1) from the shared artifacts. The output is guarded
     * byte-for-byte by a recorded fixture; deliberate content changes must re-baseline that fixture.
     *
     * @param shared shared generation results.
     * @param requestedDeploymentMode explicitly requested deployment mode id, or {@code null} for a default request;
     *            recorded in the manifest only when present so the default manifest stays byte-identical.
     * @return ordered local Docker runtime package files.
     */
    private List<GeneratedArtifactFile> composeLocalDockerFiles(SharedArtifacts shared, String requestedDeploymentMode) {
        GenerationReport report = shared.report();
        TechnicalSelection selection = shared.technicalSelection();
        List<EnvironmentRequirement> localDockerRequirements = localDockerEnvironmentRequirements(report.environmentRequirements(), selection);
        List<String> requiredEnvVars = requirementNames(localDockerRequirements);
        boolean technicalStack = !selection.isEmpty();
        TechnicalSelection runtimeSelection = localDockerRuntimeSelection(selection);
        ArtemisRuntimeSource runtimeSource = runtimeSourceResolver.resolveForLocalDocker();

        String packageReadme = templateWriter.packageReadme(report.modelId(), report.modelVersion(), report.profileId(), report.profileVersion(), selection,
                runtimeSource);
        String envExample = envExampleWriter.write(localDockerRequirements);
        String envDemo = templateWriter.envDemo(localDockerRequirements);
        String stackContent = technicalStack ? stackWriter.write(selection) : null;
        String remoteStackContent = remoteImageStackWriter.write(runtimeSelection, runtimeSource);

        List<String> packagePaths = packageFilePaths(technicalStack);
        String manifestJson = writeJson(buildManifest(report, packagePaths, requiredEnvVars, requestedDeploymentMode, runtimeSource));
        String checksJson = writeJson(buildRuntimeChecks(shared.overlay().content(), requiredEnvVars, report, packagePaths.size(),
                shared.staticValidation(), stackContent, manifestJson));

        List<GeneratedArtifactFile> files = new ArrayList<>();
        files.add(new GeneratedArtifactFile(PACKAGE_README_FILE, CONTENT_TYPE_MARKDOWN, packageReadme));
        files.add(shared.overlay());
        files.add(new GeneratedArtifactFile(shared.envExample().path(), shared.envExample().contentType(), envExample));
        files.add(new GeneratedArtifactFile(ENV_DEMO_FILE, CONTENT_TYPE_TEXT, envDemo));
        files.add(new GeneratedArtifactFile(ENV_README_FILE, CONTENT_TYPE_MARKDOWN, templateWriter.envReadme()));
        files.add(shared.baseByPath().get(ArtifactGenerationService.SELECTED_FEATURES_FILE));
        files.add(shared.baseByPath().get(ArtifactGenerationService.PROFILE_SUMMARY_FILE));
        files.add(shared.baseByPath().get(ArtifactGenerationService.REPORT_FILE));
        files.add(new GeneratedArtifactFile(MANIFEST_FILE, CONTENT_TYPE_JSON, manifestJson));
        files.add(new GeneratedArtifactFile(RUNTIME_CHECKS_FILE, CONTENT_TYPE_JSON, checksJson));
        files.add(new GeneratedArtifactFile(STATIC_VALIDATION_FILE, CONTENT_TYPE_JSON, writeJson(shared.staticValidation())));
        if (technicalStack) {
            files.add(new GeneratedArtifactFile(TECHNICAL_STACK_FILE, CONTENT_TYPE_YAML, stackContent));
        }
        files.add(new GeneratedArtifactFile(REMOTE_IMAGE_STACK_FILE, CONTENT_TYPE_YAML, remoteStackContent));
        String override = technicalStack ? templateWriter.technicalLocalRepoOverride() : templateWriter.localRepoOverride();
        String localReadme = technicalStack ? templateWriter.technicalLocalRepoReadme(selection) : templateWriter.localRepoReadme();
        files.add(new GeneratedArtifactFile(LOCAL_REPO_OVERRIDE_FILE, CONTENT_TYPE_YAML, override));
        files.add(new GeneratedArtifactFile(LOCAL_REPO_README_FILE, CONTENT_TYPE_MARKDOWN, localReadme));
        files.add(new GeneratedArtifactFile(PREPARE_ENV_SCRIPT_FILE, CONTENT_TYPE_SHELL, scriptWriter.prepareEnvScript()));
        files.add(new GeneratedArtifactFile(START_DEMO_SCRIPT_FILE, CONTENT_TYPE_SHELL, scriptWriter.startDemoScript()));
        files.add(new GeneratedArtifactFile(VALIDATE_PACKAGE_SCRIPT_FILE, CONTENT_TYPE_SHELL, scriptWriter.validatePackageScript(technicalStack)));
        files.add(new GeneratedArtifactFile(START_LOCAL_REPO_SCRIPT_FILE, CONTENT_TYPE_SHELL, scriptWriter.startLocalRepoScript(technicalStack)));
        files.add(new GeneratedArtifactFile(START_REMOTE_IMAGE_SCRIPT_FILE, CONTENT_TYPE_SHELL,
                scriptWriter.startRemoteImageScript(usesDockerSocket(runtimeSelection))));
        files.add(new GeneratedArtifactFile(STOP_SCRIPT_FILE, CONTENT_TYPE_SHELL, scriptWriter.stopScript()));
        files.add(new GeneratedArtifactFile(STOP_LOCAL_REPO_SCRIPT_FILE, CONTENT_TYPE_SHELL, scriptWriter.stopLocalRepoScript(technicalStack)));
        files.add(new GeneratedArtifactFile(PRINT_SUMMARY_SCRIPT_FILE, CONTENT_TYPE_SHELL, scriptWriter.printRuntimeSummaryScript()));
        return files;
    }

    /**
     * Determines whether the selected CI provider requires host Docker socket access.
     *
     * @param selection resolved runtime selection.
     * @return true for Integrated Code Lifecycle.
     */
    private boolean usesDockerSocket(TechnicalSelection selection) {
        return "integrated-code-lifecycle".equals(selection.ciProviderId().orElse(null));
    }

    /**
     * Applies the established MySQL/ICL runtime defaults for a legacy model without technical mappings.
     *
     * @param selection resolved technical selection.
     * @return selection suitable for both local-docker runtime writers.
     */
    private TechnicalSelection localDockerRuntimeSelection(TechnicalSelection selection) {
        if (!selection.isEmpty()) {
            return selection;
        }
        return new TechnicalSelection(List.of("localci", "buildagent", "localvc"), Optional.of("docker/mysql.yml"), Optional.of("mysql"),
                Optional.of("integrated-code-lifecycle"));
    }

    /**
     * Adds the LocalVC build credentials required by the Jenkins Docker profile family as package-only requirements,
     * so they can never disappear from {@code env/.env.example} or the report metadata.
     *
     * @param requirements environment requirements produced by the generated overlay.
     * @param selection resolved technical selection.
     * @return local-docker environment requirements including the Jenkins package-only requirements when applicable.
     */
    private List<EnvironmentRequirement> localDockerEnvironmentRequirements(List<EnvironmentRequirement> requirements, TechnicalSelection selection) {
        if (!"jenkins".equals(selection.ciProviderId().orElse(null))) {
            return requirements;
        }
        List<EnvironmentRequirement> extended = new ArrayList<>(requirements);
        extended.add(new EnvironmentRequirement(RuntimePackageConstants.VERSION_CONTROL_BUILD_AGENT_USERNAME_ENV, "jenkins", "Jenkins", null, null, false,
                EnvironmentRequirement.SOURCE_RUNTIME_PACKAGE,
                "LocalVC build-agent Git username required by the Jenkins profile family; the production image ships no application-localvc.yml."));
        extended.add(new EnvironmentRequirement(RuntimePackageConstants.VERSION_CONTROL_BUILD_AGENT_PASSWORD_ENV, "jenkins", "Jenkins", null, null, true,
                EnvironmentRequirement.SOURCE_RUNTIME_PACKAGE,
                "LocalVC build-agent Git password required by the Jenkins profile family; the production image ships no application-localvc.yml."));
        return List.copyOf(extended);
    }

    /**
     * Derives the sorted environment variable names of the given requirements.
     *
     * @param requirements environment requirements.
     * @return sorted, de-duplicated variable names.
     */
    private List<String> requirementNames(List<EnvironmentRequirement> requirements) {
        TreeSet<String> names = new TreeSet<>();
        for (EnvironmentRequirement requirement : requirements) {
            names.add(requirement.name());
        }
        return List.copyOf(names);
    }

    /**
     * Composes the dev-ide configuration package from the shared artifacts: the Level 1 overlay files, the generated
     * IntelliJ run configuration with derived active profiles, the developer README, the package manifest, and the
     * static config validation report. No Compose files or runtime scripts are included.
     *
     * @param shared shared generation results.
     * @return ordered dev-ide package files.
     */
    private List<GeneratedArtifactFile> composeDevIdeFiles(SharedArtifacts shared) {
        GenerationReport report = shared.report();
        String activeProfiles = activeProfilesDeriver.deriveActiveProfiles(report.selectedFeatureIds(), shared.technicalSelection().springProfileTokens());
        String readme = devIdeTemplateWriter.devIdeReadme(report.modelId(), report.modelVersion(), report.profileId(), activeProfiles,
                shared.requiredEnvVars(), shared.technicalSelection());
        String runConfigurationXml = devIdeTemplateWriter.runConfigurationXml(activeProfiles);
        String manifestJson = writeJson(buildDevIdeManifest(report, shared.requiredEnvVars()));

        List<GeneratedArtifactFile> files = new ArrayList<>();
        files.add(new GeneratedArtifactFile(PACKAGE_README_FILE, CONTENT_TYPE_MARKDOWN, readme));
        files.add(shared.overlay());
        files.add(new GeneratedArtifactFile(DEV_IDE_DEMO_ENV_FILE, CONTENT_TYPE_YAML, devIdeTemplateWriter.demoEnvDefaultsYaml(report.environmentRequirements())));
        files.add(shared.envExample());
        files.add(new GeneratedArtifactFile(DEV_IDE_RUN_CONFIG_FILE, CONTENT_TYPE_XML, runConfigurationXml));
        files.add(shared.baseByPath().get(ArtifactGenerationService.SELECTED_FEATURES_FILE));
        files.add(shared.baseByPath().get(ArtifactGenerationService.PROFILE_SUMMARY_FILE));
        files.add(shared.baseByPath().get(ArtifactGenerationService.REPORT_FILE));
        files.add(new GeneratedArtifactFile(MANIFEST_FILE, CONTENT_TYPE_JSON, manifestJson));
        files.add(new GeneratedArtifactFile(STATIC_VALIDATION_FILE, CONTENT_TYPE_JSON, writeJson(shared.staticValidation())));
        return files;
    }

    /**
     * Builds the package manifest for the dev-ide configuration package. The package has no startable runtime, so it
     * declares no supported runtime modes and no database, and its readiness makes the configuration-only nature
     * explicit.
     *
     * @param report Phase 5 generation report, source of the model/profile references.
     * @param requiredEnvVars environment variables the overlay references.
     * @return dev-ide package manifest.
     */
    private DeploymentPackageManifest buildDevIdeManifest(GenerationReport report, List<String> requiredEnvVars) {
        ArtemisRuntimeSource runtimeSource = runtimeSourceResolver.resolveForDevIde();
        DeploymentPackageManifest.ArtemisRuntimeInfo runtimeInfo = new DeploymentPackageManifest.ArtemisRuntimeInfo(runtimeSource.sourceCommit(),
                runtimeSource.imageRepository(), null,
                "The dev-ide package configures a local IntelliJ IDEA development run of an existing Artemis checkout. The overlay keys were verified against the "
                        + "referenced Artemis commit; a checkout at a different commit may not match all keys.");
        DeploymentPackageManifest.Readiness readiness = new DeploymentPackageManifest.Readiness(false, false,
                "Configuration-only package for IDE development; generated in DEMO mode and never resolves real secrets.");
        DeploymentPackageManifest.Database database = selectedDatabase(report, "developer-managed");
        DeploymentPackageManifest.CiProvider ciProvider = selectedCiProvider(report);
        return new DeploymentPackageManifest(DEV_IDE_PACKAGE_TYPE, RuntimePackageConstants.PACKAGE_VERSION, RuntimePackageConstants.MODE_DEMO,
                DeploymentModes.DEV_IDE, List.of(), new DeploymentPackageManifest.ModelRef(report.modelId(), report.modelVersion()),
                new DeploymentPackageManifest.ProfileRef(report.profileId(), report.profileVersion()), runtimeInfo, database, ciProvider,
                report.technicalSelection(),
                devIdePackageFilePaths(), requiredEnvVars, readiness);
    }

    /**
     * Returns the deterministic ordered list of the dev-ide package file paths, used for both the manifest and file
     * assembly.
     *
     * @return ordered dev-ide package file paths.
     */
    private List<String> devIdePackageFilePaths() {
        return List.of(PACKAGE_README_FILE, ArtifactGenerationService.OVERLAY_FILE, DEV_IDE_DEMO_ENV_FILE, ArtifactGenerationService.ENV_FILE,
                DEV_IDE_RUN_CONFIG_FILE, ArtifactGenerationService.SELECTED_FEATURES_FILE, ArtifactGenerationService.PROFILE_SUMMARY_FILE,
                ArtifactGenerationService.REPORT_FILE, MANIFEST_FILE, STATIC_VALIDATION_FILE);
    }

    /**
     * Returns the deterministic ordered list of all package file paths, used for both the manifest and file assembly.
     *
     * @return ordered package file paths.
     */
    private List<String> packageFilePaths(boolean technicalStack) {
        List<String> paths = new ArrayList<>();
        paths.addAll(List.of(PACKAGE_README_FILE, ArtifactGenerationService.OVERLAY_FILE, ArtifactGenerationService.ENV_FILE,
                ENV_DEMO_FILE, ENV_README_FILE, ArtifactGenerationService.SELECTED_FEATURES_FILE,
                ArtifactGenerationService.PROFILE_SUMMARY_FILE, ArtifactGenerationService.REPORT_FILE, MANIFEST_FILE,
                RUNTIME_CHECKS_FILE, STATIC_VALIDATION_FILE));
        if (technicalStack) {
            paths.add(TECHNICAL_STACK_FILE);
        }
        paths.add(REMOTE_IMAGE_STACK_FILE);
        paths.addAll(List.of(LOCAL_REPO_OVERRIDE_FILE, LOCAL_REPO_README_FILE, PREPARE_ENV_SCRIPT_FILE, START_DEMO_SCRIPT_FILE,
                VALIDATE_PACKAGE_SCRIPT_FILE, START_LOCAL_REPO_SCRIPT_FILE, START_REMOTE_IMAGE_SCRIPT_FILE, STOP_SCRIPT_FILE, STOP_LOCAL_REPO_SCRIPT_FILE,
                PRINT_SUMMARY_SCRIPT_FILE));
        return List.copyOf(paths);
    }

    /**
     * Builds the package manifest for the local-repo runtime layer.
     *
     * @param report Phase 5 generation report, source of the model/profile references.
     * @param packagePaths all package file paths, in order.
     * @param requiredEnvVars environment variables the overlay references.
     * @param requestedDeploymentMode explicitly requested deployment mode id, or {@code null} for a default request.
     * @return package manifest.
     */
    private DeploymentPackageManifest buildManifest(GenerationReport report, List<String> packagePaths, List<String> requiredEnvVars,
            String requestedDeploymentMode, ArtemisRuntimeSource runtimeSource) {
        DeploymentPackageManifest.ArtemisRuntimeInfo runtimeInfo = localDockerRuntimeInfo(report, runtimeSource);
        DeploymentPackageManifest.Database database = selectedDatabase(report, "local-container");
        if (database == null) {
            database = new DeploymentPackageManifest.Database(RuntimePackageConstants.DATABASE_TYPE, "local-container");
        }
        DeploymentPackageManifest.CiProvider ciProvider = selectedCiProvider(report);
        boolean jenkinsSelected = ciProvider != null && "jenkins".equals(ciProvider.type());
        DeploymentPackageManifest.Readiness readiness = localDockerReadiness(jenkinsSelected);
        return new DeploymentPackageManifest(RuntimePackageConstants.PACKAGE_TYPE, RuntimePackageConstants.PACKAGE_VERSION, RuntimePackageConstants.MODE_DEMO,
                requestedDeploymentMode, List.of(RuntimePackageConstants.RUNTIME_MODE_LOCAL_REPO, RuntimePackageConstants.RUNTIME_MODE_REMOTE_IMAGE),
                new DeploymentPackageManifest.ModelRef(report.modelId(), report.modelVersion()),
                new DeploymentPackageManifest.ProfileRef(report.profileId(), report.profileVersion()), runtimeInfo, database, ciProvider,
                report.technicalSelection(), packagePaths, requiredEnvVars, readiness);
    }

    /**
     * Builds a selection-aware Artemis runtime note.
     *
     * @param report generation report.
     * @param runtimeSource resolved runtime provenance.
     * @return runtime information.
     */
    private DeploymentPackageManifest.ArtemisRuntimeInfo localDockerRuntimeInfo(GenerationReport report, ArtemisRuntimeSource runtimeSource) {
        TechnicalSelectionMetadata metadata = report.technicalSelection();
        if (metadata == null) {
            String curatedNote = "Layer 1 (local-repo) runs the local Artemis checkout's CI-capable local-VC/local-CI stack so any selection, including "
                    + "CI-dependent features such as Hyperion, can start. The overlay keys were verified against the referenced Artemis commit; a checkout at a "
                    + "different commit may not match all keys.";
            return new DeploymentPackageManifest.ArtemisRuntimeInfo(runtimeSource.sourceCommit(), runtimeSource.imageRepository(), runtimeSource.imageDigest(),
                    curatedNote);
        }

        String runtimeDescription = "a generated stack for database '" + metadata.databaseId() + "' and CI provider '"
                + metadata.ciProviderId() + "'";
        String note = "Layer 1 (local-repo) runs " + runtimeDescription + ". The overlay keys were verified against the "
                + "referenced Artemis commit; a checkout at a different commit may not match all keys.";
        return new DeploymentPackageManifest.ArtemisRuntimeInfo(runtimeSource.sourceCommit(), runtimeSource.imageRepository(), runtimeSource.imageDigest(), note);
    }

    /**
     * Reads the selected database from technical metadata.
     *
     * @param report generation report.
     * @param mode manifest database mode.
     * @return selected database, or {@code null} without a technical database choice.
     */
    private DeploymentPackageManifest.Database selectedDatabase(GenerationReport report, String mode) {
        TechnicalSelectionMetadata metadata = report.technicalSelection();
        if (metadata == null || metadata.databaseId() == null) {
            return null;
        }
        return new DeploymentPackageManifest.Database(metadata.databaseId(), mode);
    }

    /**
     * Reads the selected CI provider from technical metadata.
     *
     * @param report generation report.
     * @return selected CI provider, or {@code null}.
     */
    private DeploymentPackageManifest.CiProvider selectedCiProvider(GenerationReport report) {
        TechnicalSelectionMetadata metadata = report.technicalSelection();
        if (metadata == null || metadata.ciProviderId() == null) {
            return null;
        }
        return new DeploymentPackageManifest.CiProvider(metadata.ciProviderId(), "spring-profiles");
    }

    /**
     * Builds local-docker readiness, making the Jenkins limitation explicit.
     *
     * @param jenkinsSelected whether Jenkins is the selected provider.
     * @return readiness metadata.
     */
    private DeploymentPackageManifest.Readiness localDockerReadiness(boolean jenkinsSelected) {
        if (jenkinsSelected) {
            return new DeploymentPackageManifest.Readiness(false, false, JENKINS_LOCAL_DOCKER_WARNING);
        }
        return new DeploymentPackageManifest.Readiness(true, false,
                "Generated in DEMO mode for local validation only; may contain placeholder values and never resolves real secrets.");
    }

    /**
     * Builds the runtime checks from the generated overlay and report. Every check is derived from already-generated
     * content so the JSON reflects the actual package.
     *
     * @param overlayContent generated YAML overlay content.
     * @param requiredEnvVars environment variables declared in {@code .env.example}.
     * @param report Phase 5 generation report.
     * @param fileCount number of files in the package.
     * @param staticValidation static overlay validation result against the Artemis config key catalog.
     * @param stackContent generated technical stack, or {@code null}.
     * @param manifestJson serialized package manifest.
     * @return runtime checks report.
     */
    private RuntimeChecksReport buildRuntimeChecks(String overlayContent, List<String> requiredEnvVars, GenerationReport report, int fileCount,
            StaticConfigValidationReport staticValidation, String stackContent, String manifestJson) {
        List<RuntimeCheck> checks = new ArrayList<>();

        checks.add(new RuntimeCheck("required-files-present", "All expected package files were generated.", RuntimeCheck.STATUS_PASS,
                fileCount + " files generated."));

        boolean leak = ENV_LEAK_PATTERN.matcher(overlayContent).find();
        checks.add(new RuntimeCheck("overlay-no-env-leaks", "The overlay contains no raw env: reference values.", leak ? RuntimeCheck.STATUS_FAIL : RuntimeCheck.STATUS_PASS,
                leak ? "A raw env: value was found in the overlay." : "Secret references are rendered as ${VARIABLE} placeholders only."));

        List<String> undeclared = new ArrayList<>();
        for (String overlayVar : extractOverlayVars(overlayContent)) {
            if (!requiredEnvVars.contains(overlayVar)) {
                undeclared.add(overlayVar);
            }
        }
        checks.add(new RuntimeCheck("env-placeholders-declared", "Every ${VARIABLE} in the overlay is declared in env/.env.example.",
                undeclared.isEmpty() ? RuntimeCheck.STATUS_PASS : RuntimeCheck.STATUS_FAIL,
                undeclared.isEmpty() ? "All " + requiredEnvVars.size() + " placeholders are declared." : "Undeclared: " + String.join(", ", undeclared) + "."));

        boolean staticPass = StaticConfigValidationReport.STATUS_PASS.equals(staticValidation.overallStatus());
        checks.add(new RuntimeCheck("static-config-keys", "Every overlay key is a verified Artemis configuration key with an acceptable value type.",
                staticPass ? RuntimeCheck.STATUS_PASS : RuntimeCheck.STATUS_FAIL,
                staticPass
                        ? staticValidation.checkedEntryCount() + " overlay entries validated against catalog " + staticValidation.catalogVersion() + " (Artemis commit "
                                + staticValidation.verifiedAgainstArtemisCommit() + ")."
                        : staticValidation.findings().size() + " finding(s); see " + STATIC_VALIDATION_FILE + "."));

        List<String> undeclaredSecrets = new ArrayList<>();
        for (EnvironmentRequirement requirement : report.environmentRequirements()) {
            if (requirement.secret() && !requiredEnvVars.contains(requirement.name())) {
                undeclaredSecrets.add(requirement.name());
            }
        }
        checks.add(new RuntimeCheck("no-plaintext-secrets", "No secret value is written as plaintext.",
                undeclaredSecrets.isEmpty() ? RuntimeCheck.STATUS_PASS : RuntimeCheck.STATUS_FAIL,
                undeclaredSecrets.isEmpty() ? "Secret values are declared environment references only."
                        : "Undeclared secret requirement(s): " + String.join(", ", undeclaredSecrets) + "."));

        int warningCount = report.warnings().size();
        checks.add(new RuntimeCheck("placeholder-values-reported", "Placeholder and integration notes are reported for review.", RuntimeCheck.STATUS_INFO,
                warningCount + " warning(s)/note(s) recorded in metadata/generation-report.json; review before real use."));

        if (stackContent != null) {
            TechnicalSelectionMetadata metadata = report.technicalSelection();
            checks.add(technicalSelectionCheck(metadata, stackContent, manifestJson));
            if ("jenkins".equals(metadata.ciProviderId())) {
                checks.add(jenkinsStackAvailabilityCheck());
            }
        }

        boolean anyFailed = checks.stream().anyMatch(check -> RuntimeCheck.STATUS_FAIL.equals(check.status()));
        return new RuntimeChecksReport(RuntimePackageConstants.MODE_DEMO, anyFailed ? RuntimeCheck.STATUS_FAIL : RuntimeCheck.STATUS_PASS, checks);
    }

    /**
     * Checks that technical metadata and the rendered stack agree.
     *
     * @param metadata technical metadata.
     * @param stackContent generated stack.
     * @param manifestJson serialized manifest.
     * @return technical-selection consistency check.
     */
    private RuntimeCheck technicalSelectionCheck(TechnicalSelectionMetadata metadata, String stackContent, String manifestJson) {
        boolean databaseMatches = stackContent.contains(metadata.databaseComposeFile())
                && manifestJson.contains("\"type\" : \"" + metadata.databaseId() + "\"");
        boolean ciMatches = manifestJson.contains("\"type\" : \"" + metadata.ciProviderId() + "\"")
                && stackContainsExpectedProfiles(stackContent, metadata.ciProviderId());
        boolean consistent = databaseMatches && ciMatches;
        String detail = consistent
                ? "Manifest, generated stack, database mapping, and Docker profile list agree."
                : technicalSelectionFailureDetail(databaseMatches, ciMatches);
        return new RuntimeCheck("technical-selection-consistent",
                "Manifest, stack file, and Docker profile environment agree with the technical selection.",
                consistent ? RuntimeCheck.STATUS_PASS : RuntimeCheck.STATUS_FAIL, detail);
    }

    /**
     * Records the deliberate local-docker Jenkins limitation as a failing runtime check.
     *
     * @return failing Jenkins availability check.
     */
    private RuntimeCheck jenkinsStackAvailabilityCheck() {
        return new RuntimeCheck("jenkins-stack-available", "A Jenkins service is available for the selected Jenkins profiles.",
                RuntimeCheck.STATUS_FAIL, JENKINS_LOCAL_DOCKER_WARNING);
    }

    /**
     * Checks the writer-owned Docker profile list for a CI provider.
     *
     * @param stackContent generated stack.
     * @param ciProviderId selected CI provider.
     * @return whether the expected exact profile list is present.
     */
    private boolean stackContainsExpectedProfiles(String stackContent, String ciProviderId) {
        String expectedProfiles = "jenkins".equals(ciProviderId)
                ? RuntimeStackWriter.JENKINS_DOCKER_PROFILES
                : RuntimeStackWriter.ICL_DOCKER_PROFILES;
        return stackContent.contains("SPRING_PROFILES_ACTIVE: \"" + expectedProfiles + "\"");
    }

    /**
     * Builds a focused consistency failure detail.
     *
     * @param databaseMatches database agreement.
     * @param ciMatches CI agreement.
     * @return failure detail.
     */
    private String technicalSelectionFailureDetail(boolean databaseMatches, boolean ciMatches) {
        if (!databaseMatches) {
            return "Database metadata and generated stack disagree.";
        }
        if (!ciMatches) {
            return "CI-provider metadata and Docker profile list disagree.";
        }
        return "Technical selection is inconsistent.";
    }

    /**
     * Extracts the sorted, de-duplicated set of {@code ${VARIABLE}} names referenced in the overlay.
     *
     * @param overlayContent overlay content.
     * @return sorted variable names.
     */
    private List<String> extractOverlayVars(String overlayContent) {
        TreeSet<String> names = new TreeSet<>();
        Matcher matcher = ENV_PLACEHOLDER_PATTERN.matcher(overlayContent);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return new ArrayList<>(names);
    }

    /**
     * Parses the environment variable names from a {@code .env.example} body (each line is {@code NAME=}).
     *
     * @param envExampleContent {@code .env.example} content.
     * @return sorted variable names.
     */
    private List<String> parseEnvNames(String envExampleContent) {
        TreeSet<String> names = new TreeSet<>();
        for (String line : envExampleContent.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            String name = equals >= 0 ? trimmed.substring(0, equals) : trimmed;
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * Serializes a value to pretty-printed JSON.
     *
     * @param value value to serialize.
     * @return pretty-printed JSON text.
     */
    private String writeJson(Object value) {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }
}
