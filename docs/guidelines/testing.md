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