package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/** Persisted artifact mapping targets the extraction pipeline generates and validates against. */
public final class ArtifactMappingTargets {

    /** Target of mappings that materialize in the generated Spring configuration overlay. */
    public static final String OVERLAY_TARGET = "application-feature-model.yml";

    private ArtifactMappingTargets() {
    }
}
