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
        ArtemisRuntimeSource source = resolver(null, "classpath-commit", "sha256:classpath").resolveForLocalDocker();

        assertThat(source.sourceCommit()).isEqualTo("classpath-commit");
        assertThat(source.imageDigest()).isEqualTo("sha256:classpath");
        assertThat(source.imageRepository()).isEqualTo("ghcr.io/ls1intum/artemis");
    }

    @Test
    void activeSnapshotValuesWinOverClasspathProperties() throws Exception {
        ArtemisRuntimeSource source = resolver("active", "classpath-commit", "sha256:classpath").resolveForLocalDocker();

        assertThat(source.sourceCommit()).isEqualTo("snapshot-commit");
        assertThat(source.imageDigest()).isEqualTo("sha256:snapshot");
    }

    @Test
    void missingActiveSnapshotValueDoesNotFallBack() throws Exception {
        assertThatThrownBy(() -> resolver("legacy", "classpath-commit", "latest").resolveForLocalDocker())
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("active snapshot 'legacy' metadata.imageDigest")
                .hasMessageContaining("regenerate the snapshot");
    }

    @Test
    void missingClasspathValueNamesTheExactProperty() {
        assertThatThrownBy(() -> resolver(null, null, "latest").resolveForLocalDocker())
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("artemis.feature-model.runtime.source-commit");
        assertThatThrownBy(() -> resolver(null, "commit", " ").resolveForLocalDocker())
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("artemis.feature-model.runtime.image-digest");
    }

    private ArtemisRuntimeSourceResolver resolver(String activeSnapshotId, String sourceCommit, String imageDigest) {
        String snapshotImageDigest = "active".equals(activeSnapshotId) ? "sha256:snapshot" : null;
        GeneratedSnapshotMetadata metadata = activeSnapshotId == null ? null
                : new GeneratedSnapshotMetadata(2, 2, "model", activeSnapshotId, "1", "generated", "snapshot-commit", snapshotImageDigest,
                        "feature-model-extractor@0.3.0", "feature-model.json", "guided-workflow.json", "config-key-catalog.json",
                        "generation-report.json", "provenance.json", "checksums.txt");
        FeatureModelSourceMode mode = metadata == null ? FeatureModelSourceMode.CLASSPATH : FeatureModelSourceMode.SNAPSHOT;
        RuntimeFeatureModelProvenance provenance = new RuntimeFeatureModelProvenance(mode, "model", "1", activeSnapshotId, null, null, null, null, null);
        RuntimeFeatureModelBundle bundle = new RuntimeFeatureModelBundle(null, null, null, provenance, metadata);
        return new ArtemisRuntimeSourceResolver(bundle, new ArtemisRuntimeProperties(sourceCommit, imageDigest));
    }
}
