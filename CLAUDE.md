# CLAUDE.md

This file provides guidance to Claude Code and other coding agents when working in this repository.

## Project Overview

`artemis-feature-model` is a standalone MVP for an interactive Artemis feature model. It turns a functional feature catalog into a constraint-aware exploration and configuration tool.

## Tech Stack

- Server: Spring Boot 4.0.x, Java 25
- Client: Angular 21, TypeScript 5.9, SCSS
- Build: Gradle wrapper, npm/Node 24
- Testing: JUnit via Spring Boot test support, Vitest for Angular tests
- Current styling baseline: Bootstrap-compatible SCSS
- Runtime model: classpath JSON resource

This MVP does not use a database, Liquibase, authentication, authorization, Helm, or the Artemis runtime.

## Current Project State

- The backend exposes `GET /api/feature-model`,
  `POST /api/feature-model/validate`, `GET /api/feature-model/guided-workflow`,
  local snapshot endpoints under `/api/feature-model/snapshots`, deployment
  profile endpoints (`GET /api/deployment-profiles`,
  `GET /api/deployment-profiles/{id}`), profile-aware availability
  (`GET /api/feature-model/profile-availability`), Level 1 configuration
  artifact download (`POST /api/feature-model/artifacts/download`), and local
  runtime deployment package endpoints
  (`POST /api/feature-model/deployment-package/preview` and `/download`).
- The Explorer route is read-only and supports tree/list inspection,
  filtering, expansion controls, and feature details.
- The Configurator route is a guided workflow. It supports use-case templates,
  guided decision cards, immediate validation, detailed violation and warning
  feedback, review summaries, a first-run tutorial, and an in-configurator
  tree view.
- The regular guided workflow should use user-facing outcome, recommendation,
  availability, and things-to-know text. Keep technical capability ids and
  artifact mappings in the advanced tree view.
- Deployment Profiles are JSON files; one bundled `default-artemis-profile`
  provides all capabilities and drives feature/option availability. The regular
  Configurator does not expose a profile selector; capability gating is a latent
  safety net for maintainer local overrides. Keep raw capability ids and
  missing-capability details in advanced tree/debug views.
- Custom configuration starts from backend-derived `defaultSelectedFeatureIds`;
  default-on guided options should be reflected as selected in the UI.
- The in-configurator tree reflects guided selections in real time and can
  directly update the selection.
- Artifact and deployment-package generation are implemented. A valid selection
  yields a downloadable ZIP: a Spring configuration overlay
  (`application-feature-model.yml`), an `.env.example`, selected-feature and
  deployment-profile metadata, and a generation report (Phase 5 Level 1); the
  deployment package adds a Docker Compose override, helper scripts, a package
  manifest, and runtime checks for local validation (Phase 6 Layer 1). The
  review page generates and downloads directly; there is no preview step.
  Generation is DEMO-mode only and never writes plaintext secrets — secret
  values appear solely as `${VARIABLE}` placeholders.
- The deployment package supports an export-time deployment-mode axis (D1+D2):
  `ArtifactGenerationRequest` takes an optional `deploymentMode` (stable string
  ids in `DeploymentModes`: `local-docker`, `dev-ide`); an omitted mode keeps
  the local Docker package byte-identical to the pre-axis output (fixture
  test), while an explicitly chosen mode is recorded in the package manifest.
  Profiles may declare `supportedDeploymentModes` (absent = all; unknown
  entries warn, never fail loading), and an unsupported or unknown mode yields
  a controlled 400. `DeploymentPackageService` composes packages per mode from
  shared artifacts; the `dev-ide` mode emits the Level 1 overlay files plus a
  deterministic IntelliJ run configuration whose `ACTIVE_PROFILES` are derived
  from the selection (`ActiveProfilesDeriver`: the localci/localvc/buildagent
  family iff programming or hyperion is selected) and a developer README. The
  profile order mirrors the run configurations Artemis ships and is semantic
  (buildagent must precede core, or the buildagent config excludes the
  JPA/DataSource auto-configuration and startup fails); an extra
  `feature-model` profile makes Spring load the overlay directly once it is
  copied under its original name into the checkout's config directory, with
  the developer's `application-local.yml` keeping final precedence. A
  `feature-model-demo` profile loads generated demo defaults for the overlay's
  `${VARIABLE}` placeholders — the dev-ide counterpart of `env/.env.demo` —
  so a DEMO run starts without manual environment setup; real environment
  variables override the dummies. The review page offers a deployment-target picker;
  the guided workflow itself has no deployment decisions. Artifact mappings
  only reach the overlay when they target `application-feature-model.yml`.
- The generated overlay is statically validated against a curated Artemis config
  key catalog (`src/main/resources/feature-model/artemis-config-key-catalog.json`):
  unknown keys and value-type mismatches are reported without booting Artemis.
  The verdict ships as `metadata/static-config-validation.json`, a
  `static-config-keys` runtime check, and a `validate-package.sh` gate, and runs
  as a branch-scoped CI workflow. A drift-guard test fails when a model mapping
  path is missing from the catalog, so the catalog must be refreshed when
  Artemis config keys change.
- `./gradlew extractFeatureModel -PartemisPath=<artemis-checkout>` scans a local
  Artemis checkout read-only (no Spring context, no user-reachable trigger) and
  writes feature candidates, evidence, relation candidates, and a drift report
  against the active curated model under `build/feature-extraction/<commit>/`.
  The bundled scope manifest classifies every current candidate as include or
  exclude and adds curation counts and decisions to the report; unlisted
  candidates remain pending and never enter a model. Source-parsed
  `@ArtemisFeature` semantics override manifest-entry semantics but never grant
  membership. Override the relocatable manifest input with
  `-PfeatureManifestPath=<manifest.yml>`. Outputs are deterministic apart from
  scan-metadata timestamps; the drift report replaces the discovery step of
  the manual weekly consistency audit.
