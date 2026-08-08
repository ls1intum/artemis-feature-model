package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * One {@code @ArtemisFeature} source fact persisted under {@code scan/annotations.json}.
 *
 * @param anchor fully-qualified type, field symbol, or namespaced enum-member anchor.
 * @param semantics parsed annotation values.
 * @param file checkout-relative source file.
 * @param line annotation target line.
 */
public record ExtractedAnnotation(String anchor, ExtractedAnnotationSemantics semantics, String file, Integer line) {
}
