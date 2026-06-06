# artemis-feature-model

Interactive feature model MVP for Artemis. Standalone Spring Boot + Angular
project that turns the WP1 functional feature catalog into a usable, constraint
aware exploration and configuration tool.

## Status

This repository has completed the guided Configurator MVP: the Angular
configurator now combines use-case templates, guided feature decisions, review
summaries, immediate validation feedback, an in-configurator tree view, and a
first-run tutorial.

Current server capabilities:

- `GET /api/feature-model` returns model metadata, source features, source
  relations, source constraints, the derived tree, server-derived default
  selected feature ids, and model warnings.
- `POST /api/feature-model/validate` validates submitted feature selections.
- `GET /api/feature-model/guided-workflow` returns the guided workflow
  metadata, use-case templates, decision steps, decision options, and review
  groups used by the Configurator.
- The server loads the runtime classpath JSON through `FeatureModelStore`.
- The guided workflow is loaded from the runtime classpath JSON through the
  selection service boundary.
- Model integrity checks, tree derivation, default selection derivation,
  mandatory hierarchy validation, unknown selected id reporting, and synthetic
  `requires`, `excludes`, and unsupported `expression` constraint handling are
  covered by server tests.

Current explorer capabilities:

- `/feature-model/explorer` loads `GET /api/feature-model` through a shared
  `FeatureModelService` and renders the 24-node feature tree with kind,
  relation, and default-state badges. A list view and a left-to-right SVG
  diagram view share expansion state.
- Branches can be expanded and collapsed individually, or in bulk via
  Expand all and Collapse all.
- A search box filters branches by feature id or name (case-insensitive) and
  preserves ancestor paths so matches stay in context.
- Selecting a feature opens a details panel that surfaces description,
  parent relation, default-selected status, and source metadata.
- Loading, error, empty-search, and model-warning states are handled.

Current configurator capabilities:

- `/feature-model/configurator` loads the feature model and guided workflow
  from the server.
- Use-case templates seed the selection. Custom configuration starts from the
  server-derived `defaultSelectedFeatureIds`, and default-on guided options are
  reflected as selected in the UI.
- Guided decision screens show option cards, selected-state badges, mapped
  feature chips, availability text, user-facing outcomes, recommendations,
  things-to-know notes, and warnings.
- `POST /api/feature-model/validate` is called after selection changes and a
  request token guards against stale responses.
- Valid/invalid status, detailed violations, detailed warnings, affected
  features, and suggestions are shown in the guided workflow.
- The review screen summarizes selected features, warnings, validation status,
  changed guided decisions, and the later artifact-generation handoff.
- The advanced tree stays inside the Configurator, reflects the current guided
  selection in real time, can directly update the selection, and surfaces
  technical capability details plus artifact mappings for advanced users.
- A first-run tutorial explains the Configurator, templates, feature selection,
  review, and tree view. Tutorial state is stored in browser `localStorage`
  using workflow and model version keys; the help button appears only in the
  guided workflow, not in tree mode.

## Prerequisites

- Java 25
- Node.js `>=24.7.0 <25`
- npm `>=11.5.1`

The Gradle wrapper handles its own Gradle distribution, so a system Gradle
install is optional.

## Repository layout

```
build.gradle                root Gradle build file
settings.gradle             Gradle project name
gradle/wrapper/             Gradle wrapper jar and properties
gradlew, gradlew.bat        Gradle wrapper launchers
package.json                Angular workspace and scripts
angular.json                Angular CLI workspace
tsconfig*.json              TypeScript configs
proxy.conf.json             dev-server proxy for /api -> :8080
src/main/java/...           Spring Boot server (Artemis-style package split)
src/main/resources/         application.yml and runtime feature-model JSON
src/main/webapp/            Angular 21 client (standalone components)
src/test/java/...           Server tests
```

## Server commands

```bash
./gradlew test              # run server unit tests
./gradlew test --rerun-tasks # force server unit tests to execute again
./gradlew bootRun           # start the server on http://localhost:8080
./gradlew bootJar           # build the Angular app and package one runnable jar
```

Gradle may report `:test UP-TO-DATE` when inputs have not changed. In that
case, the previous test result is reused. Use `--rerun-tasks` when you need to
force a fresh server test execution.

## Client commands

```bash
npm install                 # install Angular and tooling
npm run start               # start the dev server on http://localhost:9000
npm run build               # production-style build into build/webapp
npm run test                # run Angular unit tests (Vitest + jsdom)
```

The dev server proxies `/api/*` to the server on port 8080.

## Deployment build

The Spring Boot jar can serve the Angular production build from its static
resources. Running `./gradlew bootJar` installs frontend dependencies, runs the
Angular production build, and packages the generated `build/webapp/browser`
files into the jar.

For CI or Docker builds that already created `build/webapp/browser`, use:

```bash
./gradlew bootJar -PskipFrontendBuild=true
```

Build the local Docker image with:

```bash
docker build -t artemis-feature-model .
docker run --rm -p 8080:8080 artemis-feature-model
```

The repository CI workflow runs frontend tests, the frontend production build,
backend tests, and the Spring Boot jar build on every branch. It does not
deploy directly.

## Routes

- `/` redirects to `/feature-model/explorer`
- `/feature-model/explorer` — read-only feature model overview
- `/feature-model/configurator` — guided configurator with templates,
  workflow decisions, review, tutorial, and in-configurator tree view

Both routes are implemented. The configurator provides a guided workflow for
ordinary configuration and a tree view for direct model-level inspection and
selection.
