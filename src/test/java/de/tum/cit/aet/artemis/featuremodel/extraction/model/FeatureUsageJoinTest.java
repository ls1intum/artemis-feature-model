package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedFeatureUsage;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedFeatureUsage.MethodLabel;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;
import de.tum.cit.aet.artemis.featuremodel.extraction.model.FeatureUsageJoin.SubFeature;

/** Covers the functional and technical join rules, precedence, deduplication, ordering, evidence, and the unattached diagnostic. */
class FeatureUsageJoinTest {

    private static final ResolvedFeatureScope ALPHA = member("module:alpha", "alpha", CurationReport.SOURCE_FEATURES, null);

    private static final ResolvedFeatureScope CI_ONE = member("profile:cione", "ci-one", CurationReport.SOURCE_TECHNICAL, "technical");

    private static final FeatureCandidate ALPHA_CANDIDATE = new FeatureCandidate("module:alpha", FeatureCandidate.KIND_MODULE_FEATURE, "Alpha", null, null,
            "artemis.alpha.enabled", Boolean.TRUE, "ALPHA_ENABLED_PROPERTY_NAME", "MODULE_FEATURE_ALPHA", "AlphaEnabled", null, true, true, null);

    private static final FeatureCandidate CI_ONE_CANDIDATE = new FeatureCandidate("profile:cione", FeatureCandidate.KIND_SPRING_PROFILE, null, null, null, null,
            null, "PROFILE_CIONE", null, null, "cione", null, null, null);

    @Test
    void attachesConditionGuardedTypesToTheFunctionalMemberAndOrdersLabelsByAreaThenFeature() {
        ExtractedFeatureUsage chat = usage("alpha/web/AlphaChatResource.java", 57, "alpha", "AlphaChatResource", "chat/chat-sessions", List.of(),
                List.of("AlphaEnabled"), List.of());
        ExtractedFeatureUsage items = usage("alpha/web/AlphaItemsResource.java", 10, "alpha", "AlphaItemsResource", "chat-archive/items", List.of(),
                List.of("AlphaEnabled"), List.of());
        ExtractedFeatureUsage authoring = usage("alpha/web/AlphaAuthoringResource.java", 30, "alpha", "AlphaAuthoringResource", "authoring/items", List.of(),
                List.of("AlphaEnabled"), List.of());

        FeatureUsageJoin.Result result = FeatureUsageJoin.join(List.of(ALPHA), List.of(ALPHA_CANDIDATE), List.of(chat, items, authoring));

        assertThat(result.items()).isEmpty();
        assertThat(result.subFeaturesByOwner()).containsOnlyKeys("alpha");
        assertThat(result.subFeaturesByOwner().get("alpha")).extracting(SubFeature::label).as("area before feature: 'chat' precedes 'chat-archive'")
                .containsExactly("authoring/items", "chat/chat-sessions", "chat-archive/items");
        assertThat(result.subFeaturesByOwner().get("alpha").get(1)).satisfies(subFeature -> {
            assertThat(subFeature.area()).isEqualTo("chat");
            assertThat(subFeature.feature()).isEqualTo("chat-sessions");
            assertThat(subFeature.guard()).isEqualTo("AlphaEnabled");
            assertThat(subFeature.module()).isEqualTo("alpha");
            assertThat(subFeature.evidence()).containsExactly("AlphaChatResource.java:57");
        });
    }

    @Test
    void deduplicatesLabelsAcrossTypesAndMethodsAndMergesTheirEvidence() {
        ExtractedFeatureUsage first = usage("alpha/web/AlphaResource.java", 20, "alpha", "AlphaResource", "chat/sessions",
                List.of(new MethodLabel("export", "chat/exports", 44)), List.of("AlphaEnabled"), List.of());
        ExtractedFeatureUsage second = usage("alpha/web/AlphaExportResource.java", 12, "alpha", "AlphaExportResource", "chat/exports", List.of(),
                List.of("AlphaEnabled"), List.of());

        FeatureUsageJoin.Result result = FeatureUsageJoin.join(List.of(ALPHA), List.of(ALPHA_CANDIDATE), List.of(first, second));

        assertThat(result.subFeaturesByOwner().get("alpha")).extracting(SubFeature::label).containsExactly("chat/exports", "chat/sessions");
        assertThat(result.subFeaturesByOwner().get("alpha").getFirst().evidence()).as("type and method placements of one label, sorted")
                .containsExactly("AlphaExportResource.java:12", "AlphaResource.java:44");
    }

