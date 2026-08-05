package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SnapshotPropertiesTest {

    @Test
    void defaultsToClasspathModeWithoutSnapshotConfiguration() {
        SnapshotProperties properties = new SnapshotProperties(null, null, null, false);

        assertThat(properties.sourceMode()).isEqualTo(FeatureModelSourceMode.CLASSPATH);
        assertThat(properties.dataRoot()).isEqualTo("data");
        assertThat(properties.hasActiveSnapshot()).isFalse();
    }

    @Test
    void acceptsExplicitCompleteSnapshotConfiguration() {
        SnapshotProperties properties = new SnapshotProperties(FeatureModelSourceMode.SNAPSHOT, "/runtime/data", "generated-123", false);

        assertThat(properties.sourceMode()).isEqualTo(FeatureModelSourceMode.SNAPSHOT);
        assertThat(properties.dataRoot()).isEqualTo("/runtime/data");
        assertThat(properties.activeSnapshotId()).isEqualTo("generated-123");
        assertThat(properties.hasActiveSnapshot()).isTrue();
    }

    @Test
    void rejectsSnapshotIdInClasspathMode() {
        assertThatThrownBy(() -> new SnapshotProperties(FeatureModelSourceMode.CLASSPATH, "data", "generated-123", false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Classpath source mode");
    }

    @Test
    void rejectsMissingSnapshotIdInSnapshotMode() {
        assertThatThrownBy(() -> new SnapshotProperties(FeatureModelSourceMode.SNAPSHOT, "data", " ", false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("active-snapshot-id");
    }

    @Test
    void rejectsMissingDataRootInSnapshotMode() {
        assertThatThrownBy(() -> new SnapshotProperties(FeatureModelSourceMode.SNAPSHOT, " ", "generated-123", false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("data-root");
    }

    @Test
    void rejectsAdministrationApiInSnapshotMode() {
        assertThatThrownBy(() -> new SnapshotProperties(FeatureModelSourceMode.SNAPSHOT, "data", "generated-123", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot be enabled");
    }

    @Test
    void legacyConstructorCannotInferSnapshotMode() {
        assertThatThrownBy(() -> new SnapshotProperties("data", "generated-123")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selected explicitly");
    }
}
