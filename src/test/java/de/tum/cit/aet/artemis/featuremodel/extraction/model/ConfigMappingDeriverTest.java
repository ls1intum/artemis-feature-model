package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ConfigDerivationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ConfigDerivationReport.ConfigKeyResolution;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigInjection;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigInjection.ConfigurationPropertiesPrefix;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefault;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedConfigurationDefaults;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConfigurationEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.MappingHint;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;

/** Covers key attribution by guard, prefix, and namespace, the classification split, the precedence merge, technical mappings, and derived capabilities. */
class ConfigMappingDeriverTest {

    private static final String OVERLAY = "application-feature-model.yml";

    private final ConfigMappingDeriver deriver = new ConfigMappingDeriver();

    @Test
    void attributesInjectedKeysOnlyWhenEverySiteCarriesTheMembersGuard() {
        ExtractedConfigInjection guarded = injection("src/main/java/alpha/AlphaClient.java", List.of("AlphaEnabled"),
                List.of("artemis.alpha.token", "artemis.shared.instance-name"), List.of());
        ExtractedConfigInjection unguarded = injection("src/main/java/core/CoreConfiguration.java", List.of(), List.of("artemis.shared.instance-name"),
                List.of());

        ConfigMappingDeriver.Result result = deriver.derive(List.of(functionalAlpha(List.of())), List.of(guarded, unguarded),
                defaults(Map.of()), List.of(alphaCandidate()), List.of());

        assertThat(mappingPaths(result, "alpha")).containsExactly("artemis.alpha.token");
        assertThat(resolutionOf(result, "alpha", "artemis.alpha.token")).satisfies(resolution -> {
            assertThat(resolution.origin()).isEqualTo(ConfigDerivationReport.ORIGIN_DERIVED);
            assertThat(resolution.decision()).isEqualTo(ConfigDerivationReport.DECISION_DERIVED);
            assertThat(resolution.secret()).isTrue();
            assertThat(resolution.evidence()).singleElement().satisfies(evidence -> {
                assertThat(evidence.kind()).isEqualTo(EvidenceItem.KIND_USAGE_CONFIG_INJECTION);
                assertThat(evidence.file()).isEqualTo("src/main/java/alpha/AlphaClient.java");
            });
        });
        assertThat(result.derivation().members()).singleElement().satisfies(member -> assertThat(member.keys()).extracting(ConfigKeyResolution::key)
                .doesNotContain("artemis.shared.instance-name"));
    }

