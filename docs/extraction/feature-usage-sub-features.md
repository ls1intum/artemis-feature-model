# FeatureUsage sub-features

Every Artemis REST controller carries `@FeatureUsage("<area>/<feature>")`, the label the admin feature-usage page
groups endpoints under. The extraction pipeline turns these labels into **sub-feature nodes** of the generated
feature model: read-only children of the member whose guard protects the annotated controller. They make the
Explorer show what a feature consists of without turning the labels into anything a deployment can select.

`@FeatureUsage` is not `@FeatureToggle`: the toggle is a runtime kill switch, the usage annotation only names and
groups endpoints.

## Scan facts

`extractFeatureCandidates` records every annotated type as an `ExtractedFeatureUsage` in `scan/feature-usages.json`
(digest-verified payload of the scan envelope, schema 4): file, declaration line, module (first package segment below
`de.tum.cit.aet.artemis`), type, class-level label, method-level overrides, the type's `@Conditional(*Enabled.class)`
condition classes and `@Profile(...)` expressions as written. Only files lexically containing `@FeatureUsage` are
parsed. A label outside `[a-z0-9]+(-[a-z0-9]+)*/[a-z0-9]+(-[a-z0-9]+)*` warns `FEATURE_USAGE_LABEL_MALFORMED` and is
kept.

## Join rules

`assembleFeatureModel` joins the persisted usages to the resolved members (`FeatureUsageJoin`):

- A functional member owns every annotated type whose `@Conditional` guards name the member's condition class
  (`IrisEnabled` -> `iris`).
- A technical `profile:` member owns every annotated type whose `@Profile` guards name its `PROFILE_*` constant or
  its profile literal (`PROFILE_JENKINS` or `"jenkins"` -> `jenkins`), unless a functional member already claimed the
  type through a condition class.
- A type whose condition guards name no member (for example `WeaviateEnabled` while `module:weaviate` is not modeled)
  is reported as `FEATURE_USAGE_UNATTACHED` (info). Unguarded types — the `PROFILE_CORE` controllers of the mandatory
  conceptual modules — produce neither a node nor a diagnostic; attaching them would need a package join, which is a
  documented follow-up outside this track (decision D-4).

One sub-feature results per owner and distinct effective label (class label plus method labels), ordered by area and
then feature. Its evidence lists every annotated type and method that carries the label as `File.java:line`.

## Node contract

```json
{
  "id": "iris/chat/chat-sessions",
  "name": "Chat sessions",
  "kind": "sub-feature",
  "selectable": false,
  "description": "REST endpoints labelled chat/chat-sessions in module iris, guarded by IrisEnabled.",
  "defaultState": "not_applicable",
  "source": { "serverConditionClass": "IrisEnabled", "usageLabel": "chat/chat-sessions", "evidence": ["IrisChatSessionResource.java:57"] },
  "category": "derived",
  "visibleTo": ["teacher", "maintainer"],
  "configurableBy": [],
  "requiresCapabilities": [],
  "artifactMappings": [],
  "extraction": { "method": "feature-usage-annotation", "confidence": "high", "status": "generated" }
}
```

- `id` is `<owner-id>/<area>/<feature>`; `name` is the feature segment with the first letter upper-cased and hyphens
  turned into spaces; `source.usageLabel` is the raw label.
- Sub-features of a technical owner carry `source.springProfile` instead of `serverConditionClass` and are visible
  to maintainers only.
- The owner-to-sub-feature relation is `{"relationType": "mandatory", "groupType": null, "order": 1..n}` in area then
  feature order.
- `FeatureSource.usageLabel` and `FeatureSourceDTO.usageLabel` are nullable and absent from every other node; a
  classpath model without the field loads unchanged.

`GeneratedModelConformanceService` recomputes the expected sub-feature set from the persisted usages and the resolved
members without calling the assembler or the join, and compares ids, names, descriptions, kinds, selectability,
category, default state, visibility, configurability, capabilities, mappings, usage labels, guards, evidence, and the
mandatory relations with their orders. A missing or extra sub-feature is a blocking
`GENERATED_MODEL_CONFORMANCE_MISMATCH`.

## What downstream ignores

Nothing treats a sub-feature as selectable: selection validation skips non-selectable mandatory children, the default
selection requires `selectable`, guided coverage requires `selectable` and `functional`, the Ansible planner and the
artifact mapping resolver classify selectable nodes only, and sub-features carry neither capabilities nor mappings.
The Configurator tree hides `sub-feature` nodes (decision D-6); the Explorer shows them.

## Follow-up

The package join for the mandatory conceptual modules (`course-workflow`, `communication`, `exercise-common`,
`programming`, `quiz`), whose controllers are guarded by `PROFILE_CORE` only, is deferred (decision D-4). Until then
those labels appear in `scan/feature-usages.json` but produce no node.
