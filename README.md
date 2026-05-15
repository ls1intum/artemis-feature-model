# artemis-feature-model

Interactive feature model MVP for Artemis. Standalone Spring Boot + Angular
project that turns the WP1 functional feature catalog into a usable, constraint
aware exploration and configuration tool.

## Status

This repository is currently on phase 2 of the MVP: the project scaffold.
All Phase 2 work happens on the `feature/bootstrap-project-structure` branch.

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
src/main/java/...           Spring Boot backend (Artemis-style package split)
src/main/resources/         application.yml and runtime feature-model JSON
src/main/webapp/            Angular 21 frontend (standalone components)
src/test/java/...           Backend tests
devdocs/                    MVP planning documents (gitignored locally)
```

## Backend commands

```bash
./gradlew test              # run backend unit tests
./gradlew bootRun           # start the backend on http://localhost:8080
```

## Frontend commands

```bash
npm install                 # install Angular and tooling
npm run start               # start the dev server on http://localhost:9000
npm run build               # production-style build into build/webapp
npm run test                # run Angular unit tests (Vitest + jsdom)
```

The dev server proxies `/api/*` to the backend on port 8080.

## Routes

- `/` redirects to `/feature-model/explorer`
- `/feature-model/explorer` — read-only feature model overview (Phase 4)
- `/feature-model/configurator` — interactive configurator (Phase 5)

Phase 2 ships the routes as placeholder pages so later phases can wire up the
backend and tree rendering without restructuring the app shell.