    @Test
    void collectsKeysUnderPrefixesOfGuardedFilesAndUnderTheEnabledKeyNamespace() {
        ExtractedConfigInjection properties = injection("src/main/java/alpha/AlphaProperties.java", List.of("AlphaEnabled"), List.of(),
                List.of(new ConfigurationPropertiesPrefix("artemis.alpha.connector", "AlphaProperties")));
        ExtractedConfigurationDefaults defaults = defaults(Map.of(
                "artemis.alpha.enabled", Boolean.TRUE,
                "artemis.alpha.url", "http://localhost:8000",
                "artemis.alpha.mode", "production",
                "artemis.alpha.connector.endpoint", "<your-endpoint>"));

        ConfigMappingDeriver.Result result = deriver.derive(List.of(functionalAlpha(List.of())), List.of(properties), defaults,
                List.of(alphaCandidate()), List.of());

        assertThat(mappingPaths(result, "alpha")).as("non-secret inputs sorted; the own enabled key never derives")
                .containsExactly("artemis.alpha.connector.endpoint", "artemis.alpha.url");
        assertThat(resolutionOf(result, "alpha", "artemis.alpha.connector.endpoint").evidence()).extracting(EvidenceItem::kind)
                .containsExactly(EvidenceItem.KIND_USAGE_CONFIG_PREFIX, EvidenceItem.KIND_USAGE_CONFIG_YAML);
        assertThat(resolutionOf(result, "alpha", "artemis.alpha.mode").decision()).isEqualTo(ConfigDerivationReport.DECISION_SKIPPED);
        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_CONFIG_MAPPING_TUNABLE_SKIPPED);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_INFO);
            assertThat(item.message()).contains("artemis.alpha.mode");
        });
    }

    @Test
    void neverAttributesAKeyOwnedByAnotherCandidatesNamespace() {
        ExtractedConfigurationDefaults defaults = defaults(Map.of(
                "artemis.alpha.url", "<your-alpha-url>",
                "artemis.alpha.nested.enabled", Boolean.FALSE,
                "artemis.alpha.nested.auth-token", "<your-token>"));
        FeatureCandidate nested = new FeatureCandidate("module:nested", FeatureCandidate.KIND_MODULE_FEATURE, null, null, null,
                "artemis.alpha.nested.enabled", Boolean.FALSE, null, null, "NestedEnabled", null, false, false, null);

        ConfigMappingDeriver.Result result = deriver.derive(List.of(functionalAlpha(List.of())), List.of(), defaults,
                List.of(alphaCandidate(), nested), List.of());

        assertThat(mappingPaths(result, "alpha")).containsExactly("artemis.alpha.url");
        assertThat(result.derivation().members()).singleElement().satisfies(member -> assertThat(member.keys()).extracting(ConfigKeyResolution::key)
                .doesNotContain("artemis.alpha.nested.auth-token", "artemis.alpha.nested.enabled"));
    }

    @Test
    void manifestEntriesConfirmAddAndRejectDerivedKeys() {
        ResolvedFeatureScope alpha = functionalAlpha(List.of(
                new ConfigurationEntry("artemis.alpha.chat-model", null, null),
                new ConfigurationEntry("spring.ai.alpha.api-key", true, FeatureScopeManifest.CONFIGURATION_ACTION_INCLUDE),
                new ConfigurationEntry("artemis.alpha.callback-url", null, FeatureScopeManifest.CONFIGURATION_ACTION_EXCLUDE)));
        ExtractedConfigurationDefaults defaults = defaults(Map.of(
                "artemis.alpha.chat-model", "gpt-4o",
                "artemis.alpha.url", "<your-url>",
                "artemis.alpha.callback-url", "http://localhost:7000"));

        ConfigMappingDeriver.Result result = deriver.derive(List.of(alpha), List.of(), defaults, List.of(alphaCandidate()), List.of());

        assertThat(mappingPaths(result, "alpha")).as("declared entries in order, then derived keys")
                .containsExactly("artemis.alpha.chat-model", "spring.ai.alpha.api-key", "artemis.alpha.url");
        assertThat(resolutionOf(result, "alpha", "artemis.alpha.chat-model")).satisfies(resolution -> {
            assertThat(resolution.decision()).isEqualTo(ConfigDerivationReport.DECISION_CONFIRMED);
            assertThat(resolution.detail()).contains("tunable");
        });
        assertThat(resolutionOf(result, "alpha", "artemis.alpha.callback-url")).satisfies(resolution -> {
            assertThat(resolution.decision()).isEqualTo(ConfigDerivationReport.DECISION_REJECTED);
            assertThat(resolution.origin()).isEqualTo(ConfigDerivationReport.ORIGIN_MANIFEST);
        });
        assertThat(mappingsOf(result, "alpha")).extracting(MappingHint::secret).containsExactly(null, Boolean.TRUE, null);
        assertThat(result.items()).anySatisfy(item -> assertThat(item.code()).isEqualTo(ReportItem.CODE_CONFIG_MAPPING_REJECTED))
                .anySatisfy(item -> assertThat(item.code()).isEqualTo(ReportItem.CODE_CONFIG_MAPPING_DERIVED));
    }

    @Test
    void derivedInputsEmitNonSecretKeysSortedBeforeSecretKeysSorted() {
        ExtractedConfigurationDefaults defaults = defaults(Map.of(
                "artemis.alpha.zeta-url", "<your-url>",
                "artemis.alpha.api-key", "dummy-key",
                "artemis.alpha.base-url", "http://localhost:8000",
                "artemis.alpha.secret", "<your-secret>"));

        ConfigMappingDeriver.Result result = deriver.derive(List.of(functionalAlpha(List.of())), List.of(), defaults,
                List.of(alphaCandidate()), List.of());

        assertThat(mappingPaths(result, "alpha")).containsExactly("artemis.alpha.base-url", "artemis.alpha.zeta-url", "artemis.alpha.api-key",
                "artemis.alpha.secret");
    }

    @Test
    void derivesRequiredCapabilitiesFromTheEmittedDeploymentInputs() {
        ExtractedConfigurationDefaults defaults = defaults(Map.of("artemis.alpha.url", "<your-url>", "artemis.alpha.api-key", "dummy-key"));

        ConfigMappingDeriver.Result result = deriver.derive(List.of(functionalAlpha(List.of())), List.of(), defaults, List.of(alphaCandidate()), List.of());

        assertThat(result.resolvedFeatures()).singleElement().satisfies(resolved -> assertThat(resolved.requiresCapabilities())
                .as("a non-secret input yields <id>-service, a secret input <id>-secret").containsExactly("alpha-service", "alpha-secret"));
    }

    @Test
    void aMemberWithoutEmittedInputsRequiresNoCapabilityAndAConfirmedSecretRequiresOnlyTheSecretOne() {
        ConfigMappingDeriver.Result none = deriver.derive(List.of(functionalAlpha(List.of())), List.of(), defaults(Map.of()), List.of(alphaCandidate()), List.of());
        ConfigMappingDeriver.Result secretOnly = deriver.derive(List.of(functionalAlpha(List.of(new ConfigurationEntry("spring.ai.alpha.api-key", true, null)))),
                List.of(), defaults(Map.of()), List.of(alphaCandidate()), List.of());

        assertThat(none.resolvedFeatures().getFirst().requiresCapabilities()).isEmpty();
        assertThat(secretOnly.resolvedFeatures().getFirst().requiresCapabilities()).containsExactly("alpha-secret");
    }

    @Test
    void derivesTheComposeMappingOfAnInfrastructureMemberFromItsBaseComposeFile() {
        ResolvedFeatureScope mysql = technical("infra:mysql", "mysql", "db-group", List.of(), List.of());
        FeatureCandidate candidate = new FeatureCandidate("infra:mysql", FeatureCandidate.KIND_INFRASTRUCTURE, null, null, null, null, null, null, null, null, null,
                null, null, null);
        List<EvidenceItem> evidence = List.of(
                new EvidenceItem("infra:mysql", EvidenceItem.KIND_COMPOSE_FILE, "docker/artemis-dev-mysql.yml", null, "mysql", "paired with docker/artemis-dev-postgres.yml"),
                new EvidenceItem("infra:mysql", EvidenceItem.KIND_COMPOSE_FILE, "docker/mysql.yml", null, "mysql", "paired with docker/postgres.yml"));

        ConfigMappingDeriver.Result result = deriver.derive(List.of(mysql), List.of(), defaults(Map.of()), List.of(candidate), evidence);

        assertThat(mappingsOf(result, "mysql")).singleElement().satisfies(mapping -> {
            assertThat(mapping.target()).isEqualTo("docker-compose.override.yml");
            assertThat(mapping.path()).isEqualTo("db-group.composeFile");
            assertThat(mapping.source()).isEqualTo("selection");
            assertThat(mapping.valueWhenSelected()).isEqualTo("docker/mysql.yml");
        });
        assertThat(result.resolvedFeatures().getFirst().requiresCapabilities()).isEmpty();
        assertThat(result.derivation().members()).singleElement().satisfies(member -> assertThat(member.keys()).isEmpty());
        assertThat(result.items()).isEmpty();
    }

    @Test
    void reportsAnInfrastructureMemberWithoutABaseComposeFileAsUnderivable() {
        ResolvedFeatureScope mysql = technical("infra:mysql", "mysql", "db-group", List.of(), List.of());
        FeatureCandidate candidate = new FeatureCandidate("infra:mysql", FeatureCandidate.KIND_INFRASTRUCTURE, null, null, null, null, null, null, null, null, null,
                null, null, null);
        List<EvidenceItem> evidence = List.of(new EvidenceItem("infra:mysql", EvidenceItem.KIND_COMPOSE_FILE, "docker/artemis-dev-mysql.yml", null, "mysql", null));

        ConfigMappingDeriver.Result result = deriver.derive(List.of(mysql), List.of(), defaults(Map.of()), List.of(candidate), evidence);

        assertThat(mappingsOf(result, "mysql")).isEmpty();
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_TECHNICAL_MAPPING_UNDERIVABLE);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("mysql");
        });
    }

    @Test
    void derivesProfileTokensAndProfileGuardedEnvironmentKeysOfATechnicalMember() {
        ResolvedFeatureScope jenkins = technical("profile:jenkins", "jenkins", "ci-group", List.of(), List.of());
        ExtractedConfigInjection guarded = new ExtractedConfigInjection("src/main/java/jenkins/JenkinsService.java", "de.tum.cit.aet.artemis", List.of(),
                List.of("PROFILE_JENKINS"), List.of("artemis.ci.password", "artemis.ci.url", "server.url"), List.of());
        ExtractedConfigInjection unguarded = new ExtractedConfigInjection("src/main/java/core/CoreConfiguration.java", "de.tum.cit.aet.artemis", List.of(),
                List.of(), List.of("server.url"), List.of());
        ExtractedConfigurationDefaults defaults = defaults(Map.of("artemis.ci.url", "<url>", "artemis.ci.password", "<password>", "server.url", "http://localhost"));

        ConfigMappingDeriver.Result result = deriver.derive(List.of(jenkins), List.of(guarded, unguarded), defaults, List.of(profileCandidate("jenkins")), List.of());

        assertThat(mappingPaths(result, "jenkins")).as("profile tokens first, then derived non-secret and secret inputs; the cross-cutting server URL stays out")
                .containsExactly("SPRING_PROFILES_ACTIVE", "artemis.ci.url", "artemis.ci.password");
        assertThat(mappingsOf(result, "jenkins").getFirst()).satisfies(mapping -> {
            assertThat(mapping.target()).isEqualTo(".env");
            assertThat(mapping.valueWhenSelected()).isEqualTo("jenkins");
        });
        assertThat(mappingsOf(result, "jenkins").getLast().secret()).isTrue();
        assertThat(resolutionOf(result, "jenkins", "artemis.ci.url").evidence()).anySatisfy(evidence -> assertThat(evidence.detail()).contains("@Profile(PROFILE_JENKINS)"));
        assertThat(result.resolvedFeatures().getFirst().requiresCapabilities()).as("technical members carry no capabilities").isEmpty();
    }

    @Test
    void joinsDeclaredProfilesAndEmitsTechnicalConfirmationsBeforeDerivedKeys() {
        ResolvedFeatureScope localci = technical("profile:localci", "integrated-code-lifecycle", "ci-group", List.of("localci", "buildagent"),
                List.of(new ConfigurationEntry("artemis.ci.token", true, null)));
        ExtractedConfigInjection guarded = new ExtractedConfigInjection("src/main/java/localci/LocalCiService.java", "de.tum.cit.aet.artemis", List.of(),
                List.of("PROFILE_LOCALCI"), List.of("artemis.ci.url"), List.of());

        ConfigMappingDeriver.Result result = deriver.derive(List.of(localci), List.of(guarded), defaults(Map.of("artemis.ci.url", "<url>")),
                List.of(profileCandidate("localci")), List.of());

        assertThat(mappingPaths(result, "integrated-code-lifecycle")).containsExactly("SPRING_PROFILES_ACTIVE", "artemis.ci.token", "artemis.ci.url");
        assertThat(mappingsOf(result, "integrated-code-lifecycle").getFirst().valueWhenSelected()).isEqualTo("localci,buildagent");
        assertThat(resolutionOf(result, "integrated-code-lifecycle", "artemis.ci.token").decision()).isEqualTo(ConfigDerivationReport.DECISION_CONFIRMED);
    }

    /**
     * Builds a technical member.
     *
     * @param candidateId anchor candidate id.
     * @param id feature id.
     * @param group group placement.
     * @param profiles declared profile tokens.
     * @param configuration manifest configuration entries.
     * @return resolved technical member.
     */
    private ResolvedFeatureScope technical(String candidateId, String id, String group, List<String> profiles, List<ConfigurationEntry> configuration) {
        return new ResolvedFeatureScope(candidateId, id, group, null, "feature", "optional", "technical", "enabled", 1, List.of(), profiles, List.of(),
                configuration, null, null, null, CurationReport.SOURCE_TECHNICAL);
    }

    /**
     * Builds a Spring profile candidate whose server constant follows the Artemis naming convention.
     *
     * @param profile profile name.
     * @return profile candidate.
     */
    private FeatureCandidate profileCandidate(String profile) {
        return new FeatureCandidate("profile:" + profile, FeatureCandidate.KIND_SPRING_PROFILE, null, null, null, null, null, "PROFILE_" + profile.toUpperCase(),
                null, null, profile, null, null, null);
    }

    /**
     * Builds the alpha module candidate with condition class and enabled key.
     *
     * @return alpha candidate.
     */
    private FeatureCandidate alphaCandidate() {
        return new FeatureCandidate("module:alpha", FeatureCandidate.KIND_MODULE_FEATURE, "Alpha", null, null, "artemis.alpha.enabled", Boolean.TRUE,
                "ALPHA_ENABLED_PROPERTY_NAME", "MODULE_FEATURE_ALPHA", "AlphaEnabled", null, true, true, null);
    }

    /**
     * Builds the alpha member with features-entry membership.
     *
     * @param configuration manifest configuration entries.
     * @return resolved alpha member.
     */
    private ResolvedFeatureScope functionalAlpha(List<ConfigurationEntry> configuration) {
        return alphaMember(configuration, CurationReport.SOURCE_FEATURES);
    }

    private ResolvedFeatureScope alphaMember(List<ConfigurationEntry> configuration, String membershipSource) {
        return new ResolvedFeatureScope("module:alpha", "alpha", "group", null, "module", "optional", null, null, 1, List.of(), List.of(), List.of(),
                configuration, null, null, null, membershipSource);
    }

    /**
     * Builds one injection-site fact.
     *
     * @param file checkout-relative path.
     * @param guards condition guards of the file.
     * @param keys injected keys.
     * @param prefixes declared prefixes.
     * @return injection fact.
     */
    private ExtractedConfigInjection injection(String file, List<String> guards, List<String> keys, List<ConfigurationPropertiesPrefix> prefixes) {
        return new ExtractedConfigInjection(file, "de.tum.cit.aet.artemis", guards, List.of(), keys, prefixes);
    }

    /**
     * Builds a scanned-defaults index with one occurrence per key.
     *
     * @param values value per key.
     * @return configuration defaults.
     */
    private ExtractedConfigurationDefaults defaults(Map<String, Object> values) {
        Map<String, List<ExtractedConfigurationDefault>> occurrences = new LinkedHashMap<>();
        values.forEach((key, value) -> occurrences.put(key, List.of(new ExtractedConfigurationDefault("src/main/resources/config/application.yml", 3, value))));
        return new ExtractedConfigurationDefaults(occurrences, List.of());
    }

    private List<MappingHint> mappingsOf(ConfigMappingDeriver.Result result, String featureId) {
        return result.resolvedFeatures().stream().filter(feature -> feature.id().equals(featureId)).findFirst().orElseThrow().artifactMappings();
    }

    private List<String> mappingPaths(ConfigMappingDeriver.Result result, String featureId) {
        return mappingsOf(result, featureId).stream().map(MappingHint::path).toList();
    }

    private ConfigKeyResolution resolutionOf(ConfigMappingDeriver.Result result, String featureId, String key) {
        return result.derivation().members().stream().filter(member -> member.featureId().equals(featureId)).flatMap(member -> member.keys().stream())
                .filter(resolution -> resolution.key().equals(key)).findFirst().orElseThrow();
    }
}