    @Test
    void attachesProfileGuardedTypesToTheTechnicalMemberThroughTheConstantOrTheLiteral() {
        ExtractedFeatureUsage byConstant = usage("core/web/CioneStatusResource.java", 15, "core", "CioneStatusResource", "build/cione-status", List.of(),
                List.of(), List.of("PROFILE_CIONE"));
        ExtractedFeatureUsage byLiteral = usage("core/web/CioneAgentResource.java", 9, "core", "CioneAgentResource", "build/agents", List.of(), List.of(),
                List.of("cione"));
        ExtractedFeatureUsage other = usage("core/web/CoreResource.java", 9, "core", "CoreResource", "management/core", List.of(), List.of(),
                List.of("PROFILE_CORE"));

        FeatureUsageJoin.Result result = FeatureUsageJoin.join(List.of(CI_ONE), List.of(CI_ONE_CANDIDATE), List.of(byConstant, byLiteral, other));

        assertThat(result.items()).as("profile guards naming no member are silent").isEmpty();
        assertThat(result.subFeaturesByOwner().get("ci-one")).extracting(SubFeature::label, SubFeature::guard)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("build/agents", "PROFILE_CIONE"), org.assertj.core.groups.Tuple.tuple("build/cione-status", "PROFILE_CIONE"));
    }

    @Test
    void functionalOwnersWinOverProfileOwnersForATypeCarryingBothGuards() {
        ExtractedFeatureUsage both = usage("alpha/web/AlphaBuildResource.java", 7, "alpha", "AlphaBuildResource", "build/alpha", List.of(),
                List.of("AlphaEnabled"), List.of("PROFILE_CIONE"));

        FeatureUsageJoin.Result result = FeatureUsageJoin.join(List.of(ALPHA, CI_ONE), List.of(ALPHA_CANDIDATE, CI_ONE_CANDIDATE), List.of(both));

        assertThat(result.subFeaturesByOwner()).containsOnlyKeys("alpha");
    }

    @Test
    void reportsAConditionGuardNamingNoMemberAndIgnoresUnguardedTypes() {
        ExtractedFeatureUsage ghost = usage("ghost/web/GhostResource.java", 5, "ghost", "GhostResource", "ghost/items", List.of(), List.of("GhostEnabled"),
                List.of());
        ExtractedFeatureUsage unguarded = usage("beta/web/BetaResource.java", 5, "beta", "BetaResource", "review/beta", List.of(), List.of(), List.of());

        FeatureUsageJoin.Result result = FeatureUsageJoin.join(List.of(ALPHA), List.of(ALPHA_CANDIDATE), List.of(ghost, unguarded));

        assertThat(result.subFeaturesByOwner()).isEmpty();
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_FEATURE_USAGE_UNATTACHED);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_INFO);
            assertThat(item.subject()).isEqualTo("src/main/java/de/tum/cit/aet/artemis/ghost/web/GhostResource.java");
            assertThat(item.message()).contains("GhostEnabled").contains("ghost/items");
        });
    }

    private static ResolvedFeatureScope member(String candidateId, String id, String source, String category) {
        return new ResolvedFeatureScope(candidateId, id, "root", null, "module", "optional", category, null, 1, List.of(), List.of(), List.of(), List.of(), null,
                null, null, source);
    }

    private static ExtractedFeatureUsage usage(String relativeFile, int line, String module, String type, String classLabel, List<MethodLabel> methodLabels,
            List<String> conditionGuards, List<String> profileGuards) {
        return new ExtractedFeatureUsage("src/main/java/de/tum/cit/aet/artemis/" + relativeFile, line, module, type, classLabel, methodLabels, conditionGuards,
                profileGuards);
    }
}
