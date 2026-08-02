package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.featuremodel.catalog.repository.LocalSnapshotRepository;
import de.tum.cit.aet.artemis.featuremodel.catalog.repository.SnapshotProperties;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisRuntimeSource;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;
import tools.jackson.databind.ObjectMapper;

class ArtemisRuntimeSourceResolverTest {

    @TempDir
    Path dataRoot;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void usesClasspathPropertiesWithoutAnActiveSnapshot() {
        ArtemisRuntimeSource source = resolver(null, "classpath-commit", "sha256:classpath").resolveForLocalDocker();

        assertThat(source.sourceCommit()).isEqualTo("classpath-commit");
        assertThat(source.imageDigest()).isEqualTo("sha256:classpath");
        assertThat(source.imageRepository()).isEqualTo("ghcr.io/ls1intum/artemis");
    }

    @Test
    void activeSnapshotValuesWinOverClasspathProperties() throws Exception {
        writeSnapshot("active", "snapshot-commit", "sha256:snapshot");

        ArtemisRuntimeSource source = resolver("active", "classpath-commit", "sha256:classpath").resolveForLocalDocker();

        assertThat(source.sourceCommit()).isEqualTo("snapshot-commit");
        assertThat(source.imageDigest()).isEqualTo("sha256:snapshot");
    }

    @Test
    void missingActiveSnapshotValueDoesNotFallBack() throws Exception {
        writeSnapshot("legacy", "snapshot-commit", null);

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
        SnapshotProperties properties = new SnapshotProperties(dataRoot.toString(), activeSnapshotId);
        LocalSnapshotRepository repository = new LocalSnapshotRepository(properties, objectMapper);
        return new ArtemisRuntimeSourceResolver(repository, new ArtemisRuntimeProperties(sourceCommit, imageDigest));
    }

    private void writeSnapshot(String snapshotId, String sourceCommit, String imageDigest) throws Exception {
        Path directory = dataRoot.resolve("imported-models").resolve(snapshotId);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("feature-model.json"), "{}");
        Files.writeString(directory.resolve("guided-workflow.json"), "{}");
        String digestField = imageDigest == null ? "" : ",\"imageDigest\":\"" + imageDigest + "\"";
        Files.writeString(directory.resolve("metadata.json"), "{\"snapshotId\":\"" + snapshotId + "\",\"sourceCommit\":\"" + sourceCommit + "\""
                + digestField + "}");
    }
}
