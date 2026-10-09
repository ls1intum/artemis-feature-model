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
    ],
};

export default sidebars;
