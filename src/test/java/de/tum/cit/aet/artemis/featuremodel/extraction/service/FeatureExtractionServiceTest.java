package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.RelationCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end extraction over the synthetic mini-Artemis fixture, covering every accepted anchor shape including the
 * real-world asymmetries: property name without backend module constant, frontend-only module ids, a nested config
 * key, a condition-only feature, composite conditions, and a frontend/backend toggle enum mismatch.
 */
class FeatureExtractionServiceTest {

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/extraction/mini-artemis");

    private static FeatureExtractionService.Outcome outcome;

    private static FeatureExtractionService extractionService;

    @BeforeAll
    static void extractFixture() {
        extractionService = new FeatureExtractionService(new ObjectMapper());
        outcome = extractionService.scan(new LocalArtemisSourceRepository(FIXTURE_PATH), ExtractionTestModels.fixtureCuratedModel(),
                ExtractionTestModels.fixtureCatalog());
    }

    @Test
    void scansTwiceThroughTheSameFacadeWithoutLeakingState() {
        FeatureExtractionService.Outcome secondOutcome = extractionService.scan(new LocalArtemisSourceRepository(FIXTURE_PATH),
                ExtractionTestModels.fixtureCuratedModel(), ExtractionTestModels.fixtureCatalog());

        assertThat(secondOutcome).isEqualTo(outcome);
    }

    @Test
    void extractsAllCandidateNamespacesSortedById() {
        List<String> ids = outcome.candidates().stream().map(FeatureCandidate::id).toList();
        assertThat(ids).containsExactly("configkey:artemis.alpha.enabled", "configkey:artemis.alpha.gamma.enabled", "configkey:artemis.user-management.beta.enabled",
                "infra:mysql", "infra:postgres", "module:alpha", "module:beta", "module:beta-extra", "module:delta", "module:gamma", "profile:agentx", "profile:cione",
                "profile:feprofile", "profile:jenkins", "toggle:ClientOnlyToggle", "toggle:ServerOnlyToggle", "toggle:ToggleOne", "toggle:ToggleTwo");
    }

    @Test
    void extractsFullParityModuleWithAllJoins() {
        FeatureCandidate alpha = candidate("module:alpha");
        assertThat(alpha.kind()).isEqualTo(FeatureCandidate.KIND_MODULE_FEATURE);
        assertThat(alpha.name()).isEqualTo("Alpha Module");
        assertThat(alpha.description()).isEqualTo("Enables the Alpha module.");
        assertThat(alpha.configKey()).isEqualTo("artemis.alpha.enabled");
        assertThat(alpha.defaultValue()).isEqualTo(Boolean.TRUE);
        assertThat(alpha.backendConstant()).isEqualTo("MODULE_FEATURE_ALPHA");
        assertThat(alpha.frontendConstant()).isEqualTo("MODULE_FEATURE_ALPHA");
        assertThat(alpha.backendConditionClass()).isEqualTo("AlphaEnabled");
        assertThat(alpha.enumeratedByBackend()).isTrue();
        assertThat(alpha.displayedOnAdminPage()).isTrue();
        assertThat(alpha.documentationUrl()).isEqualTo("https://docs.example.org/alpha");
        assertThat(evidenceKinds("module:alpha")).contains(EvidenceItem.KIND_BACKEND_CONSTANT, EvidenceItem.KIND_FRONTEND_CONSTANT, EvidenceItem.KIND_CONDITION_CLASS,
                EvidenceItem.KIND_BACKEND_ENUMERATION, EvidenceItem.KIND_CONFIG_HELPER_ACCESSOR, EvidenceItem.KIND_YAML_DEFAULT, EvidenceItem.KIND_I18N,
                EvidenceItem.KIND_ADMIN_PAGE, EvidenceItem.KIND_USAGE_CONDITIONAL);
    }

