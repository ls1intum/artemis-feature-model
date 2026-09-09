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
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotation;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotationSemantics;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotationSemantics.ConfigurationDeclaration;
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

/** Covers key attribution by guard, prefix, and namespace, the classification split, and the precedence merge. */
class ConfigMappingDeriverTest {

    private static final String OVERLAY = "application-feature-model.yml";

    private final ConfigMappingDeriver deriver = new ConfigMappingDeriver();

    @Test
    void attributesInjectedKeysOnlyWhenEverySiteCarriesTheMembersGuard() {
        ExtractedConfigInjection guarded = injection("src/main/java/alpha/AlphaClient.java", List.of("AlphaEnabled"),
                List.of("artemis.alpha.token", "artemis.shared.instance-name"), List.of());
        ExtractedConfigInjection unguarded = injection("src/main/java/core/CoreConfiguration.java", List.of(), List.of("artemis.shared.instance-name"),
                List.of());

        ConfigMappingDeriver.Result result = deriver.derive(List.of(provisionalAlpha(List.of())), List.of(), List.of(guarded, unguarded),
                defaults(Map.of()), List.of(alphaCandidate()));

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

        ConfigMappingDeriver.Result result = deriver.derive(List.of(provisionalAlpha(List.of())), List.of(), List.of(properties), defaults,
                List.of(alphaCandidate()));

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

        ConfigMappingDeriver.Result result = deriver.derive(List.of(provisionalAlpha(List.of())), List.of(), List.of(), defaults,
                List.of(alphaCandidate(), nested));

        assertThat(mappingPaths(result, "alpha")).containsExactly("artemis.alpha.url");
        assertThat(result.derivation().members()).singleElement().satisfies(member -> assertThat(member.keys()).extracting(ConfigKeyResolution::key)
                .doesNotContain("artemis.alpha.nested.auth-token", "artemis.alpha.nested.enabled"));
    }

    @Test
    void annotationDeclarationsEmitFirstInDeclarationOrderAndDerivedDuplicatesCollapse() {
        ExtractedAnnotation annotation = annotation("alpha", List.of(new ConfigurationDeclaration("artemis.alpha.api-key", true),
                new ConfigurationDeclaration("artemis.alpha.base-url", false)));
        ExtractedConfigurationDefaults defaults = defaults(Map.of("artemis.alpha.base-url", "http://localhost:9000"));

        ConfigMappingDeriver.Result result = deriver.derive(List.of(annotatedAlpha(List.of())), List.of(annotation), List.of(), defaults,
                List.of(alphaCandidate()));

        assertThat(mappingsOf(result, "alpha")).extracting(MappingHint::path).as("declaration order wins over the sorted derived order")
                .containsExactly("artemis.alpha.api-key", "artemis.alpha.base-url");
        assertThat(mappingsOf(result, "alpha")).extracting(MappingHint::secret).containsExactly(Boolean.TRUE, null);
        assertThat(resolutionOf(result, "alpha", "artemis.alpha.base-url")).satisfies(resolution -> {
            assertThat(resolution.origin()).isEqualTo(ConfigDerivationReport.ORIGIN_ANNOTATION);
            assertThat(resolution.decision()).isEqualTo(ConfigDerivationReport.DECISION_DECLARED);
            assertThat(resolution.evidence()).as("derivation evidence attaches to the declared key").isNotEmpty();
        });
    }

    @Test
    void provisionalMembershipNeverConsumesAnnotationDeclarations() {
        ExtractedAnnotation annotation = annotation("alpha", List.of(new ConfigurationDeclaration("artemis.alpha.api-key", true)));

        ConfigMappingDeriver.Result result = deriver.derive(List.of(provisionalAlpha(List.of())), List.of(annotation), List.of(), defaults(Map.of()),
                List.of(alphaCandidate()));

        assertThat(mappingPaths(result, "alpha")).isEmpty();
    }

