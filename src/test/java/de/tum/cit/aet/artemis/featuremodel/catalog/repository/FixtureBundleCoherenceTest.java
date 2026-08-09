package de.tum.cit.aet.artemis.featuremodel.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Replaces the retired curated-vs-generated parity test after the fixture switchover: the classpath fixture is a
 * wholesale copy of a delivered generated snapshot, so the remaining guarantees are that the copied bundle loads
 * through the shared loader/integrity path and that the provenance sidecar is well-formed and consistent with the
 * copied model and catalog.
 */
class FixtureBundleCoherenceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fixtureBundleLoadsThroughTheSharedLoaderAndIntegrityPath() {
        RuntimeFeatureModelBundle bundle = new RuntimeFeatureModelBundleLoader(SnapshotProperties.classpathFallback(), new DefaultResourceLoader(),
                objectMapper).load();

        assertThat(bundle.model().model().id()).isEqualTo("artemis-generated-feature-model");
        assertThat(bundle.model().features()).isNotEmpty();
        assertThat(bundle.workflow().steps()).isNotEmpty();
        assertThat(bundle.catalog().keys()).isNotEmpty();
    }

    @Test
    void provenanceSidecarMatchesTheCopiedModelAndCatalog() throws IOException {
        RuntimeFeatureModelBundle bundle = new RuntimeFeatureModelBundleLoader(SnapshotProperties.classpathFallback(), new DefaultResourceLoader(),
                objectMapper).load();
        JsonNode sidecar = readSidecar();

        String snapshotId = sidecar.get("snapshotId").asString();
        String artemisCommit = sidecar.get("artemisCommit").asString();
        String manifestDigest = sidecar.get("manifestDigest").asString();
        assertThat(artemisCommit).matches("[0-9a-f]{40}");
        assertThat(manifestDigest).matches("sha256:[0-9a-f]{64}");
        assertThat(snapshotId).isEqualTo("generated-" + artemisCommit.substring(0, 12) + "-" + manifestDigest.substring("sha256:".length(), "sha256:".length() + 12));
        assertThat(bundle.model().model().sourceCommitSha()).isEqualTo(artemisCommit);
        assertThat(bundle.model().model().version()).endsWith(artemisCommit.substring(0, 12));
        assertThat(bundle.catalog().verifiedAgainstArtemisCommit()).isEqualTo(artemisCommit);
    }

    private JsonNode readSidecar() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/feature-model/fixture-provenance.json")) {
            assertThat(inputStream).as("fixture provenance sidecar").isNotNull();
            return objectMapper.readTree(inputStream);
        }
    }
}
