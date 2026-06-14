/**
 * Wire-format types for the deployment profile API. The Configurator reads profile-aware availability from
 * `/api/feature-model/profile-availability` and uses it to disable unavailable guided options, show the active profile,
 * and surface profile-dependent features. Raw capability ids stay in the advanced tree/debug view.
 */

export interface DeploymentProfileSummary {
    id: string;
    name: string;
    version: string;
    status: string;
    defaultProfile: boolean;
}

export interface OptionAvailability {
    optionId: string;
    available: boolean;
    requiredCapabilities: string[];
    missingCapabilities: string[];
    teacherReason: string | null;
}

export interface FeatureAvailability {
    featureId: string;
    featureName: string;
    available: boolean;
    profileDependent: boolean;
    requiredCapabilities: string[];
    missingCapabilities: string[];
    teacherReason: string | null;
}

export interface WorkflowAvailability {
    activeProfile: DeploymentProfileSummary;
    availableProfiles: DeploymentProfileSummary[];
    options: OptionAvailability[];
    features: FeatureAvailability[];
}
