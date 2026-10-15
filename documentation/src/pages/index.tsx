import type { ReactNode } from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';

import styles from './index.module.css';

interface Guide {
    title: string;
    description: string;
    to: string;
}

const GUIDES: Guide[] = [
    {
        title: 'Developer Guide',
        description: 'For people who change the code of this repository: how the project is built and the conventions it follows.',
        to: '/developer/intro',
    },
    {
        title: 'Maintainer Guide',
        description: 'For people who keep the feature model in line with Artemis and run the delivery workflows.',
        to: '/maintainer/intro',
    },
];

/**
 * The homepage: the site title, one sentence about the site, and one card per guide.
 */
export default function Home(): ReactNode {
    const { siteConfig } = useDocusaurusContext();

    return (
        <Layout description={siteConfig.tagline}>
            <main className="container">
                <header className={styles.header}>
                    <h1 className={styles.title}>{siteConfig.title}</h1>
                    <p className={styles.subtitle}>{siteConfig.tagline}</p>
                </header>
                <nav className={styles.guides} aria-label="Guides">
                    {GUIDES.map((guide) => (
                        <Link key={guide.to} to={guide.to} className={styles.guide}>
                            <h2 className={styles.guideTitle}>{guide.title}</h2>
                            <p className={styles.guideDescription}>{guide.description}</p>
                            <span className={styles.guideAction}>
                                Open the guide <span aria-hidden="true">→</span>
                            </span>
                        </Link>
                    ))}
                </nav>
            </main>
        </Layout>
    );
}
