# artemis-feature-model

Interactive feature model MVP for Artemis. Standalone Spring Boot + Angular
project that turns the WP1 functional feature catalog into a usable, constraint
aware exploration and configuration tool.

## Status

This repository has completed Phase 3 of the MVP: the server feature-model API
is implemented and tested.

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

The Angular explorer and configurator pages are still placeholder pages. Their
full implementations are planned for later MVP phases.

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

## Routes

- `/` redirects to `/feature-model/explorer`
- `/feature-model/explorer` — read-only feature model overview (Phase 4)
- `/feature-model/configurator` — interactive configurator (Phase 5)

The routes are currently placeholder pages. Later client phases will connect
them to the Phase 3 server API without restructuring the app shell.
