import { ModelMetadata } from '../../core/feature-model.types';
import { GuidedWorkflowMetadata } from '../../core/guided-workflow.types';

export interface ConfiguratorTutorialStep {
    title: string;
    summary: string;
    details: string[];
}

export const CONFIGURATOR_TUTORIAL_STEPS: ConfiguratorTutorialStep[] = [
    {
        title: 'Choose a template',
        summary: 'Start from a teaching use case instead of building a configuration from scratch.',
        details: [
            'Templates preselect a coherent set of Artemis features.',
            'You can still adjust the selection in later steps.',
        ],
    },
    {
        title: 'Decide on features',
        summary: 'Move through guided decisions and select the capabilities your course needs.',
        details: [
            'Each option explains functional impact, technical impact, artifact impact, and warnings.',
            'Validation runs after changes so invalid combinations are visible immediately.',
        ],
    },
    {
        title: 'Review before handoff',
        summary: 'Use the review page to check selected features, warnings, and validation results.',
        details: [
            'Artifact generation is still a later-phase placeholder.',
            'The advanced tree remains available when you need raw model-level control.',
        ],
    },
];

export function buildConfiguratorTutorialSeenKey(model: ModelMetadata, workflow: GuidedWorkflowMetadata): string {
    return ['artemis.configurator.tutorial.seen', workflow.id, workflow.version, model.id, model.version].join(':');
}
