# artemis-feature-model

Interactive feature model MVP for Artemis. Standalone Spring Boot + Angular
project that turns the WP1 functional feature catalog into a usable, constraint
aware exploration and configuration tool.

## Status

This repository has completed the guided Configurator MVP: the Angular
configurator combines use-case templates, guided feature decisions, review
summaries, immediate validation feedback, an in-configurator tree view, and a
first-run tutorial.

On top of that, a valid selection can be exported. The server generates Level 1
configuration artifacts (a Spring configuration overlay, an `.env.example`,
selected-feature and deployment-profile metadata, and a generation report) and a
Level 2 local runtime deployment package that adds a Docker Compose override,
helper scripts, a package manifest, and runtime checks. The generated overlay is
statically validated against a curated Artemis configuration key catalog, so
unknown keys and value-type mismatches are caught without booting Artemis.
Generation is DEMO-mode only and never writes plaintext secrets; secret values
appear solely as `${VARIABLE}` placeholders.

Current server capabilities:

- `GET /api/feature-model` returns model metadata, source features, source
  relations, source constraints, the derived tree, server-derived default
  selected feature ids, and model warnings.
- `POST /api/feature-model/validate` validates submitted feature selections.
- `GET /api/feature-model/guided-workflow` returns the guided workflow
  metadata, use-case templates, decision steps, decision options, and review
  groups used by the Configurator.
- `GET /api/deployment-profiles` and `GET /api/deployment-profiles/{id}` return
  deployment profile summaries and detail; `GET /api/feature-model/profile-availability`
  returns profile-aware option and feature availability.
- `POST /api/feature-model/artifacts/download` returns the Level 1 configuration
  artifacts as a ZIP.
- `POST /api/feature-model/deployment-package/preview` and `/download` return
  the local runtime deployment package (Level 1 artifacts plus local-checkout
  and self-contained remote-image Compose stacks, helper scripts, a package
  manifest, runtime checks, and a static config validation report).
- Static overlay validation checks every generated key against the Artemis
  config key catalog (`src/main/resources/feature-model/artemis-config-key-catalog.json`),
  reporting unknown keys and value-type mismatches; a drift-guard test keeps the
  catalog in sync with the model's mapping paths.
- The server uses an explicit `classpath` or `snapshot` source mode. The model,
  guided workflow, and config-key catalog are loaded and validated as one
  process-stable bundle; snapshot mode validates the complete generated
  snapshot and never falls back to classpath resources.
- `GET /api/feature-model/provenance` returns safe active bundle identity.
  Legacy snapshot administration routes are absent unless classpath
  development explicitly enables them.
- Model integrity checks, tree derivation, default selection derivation,
  mandatory hierarchy validation, unknown selected id reporting, and synthetic
  `requires`, `excludes`, and unsupported `expression` constraint handling are
  covered by server tests.

Current explorer capabilities:

- `/feature-model/explorer` loads `GET /api/feature-model` through a shared
  `FeatureModelService` and renders the 33-node feature tree with kind,
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
  and changed guided decisions. From there a valid selection generates and
  downloads the Level 1 artifact ZIP directly (no preview step) and offers a
  separate download of the local runtime deployment package.
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

### Local Docker package host support

One generated `local-docker` package supports both `start-demo.sh /path/to/Artemis`
for a supplied checkout and argument-free `start-demo.sh` for the configured
remote Artemis image. The manifest records the source commit, image repository,
and original image digest. `latest` is rendered as a mutable image tag and is not
guaranteed to correspond to the recorded source commit; other non-empty values
are rendered as exact digest references.

The generated `local-docker` package supports Linux with Docker Engine and
macOS with Docker Desktop. On Windows, it supports Docker Desktop only through
a WSL2 distribution with WSL integration and Linux containers enabled; run the
generated Bash scripts from WSL. Native PowerShell, Command Prompt, Git Bash,
and Windows containers are not supported. The Integrated Code Lifecycle stack
requires `/var/run/docker.sock`; Docker Desktop Enhanced Container Isolation
therefore needs an explicit socket-mount exception for the Artemis image.

## Repository layout

