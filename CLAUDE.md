# CLAUDE.md

This file provides guidance to Claude Code and other coding agents when working in this repository.

## Project Overview

`artemis-feature-model` is a standalone MVP for an interactive Artemis feature model. It turns a functional feature catalog into a constraint-aware exploration and configuration tool while staying close enough to Artemis conventions to support later migration.

Do not assume the main Artemis repository is available locally. This repository should build and run as a standalone project.

## Tech Stack

- Server: Spring Boot 4.0.x, Java 25
- Client: Angular 21, TypeScript 5.9, SCSS
- Build: Gradle wrapper, npm/Node 24
- Testing: JUnit via Spring Boot test support, Vitest for Angular tests
- Current styling baseline: Bootstrap-compatible SCSS
- Runtime model: classpath JSON resource

This MVP does not use a database, Liquibase, authentication, authorization, Docker, Helm, or the Artemis runtime.

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
- `export` is reserved for later deployment/configuration artifact generation.
- `shared` is only for truly shared exceptions, constants, and small utilities.

Client areas:

- `src/main/webapp/app/app.*` contains the application shell and routes.
- `src/main/webapp/app/feature-model/core` is for shared feature-model interfaces and pure helpers.
- `src/main/webapp/app/feature-model/api` is for Angular API services.
- `src/main/webapp/app/feature-model/explorer` is for the read-only explorer route.
- `src/main/webapp/app/feature-model/configurator` is for the interactive configurator route.
- `src/main/webapp/app/feature-model/validation` is for validation status and violation UI.

## Source Model Rules

- Runtime resource: `src/main/resources/feature-model/functional-feature-model.json`

Do not duplicate the feature model in client code. The server loads the runtime classpath copy and exposes it through APIs.

## Java Conventions

Follow Artemis-style Java conventions where they fit this standalone MVP:

- Use package-by-feature organization.
- Do not create global top-level `dto`, `repository`, `service`, `web`, or `storage` packages.
- Use 4-space indentation.
- Use PascalCase for classes and camelCase for fields and methods.
- Avoid wildcard imports.
- Prefer constructor injection for Spring beans.
- Use Java records for DTOs and immutable value objects when practical.
- Keep REST resources thin; delegate behavior to services.
- Keep domain logic in `catalog`, `validation`, `visualization`, `selection`, or `export`, not in `shared`.
- Do not introduce JPA, repositories backed by a database, Liquibase, Spring Security, or user/session persistence unless a later phase explicitly requires it.
- Do not inject `EntityManager` or `EntityManagerFactory`.
- Use controlled exceptions for model loading and integrity failures instead of raw parser errors leaking to API callers.
- Keep server services dependent on `FeatureModelStore`, not directly on JSON files.
- Prefer intention-revealing method and variable names over terse names.
- Keep methods small and focused on one action. Split validation, mapping, warning creation, and message construction into named helper methods when a method starts doing multiple things.
- Avoid long stream/lambda chains when they make control flow hard to read. Use clear local variables and ordinary loops for multi-step validation or tree traversal logic.
- Avoid horizontally long lines and deeply chained assertions or method calls. Break complex statements into named local variables.
- Define constants for repeated literal values. Use `private static final` by default and expose constants only when another class genuinely needs them.
- Use the least possible access level for fields, methods, and constants. Increase visibility only when there is a concrete caller outside the class.
- Keep methods ordered from higher-level behavior to lower-level helpers, following the order in which they are used where practical.
- Add Javadoc for server-side methods and constructors. Each Javadoc should include a short description, `@param` for every parameter, `@return` for non-void methods, and `@throws` for exceptions the method can throw.
- Comments and Javadocs must be in English and should clarify intent or non-obvious behavior. Do not add comments that merely repeat the code.
- For the feature-model server, document structural validation assumptions, such as root and group nodes being active paths even though users cannot toggle them.
- Keep DTO conversion methods explicit and local to the owning DTO. Do not expose domain records directly from REST resources.

## TypeScript and Angular Conventions

Follow current Artemis client direction as much as possible:

- Use standalone Angular components.
- Use lazy loading for route-level features so the initial bundle stays small.
- Use `ChangeDetectionStrategy.OnPush`.
- Use kebab-case filenames.
- Use PascalCase for classes, types, and enums.
- Do not prefix interfaces with `I`.
- Use camelCase for functions, properties, and local variables.
- Use SCREAMING_SNAKE_CASE for constants.
- Do not prefix private members with `_`.
- Use descriptive whole words instead of terse abbreviations.
- Use 4-space indentation and single quotes.
- Prefer `inject()` over constructor injection.
- Prefer signal-based APIs for new code:
  - `input()` / `input.required()` instead of `@Input()`
  - `output()` instead of `@Output()`
  - `viewChild()` / `viewChild.required()` instead of `@ViewChild()`
  - `viewChildren()` instead of `@ViewChildren()`
  - `signal()`, `computed()`, and `effect()` for component state
- Do not add legacy decorators such as `@Input`, `@Output`, `@ViewChild`, `@ViewChildren`, `@ContentChild`, or `@ContentChildren` in new code.
- Use Angular template control flow:
  - `@if`
  - `@for`
  - `@switch`
