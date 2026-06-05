import { ModelMetadata } from '../../core/feature-model.types';
import { GuidedWorkflowMetadata } from '../../core/guided-workflow.types';

export interface ConfiguratorTutorialStep {
    title: string;
    summary: string;
    details: string[];
    imageSrc?: string;
    imageLayout?: 'standard' | 'wide';
}

export const CONFIGURATOR_TUTORIAL_STEPS: ConfiguratorTutorialStep[] = [
    {
        title: 'Configurator Tutorial',
        summary: 'Welcome to the Artemis Configurator. This short tutorial introduces the main workflow before you start choosing features.',
        details: [
            'You will learn how to pick a template, adjust features, review the result, and inspect the live tree view.',
            'Click Next to start the tutorial.',
        ],
    },
    {
        title: 'Choose a template',
        summary: 'Start from a teaching use case instead of building a configuration from scratch.',
        details: [
            'Templates preselect a coherent set of Artemis features.',
            'You can still adjust the selection in later steps.',
        ],
        imageSrc: 'content/img/tutorial/templates.png',
        imageLayout: 'wide',
    },
    {
        title: 'Decide on features',
        summary: 'Move through guided decisions and select the capabilities your course needs.',
        details: [
            'Each option explains what it enables, when it is useful, and what course teams should know.',
            'Validation runs after changes so invalid combinations are visible immediately.',
        ],
        imageSrc: 'content/img/tutorial/feature.png',
    },
    {
        title: 'Review before handoff',
        summary: 'Use the review page to check selected features, warnings, and validation results.',
        details: [
            'Artifact generation is still a later-phase placeholder.',
            'The advanced tree remains available when you need raw model-level control.',
        ],
        imageSrc: 'content/img/tutorial/review.png',
        imageLayout: 'wide',
    },
    {
        title: 'Use the live tree view',
        summary: 'Switch to the tree view whenever you want direct model-level control.',
        details: [
            'The tree view reflects your current feature selections in real time.',
            'You can also select or deselect features directly from the tree and inspect technical or artifact details.',
        ],
        imageSrc: 'content/img/tutorial/tree.png',
        imageLayout: 'wide',
    },
];

export function buildConfiguratorTutorialSeenKey(model: ModelMetadata, workflow: GuidedWorkflowMetadata): string {
    return ['artemis.configurator.tutorial.seen', workflow.id, workflow.version, model.id, model.version].join(':');
}