    @Test
    void extractsPropertyWithoutModuleConstantAsModuleCandidate() {
        FeatureCandidate beta = candidate("module:beta");
        assertThat(beta.configKey()).isEqualTo("artemis.user-management.beta.enabled");
        assertThat(beta.defaultValue()).isEqualTo(Boolean.FALSE);
        assertThat(beta.backendConstant()).isEqualTo("BETA_ENABLED_PROPERTY_NAME");
        assertThat(beta.frontendConstant()).isEqualTo("MODULE_FEATURE_BETA");
        assertThat(beta.backendConditionClass()).isEqualTo("BetaEnabled");
        assertThat(beta.enumeratedByBackend()).isTrue();
        assertThat(beta.displayedOnAdminPage()).isTrue();
    }

    @Test
    void extractsFrontendOnlyModuleIdWithoutConfigKey() {
        FeatureCandidate betaExtra = candidate("module:beta-extra");
        assertThat(betaExtra.configKey()).isNull();
        assertThat(betaExtra.backendConstant()).isNull();
        assertThat(betaExtra.frontendConstant()).isEqualTo("MODULE_FEATURE_BETA_EXTRA");
        assertThat(betaExtra.enumeratedByBackend()).isTrue();
        assertThat(betaExtra.displayedOnAdminPage()).isTrue();
        assertThat(betaExtra.name()).isEqualTo("Beta Required for Extra Features");
    }

    @Test
    void extractsNestedConfigKeyModule() {
        FeatureCandidate gamma = candidate("module:gamma");
        assertThat(gamma.configKey()).isEqualTo("artemis.alpha.gamma.enabled");
        assertThat(gamma.defaultValue()).isEqualTo(Boolean.FALSE);
        assertThat(gamma.backendConstant()).isEqualTo("MODULE_FEATURE_GAMMA");
        assertThat(gamma.frontendConstant()).isNull();
        assertThat(gamma.backendConditionClass()).isEqualTo("GammaEnabled");
        assertThat(gamma.displayedOnAdminPage()).isFalse();
    }

    @Test
    void extractsConditionOnlyFeatureWithLiteralPropertyKey() {
        FeatureCandidate delta = candidate("module:delta");
        assertThat(delta.configKey()).isEqualTo("artemis.delta.api-base-url");
        assertThat(delta.backendConstant()).isNull();
        assertThat(delta.backendConditionClass()).isEqualTo("DeltaEnabled");
        assertThat(delta.enumeratedByBackend()).isFalse();
        assertThat(evidenceKinds("module:delta")).contains(EvidenceItem.KIND_CONDITION_CLASS, EvidenceItem.KIND_YAML_DEFAULT);
    }

    @Test
    void skipsEnabledNamedClassesThatAreNoConditions() {
        assertThat(outcome.candidates()).noneSatisfy(candidate -> assertThat(candidate.backendConditionClass()).isEqualTo("AlphaSettingEnabled"));
        assertThat(outcome.relationCandidates()).noneSatisfy(relation -> assertThat(relation.conditionClass()).isEqualTo("AlphaSettingEnabled"));
    }

    @Test
    void buildsDirectedRelationFromNestedAccessorComposite() {
        RelationCandidate relation = relation("relation:GammaEnabled");
        assertThat(relation.type()).isEqualTo(RelationCandidate.TYPE_REQUIRES);
        assertThat(relation.directed()).isTrue();
        assertThat(relation.sourceCandidateId()).isEqualTo("module:gamma");
        assertThat(relation.memberCandidateIds()).containsExactly("module:alpha", "module:gamma");
        assertThat(relation.status()).isEqualTo(RelationCandidate.STATUS_CANDIDATE);
    }

    @Test
    void buildsUndirectedRelationFromCompositeConditionClass() {
        RelationCandidate relation = relation("relation:AlphaWithBetaEnabled");
        assertThat(relation.directed()).isFalse();
        assertThat(relation.sourceCandidateId()).isNull();
        assertThat(relation.memberCandidateIds()).containsExactly("module:alpha", "module:beta");
        assertThat(outcome.relationCandidates()).hasSize(2);
    }