- Do not use new `*ngIf`, `*ngFor`, or `*ngSwitch` in new templates.
- Do not call component methods or getters from templates. Signals are the exception. Precompute values with `computed()` or assign view-ready state before rendering.
- Avoid `null` where `undefined` works.
- Prefer interfaces over type aliases whenever an interface can express the shape.
- Put local type definitions near the top of the file.
- Do not export types, constants, or helpers unless another file genuinely needs them.
- Do not introduce global types or global values.
- Do not use anonymous object shapes for meaningful data. Define named interfaces.
- Never use `any`; keep client code strictly typed.
- Treat objects and arrays as immutable outside the component or helper that created them.
- Keep API calls in feature-model API services, not directly in route components.
- Keep validation display components separate from validation service logic.
- Use buttons for actions and links for navigation.
- Associate labels with form controls using `for`/`id` or a wrapped input.
- Keep route paths explicit: every variable segment should be preceded by a unique textual segment.
- Use arrow functions instead of anonymous function expressions, and always wrap arrow parameters in parentheses.
- Always use curly braces for loop and conditional bodies except for same-line statements.

This MVP scaffold currently uses Bootstrap-compatible SCSS. Use Bootstrap utilities sparingly and do not add `ng-bootstrap`, FontAwesome, PrimeNG, or another component library until a project plan explicitly calls for it.

### Client Styling and Theming

- Use SCSS for component styles.
- Prefer component-local styles over broad global styles.
- Use BEM-style class structure for custom component CSS where it improves readability.
- Do not hard-code color values in component styles, templates, or TypeScript.
- Prefer theme-aware defaults, Bootstrap utility classes, or CSS variables such as `var(--bs-body-color)` and `var(--bs-body-bg)`.
- If a custom color is unavoidable, define a named CSS variable and provide theme-aware values in the appropriate global theme file.
- Verify UI changes in light and dark themes once theme support exists.
- Do not use `::ng-deep`.
- Use responsive layouts that adapt to small screens. Prefer a `.container` or deliberate responsive layout over brittle nested grid wrappers.

## API and Server Design Conventions

- Public MVP API routes live under `/api/feature-model`.
- `GET /api/feature-model` returns the loaded model, derived tree, default selected feature ids, and warnings.
- `POST /api/feature-model/validate` validates a submitted selection.
- Store abstraction belongs in `catalog.repository`.
- JSON-backed loading belongs in `catalog.repository`.
- API DTOs belong in the owning module's `dto` package.
- Tree DTOs belong in `visualization.dto`.
- Validation request and result DTOs belong in `validation.dto`.
- Do not expose internal domain records directly from web resources if a REST DTO is more stable.

## Testing Guidelines

Server:

- Use `./gradlew test`.
- Use `./gradlew test --rerun-tasks` when you need to prove tests executed instead of relying on Gradle's `UP-TO-DATE` result.
- Server tests must not require Docker or a database.
- Name server tests `*Test.java`.
- Prefer focused unit tests for services.
- Use Spring tests only for application context and web/API contract coverage.
- Build synthetic in-memory feature models for invalid cases. Do not mutate the runtime JSON in tests.
- Check `build/test-results/test/TEST-*.xml` when you need exact server test class counts and pass/fail evidence.

Client:

- Use `npm run test`.
- Prefer Vitest for new tests.
- Use `vi.fn()`, `vi.spyOn()`, `vi.clearAllMocks()`, and `vi.restoreAllMocks()` instead of Jest APIs.
- Keep tests co-located with Angular components or services.
- Test components in isolation. Do not import broad production modules when stubs or focused providers are enough.
- Do not use `NO_ERRORS_SCHEMA`; stub or mock child components, pipes, and directives instead.
- Do not use `overrideTemplate()` to hide a component template. The template is part of the behavior under test.
- Mock services that only fetch server data. For services with logic, keep the real service and mock HTTP requests with Angular HTTP testing utilities.
- Reset mocks in `afterEach` when they are created across tests.
- Prefer user-interaction tests over direct tests of internal component methods.
- Make expectations specific, for example `toBeUndefined()`, `toHaveLength(3)`, or `toHaveBeenCalledOnce()` instead of broad truthiness checks.
- Run `npm run build` after client-relevant changes.

Before handing off code changes, prefer:

```bash
./gradlew test
npm run test
npm run build
```

If a command cannot run, document the exact command and reason in the handoff.

## Version Control Guidelines

- Use `feature/...` branches for implementation phases.
- Keep commits small and reviewable.
- Use concise imperative commit messages, scoped where useful.
- Commit Gradle wrapper files and npm lock files.
- Do not hand-edit `package-lock.json` unless resolving a targeted lockfile issue.
- Do not commit generated build outputs, `node_modules`, `.angular`, or local IDE files.
- Do not revert user changes unless the user explicitly asks.

Recommended commit message examples:

```text
chore: add server feature model store
test: cover mandatory feature validation
docs: add phase 3 server api plan
```
