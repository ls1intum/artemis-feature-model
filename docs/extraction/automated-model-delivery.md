# Automated Feature Model Delivery

The generated snapshot is the canonical delivery artifact. The classpath fixture is a provenance-tracked copy of the
last delivered generated bundle; a published image embeds and activates one complete validated snapshot and cannot
fall back to it.

Source identity is derived, never pinned in the manifest: every extraction command resolves the Artemis checkout,
requires a clean git work tree, derives the source revision from `HEAD`, and keys every artifact layout, envelope, and
report by that derived revision. CI and publication additionally verify the derived revision against an externally
supplied expectation through `-PexpectedArtemisSha` — the committed validation pin for pull requests, an explicitly
resolved SHA for publication runs. Manifest v3 carries curation content only.

## Manifest resolution modes

`featureManifestSource` (committed default in `gradle.properties`: `repository`) selects where the manifest bytes come
from:

- `repository` — the committed in-repo manifest at
  `src/main/resources/feature-model/extraction/artemis-feature-manifest.yml`. If the Artemis checkout also contains a
  manifest at the canonical path, its bytes must be identical or the run fails; this guards the overlap window between
  the upstream file landing and the cutover.
- `checkout` — the manifest at `supportingFiles/feature-model/artemis-feature-manifest.yml` inside the Artemis
  checkout; a missing file is a hard error naming that path.

`-PfeatureManifestPath` remains a test-and-migration override of the repository-mode path. Snapshot provenance records
the active mode in `manifestSource`.

## Delivery configuration

- `delivery/artemis-runtime-image.json` — the remote Artemis runtime image reference (`image` plus `digest`) consumed
  at snapshot packaging time. The goal state is a pinned `sha256:` digest; the mutable `latest` rendering stays
  accepted during migration. The pin is bumped by a maintainer; binding it to Artemis SHAs is out of scope.
- `delivery/artemis-validation-pin` — the immutable Artemis SHA that feature-model PR CI checks out and validates
  against, bumped by the delivery auto-PR so it tracks the last commit this repository successfully delivered against.
- `delivery/verified-images.json` — the verified-image ledger: every promotion appends
  `{digest, snapshotId, artemisCommit, manifestDigest, sourceCommit, promotedBy, runId}` through an automated pull
  request. The ledger supersedes the formerly documented `LAST_VERIFIED_IMAGE_DIGEST` Environment variable, which was
  never maintained.

## GitHub workflows

`model-delivery-validation.yml` is the shared, read-only gate used by normal CI and publication. It resolves the
validation target — the `artemis_sha` workflow-call input when supplied, the committed validation pin otherwise —
checks out Artemis at exactly that commit, verifies the checkout is exact and clean, runs `buildFeatureModelSnapshot`
with the expected SHA, uploads HTML/raw reports (even on failure), the validated snapshot, and the smoke-tested image,
and exports every delivery identity as outputs. Reruns of an unchanged pull request validate the identical Artemis
commit.

`publish-snapshot-image.yml` publishes the smoke-tested image under its content-addressed discovery tag
`ghcr.io/ls1intum/artemis-feature-model:<snapshot-id>` (`generated-<artemisSha12>-<manifestDigest12>`). It is invoked
by the poller or manually with a required immutable `artemis_sha`, short-circuits when the same identity from the same
source commit is already published (compared via OCI labels), serializes on a concurrency group keyed by snapshot id,
attests build provenance to the registry, and proves the anonymous digest pull. Tags are mutable discovery pointers;
consumers must identify an image as `ghcr.io/ls1intum/artemis-feature-model@sha256:<registry-digest>`. `latest` is
never published or moved.

`promote-image.yml` (manual dispatch; inputs `image_digest` required, `expected_snapshot_id` optional) inspects the
published digest, fails on an absent digest or malformed/mismatched OCI identity labels, retags it as `verified`
without rebuilding, proves the `verified` tag resolves to the input digest, and appends the promotion to the ledger
via an automated pull request. **Rollback is this same workflow pointed at a prior ledger entry's digest** — no
rebuild, no source checkout, no special path. Promotion does not deploy anything; deployment and post-deploy
verification remain deferred.

`poll-artemis-delivery.yml` closes the loop (Stage 1, no Artemis-side cooperation needed): on a schedule or manual
dispatch it resolves the tracking ref (`refs/heads/develop` of `ls1intum/Artemis`) to one SHA at job start — an
explicit `artemis_sha` input skips resolution — short-circuits on an already-published identity, calls the reusable
publication workflow, and opens the delivery auto-PR containing the classpath fixture refresh
(`refreshFeatureModelFixture`, committed only when bytes changed), the validation-pin bump, and the guided-workflow
coverage summary listing selectable features that lack a published option. Until the cutover the poller builds newly
resolved Artemis commits against the in-repo manifest, so curation drift surfaces as a fail-closed run — the correct
signal that the manifest needs updating.

