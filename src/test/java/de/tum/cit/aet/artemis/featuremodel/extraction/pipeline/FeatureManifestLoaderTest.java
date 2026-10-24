package de.tum.cit.aet.artemis.featuremodel.extraction.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureManifestException;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;

/** Verifies schema-level loading and controlled failures of the version 5 YAML scope manifest. */
class FeatureManifestLoaderTest {

    private final FeatureManifestLoader loader = new FeatureManifestLoader();

    @Test
    void loadsValidManifest() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 5
                features:
                  - id: alpha
                    parent: root
                    configuration:
                      - { key: artemis.alpha.url }
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

        assertThat(manifest.features()).singleElement().satisfies(entry -> {
            assertThat(entry.id()).isEqualTo("alpha");
            assertThat(entry.optionality()).isNull();
            assertThat(entry.configuration()).singleElement().satisfies(configuration -> assertThat(configuration.key()).isEqualTo("artemis.alpha.url"));
        });
        assertThat(manifest.notModeled()).singleElement().satisfies(entry -> assertThat(entry.reason()).isEqualTo("operational"));
        assertThat(manifest.conceptualNodes()).anySatisfy(node -> {
            assertThat(node.id()).isEqualTo("always-on");
            assertThat(node.optionality()).isEqualTo(FeatureScopeManifest.OPTIONALITY_MANDATORY);
        });
        assertThat(manifest.declaresRoot()).isTrue();
    }

    @Test
    void loadsAnEmptyV5ManifestWithoutADeclaredRoot() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 5
                """);

        assertThat(manifest.manifestVersion()).isEqualTo(FeatureScopeManifest.CURRENT_VERSION);
        assertThat(manifest.features()).isEmpty();
        assertThat(manifest.technical()).isEmpty();
        assertThat(manifest.notModeled()).isEmpty();
        assertThat(manifest.declaresRoot()).isFalse();
    }

    @Test
    void acceptsReferencesToTheImplicitRootWhenNoRootIsDeclared() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 5
                features:
                  - id: alpha
                    parent: artemis
                technical:
                  - anchor: profile:localvc
                    id: localvc
                    parent: artemis
                conceptualNodes:
                  - id: content
                    parent: artemis
                    kind: group
                """);

        assertThat(manifest.declaresRoot()).isFalse();
        assertThat(manifest.conceptualNodes()).singleElement().satisfies(node -> assertThat(node.parent()).isEqualTo(FeatureScopeManifest.IMPLICIT_ROOT_ID));
    }

    @Test
    void rejectsAnEntryNamedLikeTheImplicitRootWhenNoRootIsDeclared() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                conceptualNodes:
                  - id: artemis
                    kind: group
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("'artemis' collides with the implicit root");
    }

    @Test
    void rejectsTheRetiredIncludeSectionWithAMigrationMessage() {
        assertThatThrownBy(() -> load("manifestVersion: 5\ninclude: [{ anchor: module:alpha, id: alpha }]\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("root.include was removed in manifestVersion 4")
                .hasMessageContaining("'features'").hasMessageContaining("'technical'");
    }

    @Test
    void rejectsTheRetiredProvisionalSectionWithAMigrationMessage() {
        assertThatThrownBy(() -> load("manifestVersion: 4\nprovisional: [{ anchor: module:alpha, id: alpha }]\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("root.provisional was removed in manifestVersion 5")
                .hasMessageContaining("'features'").hasMessageContaining("module id");
    }

    @Test
    void rejectsTheRetiredRenamesSectionWithAMigrationMessage() {
        assertThatThrownBy(() -> load("manifestVersion: 5\nrenames: [{ from: alpha, to: beta, rationale: Renamed }]\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("root.renames was removed in manifestVersion 5")
                .hasMessageContaining("guided workflow");
    }

    @Test
    void rejectsTheRetiredExcludeSectionWithAMigrationMessage() {
        assertThatThrownBy(() -> load("manifestVersion: 5\nexclude: [{ anchor: module:alpha, reason: deferred }]\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("root.exclude was removed in manifestVersion 4")
                .hasMessageContaining("'notModeled'");
    }

    @Test
    void rejectsTheRetiredArtemisCommitShaFieldWithAMigrationMessage() {
        assertThatThrownBy(() -> load("manifestVersion: 5\nartemisCommitSha: aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("artemisCommitSha")
                .hasMessageContaining("removed in manifestVersion 3").hasMessageContaining("derived from the verified Artemis checkout");
    }

    @Test
    void rejectsTheRetiredArtemisImageDigestFieldWithAMigrationMessage() {
        assertThatThrownBy(() -> load("manifestVersion: 5\nartemisImageDigest: latest\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("artemisImageDigest")
                .hasMessageContaining("removed in manifestVersion 3").hasMessageContaining("delivery/artemis-runtime-image.json");
    }

    @Test
    void rejectsUnsupportedManifestVersionsWithTheV5Message() {
        assertThatThrownBy(() -> load("manifestVersion: 3\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("Unsupported manifestVersion 3").hasMessageContaining("expected 5");
        assertThatThrownBy(() -> load("manifestVersion: 4\n"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("Unsupported manifestVersion 4").hasMessageContaining("Artemis module id");
    }

    @Test
    void rejectsAnAnchorOnAFeaturesEntryWithAMigrationMessage() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                features:
                  - anchor: module:alpha
                    id: alpha
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("features[0].anchor was removed in manifestVersion 5")
                .hasMessageContaining("module:<id>");
    }

    @Test
    void rejectsTheRetiredCapabilityFieldsWithMigrationMessages() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                features:
                  - id: alpha
                    requiresCapabilities: [alpha-service]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("features[0].requiresCapabilities was removed in manifestVersion 5")
                .hasMessageContaining("<id>-service").hasMessageContaining("<id>-secret");
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                technical:
                  - anchor: infra:mysql
                    id: mysql
                    providesCapabilities: [default-database]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("technical[0].providesCapabilities was removed in manifestVersion 5")
                .hasMessageContaining("bundled deployment profile");
    }

    @Test
    void rejectsArtifactMappingsOnFeaturesAndTechnicalEntriesWithAMigrationMessage() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                features:
                  - id: alpha
                    artifactMappings:
                      - { target: application-feature-model.yml, path: artemis.alpha.url, source: environment }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("features[0].artifactMappings was removed in manifestVersion 5")
                .hasMessageContaining("derived").hasMessageContaining("'configuration'");
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                technical:
                  - anchor: infra:mysql
                    id: mysql
                    artifactMappings:
                      - { target: docker-compose.override.yml, path: database.composeFile, source: selection, valueWhenSelected: docker/mysql.yml }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("technical[0].artifactMappings was removed in manifestVersion 5")
                .hasMessageContaining("'profiles'");
    }

    @Test
    void rejectsImpliedKindAndCategoryOnATechnicalEntry() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                technical:
                  - anchor: infra:mysql
                    id: mysql
                    kind: feature
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("technical[0].kind was removed in manifestVersion 5")
                .hasMessageContaining("implied");
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                technical:
                  - anchor: infra:mysql
                    id: mysql
                    category: technical
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("technical[0].category was removed in manifestVersion 5");
    }

    @Test
    void rejectsKindOnAFeaturesEntryAsAnUnknownField() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                features:
                  - id: alpha
                    kind: module
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("features[0] contains unknown field(s): kind");
    }

    @Test
    void rejectsInvalidOptionalityValue() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                features:
                  - id: alpha
                    optionality: required
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("features[0].optionality must be one of");
    }

    @Test
    void rejectsDuplicateAnchorsBetweenTheImpliedFeaturesAnchorAndNotModeled() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                features: [{ id: alpha }]
                notModeled: [{ anchor: module:alpha, reason: duplicate }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate manifest anchor 'module:alpha'");
    }

    @Test
    void rejectsDuplicateAnchorsBetweenTechnicalAndNotModeled() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                technical: [{ anchor: infra:mysql, id: mysql }]
                notModeled: [{ anchor: infra:mysql, reason: duplicate }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate manifest anchor 'infra:mysql'");
    }

    @Test
    void rejectsDuplicateIdsAcrossFeaturesTechnicalAndConceptualNodes() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                features: [{ id: shared }]
                conceptualNodes: [{ id: shared, kind: group }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate manifest id 'shared'");
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                features: [{ id: shared }]
                technical: [{ anchor: infra:shared, id: shared }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("Duplicate manifest id 'shared'");
    }

    @Test
    void normalizesMissingNotModeledReasonToUnspecified() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 5
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
                manifestVersion: 5
                notModeled: [{ anchor: toggle:RateLimit, reason: "" }]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("notModeled[0].reason");
    }

    @Test
    void rejectsUnknownRootField() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                pending: [module:alpha]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("unknown field(s): pending");
    }

    @Test
    void rejectsUndeclaredGroupReferenceOnFeaturesAndTechnicalEntries() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                features:
                  - id: alpha
                    group: missing-group
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("'alpha' references undeclared parent/group 'missing-group'");
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                technical:
                  - anchor: infra:mysql
                    id: mysql
                    group: missing-group
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("'mysql' references undeclared parent/group 'missing-group'");
    }

    @Test
    void loadsTechnicalSemanticsProfilesConfigurationAndConstraints() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 5
                technical:
                  - anchor: profile:tech-a
                    id: tech-a
                    group: tech-group
                    defaultState: enabled
                    order: 1
                    profiles: [tech-a, tech-agent]
                    configuration:
                      - { key: artemis.tech.url }
                      - { key: artemis.tech.secret, secret: true }
                      - { key: artemis.tech.callback, action: exclude }
                  - anchor: profile:tech-b
                    id: tech-b
                    group: tech-group
                    order: 2
                conceptualNodes:
                  - id: tech-group
                    kind: group
                    category: technical
                    groupType: alternative
                    order: 2
                constraints:
                  - id: tech-a-requires-tech-b
                    type: requires
                    source: tech-a
                    target: tech-b
                """);

        assertThat(manifest.technical()).hasSize(2).first().satisfies(entry -> {
            assertThat(entry.anchor()).isEqualTo("profile:tech-a");
            assertThat(entry.feature().id()).isEqualTo("tech-a");
            assertThat(entry.feature().defaultState()).isEqualTo("enabled");
            assertThat(entry.feature().order()).isEqualTo(1);
            assertThat(entry.profiles()).containsExactly("tech-a", "tech-agent");
            assertThat(entry.feature().configuration()).hasSize(3);
            assertThat(entry.feature().configuration().get(1).secret()).isTrue();
            assertThat(entry.feature().configuration().getLast().action()).isEqualTo(FeatureScopeManifest.CONFIGURATION_ACTION_EXCLUDE);
        });
        assertThat(manifest.technical().getLast().profiles()).isEmpty();
        assertThat(manifest.conceptualNodes()).singleElement().satisfies(node -> {
            assertThat(node.groupType()).isEqualTo(FeatureScopeManifest.GROUP_TYPE_ALTERNATIVE);
            assertThat(node.order()).isEqualTo(2);
        });
        assertThat(manifest.constraints()).singleElement().satisfies(constraint -> assertThat(constraint.type()).isEqualTo("requires"));
    }

    @Test
    void rejectsGroupTypeOnNonGroupNode() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                conceptualNodes:
                  - id: always-on
                    kind: module
                    groupType: alternative
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("groupType is only allowed on nodes of kind 'group'");
    }

    @Test
    void rejectsUnknownConstraintType() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                features: [{ id: alpha }]
                constraints:
                  - { id: bad, type: implies, source: alpha, target: alpha }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("constraints[0].type must be one of");
    }

    @Test
    void rejectsConstraintReferencingUndeclaredFeature() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                features: [{ id: alpha }]
                constraints:
                  - { id: bad, type: requires, source: alpha, target: ghost }
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("references undeclared parent/group 'ghost'");
    }

    @Test
    void rejectsNonPositiveOrder() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                features:
                  - id: alpha
                    order: 0
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("features[0].order must be a positive integer");
    }

    @Test
    void rejectsBlankProfileTokens() {
        assertThatThrownBy(() -> load("""
                manifestVersion: 5
                technical:
                  - anchor: profile:tech-a
                    id: tech-a
                    profiles: [tech-a, ""]
                """)).isInstanceOf(FeatureManifestException.class).hasMessageContaining("technical[0].profiles must contain only non-blank strings");
    }

    @Test
    void loadsConfigurationEntriesWithDefaultsAndActions() {
        FeatureScopeManifest manifest = load("""
                manifestVersion: 5
                features:
                  - id: alpha
                    configuration:
                      - { key: spring.ai.alpha.api-key, secret: true }
                      - { key: spring.ai.alpha.base-url }
                      - { key: artemis.alpha.callback-url, action: exclude }
                """);

        assertThat(manifest.features()).singleElement().satisfies(entry -> {
            assertThat(entry.configuration()).hasSize(3);
            assertThat(entry.configuration().getFirst().secret()).isTrue();
            assertThat(entry.configuration().getFirst().action()).isNull();
            assertThat(entry.configuration().get(1).secret()).isNull();
            assertThat(entry.configuration().getLast().action()).isEqualTo(FeatureScopeManifest.CONFIGURATION_ACTION_EXCLUDE);
        });
    }

    @Test
    void rejectsAConfigurationEntryWithAnUnknownField() {
        assertThatThrownBy(() -> featuresWithConfiguration("{ key: artemis.alpha.url, target: application-feature-model.yml }"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("configuration[0] contains unknown field(s): target");
    }

    @Test
    void rejectsAConfigurationEntryRepeatingAKey() {
        assertThatThrownBy(() -> featuresWithConfiguration("{ key: artemis.alpha.url }\n      - { key: artemis.alpha.url, secret: true }"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("repeats key 'artemis.alpha.url'");
    }

    @Test
    void rejectsAConfigurationEntryWithAnUnknownAction() {
        assertThatThrownBy(() -> featuresWithConfiguration("{ key: artemis.alpha.url, action: veto }"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("configuration[0].action must be one of");
    }

    @Test
    void rejectsAConfigurationExcludeEntryCarryingASecretFlag() {
        assertThatThrownBy(() -> featuresWithConfiguration("{ key: artemis.alpha.url, action: exclude, secret: true }"))
                .isInstanceOf(FeatureManifestException.class).hasMessageContaining("a rejected key is never emitted");
    }

    /**
     * Loads a manifest with one features entry carrying configuration entries.
     *
     * @param configuration inline YAML configuration entries, first entry without the list dash.
     * @return parsed manifest.
     */
    private FeatureScopeManifest featuresWithConfiguration(String configuration) {
        return load("""
                manifestVersion: 5
                features:
                  - id: alpha
                    configuration:
                      - %s
                """.formatted(configuration));
    }

    private FeatureScopeManifest load(String yaml) {
        return loader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test manifest");
    }
}
