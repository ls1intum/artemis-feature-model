package de.tum.cit.aet.artemis.featuremodel.export.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Deterministic report describing a single artifact generation run. The same report is both serialized into
 * {@code metadata/generation-report.json} and returned in the preview response.
 *
 * @param status generation status, one of {@link #STATUS_GENERATED} or {@link #STATUS_GENERATED_WITH_WARNINGS}.
 * @param mode generation mode; only {@link #MODE_DEMO} is supported in this phase.
 * @param modelId active feature model id.
 * @param modelVersion active feature model version.
 * @param profileId active deployment profile id.
 * @param profileVersion active deployment profile version.
 * @param selectedFeatureIds selected feature ids used for generation, in normalized order.
 * @param generatedFiles relative paths of the generated files.
 * @param consumedParameters profile parameters consumed by the overlay.
 * @param omittedMappings mappings that were skipped, with their reasons.
 * @param warnings warnings and informational notes.
 * @param errors blocking errors; empty in this phase because blocking cases throw before a report is built.
 * @param technicalSelection selected technical axes recorded by deployment-package generation, or {@code null} for
 *            models without technical mappings.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GenerationReport(String status, String mode, String modelId, String modelVersion, String profileId, String profileVersion,
        List<String> selectedFeatureIds, List<String> generatedFiles, List<ConsumedParameter> consumedParameters, List<OmittedMapping> omittedMappings,
        List<GenerationMessage> warnings, List<GenerationMessage> errors, TechnicalSelectionMetadata technicalSelection) {

    /** Generation succeeded with no warnings. */
    public static final String STATUS_GENERATED = "GENERATED";

    /** Generation succeeded but the report contains warnings (for example placeholder or unresolved values). */
    public static final String STATUS_GENERATED_WITH_WARNINGS = "GENERATED_WITH_WARNINGS";

    /** Demo mode: placeholder and unresolved values are allowed but reported as warnings. */
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
     * @param consumedParameters consumed profile parameters.
     * @param omittedMappings skipped mappings.
     * @param warnings warnings and informational notes.
     * @param errors blocking errors.
     * @param technicalSelection selected technical axes, or {@code null}.
     */
    public GenerationReport {
        selectedFeatureIds = selectedFeatureIds == null ? List.of() : List.copyOf(selectedFeatureIds);
        generatedFiles = generatedFiles == null ? List.of() : List.copyOf(generatedFiles);
        consumedParameters = consumedParameters == null ? List.of() : List.copyOf(consumedParameters);
        omittedMappings = omittedMappings == null ? List.of() : List.copyOf(omittedMappings);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * Creates a Phase 5 report without technical-selection metadata.
     *
     * @param status generation status.
     * @param mode generation mode.
     * @param modelId active feature model id.
     * @param modelVersion active feature model version.
     * @param profileId active deployment profile id.
     * @param profileVersion active deployment profile version.
     * @param selectedFeatureIds selected feature ids.
     * @param generatedFiles generated file paths.
     * @param consumedParameters consumed profile parameters.
     * @param omittedMappings skipped mappings.
     * @param warnings warnings and informational notes.
     * @param errors blocking errors.
     */
    public GenerationReport(String status, String mode, String modelId, String modelVersion, String profileId, String profileVersion,
            List<String> selectedFeatureIds, List<String> generatedFiles, List<ConsumedParameter> consumedParameters, List<OmittedMapping> omittedMappings,
            List<GenerationMessage> warnings, List<GenerationMessage> errors) {
        this(status, mode, modelId, modelVersion, profileId, profileVersion, selectedFeatureIds, generatedFiles, consumedParameters, omittedMappings, warnings, errors,
                null);
    }

    /**
     * Copies this report with technical-selection recording metadata.
     *
     * @param metadata technical-selection metadata.
     * @return copied report.
     */
    public GenerationReport withTechnicalSelection(TechnicalSelectionMetadata metadata) {
        return new GenerationReport(status, mode, modelId, modelVersion, profileId, profileVersion, selectedFeatureIds, generatedFiles, consumedParameters,
                omittedMappings, warnings, errors, metadata);
    }
}
