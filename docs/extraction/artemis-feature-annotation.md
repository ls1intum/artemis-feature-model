# `@ArtemisFeature` Source Contract

`@ArtemisFeature` declares feature-model semantics beside a canonical Artemis source anchor. The extractor reads it
with JavaParser; it never loads the annotated class, starts Artemis, or requires the annotation to be present on the
extractor runtime classpath.

The contract source currently lives at
`src/main/java/de/tum/cit/aet/artemis/featuremodel/extraction/annotation/ArtemisFeature.java`. It is intentionally
dependency-free and ready to move to Artemis when upstream integration begins. No Artemis source is annotated during
Phase E2.

## Placement

- Use `TYPE` on a `*Enabled` Spring `Condition` class when one is the canonical feature anchor.
- Use `FIELD` on a `Constants.MODULE_FEATURE_*` field when no condition class exists.
- Use `FIELD` on a backend `Feature` enum constant for a runtime-toggle anchor.
- Put at most one feature annotation on the canonical anchor. When several annotations resolve to one candidate, the
  first one wins and the report flags the collision as `MANIFEST_CURATION_CONFLICT`.

```java
@ArtemisFeature(id = "iris",
        group = "adaptive-learning-and-ai",
        requiresCapabilities = { "pyris-service", "pyris-secret" })
public class IrisEnabled implements Condition {
}
```

`id` is mandatory. `group`, `parent`, `kind`, `requiresCapabilities`, `providesCapabilities`, `name`, `description`,
and `documentationUrl` are optional. Names, descriptions, and documentation links should normally remain absent so
the extractor can use Artemis i18n and admin-page data.

## Membership and precedence

The feature scope manifest is the only membership arbiter:

- an included anchor enters the E3 generated model;
- an excluded anchor remains out with its reason code;
- an unlisted anchor is pending and remains out;
- an annotation never auto-includes its anchor.

For an included anchor, explicitly written annotation attributes override the corresponding manifest-entry semantics.
Unspecified annotation attributes retain their manifest values. The report emits `ANNOTATION_OVERRIDES_MANIFEST` so
redundant interim semantics can be removed from the manifest after upstream adoption.

An annotation on an excluded or pending candidate emits `ANNOTATED_BUT_UNSCOPED`. An annotation that cannot be joined
to any extracted candidate emits `ANNOTATED_ANCHOR_NOT_EXTRACTED`.