**Stage 2 input contract** (Artemis-side implementation out of scope): an Artemis-side trigger invokes
`repository_dispatch` on this repository with event type `artemis-commit` and client payload
`{"artemis_sha": "<full-40-hex-sha>"}`. The payload enters the same explicit-SHA path as a manual dispatch; the
scheduled poller remains as a fallback.

`tag-retention.yml` deletes discovery tags beyond the most recent ten; digests referenced by the ledger or tagged
`verified` are never deleted.

The former `deployment/image-publish-test` branch ritual and its run-scoped `test-<sha>-<attempt>` tags are retired;
old tags are left to expire under retention.

## Environments and permissions (owner-configured)

Only publication and promotion jobs hold `packages: write`; pull-request and validation jobs keep `contents: read` and
no registry or deployment identity. The publication job runs in the `image-publish` Environment and the promotion job
in the `image-promote` Environment. GitHub auto-creates a referenced Environment without protection rules; the
restrictions below need repository-owner permissions and must be configured by the owner (they are documented here,
not applied by automation):

- `image-publish`: allow only the development branch (and, during phase verification, the phase branch); no required
  reviewers so the poller can publish unattended.
- `image-promote`: allow the same branches and add required reviewers if promotion should be a two-person action.
- The retired `image-publish-test` Environment can be deleted together with the `deployment/image-publish-test`
  branch.
- Deleting stale package versions requires granting this repository's Actions admin access on the
  `artemis-feature-model` container package (package settings → manage Actions access).
- Creating pull requests from workflows requires "Allow GitHub Actions to create and approve pull requests" in the
  repository (or organization) Actions settings.

## Local reproduction

Use a disposable, clean Artemis checkout. Do not reset or clean a developer checkout.

```bash
./gradlew featureModelManifestPreflight -PartemisPath=<clean-artemis-checkout>
./gradlew test --rerun-tasks
npm run test -- --watch=false
npm run build:prod
./gradlew buildFeatureModelSnapshot -PartemisPath=<clean-artemis-checkout>
./gradlew validateFeatureModelSnapshot \
  -PsnapshotPath=build/feature-extraction/<derived-source-revision>/snapshot
./gradlew stageFeatureModelDockerContext \
  -PsnapshotPath=build/feature-extraction/<derived-source-revision>/snapshot
scripts/build-snapshot-image.sh \
  build/docker/feature-model-snapshot artemis-feature-model:snapshot-local
scripts/verify-snapshot-image.sh \
  artemis-feature-model:snapshot-local build/docker/feature-model-snapshot
```

The derived source revision is the checkout's `HEAD`, printed by `featureModelManifestPreflight` as
`artemisCommitSha`. Interpret `report/index.html` first. A non-zero workflow result is ineligible for snapshot/image
publication; do not bypass it by editing generated output.

## Maintainer flows

**Deliver a newer Artemis commit.** Nothing to edit: dispatch the poller with the explicit SHA (or let the schedule
resolve the tracking ref). A conformance failure means the manifest no longer describes that commit — update the
in-repo manifest's curation entries until the run passes. There is no commit pin to advance; never point production
generation at a tag, an abbreviated SHA, or a dirty checkout.

**Verify a published image.** Promote its digest with `promote-image.yml`; the ledger PR records it. Roll back by
promoting the previous ledger entry's digest.

**Change guided prose or curation.** Ordinary pull requests; PR CI validates against the committed validation pin
deterministically.

## Cutover checklist (documented only — NOT executed; `featureManifestSource` stays `repository`)

The Artemis-side landing is a config-only cutover executable at any time. Its only upstream deliverable is the proven
manifest file existing at the canonical path inside an Artemis commit.

1. **Upstream file-add PR**: add the byte-for-byte in-repo manifest as
   `supportingFiles/feature-model/artemis-feature-manifest.yml` in `ls1intum/Artemis`. Mark the file `-text` in
   `.gitattributes` (or accept the normalized bytes as the new baseline): if git normalizes its line endings, the
   committed bytes — and hence the manifest digest and every snapshot id — differ from the local copy.
   Acceptance command against the PR branch: `./gradlew featureModelManifestPreflight -PartemisPath=<pr-checkout>
   -PfeatureManifestSource=checkout` — green output proves the contract. Record the merged Artemis commit `X`.
2. **Flip commit** in this repository, as one reviewable change:
   - set `featureManifestSource=checkout` in `gradle.properties`;
   - delete `src/main/resources/feature-model/extraction/artemis-feature-manifest.yml`;
   - bump `delivery/artemis-validation-pin` to `X`.
3. **Flip PR CI** runs the pipeline in `checkout` mode end to end against the pin; green means plugged. The flip
   changes configuration values and deletes one file — no schema, payload, or workflow shape changes; provenance
   `manifestSource` starts reading `checkout`.
4. After the flip, manifest edits move into Artemis PRs (feature and manifest change atomically) and the overlap-window
   guard becomes unreachable. The scaffold task reads the manifest from `-PartemisPath`.

Until the cutover is executed, the in-repo manifest stays present and authoritative, and the two §14 acceptance
criteria that structurally require the executed cutover (co-located manifest as the single source; no in-repo
manifest) remain open by design.
