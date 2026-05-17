# artemis-feature-model

Interactive feature model MVP for Artemis. Standalone Spring Boot + Angular
project that turns the WP1 functional feature catalog into a usable, constraint
aware exploration and configuration tool.

## Status

This repository has completed Phase 5 of the MVP: the Angular feature-model
configurator now loads defaults from the server, lets the user toggle
selectable modules in a tree diagram, and revalidates the selection on every
change.

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

- `/feature-model/configurator` renders the model as a tree diagram only —
  there is no list/outline view or view-mode toggle.
- Selection is seeded from `defaultSelectedFeatureIds` and initial validation
  runs as soon as the model loads.
- Clicking a selectable module node in the diagram or flipping the switch in
  the details panel toggles its selection; root and group nodes remain
  structural and focus-only.
- `POST /api/feature-model/validate` is called immediately after every toggle
  and a request-token guards against stale responses.
- Valid/invalid status, the full violation list (code, message, affected
  features, relation, suggestion), the full warning list, and inline diagram
  overlays for violated/warned features are surfaced together.
- Reset to defaults restores the server-derived defaults and revalidates.
- Search by feature id or name highlights and auto-expands matches in the
  diagram. Expand all / Collapse all also work in configurator mode.

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
- `/feature-model/explorer` — read-only feature model overview
- `/feature-model/configurator` — interactive diagram-only configurator with
  immediate validation

Both routes are implemented. The configurator represents the feature model
only as a tree diagram; it does not provide a list/outline view or a
view-mode toggle.
