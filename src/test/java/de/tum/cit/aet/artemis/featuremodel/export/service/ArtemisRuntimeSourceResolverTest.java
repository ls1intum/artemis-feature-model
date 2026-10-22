package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.FeatureModelSourceMode;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelBundle;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.RuntimeFeatureModelProvenance;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisRuntimeSource;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.GeneratedSnapshotMetadata;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;

class ArtemisRuntimeSourceResolverTest {

    private static final String MODEL_COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void usesClasspathPropertiesWithoutAnActiveSnapshot() {
        ArtemisRuntimeSource source = classpathResolver("sha256:classpath").resolveForLocalDocker(MODEL_COMMIT);

        assertThat(source.imageDigest()).isEqualTo("sha256:classpath");
        assertThat(source.imageTag()).isNull();
        assertThat(source.imageRepository()).isEqualTo("ghcr.io/ls1intum/artemis");
        assertThat(source.imageReference()).isEqualTo("ghcr.io/ls1intum/artemis@sha256:classpath");
    }

    @Test
    void activeSnapshotValuesWinOverClasspathProperties() {
        ArtemisRuntimeSource source = snapshotResolver("active", "snapshot-commit", "sha256:snapshot").resolveForLocalDocker(MODEL_COMMIT);

        assertThat(source.imageDigest()).isEqualTo("sha256:snapshot");
    }

    @Test
    void pinnedDigestNeedsNoSourceCommit() {
        ArtemisRuntimeSource source = snapshotResolver("active", null, "sha256:snapshot").resolveForLocalDocker(null);

        assertThat(source.imageDigest()).isEqualTo("sha256:snapshot");
    }

    @Test
    void withoutAPinnedDigestThePackageRunsTheImageOfTheModelCommit() {
        for (ArtemisRuntimeSourceResolver resolver : List.of(classpathResolver("latest"), snapshotResolver("active", MODEL_COMMIT, "latest"))) {
            ArtemisRuntimeSource source = resolver.resolveForLocalDocker(MODEL_COMMIT);

            assertThat(source.imageDigest()).isNull();
            assertThat(source.imageTag()).isEqualTo("sha-" + MODEL_COMMIT);
            assertThat(source.imageReference()).isEqualTo("ghcr.io/ls1intum/artemis:sha-" + MODEL_COMMIT);
        }
    }

    @Test
    void withoutAPinnedDigestAMissingOrAbbreviatedModelCommitFails() {
        for (String modelCommit : new String[] { null, MODEL_COMMIT.substring(0, 12) }) {
            assertThatThrownBy(() -> classpathResolver("latest").resolveForLocalDocker(modelCommit)).isInstanceOf(ArtifactGenerationException.class)
                    .hasMessageContaining("model.sourceCommitSha");
        }
    }

    @Test
    void devIdeReportsOnlyAPinnedDigest() {
        assertThat(classpathResolver("latest").resolveForDevIde()).isEqualTo(new ArtemisRuntimeSource("ghcr.io/ls1intum/artemis", null, null));
        assertThat(classpathResolver("sha256:classpath").resolveForDevIde().imageDigest()).isEqualTo("sha256:classpath");
    }

    @Test
    void missingActiveSnapshotValueDoesNotFallBack() {
        assertThatThrownBy(() -> snapshotResolver("legacy", "snapshot-commit", null).resolveForLocalDocker(MODEL_COMMIT))
                .isInstanceOf(ArtifactGenerationException.class).hasMessageContaining("active snapshot 'legacy' metadata.imageDigest")
                .hasMessageContaining("regenerate the snapshot");
    }

    @Test
    void missingClasspathValueNamesTheExactProperty() {
        assertThatThrownBy(() -> classpathResolver(" ").resolveForLocalDocker(MODEL_COMMIT))
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
