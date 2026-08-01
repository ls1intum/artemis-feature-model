package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/** The four composable extraction commands, in the order in which their artifacts build on each other. */
public enum ExtractionStage {

    /** Reads the pinned Artemis checkout and writes the raw source discovery artifacts. */
    SCAN,

    /** Applies the manifest to a scan and assembles the generated model, catalog, and comparison. */
    MODEL,

    /** Validates and prepares the guided workflow against a generated model. */
    WORKFLOW,

    /** Consolidates the diagnostics of all stages and publishes the importable snapshot. */
    PACKAGE
}