    @Test
    void extractsToggleCandidatesWithSemanticsAndUsage() {
        FeatureCandidate toggleOne = candidate("toggle:ToggleOne");
        assertThat(toggleOne.kind()).isEqualTo(FeatureCandidate.KIND_RUNTIME_TOGGLE);
        assertThat(toggleOne.name()).isEqualTo("Toggle One");
        assertThat(toggleOne.disableWarning()).isEqualTo("Disabling toggle one hides the export.");
        assertThat(toggleOne.documentationUrl()).isEqualTo("https://docs.example.org/toggle-one");
        assertThat(evidenceKinds("toggle:ToggleOne")).contains(EvidenceItem.KIND_BACKEND_ENUM, EvidenceItem.KIND_FRONTEND_ENUM, EvidenceItem.KIND_I18N,
                EvidenceItem.KIND_ADMIN_PAGE, EvidenceItem.KIND_USAGE_FEATURE_TOGGLE);
        assertThat(evidenceKinds("toggle:ToggleTwo")).contains(EvidenceItem.KIND_USAGE_TEMPLATE);
    }

    @Test
    void reportsToggleEnumMirrorMismatchesAsErrors() {
        List<ReportItem> mismatches = itemsWithCode(ReportItem.CODE_FE_BE_MIRROR_MISMATCH);
        assertThat(mismatches).anySatisfy(item -> {
            assertThat(item.subject()).isEqualTo("toggle:ServerOnlyToggle");
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
        });
        assertThat(mismatches).anySatisfy(item -> {
            assertThat(item.subject()).isEqualTo("toggle:ClientOnlyToggle");
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
        });
    }

    @Test
    void reportsModuleConstantMirrorMismatchesAsWarnings() {
        List<ReportItem> mismatches = itemsWithCode(ReportItem.CODE_FE_BE_MIRROR_MISMATCH);
        assertThat(mismatches.stream().map(ReportItem::subject)).contains("module:gamma", "module:beta", "module:beta-extra");
        assertThat(mismatches).anySatisfy(item -> {
            assertThat(item.subject()).isEqualTo("module:beta");
            assertThat(item.message()).contains("enumerates the id at runtime");
        });
        assertThat(itemsWithCode(ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY)).singleElement()
                .satisfies(item -> assertThat(item.subject()).isEqualTo("BETA_ENABLED_PROPERTY_NAME"));
    }

    @Test
    void extractsProfileCandidatesFromBothSidesSkippingNonLiteralConstants() {
        FeatureCandidate cione = candidate("profile:cione");
        assertThat(cione.kind()).isEqualTo(FeatureCandidate.KIND_SPRING_PROFILE);
        assertThat(cione.springProfile()).isEqualTo("cione");
        assertThat(cione.name()).isEqualTo("CI One");
        assertThat(cione.displayedOnAdminPage()).isTrue();
        assertThat(cione.documentationUrl()).isEqualTo("https://docs.example.org/cione");
        assertThat(evidenceKinds("profile:cione")).contains(EvidenceItem.KIND_PROFILE_YAML);
        assertThat(evidenceKinds("profile:jenkins")).contains(EvidenceItem.KIND_COMPOSE_FILE);
        assertThat(candidate("profile:feprofile").backendConstant()).isNull();
        assertThat(outcome.candidates().stream().map(FeatureCandidate::id)).doesNotContain("profile:cione & agentx");
    }

    @Test
    void extractsDatabaseComposeAlternativesWithPairingEvidence() {
        List<EvidenceItem> mysqlEvidence = evidence("infra:mysql");
        assertThat(mysqlEvidence).hasSize(2);
        assertThat(mysqlEvidence).allSatisfy(item -> assertThat(item.detail()).startsWith("paired with "));
        List<EvidenceItem> postgresEvidence = evidence("infra:postgres");
        assertThat(postgresEvidence).hasSize(3);
        assertThat(postgresEvidence).anySatisfy(item -> {
            assertThat(item.file()).isEqualTo("docker/e2e-only-postgres.yml");
            assertThat(item.detail()).isEqualTo("no paired alternative found");
        });
    }

