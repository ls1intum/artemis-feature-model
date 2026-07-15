package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureNode;
import de.tum.cit.aet.artemis.featuremodel.catalog.domain.ModelMetadata;
import de.tum.cit.aet.artemis.featuremodel.catalog.service.FeatureModelCatalogService;
import de.tum.cit.aet.artemis.featuremodel.deployment.domain.DeploymentProfile;
import de.tum.cit.aet.artemis.featuremodel.deployment.service.DeploymentProfileService;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactFile;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GeneratedArtifactPackage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GenerationMessage;
import de.tum.cit.aet.artemis.featuremodel.export.domain.GenerationReport;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ResolutionResult;
import de.tum.cit.aet.artemis.featuremodel.export.dto.ArtifactGenerationRequest;
import de.tum.cit.aet.artemis.featuremodel.export.dto.DeploymentProfileSummaryMetadata;
import de.tum.cit.aet.artemis.featuremodel.export.dto.SelectedFeatureRef;
import de.tum.cit.aet.artemis.featuremodel.export.dto.SelectedFeaturesMetadata;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;
import de.tum.cit.aet.artemis.featuremodel.validation.dto.ValidationRequest;
import de.tum.cit.aet.artemis.featuremodel.validation.dto.ValidationResultDTO;
import de.tum.cit.aet.artemis.featuremodel.validation.service.FeatureModelValidationService;
import tools.jackson.databind.ObjectMapper;

/**
 * Generates deterministic Level 1 configuration artifacts from a validated feature selection and an active deployment
 * profile.
 *
 * <p>
 * The service validates the selection, resolves the active profile, maps the selection and profile to overlay entries,
 * and assembles an in-memory package: a YAML configuration overlay, a {@code .env.example}, selected-feature and
 * deployment-profile metadata, a generation report, and a README. The result feeds both the preview response and the
 * downloadable ZIP. Generation uses placeholder and reference values in demo mode and reports them clearly; it never
 * writes plaintext secrets or modifies Artemis source configuration.
 */
