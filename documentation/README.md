# Documentation

This folder contains the documentation site of the Artemis Feature Model. It is built with
[Docusaurus](https://docusaurus.io/) and follows the structure and style of the
[Artemis documentation](https://docs.artemis.tum.de).

Run every command below in this folder. The site has its own `package.json` and lockfile and does not depend on the
Angular workspace in the repository root.

## Installation

```bash
npm ci
```

## Local development

```bash
npm run start
```

This starts a preview at `http://localhost:3000/artemis-feature-model/` that reloads when you save a file.

## Build

```bash
npm run typecheck
npm run build
npm run serve
```

`npm run build` writes the static site to `build/` and fails on broken links and anchors. `npm run serve` serves that
build locally. The site is configured for GitHub Pages, below the base path `/artemis-feature-model/`.

## Structure

- `docs/developer/`: pages of the Developer Guide, listed in `sidebar-developer.ts`
- `docs/maintainer/`: pages of the Maintainer Guide, listed in `sidebar-maintainer.ts`
- `src/pages/`: the homepage
- `src/components/`: components that pages can use, such as `Callout` and `Image`
- `src/theme/`: theme overrides that connect the navbar search button to the search dialog
- `src/css/custom.css`: colors and global styles

The writing rules are part of the site: see [Writing Documentation](docs/developer/guidelines/documentation.mdx).
