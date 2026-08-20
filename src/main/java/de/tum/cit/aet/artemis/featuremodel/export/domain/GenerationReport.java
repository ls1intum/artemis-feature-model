package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Deterministic report describing a single artifact generation run. The same report is both serialized into
 * {@code metadata/generation-report.json} and returned in the preview response.
 *
 * @param status generation status, one of {@link #STATUS_GENERATED} or {@link #STATUS_GENERATED_WITH_WARNINGS}.
 * @param mode generation mode; only {@link #MODE_DEMO} is supported.
 * @param modelId active feature model id.
 * @param modelVersion active feature model version.
 * @param profileId active deployment profile id.
 * @param profileVersion active deployment profile version.
 * @param selectedFeatureIds selected feature ids used for generation, in normalized order.
 * @param generatedFiles relative paths of the generated files.
 * @param environmentRequirements structured environment values the deployment environment must supply.
 * @param warnings warnings and informational notes.
 * @param errors blocking errors; always empty because blocking cases throw before a report is built.
 * @param technicalSelection selected technical axes recorded by deployment-package generation, or {@code null} for
 *            models without technical mappings.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GenerationReport(String status, String mode, String modelId, String modelVersion, String profileId, String profileVersion,
        List<String> selectedFeatureIds, List<String> generatedFiles, List<EnvironmentRequirement> environmentRequirements,
        List<GenerationMessage> warnings, List<GenerationMessage> errors, TechnicalSelectionMetadata technicalSelection) {

    /** Generation succeeded with no warnings. */
    public static final String STATUS_GENERATED = "GENERATED";

    /** Generation succeeded but the report contains warnings (for example environment values still to supply). */
    public static final String STATUS_GENERATED_WITH_WARNINGS = "GENERATED_WITH_WARNINGS";

    /** Demo mode: placeholder and unresolved values are allowed but reported. */
    public static final String MODE_DEMO = "DEMO";

    /**
     * Normalizes nullable collections to immutable empty collections.
     *
     * @param status generation status.
     * @param mode generation mode.
     * @param modelId active feature model id.
     * @param modelVersion active feature model version.
     * @param profileId active deployment profile id.
     * @param profileVersion active deployment profile version.
     * @param selectedFeatureIds selected feature ids.
     * @param generatedFiles generated file paths.
     * @param environmentRequirements structured environment requirements.
     * @param warnings warnings and informational notes.
     * @param errors blocking errors.
     * @param technicalSelection selected technical axes, or {@code null}.
     */
    public GenerationReport {
        selectedFeatureIds = selectedFeatureIds == null ? List.of() : List.copyOf(selectedFeatureIds);
        generatedFiles = generatedFiles == null ? List.of() : List.copyOf(generatedFiles);
        environmentRequirements = environmentRequirements == null ? List.of() : List.copyOf(environmentRequirements);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Creates a report without technical-selection metadata.
     *
     * @param status generation status.
     * @param mode generation mode.
     * @param modelId active feature model id.
     * @param modelVersion active feature model version.
     * @param profileId active deployment profile id.
     * @param profileVersion active deployment profile version.
     * @param selectedFeatureIds selected feature ids.
     * @param generatedFiles generated file paths.
     * @param environmentRequirements structured environment requirements.
     * @param warnings warnings and informational notes.
     * @param errors blocking errors.
     */
    public GenerationReport(String status, String mode, String modelId, String modelVersion, String profileId, String profileVersion,
            List<String> selectedFeatureIds, List<String> generatedFiles, List<EnvironmentRequirement> environmentRequirements,
            List<GenerationMessage> warnings, List<GenerationMessage> errors) {
        this(status, mode, modelId, modelVersion, profileId, profileVersion, selectedFeatureIds, generatedFiles, environmentRequirements, warnings, errors, null);
    }

    /**
     * Copies this report with the complete environment requirements of the composed package, so package-only
     * requirements can never disappear from the report metadata.
     *
     * @param completeRequirements environment requirements including package-only requirements.
     * @return copied report.
     */
    public GenerationReport withEnvironmentRequirements(List<EnvironmentRequirement> completeRequirements) {
        return new GenerationReport(status, mode, modelId, modelVersion, profileId, profileVersion, selectedFeatureIds, generatedFiles, completeRequirements,
                warnings, errors, technicalSelection);
    }

    /**
     * Copies this report with technical-selection recording metadata.
     *
     * @param metadata technical-selection metadata.
     * @return copied report.
     */
    public GenerationReport withTechnicalSelection(TechnicalSelectionMetadata metadata) {
        return new GenerationReport(status, mode, modelId, modelVersion, profileId, profileVersion, selectedFeatureIds, generatedFiles, environmentRequirements,
                warnings, errors, metadata);
    }

    /**
     * Copies this report with technical metadata and one additional warning.
     *
     * @param metadata technical-selection metadata.
     * @param warning mode-specific generation warning.
     * @return copied report with warning status.
     */
    public GenerationReport withTechnicalSelectionAndWarning(TechnicalSelectionMetadata metadata, GenerationMessage warning) {
        List<GenerationMessage> updatedWarnings = new ArrayList<>(warnings);
        updatedWarnings.add(warning);
        return new GenerationReport(STATUS_GENERATED_WITH_WARNINGS, mode, modelId, modelVersion, profileId, profileVersion, selectedFeatureIds,
                generatedFiles, environmentRequirements, updatedWarnings, errors, metadata);
    }
}
