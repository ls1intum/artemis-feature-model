package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuration for the complete runtime feature-model artifact source.
 *
 * <p>
 * The source mode is explicit. Classpath mode rejects an active snapshot id, while snapshot mode requires both a data
 * root and an active snapshot id. The snapshot administration API is opt-in and cannot be enabled in snapshot mode.
 */
@ConfigurationProperties(prefix = "artemis.feature-model")
public record SnapshotProperties(FeatureModelSourceMode sourceMode, String dataRoot, String activeSnapshotId, boolean snapshotAdminApiEnabled) {

    private static final String DEFAULT_DATA_ROOT = "data";

    /**
     * Normalizes classpath defaults and rejects ambiguous or unsafe source combinations.
     *
     * @param sourceMode complete artifact source, defaulting to {@link FeatureModelSourceMode#CLASSPATH}.
     * @param dataRoot local application data root.
     * @param activeSnapshotId id of the active generated snapshot.
     * @param snapshotAdminApiEnabled whether the legacy snapshot administration API is explicitly enabled.
     */
    @ConstructorBinding
    public SnapshotProperties {
        sourceMode = sourceMode == null ? FeatureModelSourceMode.CLASSPATH : sourceMode;
        activeSnapshotId = normalize(activeSnapshotId);
        if (sourceMode == FeatureModelSourceMode.SNAPSHOT && (dataRoot == null || dataRoot.isBlank())) {
            throw new IllegalArgumentException("Snapshot source mode requires artemis.feature-model.data-root.");
        }
        if (sourceMode == FeatureModelSourceMode.CLASSPATH && activeSnapshotId != null) {
            throw new IllegalArgumentException("Classpath source mode does not accept artemis.feature-model.active-snapshot-id.");
        }
        if (sourceMode == FeatureModelSourceMode.SNAPSHOT && activeSnapshotId == null) {
            throw new IllegalArgumentException("Snapshot source mode requires artemis.feature-model.active-snapshot-id.");
        }
        if (sourceMode == FeatureModelSourceMode.SNAPSHOT && snapshotAdminApiEnabled) {
            throw new IllegalArgumentException("The snapshot administration API cannot be enabled in snapshot source mode.");
        }
        if (dataRoot == null || dataRoot.isBlank()) {
            dataRoot = DEFAULT_DATA_ROOT;
        }
    }

    /**
     * Creates classpath properties for focused tests and non-runtime data-root consumers.
     *
     * @param dataRoot local application data root.
     * @param activeSnapshotId must be {@code null}; snapshot mode must be selected explicitly.
     */
    public SnapshotProperties(String dataRoot, String activeSnapshotId) {
        this(requireImplicitClasspath(activeSnapshotId), dataRoot, null, false);
    }

    /**
     * Creates properties that always use the classpath fallback. Used by stores constructed without a local snapshot
     * source, such as focused unit tests.
     *
     * @return classpath-only snapshot properties.
     */
    public static SnapshotProperties classpathFallback() {
        return new SnapshotProperties(FeatureModelSourceMode.CLASSPATH, DEFAULT_DATA_ROOT, null, false);
    }

    /**
     * Indicates whether an active local snapshot is configured.
     *
     * @return true if an active snapshot id is set.
     */
    public boolean hasActiveSnapshot() {
        return sourceMode == FeatureModelSourceMode.SNAPSHOT;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static FeatureModelSourceMode requireImplicitClasspath(String activeSnapshotId) {
        if (normalize(activeSnapshotId) != null) {
            throw new IllegalArgumentException("Snapshot source mode must be selected explicitly.");
        }
        return FeatureModelSourceMode.CLASSPATH;
    }
}
