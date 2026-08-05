package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

/** Selects the source of the complete runtime feature-model artifact bundle. */
public enum FeatureModelSourceMode {

    /** Hand-maintained model, workflow, and catalog resources used for local development. */
    CLASSPATH,

    /** One complete, validated generated snapshot used for production-like execution. */
    SNAPSHOT;

    /**
     * Returns the stable lowercase configuration and API value.
     *
     * @return lowercase source-mode value.
     */
    public String value() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
