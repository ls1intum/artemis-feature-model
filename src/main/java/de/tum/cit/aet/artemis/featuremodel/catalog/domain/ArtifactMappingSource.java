package de.tum.cit.aet.artemis.featuremodel.catalog.domain;

import java.util.Set;

/**
 * Stable value-source ids of an artifact mapping. Every mapping declares exactly one source explicitly; membership in
 * this set is validated by shared model integrity, so an unknown source is a hard integrity error instead of a silent
 * reinterpretation.
 */
public final class ArtifactMappingSource {

    /** The mapping writes a static value chosen by whether the owning feature is selected. */
    public static final String SELECTION = "selection";

    /** The mapping writes a {@code ${VARIABLE}} placeholder whose value the deployment environment supplies. */
    public static final String ENVIRONMENT = "environment";

    private static final Set<String> KNOWN_SOURCES = Set.of(SELECTION, ENVIRONMENT);

    private ArtifactMappingSource() {
    }

    /**
     * Checks whether a source id is one of the supported mapping sources.
     *
     * @param source source id to check.
     * @return true if the id names a supported mapping source.
     */
    public static boolean isKnown(String source) {
        return source != null && KNOWN_SOURCES.contains(source);
    }
}