@Service
public class ArtifactGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGenerationService.class);

    static final String OVERLAY_FILE = "config/application-feature-model.yml";

    static final String ENV_FILE = "env/.env.example";

    static final String SELECTED_FEATURES_FILE = "metadata/selected-features.json";

    static final String PROFILE_SUMMARY_FILE = "metadata/deployment-profile-summary.json";

    static final String REPORT_FILE = "metadata/generation-report.json";

    static final String README_FILE = "README.md";

    private static final String CONTENT_TYPE_YAML = "application/x-yaml";

    private static final String CONTENT_TYPE_JSON = "application/json";

    private static final String CONTENT_TYPE_TEXT = "text/plain";

    private static final String CONTENT_TYPE_MARKDOWN = "text/markdown";

    private final FeatureModelCatalogService catalogService;

    private final FeatureModelValidationService validationService;

    private final DeploymentProfileService deploymentProfileService;

    private final ArtifactMappingResolver mappingResolver;

    private final YamlOverlayWriter yamlOverlayWriter;

    private final EnvExampleWriter envExampleWriter;

    private final ObjectMapper objectMapper;

    /**
     * Creates the artifact generation service.
     *
     * @param catalogService service used to load the active feature model.
     * @param validationService service used to validate the selection before generation.
     * @param deploymentProfileService service used to load and resolve the active profile.
     * @param mappingResolver resolver from selection and profile to overlay entries and report fragments.
     * @param yamlOverlayWriter writer for the YAML overlay.
     * @param envExampleWriter writer for the {@code .env.example} file.
     * @param objectMapper Jackson mapper used to serialize metadata and the report.
     */
    public ArtifactGenerationService(FeatureModelCatalogService catalogService, FeatureModelValidationService validationService,
            DeploymentProfileService deploymentProfileService, ArtifactMappingResolver mappingResolver, YamlOverlayWriter yamlOverlayWriter,
            EnvExampleWriter envExampleWriter, ObjectMapper objectMapper) {
        this.catalogService = catalogService;
        this.validationService = validationService;
        this.deploymentProfileService = deploymentProfileService;
        this.mappingResolver = mappingResolver;
        this.yamlOverlayWriter = yamlOverlayWriter;
        this.envExampleWriter = envExampleWriter;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates the in-memory artifact package for a request.
     *
     * @param request artifact generation request.
     * @return generated artifact package with files and report.
     * @throws ArtifactGenerationException if the selection is invalid.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.DeploymentProfileException if the requested or default profile cannot be resolved.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.FeatureModelLoadException if the active model cannot be loaded.
     */
    public GeneratedArtifactPackage generate(ArtifactGenerationRequest request) {
        FeatureModel model = catalogService.loadActiveModel();
        List<String> normalizedSelection = validateSelection(model, request.selectedFeatureIds());

        List<DeploymentProfile> profiles = deploymentProfileService.loadProfiles();
        DeploymentProfile profile = deploymentProfileService.resolveProfileOrDefault(profiles, request.profileId());

        Set<String> selectedFeatureIds = new LinkedHashSet<>(normalizedSelection);
        ResolutionResult resolution = mappingResolver.resolve(model, selectedFeatureIds, profile);

        String overlayYaml = yamlOverlayWriter.write(resolution.entries());
        String envExample = envExampleWriter.write(resolution.environmentVariables());
        String selectedFeaturesJson = writeJson(selectedFeaturesMetadata(model, normalizedSelection));
        String profileSummaryJson = writeJson(DeploymentProfileSummaryMetadata.from(profile));

        List<String> generatedFiles = List.of(README_FILE, OVERLAY_FILE, ENV_FILE, SELECTED_FEATURES_FILE, PROFILE_SUMMARY_FILE, REPORT_FILE);
        GenerationReport report = buildReport(model, profile, normalizedSelection, generatedFiles, resolution);
        String reportJson = writeJson(report);
        String readme = buildReadme(model, profile);

        List<GeneratedArtifactFile> files = List.of(new GeneratedArtifactFile(README_FILE, CONTENT_TYPE_MARKDOWN, readme),
                new GeneratedArtifactFile(OVERLAY_FILE, CONTENT_TYPE_YAML, overlayYaml), new GeneratedArtifactFile(ENV_FILE, CONTENT_TYPE_TEXT, envExample),
                new GeneratedArtifactFile(SELECTED_FEATURES_FILE, CONTENT_TYPE_JSON, selectedFeaturesJson),
                new GeneratedArtifactFile(PROFILE_SUMMARY_FILE, CONTENT_TYPE_JSON, profileSummaryJson),
                new GeneratedArtifactFile(REPORT_FILE, CONTENT_TYPE_JSON, reportJson));

        log.info("Generated {} artifact files for {} selected features under profile '{}' with status {}.", files.size(), normalizedSelection.size(),
                profile.id(), report.status());
        return new GeneratedArtifactPackage(files, report);
    }

    /**
     * Validates the selection against the model and returns the normalized selected ids.
     *
     * @param model active feature model.
     * @param selectedFeatureIds submitted selected feature ids.
     * @return normalized selected feature ids in stable order.
     * @throws ArtifactGenerationException if the selection is invalid.
     */
    private List<String> validateSelection(FeatureModel model, List<String> selectedFeatureIds) {
        ValidationResultDTO result = validationService.validateSelection(model, new ValidationRequest(selectedFeatureIds));
        if (!result.valid()) {
            String firstMessage = result.violations().isEmpty() ? "" : result.violations().get(0).message();
            log.warn("Rejected artifact generation for an invalid selection with {} violation(s).", result.violations().size());
            throw ArtifactGenerationException.invalidSelection(result.violations().size(), firstMessage);
        }
        return result.normalizedSelection();
    }

    /**
     * Builds the generation report from the resolution result.
     *
     * @param model active feature model.
     * @param profile active deployment profile.
     * @param selectedFeatureIds normalized selected feature ids.
     * @param generatedFiles generated file paths.
     * @param resolution resolution result with messages and consumed parameters.
     * @return generation report.
     */
    private GenerationReport buildReport(FeatureModel model, DeploymentProfile profile, List<String> selectedFeatureIds, List<String> generatedFiles,
            ResolutionResult resolution) {
        boolean hasWarning = resolution.messages().stream().anyMatch(message -> GenerationMessage.WARNING.equals(message.severity()));
        String status = hasWarning ? GenerationReport.STATUS_GENERATED_WITH_WARNINGS : GenerationReport.STATUS_GENERATED;
        ModelMetadata metadata = model.model();
        return new GenerationReport(status, GenerationReport.MODE_DEMO, metadata.id(), metadata.version(), profile.id(), profile.version(), selectedFeatureIds,
                generatedFiles, resolution.consumedParameters(), resolution.omittedMappings(), resolution.messages(), List.of());
    }

    /**
     * Builds the selected-features metadata from the normalized selection.
     *
     * @param model active feature model.
     * @param selectedFeatureIds normalized selected feature ids.
     * @return selected-features metadata.
     */
    private SelectedFeaturesMetadata selectedFeaturesMetadata(FeatureModel model, List<String> selectedFeatureIds) {
        Map<String, FeatureNode> featuresById = model.features().stream().collect(Collectors.toMap(FeatureNode::id, Function.identity()));
        List<SelectedFeatureRef> selectedFeatures = new ArrayList<>();
        for (String featureId : selectedFeatureIds) {
            FeatureNode feature = featuresById.get(featureId);
            String name = feature != null ? feature.name() : featureId;
            selectedFeatures.add(new SelectedFeatureRef(featureId, name));
        }
        ModelMetadata metadata = model.model();
        return new SelectedFeaturesMetadata(metadata.id(), metadata.version(), GenerationReport.MODE_DEMO, selectedFeatures);
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

    /**
     * Builds the README explaining how to use the generated overlay and its placeholder/secret handling.
     *
     * @param model active feature model.
     * @param profile active deployment profile.
     * @return README markdown text.
     */
    private String buildReadme(FeatureModel model, DeploymentProfile profile) {
        ModelMetadata metadata = model.model();
        return """
                # Artemis Feature Model Configuration Artifacts

                Generated from feature model `%s` version `%s` and deployment context `%s` (%s) in DEMO mode.

                ## What this is

                - `config/application-feature-model.yml` is an external Spring configuration **overlay**. It does not replace \
                Artemis' `application.yml`, `application-core.yml`, or `application-artemis.yml`; it is meant to be applied \
                on top of the normal Artemis configuration stack (for example through an additional Spring config location, \
                a mounted config file, or a deployment-specific configuration mechanism).
                - `env/.env.example` lists the environment variables the overlay references. Provide their values through \
                the deployment environment; the overlay reads them as `${VARIABLE}` placeholders.
                - `metadata/` contains the selected features, a deployment-profile summary, and the generation report.

                ## Before real deployment

                - This is a DEMO artifact. Placeholder values (for example `*.example.com` URLs) must be replaced with real \
                service values before any production use. See `metadata/generation-report.json` for the exact list.
                - Secret values are never written here as plaintext. They appear only as `${VARIABLE}` placeholders, and the \
                values must be supplied securely by the deployment environment.
                - Real production parameters may later be managed outside this tool, for example through Ansible and \
                HashiCorp Vault. That integration is out of scope for this phase.
                - Some integrations require post-deployment administrator steps. In particular, LTI platform registration is \
                managed in the Artemis database and cannot be configured by this overlay alone.

                Review `metadata/generation-report.json` for warnings, consumed parameters, and omitted mappings.
                """
                .formatted(metadata.id(), metadata.version(), profile.name(), profile.id());
    }
}
