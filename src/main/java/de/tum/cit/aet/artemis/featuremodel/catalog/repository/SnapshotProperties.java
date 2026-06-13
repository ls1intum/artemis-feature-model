package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for local feature model snapshot loading.
 *
 * <p>
 * The application loads the active model and guided workflow from classpath resources by default. When an active local
 * snapshot is configured, the snapshot's {@code feature-model.json} and {@code guided-workflow.json} are loaded instead,
 * keeping both files in sync as one consistent snapshot unit.
 */
@ConfigurationProperties(prefix = "artemis.feature-model")
public record SnapshotProperties(String dataRoot, String activeSnapshotId) {

    private static final String DEFAULT_DATA_ROOT = "data";

    /**
     * Normalizes blank configuration values so that a missing data root falls back to {@code data} and a blank active
     * snapshot id means "use the classpath fallback".
     *
     * @param dataRoot local application data root, defaulting to {@code data} when blank.
     * @param activeSnapshotId id of the active local snapshot, or {@code null} to use the classpath fallback.
     */
    public SnapshotProperties {
        if (dataRoot == null || dataRoot.isBlank()) {
            dataRoot = DEFAULT_DATA_ROOT;
        }
        if (activeSnapshotId != null && activeSnapshotId.isBlank()) {
            activeSnapshotId = null;
        }
    }

    /**
     * Creates properties that always use the classpath fallback. Used by stores constructed without a local snapshot
     * source, such as focused unit tests.
     *
     * @return classpath-only snapshot properties.
     */
    public static SnapshotProperties classpathFallback() {
        return new SnapshotProperties(DEFAULT_DATA_ROOT, null);
    }

    /**
     * Indicates whether an active local snapshot is configured.
     *
     * @return true if an active snapshot id is set.
     */
    public boolean hasActiveSnapshot() {
        return activeSnapshotId != null;
    }
}
