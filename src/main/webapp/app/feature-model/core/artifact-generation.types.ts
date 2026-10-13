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
    /** Target identity of the remote-ansible mode; omitted when no target name was entered. */
    remoteEnvironment?: RemoteEnvironmentInput;
}

/** Target identity of a remote-ansible request: it names the inventory group and routes a published package. */
export interface RemoteEnvironmentInput {
    targetName: string;
}

/** Publish request: the unchanged generation request plus the deployment repository it is pushed to. */
export interface DeploymentPackagePublishRequest {
    request: ArtifactGenerationRequest;
    /** Deployment repository URL; the server falls back to its default when blank. */
    repositoryUrl: string;
}

/**
 * Where a deployment repository publish would go; `configured: false` hides the repository field and the publish
 * action. The default repository URL prefills the field.
 */
export interface DeploymentPackagePublishTarget {
    configured: boolean;
    defaultRepositoryUrl: string | null;
    branch: string;
    targetDirectoryRoot: string;
}

/** Result of a deployment repository publish: the commit carrying the package, or the up-to-date notice. */
export interface DeploymentPackagePublishResponse {
    repositoryUrl: string;
    branch: string;
    targetDirectory: string;
    commitSha: string;
    commitUrl: string | null;
    upToDate: boolean;
}
