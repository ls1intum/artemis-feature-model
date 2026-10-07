import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

// A page is reachable only when it is listed here.
const sidebars: SidebarsConfig = {
    default: [
        'intro',
        {
            type: 'category',
            label: 'Coding Guidelines',
            items: ['guidelines/documentation'],
        },
    ],
};

export default sidebars;
