import { themes as prismThemes } from 'prism-react-renderer';
import type { Config } from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This file runs in Node.js. Do not use browser APIs or JSX here.
const REPOSITORY_URL = 'https://github.com/ls1intum/artemis-feature-model';
const EDIT_URL = `${REPOSITORY_URL}/tree/mvp/documentation/`;
const PRODUCT_NAME = 'Artemis Feature Model';

const config: Config = {
    title: `${PRODUCT_NAME} Documentation`,
    tagline: 'Guides for building the configurator and for keeping the feature model in sync with Artemis.',

    // GitHub Pages serves the site as a project site below the repository name.
    url: 'https://ls1intum.github.io',
    baseUrl: '/artemis-feature-model/',
    trailingSlash: false,
    organizationName: 'ls1intum',
    projectName: 'artemis-feature-model',

    onBrokenLinks: 'throw',
    onBrokenAnchors: 'throw',

    markdown: {
        mermaid: true,
        hooks: {
            onBrokenMarkdownLinks: 'throw',
        },
    },

    i18n: {
        defaultLocale: 'en',
        locales: ['en'],
    },

    presets: [
        [
            'classic',
            {
                // Each guide is its own docs plugin instance below.
                docs: false,
                blog: false,
                theme: {
                    customCss: './src/css/custom.css',
                },
            } satisfies Preset.Options,
        ],
    ],

    themes: [
        '@docusaurus/theme-mermaid',
        [
            '@easyops-cn/docusaurus-search-local',
            {
                hashed: true,
                language: ['en'],
                indexDocs: true,
                indexBlog: false,
                docsRouteBasePath: ['developer', 'maintainer'],
                searchContextByPaths: [
                    {
                        label: 'Developer Guide',
                        path: 'developer',
                    },
                    {
                        label: 'Maintainer Guide',
                        path: 'maintainer',
                    },
                ],
                useAllContextsWithNoSearchContext: true,
            },
        ],
    ],

    plugins: [
        [
            '@docusaurus/plugin-content-docs',
            {
                path: 'docs/developer',
                routeBasePath: 'developer',
                sidebarPath: './sidebar-developer.ts',
                editUrl: EDIT_URL,
                exclude: ['**/README.md'],
            },
        ],
        [
            '@docusaurus/plugin-content-docs',
            {
                id: 'maintainer',
                path: 'docs/maintainer',
                routeBasePath: 'maintainer',
                sidebarPath: './sidebar-maintainer.ts',
                editUrl: EDIT_URL,
                exclude: ['**/README.md'],
            },
        ],
    ],

    themeConfig: {
        colorMode: {
            respectPrefersColorScheme: true,
        },
        navbar: {
            title: PRODUCT_NAME,
            items: [
                {
                    type: 'docSidebar',
                    sidebarId: 'default',
                    label: 'Developer Guide',
                    position: 'left',
                },
                {
                    type: 'docSidebar',
                    sidebarId: 'default',
                    docsPluginId: 'maintainer',
                    label: 'Maintainer Guide',
                    position: 'left',
                },
                {
                    href: REPOSITORY_URL,
                    label: 'GitHub',
                    position: 'right',
                },
            ],
        },
        footer: {
            style: 'dark',
            links: [
                {
                    title: 'Guides',
                    items: [
                        {
                            label: 'Developer Guide',
                            to: '/developer/intro',
                        },
                        {
                            label: 'Maintainer Guide',
                            to: '/maintainer/intro',
                        },
                    ],
                },
                {
                    title: 'Project',
                    items: [
                        {
                            label: 'GitHub repository',
                            href: REPOSITORY_URL,
                        },
                        {
                            label: 'Issues',
                            href: `${REPOSITORY_URL}/issues`,
                        },
                    ],
                },
                {
                    title: 'Related',
                    items: [
                        {
                            label: 'Artemis',
                            href: 'https://github.com/ls1intum/Artemis',
                        },
                        {
                            label: 'Artemis documentation',
                            href: 'https://docs.artemis.tum.de',
                        },
                        {
                            label: 'Applied Education Technologies',
                            href: 'https://aet.cit.tum.de',
                        },
                    ],
                },
            ],
            copyright: '© 2026 TUM Applied Education Technologies. Released under the MIT License.',
        },
        mermaid: {
            theme: {
                light: 'neutral',
                dark: 'dark',
            },
        },
        prism: {
            theme: prismThemes.github,
            darkTheme: prismThemes.dracula,
            additionalLanguages: ['java', 'bash', 'groovy'],
        },
    } satisfies Preset.ThemeConfig,
};

export default config;
