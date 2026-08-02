# Remote Artemis Image Support — Implementation Handoff

## Outcome

The existing `local-docker` package now contains both runtime paths:

- `bash scripts/start-demo.sh /path/to/Artemis` prepares the package environment and delegates to the existing
  local-checkout stack.
- `bash scripts/start-demo.sh` prepares the same environment and delegates to a self-contained remote-image stack.

No deployment mode, request field, profile field, frontend option, or Feature Model decision was added.

## Assumptions and deviations

- The repository has evolved beyond the class names named in the execution prompt. Metadata publication is currently
  owned by `PackageStageService` and `SnapshotPublisher`, so the implementation extends those current components.
- The current extraction manifest uses `artemisCommitSha`, not the older `verifiedAgainstArtemisCommit` root name.
  The new required `artemisImageDigest` sits beside the current commit field. Static catalog
  `verifiedAgainstArtemisCommit` semantics remain unchanged.
- A legacy model without technical mappings retains the established MySQL + Integrated Code Lifecycle runtime
  defaults. Current classpath and generated models provide explicit technical selections.
- No runtime smoke start was performed because the Docker host already contains the three fixed package volumes
  `artemis-feature-model-local-data`, `artemis-feature-model-local-mysqldata`, and
  `artemis-feature-model-local-postgresdata`. Starting the generated stack would reuse and mutate user-owned data.

## Runtime provenance

The resolved value is `ArtemisRuntimeSource(sourceCommit, imageRepository, imageDigest)`.

- Active snapshot: `sourceCommit` and `imageDigest` come exclusively from active snapshot `metadata.json`.
- No active snapshot: values come from `artemis.feature-model.runtime.source-commit` and
  `artemis.feature-model.runtime.image-digest` in `application.yml`.
- Repository: always `ghcr.io/ls1intum/artemis`.
- Missing active-snapshot values never fall back to classpath properties. Missing values block only local-docker
  generation and identify either the snapshot field or exact classpath property.
- Legacy snapshots still deserialize and remain browsable. `dev-ide` does not require an image digest.

Extraction writes the actual scanned checkout commit to snapshot `sourceCommit` and the manifest-authored
`artemisImageDigest` to snapshot `imageDigest`.

## Image reference behavior

- Original `imageDigest == latest`: `ghcr.io/ls1intum/artemis:latest`.
- Any other non-blank value: `ghcr.io/ls1intum/artemis@<imageDigest>`.

The manifest preserves the original value. Generation performs no registry query, digest resolution, or full digest
syntax validation. Generated documentation warns that `latest` is mutable and is not guaranteed to correspond to
`sourceCommit`.

## Package manifest and contents

Package format version is `2.0.0`. Local-docker manifests advertise:

```json
"supportedRuntimeModes": ["local-repo", "remote-image"]
```

The runtime block is:

```json
"artemisRuntime": {
  "sourceCommit": "...",
  "imageRepository": "ghcr.io/ls1intum/artemis",
  "imageDigest": "latest or sha256:...",
  "note": "..."
}
```

New package files are:

- `deployment/remote-image/artemis-feature-model-stack.yml`
- `scripts/start-remote-image.sh`
- `scripts/stop.sh`

The required-file validator and deterministic package fixture include all three.

## Remote Compose stack

`RemoteImageStackWriter` receives resolved runtime and technical values and directly declares:

- `artemis-app` using the resolved remote image reference;
- selected MySQL 9.7.0 or PostgreSQL 18.4-alpine service settings copied minimally from the inspected Artemis
  checkout;
- package env and generated Spring overlay;
- package-scoped network, application data volume, and selected database volume;
- PostgreSQL datasource URL and username when PostgreSQL is selected;
- exact ICL or Jenkins Spring profile order;
- LocalVC URL for the containerized server;
- Docker socket and `FM_DOCKER_GID` only for ICL.

The file contains no `FM_ARTEMIS_REPO`, checkout-relative `extends.file`, Git operation, or runtime file fetch.
Jenkins remains configuration/profiles only: no Jenkins service is generated, readiness remains false, the warning is
prominent, and `jenkins-stack-available` still fails deliberately.

