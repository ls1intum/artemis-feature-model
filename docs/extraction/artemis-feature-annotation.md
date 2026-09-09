# `@ArtemisFeature` Source Contract

`@ArtemisFeature` declares that an Artemis source anchor is a feature of the feature model and names it. The
extractor reads it with JavaParser; it never loads the annotated class, starts Artemis, or requires the annotation on
the extractor runtime classpath.

The reference copy of the two annotation types lives in
`src/main/java/de/tum/cit/aet/artemis/featuremodel/extraction/annotation/`. The copies placed in Artemis under
`de.tum.cit.aet.artemis.core.config.featuremodel` are byte-identical apart from the package declaration. The scan
matches the simple name, so the Artemis package is free.

## Contract v2

The annotation carries exactly two attributes:

- `id` (required): the stable kebab-case feature id, unique across all annotated anchors.
- `configuration` (optional): nested `@ArtemisFeatureConfig(key, secret)` declarations naming the configuration
  keys a deployment must supply when the feature is selected. A key declared here has the final word; the manifest
  can neither alter nor remove it. Configuration derivation and precedence are implemented in Phase A2; in Phase A1
  the declarations are scanned and recorded only.

Retention is `RUNTIME` so a future Artemis-side rule can see the annotation; the extractor still reads source.

```java
@ArtemisFeature(id = "iris")
public class IrisEnabled implements Condition {
}
```

The contract-v1 attributes `group`, `parent`, `kind`, `requiresCapabilities`, `providesCapabilities`, `name`,
`description`, and `documentationUrl` are retired. The reader rejects them with a migration message: those values
belong in the manifest `features` entry of the feature id. Capability ids never enter Artemis; they are the
deployment-profile vocabulary of this repository.

## Placement

- Use `TYPE` on a `*Enabled` Spring `Condition` class when one is the canonical feature anchor.
- Use `FIELD` on a `Constants.MODULE_FEATURE_*` field when no condition class exists.
- Use `FIELD` on a backend `Feature` enum constant for a runtime-toggle anchor.
- Put at most one feature annotation on the canonical anchor. When several annotations resolve to one candidate, the
  first one wins and the report flags the collision as `MANIFEST_CURATION_CONFLICT`.

## Membership and the manifest

Membership of a functional feature comes from the annotation. The scope manifest (schema version 4, kept permanently
in this repository) carries everything else:

- `features`: modeling semantics per member id (group or parent, order, optionality, category, default state,
  capabilities, artifact-mapping hints, prose overrides, rationale). A member without a `features` entry is a blocking
  `ANNOTATED_FEATURE_UNPLACED`; a `features` entry without a member is a blocking `MANIFEST_FEATURE_UNKNOWN`.
- `provisional`: `(anchor, id)` membership for anchors whose annotation has not landed in upstream Artemis. When an
  annotation resolves to the same candidate the annotation wins, the ids must match (`MANIFEST_CURATION_CONFLICT`
  otherwise), and the entry is reported as `PROVISIONAL_REDUNDANT`. While it carries membership it is reported as
  `PROVISIONAL_MEMBERSHIP` (information).
- `technical`: anchor, id, and semantics of the maintainer-only technical and infrastructure members (`infra:*`,
  `profile:*`), which have no Java symbol an annotation could sit on. Technical membership is manifest-only.
- `notModeled`: explicit exclusions with a reason. An annotated candidate listed here is a blocking
  `NOT_MODELED_ANCHOR_ANNOTATED`.

Precedence:

```text
membership          annotation > provisional            technical: manifest only
id                  annotation; a provisional id must match
other semantics     manifest features[] only
source facts        never overridable
```

## Tiered gate

- A module candidate Artemis itself presents as a feature (enumerated by `getEnabledFeatures` or displayed on the
  admin Features page) with no annotation and no manifest decision is a blocking `UNDECLARED_CANDIDATE`.
- Any other undecided candidate (a runtime toggle, a Spring profile, a condition-class-only module) is an
  informational `UNMODELED_ANCHOR`: it is listed in the report and stays outside the model until someone annotates or
  declares it.
- An annotation that resolves to no candidate or to several is a blocking `ANNOTATED_ANCHOR_NOT_EXTRACTED`.
- Relations between members still require a constraint or an `ignoredRelations` entry.

The retired codes `ANNOTATED_BUT_UNSCOPED` and `MANIFEST_OVERRIDES_ANNOTATION` no longer exist.
