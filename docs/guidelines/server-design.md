## API and Server Design Conventions

- Public MVP API routes live under `/api/feature-model` and `/api/deployment-profiles`.
- `GET /api/feature-model` returns the loaded model, derived tree, default selected feature ids, and warnings.
- `POST /api/feature-model/validate` validates a submitted selection.
- `GET /api/feature-model/guided-workflow` returns the guided workflow
  metadata, templates, steps, decision options, and review groups.
- `GET /api/feature-model/provenance` exposes safe, read-only active runtime bundle identity.
- `/api/feature-model/snapshots...` lists, details, imports, and exports local snapshots only when the
  explicit classpath-development administration property is enabled; it is absent by default and in snapshot mode.
- `GET /api/deployment-profiles` and `GET /api/deployment-profiles/{id}` return deployment profile summaries and detail.
- `GET /api/feature-model/profile-availability` returns profile-aware option and feature availability; an optional `profileId` is for tests/maintainers, not the regular UI.
- Bootstrap profiles live in `src/main/resources/deployment-profiles`; local overrides under `<data-root>/deployment-profiles`.
- Store abstraction belongs in `catalog.repository`.
- JSON-backed loading belongs in `catalog.repository`.
- API DTOs belong in the owning module's `dto` package.
- Tree DTOs belong in `visualization.dto`.
- Validation request and result DTOs belong in `validation.dto`.
- Deployment profile and availability DTOs belong in `deployment.dto`.
- Do not expose internal domain records directly from web resources if a REST DTO is more stable.