    @Test
    void rediscoverdriftAgainstCuratedModelWithoutFalseAlarms() {
        List<String> newCandidateSubjects = itemsWithCode(ReportItem.CODE_NEW_CANDIDATE_NOT_IN_MODEL).stream().map(ReportItem::subject).toList();
        assertThat(newCandidateSubjects).containsExactlyInAnyOrder("module:beta", "module:beta-extra", "module:delta", "module:gamma", "toggle:ClientOnlyToggle",
                "toggle:ServerOnlyToggle", "toggle:ToggleOne", "toggle:ToggleTwo");
        assertThat(newCandidateSubjects).doesNotContain("module:alpha");
        assertThat(itemsWithCode(ReportItem.CODE_CURATED_ANCHOR_MISSING)).singleElement().satisfies(item -> {
            assertThat(item.subject()).isEqualTo("ghost");
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
        });
        assertThat(itemsWithCode(ReportItem.CODE_UNANCHORED_CURATED_FEATURE)).singleElement()
                .satisfies(item -> assertThat(item.subject()).isEqualTo("fixture-root"));
    }

    @Test
    void verifiesCuratedEvidenceAndFlagsOnlyMovedReferences() {
        List<ReportItem> staleItems = itemsWithCode(ReportItem.CODE_CURATED_EVIDENCE_STALE);
        assertThat(staleItems).singleElement().satisfies(item -> {
            assertThat(item.subject()).isEqualTo("alpha-feature");
            assertThat(item.message()).contains("Constants.java:999").contains("no longer at the referenced lines");
        });
    }

    @Test
    void reportsConfigKeyCatalogDrift() {
        List<ReportItem> catalogItems = itemsWithCode(ReportItem.CODE_CONFIG_KEY_CATALOG_DRIFT);
        assertThat(catalogItems).hasSize(5);
        assertThat(catalogItems).anySatisfy(item -> assertThat(item.message()).contains("pinned to Artemis commit 'fixturepin'"));
        assertThat(catalogItems).anySatisfy(item -> {
            assertThat(item.subject()).isEqualTo("artemis.ghost.enabled");
            assertThat(item.message()).contains("no longer declared");
        });
        assertThat(catalogItems).anySatisfy(item -> {
            assertThat(item.subject()).isEqualTo("artemis.absent.url");
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_INFO);
        });
        assertThat(catalogItems.stream().map(ReportItem::subject)).contains("artemis.alpha.gamma.enabled", "artemis.user-management.beta.enabled")
                .doesNotContain("artemis.alpha.url");
    }

    @Test
    void completesWithoutExtractorErrors() {
        assertThat(itemsWithCode(ReportItem.CODE_EXTRACTOR_ERROR)).isEmpty();
        assertThat(outcome.annotations()).isEmpty();
        assertThat(outcome.configDefaults().occurrencesByKey()).containsKey("artemis.alpha.enabled");
    }


    private FeatureCandidate candidate(String id) {
        return outcome.candidates().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
    }

    private RelationCandidate relation(String id) {
        return outcome.relationCandidates().stream().filter(relation -> relation.id().equals(id)).findFirst().orElseThrow();
    }

    private List<EvidenceItem> evidence(String candidateId) {
        return outcome.evidence().stream().filter(item -> item.candidateId().equals(candidateId)).toList();
    }

    private List<String> evidenceKinds(String candidateId) {
        return evidence(candidateId).stream().map(EvidenceItem::kind).toList();
    }

    private List<ReportItem> itemsWithCode(String code) {
        return outcome.items().stream().filter(item -> item.code().equals(code)).toList();
    }
}