- The extraction run additionally assembles a complete generated feature model
  from the manifest's include entries and conceptual nodes — including the
  first technical subtree (`database` mysql/postgresql and `ci-provider`
  integrated-code-lifecycle/jenkins as maintainer-only xor groups plus the
  mandatory `localvc` baseline, enforced through `alternative` group relations
  and `excludes` constraints) — regenerates the Artemis config-key catalog
  from the scanned YAML defaults, validates model and bundled workflow through
  the shared loader/integrity/diagnostics code paths, and classifies every
  generated-versus-curated difference as `intentional-curation`,
  `missing-manifest-entry`, `artemis-drift`, or `extractor-gap`. It also
  emits a `snapshot/` folder importable via the snapshot API; the curated
  bundled model stays canonical and the generated model remains a parallel
  artifact. `StaticConfigValidationService` accepts an explicitly selected
  generated catalog via `featuremodel.static-validation.catalog-location`;
  the curated catalog remains the default.
- The authored `guided-workflow.json` is lean: decision structure and teacher
  prose only. Model-owned wiring — option `requiresCapabilities` and
  `artifactImpacts`, the workflow's feature model pin, and review group
  members (now referenced by `groupNodeId`) — is derived at serve time by
  `GuidedWorkflowAssembler`, so the served DTO shape is unchanged and
  capabilities are single-source on model features.
  `GuidedWorkflowDiagnosticsService` surfaces coverage, capability-validity,
  template-consistency, and stub-prose findings as logged warnings in the app
  and as `guided-workflow-validation.json` (with an automation `status`) in
  the extraction output; hard reference errors still fail hard.
  `./gradlew syncGuidedWorkflowScaffold` is a deliberate maintainer task that
  stubs newly included features with TODO prose, flags orphans without
  deleting, and leaves an already-covered workflow byte-identical.

## Build and Development Commands

Run commands from the repository root:

```bash
./gradlew test                    # server tests
./gradlew bootRun                 # server on http://localhost:8080

npm install                       # install client dependencies
npm run start                     # Angular dev server on http://localhost:9000
npm run build                     # Angular build into build/webapp
npm run test                      # Angular unit tests with Vitest
```

The Angular dev server proxies `/api/**` to `http://localhost:8080`.

## Project Structure

```text
build.gradle
settings.gradle
gradle.properties
gradlew
gradlew.bat
package.json
package-lock.json
angular.json
tsconfig.json
tsconfig.app.json
tsconfig.spec.json
proxy.conf.json
src/main/java/de/tum/cit/aet/artemis/featuremodel/
src/main/resources/
src/main/webapp/
src/test/java/de/tum/cit/aet/artemis/featuremodel/
```

Server package areas:

- `catalog` owns the feature model catalog, source model records, storage boundary, JSON-backed store, and model metadata.
- `validation` owns model and selection validation.
- `visualization` owns derived tree/read-model structures.
- `selection` owns user selection concepts and future selection sessions.
- `snapshot` owns local feature model snapshot listing, import, and export.
- `deployment` owns deployment profiles, profile loading, and capability resolution.
- `export` owns Level 1 configuration artifact generation, the Level 2 local
  runtime deployment package, and static overlay validation against the Artemis
  config key catalog.
- `extraction` owns the read-only Artemis checkout scan: anchor extractors,
  candidate assembly with evidence, relation candidates, the drift comparison
  against the active curated model, and the deterministic output writers.
- `shared` is only for truly shared exceptions, constants, and small utilities.

Client areas:

- `src/main/webapp/app/app.*` contains the application shell and routes.
- `src/main/webapp/app/feature-model/core` is for shared feature-model interfaces and pure helpers.
- `src/main/webapp/app/feature-model/api` is for Angular API services.
- `src/main/webapp/app/feature-model/explorer` is for the read-only explorer route.
- `src/main/webapp/app/feature-model/configurator` is for the interactive configurator route.
  - `guided/` contains the guided workflow UI.
  - `guided/tutorial/` contains the tutorial panel UI.
  - `tree/` contains the in-configurator tree view.
  - `shared/` contains configurator-local types and selection helpers.
- `src/main/webapp/app/feature-model/validation` is for validation status and violation UI.

## Source Model Rules

- Runtime resource: `src/main/resources/feature-model/functional-feature-model.json`

Do not duplicate the feature model in client code. The server loads the runtime classpath copy and exposes it through APIs.

## Behavioral Guidelines

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

## Guidelines and Conventions

Detailed project guidelines are maintained in `docs/guidelines/`. Treat those
files as the source of truth and avoid duplicating their contents here.

- [Java Conventions](docs/guidelines/java.md)
- [TypeScript and Angular Conventions](docs/guidelines/typescript-angular.md)
- [Client Styling and Theming](docs/guidelines/client-styling-theming.md)
- [API and Server Design Conventions](docs/guidelines/server-design.md)
- [Testing Guidelines](docs/guidelines/testing.md)
- [Version Control Guidelines](docs/guidelines/version-control.md)

When a change touches one of these areas, read the corresponding guideline
before editing code. Update the guideline document itself when a convention
changes; keep `CLAUDE.md` as the project overview and navigation entry point.