    @Test
    void manifestEntriesConfirmAddAndRejectDerivedKeys() {
        ResolvedFeatureScope alpha = provisionalAlpha(List.of(
                new ConfigurationEntry("artemis.alpha.chat-model", null, null),
                new ConfigurationEntry("spring.ai.alpha.api-key", true, FeatureScopeManifest.CONFIGURATION_ACTION_INCLUDE),
                new ConfigurationEntry("artemis.alpha.callback-url", null, FeatureScopeManifest.CONFIGURATION_ACTION_EXCLUDE)));
        ExtractedConfigurationDefaults defaults = defaults(Map.of(
                "artemis.alpha.chat-model", "gpt-4o",
                "artemis.alpha.url", "<your-url>",
                "artemis.alpha.callback-url", "http://localhost:7000"));

        ConfigMappingDeriver.Result result = deriver.derive(List.of(alpha), List.of(), List.of(), defaults, List.of(alphaCandidate()));

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
    void manifestEntryAgreeingWithAnAnnotationDeclaredKeyIsRedundant() {
        ExtractedAnnotation annotation = annotation("alpha", List.of(new ConfigurationDeclaration("spring.ai.alpha.api-key", true)));
        ResolvedFeatureScope alpha = annotatedAlpha(List.of(new ConfigurationEntry("spring.ai.alpha.api-key", true, null)));

        ConfigMappingDeriver.Result result = deriver.derive(List.of(alpha), List.of(annotation), List.of(), defaults(Map.of()),
                List.of(alphaCandidate()));

        assertThat(mappingPaths(result, "alpha")).containsExactly("spring.ai.alpha.api-key");
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_MANIFEST_CONFIGURATION_REDUNDANT);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_INFO);
            assertThat(item.subject()).isEqualTo("alpha");
        });
    }

    @Test
    void manifestEntryAlteringOrRejectingAnAnnotationDeclaredKeyIsIgnoredWithAWarning() {
        ExtractedAnnotation annotation = annotation("alpha", List.of(new ConfigurationDeclaration("spring.ai.alpha.api-key", true)));
        ResolvedFeatureScope alpha = annotatedAlpha(List.of(new ConfigurationEntry("spring.ai.alpha.api-key", null,
                FeatureScopeManifest.CONFIGURATION_ACTION_EXCLUDE)));

        ConfigMappingDeriver.Result result = deriver.derive(List.of(alpha), List.of(annotation), List.of(), defaults(Map.of()),
                List.of(alphaCandidate()));

        assertThat(mappingPaths(result, "alpha")).as("the annotation-declared key stays emitted").containsExactly("spring.ai.alpha.api-key");
        assertThat(mappingsOf(result, "alpha")).extracting(MappingHint::secret).containsExactly(Boolean.TRUE);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_ANNOTATION_OVERRIDES_MANIFEST);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_WARNING);
        });
    }

    @Test
    void derivedInputsEmitNonSecretKeysSortedBeforeSecretKeysSorted() {
        ExtractedConfigurationDefaults defaults = defaults(Map.of(
                "artemis.alpha.zeta-url", "<your-url>",
                "artemis.alpha.api-key", "dummy-key",
                "artemis.alpha.base-url", "http://localhost:8000",
                "artemis.alpha.secret", "<your-secret>"));

        ConfigMappingDeriver.Result result = deriver.derive(List.of(provisionalAlpha(List.of())), List.of(), List.of(), defaults,
                List.of(alphaCandidate()));

        assertThat(mappingPaths(result, "alpha")).containsExactly("artemis.alpha.base-url", "artemis.alpha.zeta-url", "artemis.alpha.api-key",
                "artemis.alpha.secret");
    }

    @Test
    void technicalMembersKeepTheirDeclaredMappingHintsUntouched() {
        MappingHint declared = new MappingHint(".env", "SPRING_PROFILES_ACTIVE", "selection", "alpha-profile", null, null);
        ResolvedFeatureScope technical = new ResolvedFeatureScope("infra:tech", "tech", null, "root", "feature", "optional", "technical", "enabled", 1,
                List.of(), List.of(), List.of(declared), List.of(), "Tech", null, null, CurationReport.SOURCE_TECHNICAL);

        ConfigMappingDeriver.Result result = deriver.derive(List.of(technical), List.of(), List.of(), defaults(Map.of("tech.url", "<your-url>")),
                List.of());

        assertThat(result.resolvedFeatures()).singleElement().satisfies(resolved -> assertThat(resolved.artifactMappings()).containsExactly(declared));
        assertThat(result.derivation().members()).as("technical members never enter the derivation report").isEmpty();
        assertThat(result.items()).isEmpty();
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
     * Builds the alpha member with provisional membership.
     *
     * @param configuration manifest configuration entries.
     * @return resolved alpha member.
     */
    private ResolvedFeatureScope provisionalAlpha(List<ConfigurationEntry> configuration) {
        return alphaMember(configuration, CurationReport.SOURCE_PROVISIONAL);
    }

    /**
     * Builds the alpha member with annotation membership.
     *
     * @param configuration manifest configuration entries.
     * @return resolved alpha member.
     */
    private ResolvedFeatureScope annotatedAlpha(List<ConfigurationEntry> configuration) {
        return alphaMember(configuration, CurationReport.SOURCE_ANNOTATION);
    }

    private ResolvedFeatureScope alphaMember(List<ConfigurationEntry> configuration, String membershipSource) {
        return new ResolvedFeatureScope("module:alpha", "alpha", "group", null, "module", "optional", null, null, 1, List.of(), List.of(), List.of(),
                configuration, null, null, null, membershipSource);
    }

    /**
     * Builds an annotation fact declaring configuration keys for a feature id.
     *
     * @param id feature id.
     * @param configuration declared keys.
     * @return annotation fact.
     */
    private ExtractedAnnotation annotation(String id, List<ConfigurationDeclaration> configuration) {
        return new ExtractedAnnotation("de.tum.cit.aet.artemis.alpha.config.AlphaEnabled", new ExtractedAnnotationSemantics(id, configuration),
                "src/main/java/alpha/AlphaEnabled.java", 10);
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
