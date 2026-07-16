package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureManifestException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;

/** Verifies schema-level loading and controlled failures of the YAML scope manifest. */
class FeatureManifestLoaderTest {

    private final FeatureManifestLoader loader = new FeatureManifestLoader();

    @Test
    void loadsValidManifest() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 1
                verifiedAgainstArtemisCommit: abc123
                include:
                  - anchor: module:alpha
                    id: alpha
                    requiresCapabilities: [alpha-service]
                exclude:
                  - anchor: toggle:RateLimit
                    reason: operational
                conceptualNodes:
                  - id: root
                    kind: root
                """);

        assertThat(manifest.include()).singleElement().satisfies(entry -> {
            assertThat(entry.anchor()).isEqualTo("module:alpha");
            assertThat(entry.requiresCapabilities()).containsExactly("alpha-service");
        });
        assertThat(manifest.exclude()).singleElement().satisfies(entry -> assertThat(entry.reason()).isEqualTo("operational"));
    }

    @Test
    void rejectsDuplicateAnchorsAcrossStates() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 1
                verifiedAgainstArtemisCommit: abc123
                include: [{ anchor: module:alpha, id: alpha }]
                exclude: [{ anchor: module:alpha, reason: duplicate }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate manifest anchor 'module:alpha'");
    }

    @Test
    void rejectsDuplicateCuratedIds() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 1
                verifiedAgainstArtemisCommit: abc123
                include: [{ anchor: module:alpha, id: shared }]
                conceptualNodes: [{ id: shared, kind: group }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate curated id 'shared'");
    }

    @Test
    void rejectsMissingExcludeReason() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 1
                verifiedAgainstArtemisCommit: abc123
                exclude: [{ anchor: toggle:RateLimit }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("exclude[0].reason");
    }

    @Test
    void rejectsUnknownStateField() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 1
                verifiedAgainstArtemisCommit: abc123
                pending: [module:alpha]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("unknown field(s): pending");
    }

    private FeatureScopeManifest load(String yaml) {
        return loader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test manifest");
    }
}
