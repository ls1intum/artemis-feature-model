package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureManifestException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;

/** Verifies schema-level loading and controlled failures of the YAML scope manifest. */
class FeatureManifestLoaderTest {

    private final FeatureManifestLoader loader = new FeatureManifestLoader();

    @Test
    void loadsValidManifest() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
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
    void loadsThePinnedArtemisCommit() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                """);

        assertThat(manifest.manifestVersion()).isEqualTo(FeatureScopeManifest.CURRENT_VERSION);
        assertThat(manifest.artemisCommitSha()).isEqualTo("aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee");
        assertThat(manifest.artemisImageDigest()).isEqualTo("latest");
    }

    @Test
    void rejectsAMissingArtemisImageDigest() {
        String yaml = "manifestVersion: 2\nartemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee\n";

        assertThatThrownBy(() -> loader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test manifest"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("artemisImageDigest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "develop", "v8.3.1", "aaaaaaa", "aaaaaaaabbbbbbbbccccccccddddddddeeeeeee", "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeeef",
            "AAAAAAAABBBBBBBBCCCCCCCCDDDDDDDDEEEEEEEE", "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeeg", "refs/heads/develop" })
    void rejectsEverySourceSelectorThatIsNotOneImmutableCommit(String selector) {
        assertThatThrownBy(() -> load("manifestVersion: 2\nartemisCommitSha: " + selector + "\n")).isInstanceOf(FeatureManifestException.class)
                .hasMessageContaining("artemisCommitSha").hasMessageContaining("40-character");
    }

    @Test
    void rejectsABlankArtemisCommit() {
        assertThatThrownBy(() -> load("manifestVersion: 2\nartemisCommitSha: \"\"\n")).isInstanceOf(FeatureManifestException.class)
                .hasMessageContaining("artemisCommitSha");
    }

    @Test
    void rejectsADigitsOnlyCommitThatYamlReadsAsANumber() {
        assertThatThrownBy(() -> load("manifestVersion: 2\nartemisCommitSha: 1111111111111111111111111111111111111111\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("quote");
    }

    @Test
    void rejectsAnUnsupportedManifestVersion() {
        assertThatThrownBy(() -> load("manifestVersion: 1\nartemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("Unsupported manifestVersion 1");
    }

    @Test
    void rejectsInvalidOptionalityValue() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include:
                  - anchor: module:alpha
                    id: alpha
                    optionality: required
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("include[0].optionality must be one of");
    }

    @Test
    void rejectsDuplicateAnchorsAcrossStates() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include: [{ anchor: module:alpha, id: alpha }]
                exclude: [{ anchor: module:alpha, reason: duplicate }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate manifest anchor 'module:alpha'");
    }

    @Test
    void rejectsDuplicateCuratedIds() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include: [{ anchor: module:alpha, id: shared }]
                conceptualNodes: [{ id: shared, kind: group }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate curated id 'shared'");
    }

    @Test
    void normalizesMissingExcludeReasonToUnspecified() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                exclude: [{ anchor: toggle:RateLimit }]
                """);

        assertThat(manifest.exclude()).singleElement().satisfies(entry -> {
            assertThat(entry.reason()).isEqualTo(FeatureScopeManifest.EXCLUSION_REASON_UNSPECIFIED);
            assertThat(entry.rationale()).isNull();
        });
    }

    @Test
    void rejectsBlankExcludeReasonWhenPresent() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                exclude: [{ anchor: toggle:RateLimit, reason: "" }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("exclude[0].reason");
    }

    @Test
    void rejectsUnknownStateField() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                pending: [module:alpha]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("unknown field(s): pending");
    }

    @Test
    void rejectsUndeclaredParentReference() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include:
                  - anchor: module:alpha
                    id: alpha
                    group: missing-group
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("'alpha' references undeclared parent/group 'missing-group'");
    }

    @Test
    void loadsGenerationSemanticsAndConstraints() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include:
                  - anchor: infra:tech-a
                    id: tech-a
                    group: tech-group
                    kind: feature
                    category: technical
                    defaultState: enabled
                    order: 1
                    providesCapabilities: [tech-capability]
                    artifactMappings:
                      - { target: .env, path: SPRING_PROFILES_ACTIVE, source: selection, valueWhenSelected: tech-a-profile }
                      - { target: application-feature-model.yml, path: artemis.tech.url, source: environment, secret: true }
                conceptualNodes:
                  - id: tech-group
                    kind: group
                    category: technical
                    groupType: alternative
                    order: 2
                constraints:
                  - id: tech-a-excludes-tech-a
                    type: excludes
                    source: tech-a
                    target: tech-a
                """);

        assertThat(manifest.include()).singleElement().satisfies(entry -> {
            assertThat(entry.category()).isEqualTo("technical");
            assertThat(entry.defaultState()).isEqualTo("enabled");
            assertThat(entry.order()).isEqualTo(1);
            assertThat(entry.artifactMappings()).hasSize(2);
            assertThat(entry.artifactMappings().get(1).source()).isEqualTo("environment");
            assertThat(entry.artifactMappings().get(1).secret()).isTrue();
        });
        assertThat(manifest.conceptualNodes()).singleElement().satisfies(node -> {
            assertThat(node.groupType()).isEqualTo("alternative");
            assertThat(node.order()).isEqualTo(2);
        });
        assertThat(manifest.constraints()).singleElement().satisfies(constraint -> assertThat(constraint.type()).isEqualTo("excludes"));
    }

    @Test
    void rejectsGroupTypeOnNonGroupNode() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                conceptualNodes:
                  - id: always-on
                    kind: module
                    groupType: alternative
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("groupType is only allowed on nodes of kind 'group'");
    }

    @Test
    void rejectsUnknownConstraintType() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include: [{ anchor: module:alpha, id: alpha }]
                constraints:
                  - { id: bad, type: implies, source: alpha, target: alpha }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("constraints[0].type must be one of");
    }

    @Test
    void rejectsConstraintReferencingUndeclaredFeature() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include: [{ anchor: module:alpha, id: alpha }]
                constraints:
                  - { id: bad, type: requires, source: alpha, target: ghost }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("references undeclared parent/group 'ghost'");
    }

    @Test
    void loadsExplicitRenameWithRationale() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include: [{ anchor: module:alpha, id: alpha-renamed }]
                renames:
                  - from: alpha
                    to: alpha-renamed
                    rationale: The module kept its semantics.
                """);

        assertThat(manifest.renames()).singleElement().satisfies(rename -> {
            assertThat(rename.from()).isEqualTo("alpha");
            assertThat(rename.to()).isEqualTo("alpha-renamed");
            assertThat(rename.rationale()).isEqualTo("The module kept its semantics.");
        });
    }

    @Test
    void rejectsRenameWithoutRationale() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include: [{ anchor: module:alpha, id: alpha-renamed }]
                renames: [{ from: alpha, to: alpha-renamed }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("renames[0].rationale");
    }

    @Test
    void rejectsDuplicateRenameSource() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include:
                  - { anchor: module:alpha, id: alpha-renamed }
                  - { anchor: module:beta, id: beta-renamed }
                renames:
                  - { from: old, to: alpha-renamed, rationale: First }
                  - { from: old, to: beta-renamed, rationale: Second }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate rename source 'old'");
    }

    @Test
    void rejectsConflictingRenameTarget() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include: [{ anchor: module:alpha, id: alpha-renamed }]
                renames:
                  - { from: old-alpha, to: alpha-renamed, rationale: First }
                  - { from: other-alpha, to: alpha-renamed, rationale: Second }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate rename target 'alpha-renamed'");
    }

    @Test
    void rejectsSelfRename() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include: [{ anchor: module:alpha, id: alpha }]
                renames: [{ from: alpha, to: alpha, rationale: Invalid }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("source and target must differ");
    }

    @Test
    void rejectsUnknownRenameTarget() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                renames: [{ from: alpha, to: ghost, rationale: Invalid }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("target 'ghost' is not a current manifest-declared id");
    }

    @Test
    void rejectsRenameFromCurrentIdAndChainedMappings() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include:
                  - { anchor: module:alpha, id: alpha }
                  - { anchor: module:beta, id: beta }
                renames:
                  - { from: old-alpha, to: alpha, rationale: First }
                  - { from: alpha, to: beta, rationale: Chained }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("source 'alpha' is still a current manifest-declared id");
    }

    @Test
    void rejectsNonPositiveOrder() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include:
                  - anchor: module:alpha
                    id: alpha
                    order: 0
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("include[0].order must be a positive integer");
    }

    @Test
    void rejectsTheRetiredProfileValueMappingShape() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include:
                  - anchor: module:alpha
                    id: alpha
                    artifactMappings:
                      - { target: application-feature-model.yml, path: artemis.alpha.url, valueFromProfile: artemis.alpha.url, requiredWhenSelected: true }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("unknown field").hasMessageContaining("valueFromProfile");
    }

    @Test
    void rejectsAMappingWithoutASource() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include:
                  - anchor: module:alpha
                    id: alpha
                    artifactMappings:
                      - { target: application-feature-model.yml, path: artemis.alpha.url }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("source");
    }

    @Test
    void rejectsAnUnknownMappingSource() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include:
                  - anchor: module:alpha
                    id: alpha
                    artifactMappings:
                      - { target: application-feature-model.yml, path: artemis.alpha.url, source: profile }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("source must be one of");
    }

    @Test
    void rejectsASelectionMappingWithoutAnyValue() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include:
                  - anchor: module:alpha
                    id: alpha
                    artifactMappings:
                      - { target: application-feature-model.yml, path: artemis.alpha.enabled, source: selection }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("selection");
    }

    @Test
    void rejectsAnEnvironmentMappingCarryingASelectionValue() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 2
                artemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee
                include:
                  - anchor: module:alpha
                    id: alpha
                    artifactMappings:
                      - { target: application-feature-model.yml, path: artemis.alpha.url, source: environment, valueWhenSelected: on }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("environment");
    }

    private FeatureScopeManifest load(String yaml) {
        if (!yaml.contains("artemisImageDigest:")) {
            yaml = yaml.replaceFirst("artemisCommitSha: ([^\\n]+)\\n", "artemisCommitSha: $1\nartemisImageDigest: latest\n");
        }
        return loader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test manifest");
    }
}
