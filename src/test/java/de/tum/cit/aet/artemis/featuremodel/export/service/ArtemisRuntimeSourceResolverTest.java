package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.FeatureModelSourceMode;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundle;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelProvenance;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisRuntimeSource;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GeneratedSnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;

class ArtemisRuntimeSourceResolverTest {

    @Test
    void usesClasspathPropertiesWithoutAnActiveSnapshot() {
        ArtemisRuntimeSource source = classpathResolver("sha256:classpath").resolveForLocalDocker();

        assertThat(source.imageDigest()).isEqualTo("sha256:classpath");
        assertThat(source.imageRepository()).isEqualTo("ghcr.io/ls1intum/artemis");
    }

    @Test
    void activeSnapshotValuesWinOverClasspathProperties() {
        ArtemisRuntimeSource source = snapshotResolver("active", "snapshot-commit", "sha256:snapshot").resolveForLocalDocker();

        assertThat(source.imageDigest()).isEqualTo("sha256:snapshot");
    }

    @Test
    void doesNotRequireASnapshotSourceCommit() {
        ArtemisRuntimeSource source = snapshotResolver("active", null, "sha256:snapshot").resolveForLocalDocker();

        assertThat(source.imageDigest()).isEqualTo("sha256:snapshot");
    }

    @Test
    void missingActiveSnapshotValueDoesNotFallBack() {
        assertThatThrownBy(() -> snapshotResolver("legacy", "snapshot-commit", null).resolveForLocalDocker())
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("active snapshot 'legacy' metadata.imageDigest")
                .hasMessageContaining("regenerate the snapshot");
    }

    @Test
    void missingClasspathValueNamesTheExactProperty() {
        assertThatThrownBy(() -> classpathResolver(" ").resolveForLocalDocker())
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("artemis.feature-model.runtime.image-digest");
    }

    private ArtemisRuntimeSourceResolver classpathResolver(String imageDigest) {
        return resolver(null, imageDigest);
    }

    private ArtemisRuntimeSourceResolver snapshotResolver(String snapshotId, String sourceCommit, String imageDigest) {
        GeneratedSnapshotMetadata metadata = new GeneratedSnapshotMetadata(2, 2, "model", snapshotId, "1", "generated", sourceCommit, imageDigest,
                "feature-model-extractor@0.3.0", "feature-model.json", "guided-workflow.json", "config-key-catalog.json", "generation-report.json",
                "provenance.json", "checksums.txt");
        return resolver(metadata, "latest");
    }

    private ArtemisRuntimeSourceResolver resolver(GeneratedSnapshotMetadata metadata, String classpathImageDigest) {
        FeatureModelSourceMode mode = metadata == null ? FeatureModelSourceMode.CLASSPATH : FeatureModelSourceMode.SNAPSHOT;
        String snapshotId = metadata == null ? null : metadata.snapshotId();
        RuntimeFeatureModelProvenance provenance = new RuntimeFeatureModelProvenance(mode, "model", "1", snapshotId, null, null, null, null, null);
        RuntimeFeatureModelBundle bundle = new RuntimeFeatureModelBundle(null, null, null, provenance, metadata);
        return new ArtemisRuntimeSourceResolver(bundle, new ArtemisRuntimeProperties(classpathImageDigest));
    }
}
