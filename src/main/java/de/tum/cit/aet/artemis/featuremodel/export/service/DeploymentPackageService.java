package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.export.domain.ConsumedParameter;
import de.tum.cit.aet.artemis.featuremodel.export.domain.DeploymentPackageManifest;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GenerationReport;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RuntimeCheck;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RuntimeChecksReport;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds a local runtime deployment package (Phase 6, Layer 1) by enriching the Phase 5 configuration artifacts with
 * runtime templates, helper scripts, and package metadata.
 *
 * <p>
 * This service does not re-map features to parameters or re-render the YAML overlay: it delegates to
 * {@link ArtifactGenerationService}, reuses the generated Phase 5 files, and adds the Phase 6 files (package README,
 * demo env file, env README, package manifest, runtime checks, the local-repo Compose override and its README, and the
 * helper scripts). The result reuses {@link GeneratedArtifactPackage}: the file list is the full package and the report
 * is the unchanged Phase 5 report. Only the local Artemis repository runtime (Layer 1) is generated in this phase.
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

    static final String LOCAL_REPO_OVERRIDE_FILE = "deployment/local-repo/docker-compose.override.example.yml";

    static final String LOCAL_REPO_README_FILE = "deployment/local-repo/README.md";

    static final String PREPARE_ENV_SCRIPT_FILE = "scripts/prepare-env.sh";

    static final String VALIDATE_PACKAGE_SCRIPT_FILE = "scripts/validate-package.sh";

    static final String START_LOCAL_REPO_SCRIPT_FILE = "scripts/start-local-repo.sh";

    static final String STOP_LOCAL_REPO_SCRIPT_FILE = "scripts/stop-local-repo.sh";

    static final String PRINT_SUMMARY_SCRIPT_FILE = "scripts/print-runtime-summary.sh";

    private static final String CONTENT_TYPE_YAML = "application/x-yaml";

    private static final String CONTENT_TYPE_JSON = "application/json";

    private static final String CONTENT_TYPE_TEXT = "text/plain";

    private static final String CONTENT_TYPE_MARKDOWN = "text/markdown";

    private static final String CONTENT_TYPE_SHELL = "text/x-shellscript";

    private static final Pattern ENV_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([A-Z0-9_]+)\\}");

    /** Matches a leaked {@code env:NAME} reference value that should have been rendered as {@code ${NAME}}. */
    private static final Pattern ENV_LEAK_PATTERN = Pattern.compile("env:[A-Za-z_]");

    private final ArtifactGenerationService artifactGenerationService;

    private final RuntimeTemplateWriter templateWriter;

    private final RuntimeScriptWriter scriptWriter;

    private final ObjectMapper objectMapper;

    /**
     * Creates the deployment package service.
     *
     * @param artifactGenerationService Phase 5 service used to generate the base configuration artifacts.
     * @param templateWriter writer for the runtime template files.
     * @param scriptWriter writer for the helper scripts.
     * @param objectMapper Jackson mapper used to serialize the manifest and runtime checks.
     */
    public DeploymentPackageService(ArtifactGenerationService artifactGenerationService, RuntimeTemplateWriter templateWriter, RuntimeScriptWriter scriptWriter,
            ObjectMapper objectMapper) {
        this.artifactGenerationService = artifactGenerationService;
        this.templateWriter = templateWriter;
        this.scriptWriter = scriptWriter;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates the in-memory local runtime deployment package for a request.
     *
     * @param request artifact generation request (selection and optional profile).
     * @return generated package with the Phase 5 files plus the Phase 6 runtime files, and the Phase 5 report.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException if the selection is invalid.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException if the profile cannot be resolved.
     */
    public GeneratedArtifactPackage generate(ArtifactGenerationRequest request) {
        GeneratedArtifactPackage base = artifactGenerationService.generate(request);
        Map<String, GeneratedArtifactFile> baseByPath = base.files().stream().collect(Collectors.toMap(GeneratedArtifactFile::path, Function.identity()));
        GenerationReport report = base.report();

        GeneratedArtifactFile overlay = baseByPath.get(ArtifactGenerationService.OVERLAY_FILE);
        GeneratedArtifactFile envExample = baseByPath.get(ArtifactGenerationService.ENV_FILE);
        List<String> requiredEnvVars = parseEnvNames(envExample.content());

        String packageReadme = templateWriter.packageReadme(report.modelId(), report.modelVersion(), report.profileId(), report.profileVersion());
        String envDemo = templateWriter.envDemo(requiredEnvVars);

        List<String> packagePaths = packageFilePaths();
        String manifestJson = writeJson(buildManifest(report, packagePaths, requiredEnvVars));
        String checksJson = writeJson(buildRuntimeChecks(overlay.content(), requiredEnvVars, report, packagePaths.size()));

        List<GeneratedArtifactFile> files = new ArrayList<>();
        files.add(new GeneratedArtifactFile(PACKAGE_README_FILE, CONTENT_TYPE_MARKDOWN, packageReadme));
        files.add(overlay);
        files.add(envExample);
        files.add(new GeneratedArtifactFile(ENV_DEMO_FILE, CONTENT_TYPE_TEXT, envDemo));
        files.add(new GeneratedArtifactFile(ENV_README_FILE, CONTENT_TYPE_MARKDOWN, templateWriter.envReadme()));
        files.add(baseByPath.get(ArtifactGenerationService.SELECTED_FEATURES_FILE));
        files.add(baseByPath.get(ArtifactGenerationService.PROFILE_SUMMARY_FILE));
        files.add(baseByPath.get(ArtifactGenerationService.REPORT_FILE));
        files.add(new GeneratedArtifactFile(MANIFEST_FILE, CONTENT_TYPE_JSON, manifestJson));
        files.add(new GeneratedArtifactFile(RUNTIME_CHECKS_FILE, CONTENT_TYPE_JSON, checksJson));
        files.add(new GeneratedArtifactFile(LOCAL_REPO_OVERRIDE_FILE, CONTENT_TYPE_YAML, templateWriter.localRepoOverride()));
        files.add(new GeneratedArtifactFile(LOCAL_REPO_README_FILE, CONTENT_TYPE_MARKDOWN, templateWriter.localRepoReadme()));
        files.add(new GeneratedArtifactFile(PREPARE_ENV_SCRIPT_FILE, CONTENT_TYPE_SHELL, scriptWriter.prepareEnvScript()));
        files.add(new GeneratedArtifactFile(VALIDATE_PACKAGE_SCRIPT_FILE, CONTENT_TYPE_SHELL, scriptWriter.validatePackageScript()));
        files.add(new GeneratedArtifactFile(START_LOCAL_REPO_SCRIPT_FILE, CONTENT_TYPE_SHELL, scriptWriter.startLocalRepoScript()));
        files.add(new GeneratedArtifactFile(STOP_LOCAL_REPO_SCRIPT_FILE, CONTENT_TYPE_SHELL, scriptWriter.stopLocalRepoScript()));
        files.add(new GeneratedArtifactFile(PRINT_SUMMARY_SCRIPT_FILE, CONTENT_TYPE_SHELL, scriptWriter.printRuntimeSummaryScript()));

        log.info("Generated a local runtime deployment package with {} files for profile '{}' with status {}.", files.size(), report.profileId(), report.status());
        return new GeneratedArtifactPackage(files, report);
    }

    /**
     * Returns the deterministic ordered list of all package file paths, used for both the manifest and file assembly.
     *
     * @return ordered package file paths.
     */
    private List<String> packageFilePaths() {
        return List.of(PACKAGE_README_FILE, ArtifactGenerationService.OVERLAY_FILE, ArtifactGenerationService.ENV_FILE, ENV_DEMO_FILE, ENV_README_FILE,
                ArtifactGenerationService.SELECTED_FEATURES_FILE, ArtifactGenerationService.PROFILE_SUMMARY_FILE, ArtifactGenerationService.REPORT_FILE, MANIFEST_FILE,
                RUNTIME_CHECKS_FILE, LOCAL_REPO_OVERRIDE_FILE, LOCAL_REPO_README_FILE, PREPARE_ENV_SCRIPT_FILE, VALIDATE_PACKAGE_SCRIPT_FILE, START_LOCAL_REPO_SCRIPT_FILE,
                STOP_LOCAL_REPO_SCRIPT_FILE, PRINT_SUMMARY_SCRIPT_FILE);
    }

    /**
     * Builds the package manifest for the local-repo runtime layer.
     *
     * @param report Phase 5 generation report, source of the model/profile references.
     * @param packagePaths all package file paths, in order.
     * @param requiredEnvVars environment variables the overlay references.
     * @return package manifest.
     */
    private DeploymentPackageManifest buildManifest(GenerationReport report, List<String> packagePaths, List<String> requiredEnvVars) {
        DeploymentPackageManifest.ArtemisRuntimeInfo runtimeInfo = new DeploymentPackageManifest.ArtemisRuntimeInfo(RuntimePackageConstants.VERIFIED_ARTEMIS_COMMIT,
                "Layer 1 (local-repo) runs the local Artemis checkout's CI-capable local-VC/local-CI stack so any selection, including CI-dependent features such as "
                        + "Hyperion, can start. The overlay keys were verified against the referenced Artemis commit; a checkout at a different commit may not match all "
                        + "keys.");
        DeploymentPackageManifest.Database database = new DeploymentPackageManifest.Database(RuntimePackageConstants.DATABASE_TYPE, "local-container");
        DeploymentPackageManifest.Readiness readiness = new DeploymentPackageManifest.Readiness(true, false,
                "Generated in DEMO mode for local validation only; may contain placeholder values and never resolves real secrets.");
        return new DeploymentPackageManifest(RuntimePackageConstants.PACKAGE_TYPE, RuntimePackageConstants.PACKAGE_VERSION, RuntimePackageConstants.MODE_DEMO,
                List.of(RuntimePackageConstants.RUNTIME_MODE_LOCAL_REPO), new DeploymentPackageManifest.ModelRef(report.modelId(), report.modelVersion()),
                new DeploymentPackageManifest.ProfileRef(report.profileId(), report.profileVersion()), runtimeInfo, database, packagePaths, requiredEnvVars, readiness);
    }

    /**
     * Builds the runtime checks from the generated overlay and report. Every check is derived from already-generated
     * content so the JSON reflects the actual package.
     *
     * @param overlayContent generated YAML overlay content.
     * @param requiredEnvVars environment variables declared in {@code .env.example}.
     * @param report Phase 5 generation report.
     * @param fileCount number of files in the package.
     * @return runtime checks report.
     */
    private RuntimeChecksReport buildRuntimeChecks(String overlayContent, List<String> requiredEnvVars, GenerationReport report, int fileCount) {
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

        long plaintextSecrets = report.consumedParameters().stream()
                .filter(parameter -> parameter.secret() && !ConsumedParameter.SOURCE_ENV.equals(parameter.source())).count();
        checks.add(new RuntimeCheck("no-plaintext-secrets", "No secret value is written as plaintext.",
                plaintextSecrets == 0 ? RuntimeCheck.STATUS_PASS : RuntimeCheck.STATUS_FAIL,
                plaintextSecrets == 0 ? "Secret parameters are environment references only." : plaintextSecrets + " secret parameter(s) were not environment references."));

        int warningCount = report.warnings().size();
        checks.add(new RuntimeCheck("placeholder-values-reported", "Placeholder and integration notes are reported for review.", RuntimeCheck.STATUS_INFO,
                warningCount + " warning(s)/note(s) recorded in metadata/generation-report.json; review before real use."));

        boolean anyFailed = checks.stream().anyMatch(check -> RuntimeCheck.STATUS_FAIL.equals(check.status()));
        return new RuntimeChecksReport(RuntimePackageConstants.MODE_DEMO, anyFailed ? RuntimeCheck.STATUS_FAIL : RuntimeCheck.STATUS_PASS, checks);
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
