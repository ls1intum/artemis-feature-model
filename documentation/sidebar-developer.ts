import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';

// A page is reachable only when it is listed here.
const sidebars: SidebarsConfig = {
    default: [
        'intro',
        'setup',
        'development-process',
        {
            type: 'category',
            label: 'Coding Guidelines',
            link: { type: 'doc', id: 'guidelines/index' },
            items: [
                'guidelines/java',
                'guidelines/server-design',
                'guidelines/typescript-angular',
                'guidelines/client-styling-theming',
                'guidelines/testing',
                'guidelines/documentation',
            ],
        },
    ],
};

export default sidebars;
