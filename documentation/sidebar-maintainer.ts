import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

// A page is reachable only when it is listed here.
const sidebars: SidebarsConfig = {
    default: [
        'intro',
        'setup',
        'concepts',
        {
            type: 'category',
            label: 'Extraction System',
            link: { type: 'doc', id: 'extraction/index' },
            items: [
                'extraction/running-locally',
                'extraction/stages',
                'extraction/manifest',
                'extraction/curation-and-conformance',
                'extraction/source-scanners',
                'extraction/reports',
                'extraction/snapshots',
            ],
        },
        {
            type: 'category',
            label: 'Delivery Pipeline',
            link: { type: 'doc', id: 'pipeline/index' },
            items: ['pipeline/workflows', 'pipeline/delivery-files', 'pipeline/images', 'pipeline/repository-settings'],
        },
        {
            type: 'category',
            label: 'Maintenance Tasks',
            link: { type: 'doc', id: 'tasks/index' },
            items: [
                'tasks/review-delivery-pr',
                'tasks/fix-failed-extraction',
                'tasks/curate-a-feature',
                'tasks/update-guided-workflow',
                'tasks/follow-artemis-changes',
                'tasks/deliver-artemis-commit',
                'tasks/promote-and-roll-back',
                'tasks/update-runtime-image',
                'tasks/update-ansible-bindings',
                'tasks/manifest-cutover',
            ],
        },
        'troubleshooting',
        {
            type: 'category',
            label: 'Reference',
            items: ['reference/gradle-tasks', 'reference/files', 'reference/diagnostic-codes'],
        },
    ],
};

export default sidebars;
