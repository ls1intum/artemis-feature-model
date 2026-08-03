package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Parsed {@code @ArtemisFeature} values. Null optional values mean that the annotation omitted the attribute and
 * therefore cannot override manifest-authored semantics.
 *
 * @param id required curated id.
 * @param group group override.
 * @param parent parent override.
 * @param kind kind override.
 * @param requiresCapabilities required capabilities override.
 * @param providesCapabilities provided capabilities override.
 * @param name name override.
 * @param description description override.
 * @param documentationUrl documentation URL override.
 */
public record ExtractedAnnotationSemantics(String id, String group, String parent, String kind, List<String> requiresCapabilities,
        List<String> providesCapabilities, String name, String description, String documentationUrl) {

    /** Normalizes present capability lists to immutable copies while preserving null for omitted attributes. */
    public ExtractedAnnotationSemantics {
        requiresCapabilities = requiresCapabilities == null ? null : List.copyOf(requiresCapabilities);
        providesCapabilities = providesCapabilities == null ? null : List.copyOf(providesCapabilities);
    }
}