## Script behavior

`start-demo.sh` is a short dispatcher:

- zero arguments: remote image;
- one checkout path: local repository;
- `-h` or `--help`: both forms, successful exit;
- more than one argument: concise usage error and failure.

`start-remote-image.sh` checks Docker, Compose v2, package env, and the generated remote stack, derives
`FM_DOCKER_GID` only for ICL, then runs Compose without a registry preflight. `stop.sh` addresses the stable
`artemis-feature-model-local` project without requiring a checkout and preserves volumes unless `--volumes` is
explicitly supplied.

## Files changed

- Runtime provenance: `SnapshotMetadata`, `LocalSnapshotRepository`, `FeatureScopeManifest`,
  `FeatureManifestLoader`, `PackageStageService`, `SnapshotPublisher`, `application.yml`, and the extraction manifest.
- Export domain/config: `ArtemisRuntimeSource`, `ArtemisRuntimeProperties`, `ArtemisRuntimeSourceResolver`,
  `DeploymentPackageManifest`, `RuntimePackageConstants`, and `ArtifactGenerationException`.
- Package generation: `DeploymentPackageService`, `RemoteImageStackWriter`, `RuntimeScriptWriter`, and
  `RuntimeTemplateWriter`.
- Documentation: repository `README.md` and this handoff.
- Tests/fixtures: resolver, remote writer, extraction/snapshot, package composition, technical matrix, web wiring,
  generated-model parity, and the deliberate package-format byte fixture re-baseline.

No frontend file changed.

## Verification

- `./gradlew test --rerun-tasks`: PASS, 364 tests, 7 conditionally skipped, 0 failures/errors.
- `./gradlew test --rerun-tasks -PartemisPath=/Users/juntingning/thesis/Repositories/Artemis`: PASS, 364 tests,
  0 skipped/failures/errors against `f4428ab20bab3595d4c51a420c2004bf0a6afbe3`.
- `./gradlew extractFeatureModel -PartemisPath=/Users/juntingning/thesis/Repositories/Artemis`: PASS; 73 candidates,
  900 evidence items, 3 relation candidates, 20 includes, 53 exclusions, 0 undeclared candidates, generated model
  33/32/3, generated catalog 33 keys, guided workflow validation PASS, and an importable snapshot with
  `sourceCommit=f4428ab20bab3595d4c51a420c2004bf0a6afbe3` plus `imageDigest=latest`.
- MySQL + ICL package generated through the running download API: PASS.
- PostgreSQL + ICL package generated through the running download API: PASS.
- `docker compose config --quiet` for the generated MySQL + ICL remote stack: PASS.
- `docker compose config --quiet` for the generated PostgreSQL + ICL remote stack: PASS.
- Generated manifest inspection: PASS; format `2.0.0`, both runtime modes, full source commit, official image
  repository, and original `latest` value were present.
- Remote stack inspection: PASS; both used `ghcr.io/ls1intum/artemis:latest`, selected datasource/image settings,
  Docker socket/GID behavior, and no local-checkout references.
- Runtime smoke start: NOT RUN to avoid mutating pre-existing user-owned fixed-name Docker volumes.
- The updated Artemis commit introduced `module:oidc` and `configkey:artemis.user-management.oidc.enabled`.
  Both are explicitly classified as deferred external user-management mechanisms, matching the existing LDAP,
  SAML2, and passkey scope decisions. The extraction conformance gate then passed with no undeclared candidates.

## Remaining risks and deferred work

- A real remote Artemis boot remains unverified in this Docker host. Repeat in an isolated Docker environment or
  after the user deliberately provides disposable package volumes.
- Linux and WSL2 socket/GID behavior is content-tested but was generated on macOS.
- Advance the extraction manifest pin only through its existing deliberate curation workflow and classify every new
  candidate explicitly.
- Moving the extraction manifest into Artemis remains deferred and was not implemented.
- A Jenkins container/service remains deferred pending the separate Jenkins Docker work.
