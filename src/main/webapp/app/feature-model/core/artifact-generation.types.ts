/**
 * Wire-format types for the artifact generation API. The guided Configurator review page downloads the artifact ZIP
 * package for a valid selection. Secret values appear only as `${VARIABLE}` placeholders; raw secrets are never
 * transferred.
 */

export interface ArtifactGenerationRequest {
    selectedFeatureIds: string[];
    profileId?: string;
    mode?: string;
}
