/**
 * Wire-format types for the artifact generation API. The guided Configurator review page previews the generated
 * configuration overlay and report warnings, and downloads the artifact ZIP package. Secret values appear only as
 * `${VARIABLE}` placeholders; raw secrets are never transferred.
 */

export interface ArtifactGenerationRequest {
    selectedFeatureIds: string[];
    profileId?: string;
    mode?: string;
}

export interface GeneratedArtifactFile {
    path: string;
    contentType: string;
    preview: string;
}

export interface GenerationMessage {
    severity: string;
    featureId: string | null;
    parameter: string | null;
    message: string;
}

export interface ConsumedParameter {
    featureId: string;
    profileKey: string;
    targetPath: string;
    secret: boolean;
    source: string;
}

export interface OmittedMapping {
    featureId: string;
    targetPath: string;
    reason: string;
}

export interface GenerationReport {
    status: string;
    mode: string;
    modelId: string;
    modelVersion: string;
    profileId: string;
    profileVersion: string;
    selectedFeatureIds: string[];
    generatedFiles: string[];
    consumedParameters: ConsumedParameter[];
    omittedMappings: OmittedMapping[];
    warnings: GenerationMessage[];
    errors: GenerationMessage[];
}

export interface ArtifactGenerationResult {
    status: string;
    files: GeneratedArtifactFile[];
    report: GenerationReport;
    downloadAvailable: boolean;
}
