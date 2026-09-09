package de.tum.cit.aet.artemis.featuremodel.extraction.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureManifestException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;

/** Verifies schema-level loading and controlled failures of the version 4 YAML scope manifest. */
class FeatureManifestLoaderTest {

    private final FeatureManifestLoader loader = new FeatureManifestLoader();

    @Test
    void loadsValidManifest() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 4
                provisional:
                  - anchor: module:alpha
                    id: alpha
                features:
                  - id: alpha
                    parent: root
                    requiresCapabilities: [alpha-service]
                notModeled:
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

        assertThat(manifest.provisional()).singleElement().satisfies(entry -> {
            assertThat(entry.anchor()).isEqualTo("module:alpha");
            assertThat(entry.id()).isEqualTo("alpha");
        });
        assertThat(manifest.features()).singleElement().satisfies(entry -> {
            assertThat(entry.id()).isEqualTo("alpha");
            assertThat(entry.optionality()).isNull();
            assertThat(entry.requiresCapabilities()).containsExactly("alpha-service");
        });
        assertThat(manifest.notModeled()).singleElement().satisfies(entry -> assertThat(entry.reason()).isEqualTo("operational"));
        assertThat(manifest.conceptualNodes()).anySatisfy(node -> {
            assertThat(node.id()).isEqualTo("always-on");
            assertThat(node.optionality()).isEqualTo(FeatureScopeManifest.OPTIONALITY_MANDATORY);
        });
    }

    @Test
    void loadsAnEmptyV4Manifest() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 4
                """);

        assertThat(manifest.manifestVersion()).isEqualTo(FeatureScopeManifest.CURRENT_VERSION);
        assertThat(manifest.features()).isEmpty();
        assertThat(manifest.provisional()).isEmpty();
        assertThat(manifest.technical()).isEmpty();
        assertThat(manifest.notModeled()).isEmpty();
    }

    @Test
    void rejectsTheRetiredIncludeSectionWithAMigrationMessage() {
        assertThatThrownBy(() -> load("manifestVersion: 4\ninclude: [{ anchor: module:alpha, id: alpha }]\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("root.include was removed in manifestVersion 4")
                .hasMessageContaining("@ArtemisFeature").hasMessageContaining("'provisional'").hasMessageContaining("'technical'").hasMessageContaining("'features'");
    }

    @Test
    void rejectsTheRetiredExcludeSectionWithAMigrationMessage() {
        assertThatThrownBy(() -> load("manifestVersion: 4\nexclude: [{ anchor: module:alpha, reason: deferred }]\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("root.exclude was removed in manifestVersion 4")
                .hasMessageContaining("'notModeled'");
    }

    @Test
    void rejectsTheRetiredArtemisCommitShaFieldWithAMigrationMessage() {
        assertThatThrownBy(() -> load("manifestVersion: 4\nartemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("artemisCommitSha")
                .hasMessageContaining("removed in manifestVersion 3").hasMessageContaining("derived from the verified Artemis checkout");
    }

    @Test
    void rejectsTheRetiredArtemisImageDigestFieldWithAMigrationMessage() {
        assertThatThrownBy(() -> load("manifestVersion: 4\nartemisImageDigest: latest\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("artemisImageDigest")
                .hasMessageContaining("removed in manifestVersion 3").hasMessageContaining("delivery/artemis-runtime-image.json");
    }

    @Test
    void rejectsAnUnsupportedManifestVersion() {
        assertThatThrownBy(() -> load("manifestVersion: 3\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("Unsupported manifestVersion 3");
    }

    @Test
    void rejectsAnAnchorOnAFeaturesEntry() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features:
                  - anchor: module:alpha
                    id: alpha
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("features[0] contains unknown field(s): anchor");
    }

    @Test
    void rejectsSemanticsOnAProvisionalEntry() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                provisional:
                  - anchor: module:alpha
                    id: alpha
                    group: some-group
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("provisional[0] contains unknown field(s): group");
    }

    @Test
    void rejectsInvalidOptionalityValue() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features:
                  - id: alpha
                    optionality: required
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("features[0].optionality must be one of");
    }

    @Test
    void rejectsDuplicateAnchorsAcrossMembershipSections() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                provisional: [{ anchor: module:alpha, id: alpha }]
                features: [{ id: alpha }]
                notModeled: [{ anchor: module:alpha, reason: duplicate }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate manifest anchor 'module:alpha'");
    }

    @Test
    void rejectsDuplicateAnchorsBetweenTechnicalAndNotModeled() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                technical: [{ anchor: infra:mysql, id: mysql }]
                notModeled: [{ anchor: infra:mysql, reason: duplicate }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate manifest anchor 'infra:mysql'");
    }

    @Test
    void rejectsDuplicateIdsAcrossFeaturesTechnicalAndConceptualNodes() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features: [{ id: shared }]
                conceptualNodes: [{ id: shared, kind: group }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate manifest id 'shared'");
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features: [{ id: shared }]
                technical: [{ anchor: infra:shared, id: shared }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate manifest id 'shared'");
    }

    @Test
    void acceptsAProvisionalIdThatMatchesItsFeaturesEntryButRejectsOneNamingATechnicalId() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 4
                provisional: [{ anchor: module:alpha, id: alpha }]
                features: [{ id: alpha }]
                """);
        assertThat(manifest.provisional()).singleElement().satisfies(entry -> assertThat(entry.id()).isEqualTo("alpha"));

        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                provisional: [{ anchor: module:alpha, id: mysql }]
                technical: [{ anchor: infra:mysql, id: mysql }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Provisional id 'mysql' collides");
    }

    @Test
    void rejectsDuplicateProvisionalIds() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                provisional:
                  - { anchor: module:alpha, id: alpha }
                  - { anchor: module:beta, id: alpha }
                features: [{ id: alpha }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate provisional id 'alpha'");
    }

    @Test
    void normalizesMissingNotModeledReasonToUnspecified() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 4
                notModeled: [{ anchor: toggle:RateLimit }]
                """);

        assertThat(manifest.notModeled()).singleElement().satisfies(entry -> {
            assertThat(entry.reason()).isEqualTo(FeatureScopeManifest.EXCLUSION_REASON_UNSPECIFIED);
            assertThat(entry.rationale()).isNull();
        });
    }

    @Test
    void rejectsBlankNotModeledReasonWhenPresent() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                notModeled: [{ anchor: toggle:RateLimit, reason: "" }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("notModeled[0].reason");
    }

    @Test
    void rejectsUnknownRootField() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                pending: [module:alpha]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("unknown field(s): pending");
    }

    @Test
    void rejectsUndeclaredGroupReferenceOnFeaturesAndTechnicalEntries() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features:
                  - id: alpha
                    group: missing-group
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("'alpha' references undeclared parent/group 'missing-group'");
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                technical:
                  - anchor: infra:mysql
                    id: mysql
                    group: missing-group
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("'mysql' references undeclared parent/group 'missing-group'");
    }

    @Test
    void loadsTechnicalSemanticsAndConstraints() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 4
                technical:
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

        assertThat(manifest.technical()).singleElement().satisfies(entry -> {
            assertThat(entry.anchor()).isEqualTo("infra:tech-a");
            assertThat(entry.feature().id()).isEqualTo("tech-a");
            assertThat(entry.feature().category()).isEqualTo("technical");
            assertThat(entry.feature().defaultState()).isEqualTo("enabled");
            assertThat(entry.feature().order()).isEqualTo(1);
            assertThat(entry.feature().artifactMappings()).hasSize(2);
            assertThat(entry.feature().artifactMappings().get(1).source()).isEqualTo("environment");
            assertThat(entry.feature().artifactMappings().get(1).secret()).isTrue();
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
                manifestVersion: 4
                conceptualNodes:
                  - id: always-on
                    kind: module
                    groupType: alternative
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("groupType is only allowed on nodes of kind 'group'");
    }

    @Test
    void rejectsUnknownConstraintType() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features: [{ id: alpha }]
                constraints:
                  - { id: bad, type: implies, source: alpha, target: alpha }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("constraints[0].type must be one of");
    }

    @Test
    void rejectsConstraintReferencingUndeclaredFeature() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features: [{ id: alpha }]
                constraints:
                  - { id: bad, type: requires, source: alpha, target: ghost }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("references undeclared parent/group 'ghost'");
    }

    @Test
    void loadsExplicitRenameWithRationale() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 4
                features: [{ id: alpha-renamed }]
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
                manifestVersion: 4
                features: [{ id: alpha-renamed }]
                renames: [{ from: alpha, to: alpha-renamed }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("renames[0].rationale");
    }

    @Test
    void rejectsDuplicateRenameSource() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features:
                  - { id: alpha-renamed }
                  - { id: beta-renamed }
                renames:
                  - { from: old, to: alpha-renamed, rationale: First }
                  - { from: old, to: beta-renamed, rationale: Second }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate rename source 'old'");
    }

    @Test
    void rejectsConflictingRenameTarget() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features: [{ id: alpha-renamed }]
                renames:
                  - { from: old-alpha, to: alpha-renamed, rationale: First }
                  - { from: other-alpha, to: alpha-renamed, rationale: Second }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate rename target 'alpha-renamed'");
    }

    @Test
    void rejectsSelfRename() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features: [{ id: alpha }]
                renames: [{ from: alpha, to: alpha, rationale: Invalid }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("source and target must differ");
    }

    @Test
    void rejectsUnknownRenameTarget() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                renames: [{ from: alpha, to: ghost, rationale: Invalid }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("target 'ghost' is not a current manifest-declared id");
    }

    @Test
    void rejectsRenameFromCurrentIdAndChainedMappings() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features:
                  - { id: alpha }
                  - { id: beta }
                renames:
                  - { from: old-alpha, to: alpha, rationale: First }
                  - { from: alpha, to: beta, rationale: Chained }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("source 'alpha' is still a current manifest-declared id");
    }

    @Test
    void rejectsNonPositiveOrder() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features:
                  - id: alpha
                    order: 0
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("features[0].order must be a positive integer");
    }

    @Test
    void rejectsTheRetiredProfileValueMappingShape() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features:
                  - id: alpha
                    artifactMappings:
                      - { target: application-feature-model.yml, path: artemis.alpha.url, valueFromProfile: artemis.alpha.url, requiredWhenSelected: true }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("unknown field").hasMessageContaining("valueFromProfile");
    }

    @Test
    void rejectsAMappingWithoutASource() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features:
                  - id: alpha
                    artifactMappings:
                      - { target: application-feature-model.yml, path: artemis.alpha.url }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("source");
    }

    @Test
    void rejectsAnUnknownMappingSource() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features:
                  - id: alpha
                    artifactMappings:
                      - { target: application-feature-model.yml, path: artemis.alpha.url, source: profile }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("source must be one of");
    }

    @Test
    void rejectsASelectionMappingWithoutAnyValue() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features:
                  - id: alpha
                    artifactMappings:
                      - { target: application-feature-model.yml, path: artemis.alpha.enabled, source: selection }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("selection");
    }

    @Test
    void rejectsAnEnvironmentMappingCarryingASelectionValue() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 4
                features:
                  - id: alpha
                    artifactMappings:
                      - { target: application-feature-model.yml, path: artemis.alpha.url, source: environment, valueWhenSelected: on }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("environment");
    }

    private FeatureScopeManifest load(String yaml) {
        return loader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test manifest");
    }
}
