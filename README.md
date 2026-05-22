# artemis-feature-model

Interactive feature model MVP for Artemis. Standalone Spring Boot + Angular
project that turns the WP1 functional feature catalog into a usable, constraint
aware exploration and configuration tool.

## Status

This repository has completed Phase 4 of the MVP: the Angular feature-model
explorer now loads the server API and renders the model interactively.

Current server capabilities:

- `GET /api/feature-model` returns model metadata, source features, source
  relations, source constraints, the derived tree, server-derived default
  selected feature ids, and model warnings.
- `POST /api/feature-model/validate` validates submitted feature selections.
- The server loads the runtime classpath JSON through `FeatureModelStore`.
- Model integrity checks, tree derivation, default selection derivation,
  mandatory hierarchy validation, unknown selected id reporting, and synthetic
  `requires`, `excludes`, and unsupported `expression` constraint handling are
  covered by server tests.

Current explorer capabilities:

- `/feature-model/explorer` loads `GET /api/feature-model` through a shared
  `FeatureModelService` and renders the 24-node feature tree with kind,
  relation, and default-state badges.
- Branches can be expanded and collapsed individually, or in bulk via
  Expand all and Collapse all.
- A search box filters branches by feature id or name (case-insensitive) and
  preserves ancestor paths so matches stay in context.
- Selecting a feature opens a details panel that surfaces description,
  parent relation, default-selected status, and source metadata (config
  key, Spring profile, frontend constant, backend condition class, and
  evidence entries) when present.
- Loading, error, empty-search, and model-warning states are handled.

The configurator page is still a placeholder; its interactive selection and
validation behavior is planned for Phase 5.

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
- `/feature-model/configurator` — interactive configurator (Phase 5)

The explorer is implemented. The configurator remains a placeholder page;
Phase 5 will connect it to `POST /api/feature-model/validate` without
restructuring the app shell.
