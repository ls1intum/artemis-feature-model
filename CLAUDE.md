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
  `GET /api/deployment-profiles/{id}`), and profile-aware availability
  (`GET /api/feature-model/profile-availability`).
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
- Artifact generation is still a later-phase placeholder. Do not add
  export/download behavior unless a plan explicitly asks for it.

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
- `export` is reserved for later deployment/configuration artifact generation.
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
