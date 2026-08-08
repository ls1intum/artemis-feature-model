package de.tum.cit.aet.artemis.featuremodel.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class DockerImageContractTest {

    @Test
    void productionImageUsesOnlyTheValidatedNamedSnapshotContext() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertThat(dockerfile).contains("COPY --from=feature_model_snapshot")
                .contains("COPY --from=application_jar")
                .contains("/opt/artemis-feature-model/data/imported-models/${SNAPSHOT_ID}/")
                .contains("ARTEMIS_FEATURE_MODEL_SOURCE_MODE=snapshot")
                .contains("ARTEMIS_FEATURE_MODEL_ACTIVE_SNAPSHOT_ID=${SNAPSHOT_ID}")
                .contains("ARTEMIS_FEATURE_MODEL_SNAPSHOT_ADMIN_API_ENABLED=false").contains("USER 10001:10001")
                .contains("--chmod=0444").doesNotContain("VOLUME").doesNotContain("COPY build/");
    }

    @Test
    void productionImageDeclaresEveryImmutableSnapshotLabel() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertThat(dockerfile).contains("org.opencontainers.image.revision=\"${FEATURE_MODEL_REPOSITORY_COMMIT}\"")
                .contains("artemis-commit=\"${ARTEMIS_COMMIT}\"").contains("manifest-digest=\"${MANIFEST_DIGEST}\"")
                .contains("snapshot-id=\"${SNAPSHOT_ID}\"").contains("snapshot-digest=\"${SNAPSHOT_DIGEST}\"")
                .contains("extractor-version=\"${EXTRACTOR_VERSION}\"");
    }
}
