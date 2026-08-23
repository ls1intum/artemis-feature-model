/** One review-page deployment target: its mode id and the copy the picker, note, and download button show for it. */
export interface DeploymentTarget {
    id: string;
    label: string;
    /** Optional small label rendered after the option label. */
    hint?: string;
    note: string;
    downloadLabel: string;
    fileName: string;
}

/** Mode id of the default target; it is sent without a `deploymentMode` field to keep the pre-mode-axis request shape. */
export const DEFAULT_DEPLOYMENT_MODE = 'local-docker';

/** Deployment targets in picker order; the first entry is the default. */
export const DEPLOYMENT_TARGETS: readonly DeploymentTarget[] = [
    {
        id: DEFAULT_DEPLOYMENT_MODE,
        label: 'Docker runtime',
        hint: '(default)',
        note:
            'A validation package that wraps the configuration artifacts above with a Docker Compose override and helper scripts, so you can run Artemis ' +
            'with Docker Compose from an existing checkout and confirm the generated overlay loads. It is for validation only, not production.',
        downloadLabel: 'Download Docker runtime package',
        fileName: 'artemis-feature-model-deployment-package.zip',
    },
    {
        id: 'dev-ide',
        label: 'IntelliJ IDE development setup',
        note:
            'A configuration-only package for developers: the configuration artifacts above plus a generated IntelliJ run configuration whose Spring ' +
            'profiles match your selection, and a short setup README. Nothing is deployed; you run Artemis from your own local checkout.',
        downloadLabel: 'Download IDE setup package',
        fileName: 'artemis-feature-model-dev-ide-package.zip',
    },
    {
        id: 'remote-ansible',
        label: 'Remote server (Ansible)',
        note:
            'An admin-consumable Ansible package for deploying your selection to a remote server: generated inventory values, a pinned collection ' +
            'reference, a playbook, and a preflight script. It contains no credentials and deploys nothing itself; a server administrator completes ' +
            'the environment values and runs it.',
        downloadLabel: 'Download Ansible deployment package',
        fileName: 'artemis-feature-model-remote-ansible-package.zip',
    },
];

/** Resolves a target by mode id, falling back to the default target. */
export function deploymentTargetFor(deploymentMode: string): DeploymentTarget {
    return DEPLOYMENT_TARGETS.find((target) => target.id === deploymentMode) ?? DEPLOYMENT_TARGETS[0];
}
