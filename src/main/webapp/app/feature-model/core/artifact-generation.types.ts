/**
 * Wire-format types for the artifact generation API. The guided Configurator review page downloads the artifact ZIP
 * package for a valid selection. Secret values appear only as `${VARIABLE}` placeholders; raw secrets are never
 * transferred.
 */

export interface ArtifactGenerationRequest {
    selectedFeatureIds: string[];
    profileId?: string;
    mode?: string;
    /** Deployment mode id for the deployment package; omitted for the default local Docker runtime behavior. */
    deploymentMode?: string;
}
