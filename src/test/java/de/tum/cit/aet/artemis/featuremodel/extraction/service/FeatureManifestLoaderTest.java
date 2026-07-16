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
                  - id: always-on
                    parent: root
                    kind: module
                    optionality: mandatory
                """);

        assertThat(manifest.include()).singleElement().satisfies(entry -> {
            assertThat(entry.anchor()).isEqualTo("module:alpha");
            assertThat(entry.optionality()).isNull();
            assertThat(entry.requiresCapabilities()).containsExactly("alpha-service");
        });
        assertThat(manifest.exclude()).singleElement().satisfies(entry -> assertThat(entry.reason()).isEqualTo("operational"));
        assertThat(manifest.conceptualNodes()).anySatisfy(node -> {
            assertThat(node.id()).isEqualTo("always-on");
            assertThat(node.optionality()).isEqualTo(FeatureScopeManifest.OPTIONALITY_MANDATORY);
        });
    }

    @Test
    void rejectsInvalidOptionalityValue() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 1
                verifiedAgainstArtemisCommit: abc123
                include:
                  - anchor: module:alpha
                    id: alpha
                    optionality: required
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("include[0].optionality must be one of");
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

    @Test
    void rejectsUndeclaredParentReference() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 1
                verifiedAgainstArtemisCommit: abc123
                include:
                  - anchor: module:alpha
                    id: alpha
                    group: missing-group
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("'alpha' references undeclared parent/group 'missing-group'");
    }

    private FeatureScopeManifest load(String yaml) {
        return loader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test manifest");
    }
}