```
build.gradle                root Gradle build file
settings.gradle             Gradle project name
gradle/wrapper/             Gradle wrapper jar and properties
gradlew, gradlew.bat        Gradle wrapper launchers
package.json                Angular workspace and scripts
angular.json                Angular CLI workspace
tsconfig*.json              TypeScript configs
proxy.conf.json             dev-server proxy for /api -> :8090
src/main/java/...           Spring Boot server (Artemis-style package split)
src/main/resources/         application.yml and runtime feature-model JSON
src/main/webapp/            Angular 21 client (standalone components)
src/test/java/...           Server tests
```

## Server commands

```bash
./gradlew test              # run server unit tests
./gradlew test --rerun-tasks # force server unit tests to execute again
./gradlew bootRun           # start the server on http://localhost:8090
./gradlew bootJar           # build the Angular app and package one runnable jar
```

Gradle may report `:test UP-TO-DATE` when inputs have not changed. In that
case, the previous test result is reused. Use `--rerun-tasks` when you need to
force a fresh server test execution.

## Client commands

```bash
npm install                 # install Angular and tooling
npm run start               # start the dev server on http://localhost:9090
npm run build               # production-style build into build/webapp
npm run test                # run Angular unit tests (Vitest + jsdom)
```

The dev server proxies `/api/*` to the server on port 8090. The ports deliberately avoid the Artemis dev defaults (8080/9000) so both applications can run side by side.

## Deployment build

The Spring Boot jar can serve the Angular production build from its static
resources. Running `./gradlew bootJar` installs frontend dependencies, runs the
Angular production build, and packages the generated `build/webapp/browser`
files into the jar.

For CI or Docker builds that already created `build/webapp/browser`, use:

```bash
./gradlew bootJar -PskipFrontendBuild=true
```

Local development defaults to the hand-maintained classpath bundle:

```bash
./gradlew bootRun
curl http://localhost:8090/api/feature-model/provenance
```

The legacy local snapshot administration API is disabled by default. A focused
classpath development session may opt in explicitly with
`./gradlew bootRun --args='--artemis.feature-model.snapshot-admin-api-enabled=true'`.
Snapshot mode never accepts this opt-in.

For a production-like image, first generate or select a complete snapshot,
validate it, and stage the controlled BuildKit named context:

```bash
./gradlew validateFeatureModelSnapshot \
  -PsnapshotPath=build/feature-extraction/<artemis-sha>/snapshot
./gradlew stageFeatureModelDockerContext \
  -PsnapshotPath=build/feature-extraction/<artemis-sha>/snapshot
scripts/build-snapshot-image.sh \
  build/docker/feature-model-snapshot \
  artemis-feature-model:snapshot-local
scripts/verify-snapshot-image.sh \
  artemis-feature-model:snapshot-local \
  build/docker/feature-model-snapshot
```

Run the verified image without a model volume:

```bash
docker run --rm --read-only --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  -p 8090:8080 \
  artemis-feature-model:snapshot-local
```

The image embeds exactly one read-only snapshot under
`/opt/artemis-feature-model/data/imported-models/<snapshot-id>/`, runs as uid
`10001`, and selects snapshot mode through explicit environment variables. The
container needs no data volume for normal operation. Image tags are convenient
local names; registry delivery in the next stage must use an immutable digest.

The repository delivery workflows build the frontend, resolve and check out the
manifest-pinned Artemis commit, build the strict generated snapshot,
upload HTML/raw reports, validate the snapshot offline, and smoke-test the
snapshot-bearing image. Pull requests and development branches have read-only
repository permission and cannot publish. Only a push to
`deployment/image-publish-test` may publish the already-tested `linux/amd64`
image to public GHCR, where its registry digest is the authoritative identity;
no workflow publishes `latest` or deploys the image. See
[`docs/extraction/automated-model-delivery.md`](docs/extraction/automated-model-delivery.md)
for reproduction, manifest advancement, publication, and deferred rollback
semantics.

## Routes

- `/` redirects to `/feature-model/explorer`
- `/feature-model/explorer` — read-only feature model overview
- `/feature-model/configurator` — guided configurator with templates,
  workflow decisions, review, tutorial, and in-configurator tree view

Both routes are implemented. The configurator provides a guided workflow for
ordinary configuration and a tree view for direct model-level inspection and
selection.
