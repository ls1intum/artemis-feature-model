# Automated Feature Model Delivery

The generated snapshot is the canonical delivery artifact. The hand-maintained classpath model, workflow, and
config-key catalog remain local-development fixtures; a published image embeds and activates one complete validated
snapshot and cannot fall back to them.

## GitHub workflow

`.github/workflows/model-delivery-validation.yml` is the shared, read-only gate used by normal CI and publication. It:

1. runs frontend tests/build and backend tests;
2. obtains the exact Artemis commit and manifest digest from `featureModelManifestPreflight`;
3. checks out that commit and verifies the checkout is exact and clean;
4. runs `buildFeatureModelSnapshot` without invoking `syncGuidedWorkflowScaffold`;
5. uploads `report/index.html`, extraction/conformance/workflow/release-delta JSON with `if: always()`;
6. validates the snapshot offline, stages its controlled Docker context, and builds with the default runtime base;
7. runs the provenance, read-only runtime, absent-admin-endpoint, missing-snapshot, and tampered-snapshot smoke checks;
8. uploads the smoke-tested image for the publication job only after every gate passes.

Normal CI runs for development-branch pushes and pull requests and also provides a read-only manual dispatch for
maintainer revalidation. Manual dispatch cannot enter the publication workflow or receive package-write permission.

`.github/workflows/publish-snapshot-image.yml` triggers only on a push to `deployment/image-publish-test`. Its
validation job still has only `contents: read`. A separate job uses the branch-restricted `image-publish-test` GitHub
Environment and receives only `contents: read` plus `packages: write`. It loads the exact smoke-tested image, publishes
`ghcr.io/ls1intum/artemis-feature-model:test-<full-commit-sha>-<run-attempt>`, records the registry-provided digest in
job outputs, an evidence artifact, and the Job Summary, then logs out and proves anonymous digest pull and provenance.

The tag is for discovery only. `latest` is forbidden, and consumers must identify an image as
`ghcr.io/ls1intum/artemis-feature-model@sha256:<registry-digest>`.

## Local reproduction

Use a disposable, clean Artemis checkout at the SHA printed by preflight. Do not reset or clean a developer checkout.

```bash
./gradlew featureModelManifestPreflight
./gradlew test --rerun-tasks
npm run test -- --watch=false
npm run build:prod
./gradlew buildFeatureModelSnapshot -PartemisPath=<clean-exact-artemis-checkout>
./gradlew validateFeatureModelSnapshot \
  -PsnapshotPath=build/feature-extraction/<artemis-sha>/snapshot
./gradlew stageFeatureModelDockerContext \
  -PsnapshotPath=build/feature-extraction/<artemis-sha>/snapshot
scripts/build-snapshot-image.sh \
  build/docker/feature-model-snapshot artemis-feature-model:snapshot-local
scripts/verify-snapshot-image.sh \
  artemis-feature-model:snapshot-local build/docker/feature-model-snapshot
```

Interpret `report/index.html` first. The accompanying JSON contains the exact manifest conformance, extraction,
guided-workflow, and release-delta records. A non-zero workflow result is ineligible for snapshot/image publication;
do not bypass it by editing generated output.

## Advancing the Artemis manifest pin

1. Choose an immutable full Artemis commit SHA and obtain a clean checkout at that exact commit.
2. Change only `artemisCommitSha` in
   `src/main/resources/feature-model/extraction/artemis-feature-manifest.yml` initially.
3. Run the complete extraction against that checkout and inspect the HTML/conformance reports.
4. Give every new candidate exactly one `include` or `exclude` decision. Resolve missing/ambiguous anchors and every
   included-feature relation through a declared constraint or a justified `ignoredRelations` entry.
5. Update authored semantics or guided-workflow prose only where the evidence requires it. Manifest-authored semantics
   remain authoritative; annotations never grant membership.
6. Repeat extraction until conformance, workflow, catalog, snapshot, and image gates all pass, then review the report
   and immutable identities before merging.

Never point production generation at Artemis `develop`, a tag, an abbreviated SHA, or a dirty checkout.

## Publication and future promotion

All substantive work is committed on `feature/automated-model-delivery`. The `deployment/image-publish-test` branch is
only a CI exercise branch: merge the development branch into it and do not develop unrelated changes there. The
`image-publish-test` Environment must allow only that branch and require no reviewers. Pull requests, the development
branch, and all other branches have no package-write job.

A successful publication digest is not automatically a healthy rollback target. `LAST_VERIFIED_IMAGE_DIGEST` is a
future Environment variable that may be updated manually only after the owner deploys that digest and verifies it
healthy. The current workflows do not create or mutate it. Automatic deployment, stale-run deployment checks,
post-deploy verification, and rollback are intentionally deferred; publication does not complete Phase E4.
