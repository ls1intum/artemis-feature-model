package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport.CurationDecision;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConceptualNode;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.FeatureEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.NotModeledEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.TechnicalEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;

/** Covers features-entry and technical membership, the tiered gate, and every curation diagnostic. */
class ScopeCurationServiceTest {

    private static final String PINNED_COMMIT = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee";

    private static final ConceptualNode ROOT = new ConceptualNode("root", null, "root", null, null, null, null, null, null);

    @Test
    void featuresEntryGrantsMembershipThroughItsImpliedModuleAnchorAndSuppliesTheSemantics() {
        FeatureScopeManifest manifest = manifest(List.of(feature("alpha", "root")), List.of(), List.of());

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled")), PINNED_COMMIT);

        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> {
            assertThat(feature.candidateId()).isEqualTo("module:alpha");
            assertThat(feature.id()).isEqualTo("alpha");
            assertThat(feature.parent()).isEqualTo("root");
            assertThat(feature.kind()).isEqualTo("module");
            assertThat(feature.category()).isNull();
            assertThat(feature.optionality()).isEqualTo(FeatureScopeManifest.OPTIONALITY_OPTIONAL);
            assertThat(feature.requiresCapabilities()).as("capabilities are derived later").isEmpty();
            assertThat(feature.profiles()).isEmpty();
            assertThat(feature.membershipSource()).isEqualTo(CurationReport.SOURCE_FEATURES);
        });
        assertThat(result.report().decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.state()).isEqualTo(CurationReport.STATE_INCLUDE);
            assertThat(decision.curatedId()).isEqualTo("alpha");
            assertThat(decision.membershipSource()).isEqualTo(CurationReport.SOURCE_FEATURES);
        });
        assertThat(result.items()).isEmpty();
        assertThat(conformant(manifest, result)).isTrue();
    }

    @Test
    void technicalEntryGrantsMembershipWithImpliedKindAndCategoryAndItsProfiles() {
        FeatureEntry technicalSemantics = new FeatureEntry("tech-a", null, "root", null, null, "enabled", 1, List.of(), "Tech A", null, null, null);
        FeatureScopeManifest manifest = manifest(List.of(), List.of(new TechnicalEntry("profile:tech-a", technicalSemantics, List.of("tech-a", "tech-agent"))),
                List.of());
        FeatureCandidate profile = new FeatureCandidate("profile:tech-a", FeatureCandidate.KIND_SPRING_PROFILE, null, null, null, null, null, "PROFILE_TECH_A",
                null, null, "tech-a", null, null, null);

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(profile), PINNED_COMMIT);

        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> {
            assertThat(feature.id()).isEqualTo("tech-a");
            assertThat(feature.kind()).isEqualTo("feature");
            assertThat(feature.category()).isEqualTo(FeatureScopeManifest.CATEGORY_TECHNICAL);
            assertThat(feature.profiles()).containsExactly("tech-a", "tech-agent");
            assertThat(feature.name()).isEqualTo("Tech A");
            assertThat(feature.membershipSource()).isEqualTo(CurationReport.SOURCE_TECHNICAL);
        });
        assertThat(result.items()).isEmpty();
    }

    @Test
    void undecidedFeatureShapedModuleBlocksWhileOtherUndecidedAnchorsOnlyInform() {
        FeatureScopeManifest manifest = manifest(List.of(), List.of(), List.of());
        FeatureCandidate hiddenModule = new FeatureCandidate("module:hidden", FeatureCandidate.KIND_MODULE_FEATURE, null, null, null, null, null, null, null,
                "HiddenEnabled", null, false, false, null);
        FeatureCandidate toggle = new FeatureCandidate("toggle:RateLimit", FeatureCandidate.KIND_RUNTIME_TOGGLE, null, null, null, null, null, null, null, null,
                null, null, null, null);

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled"), hiddenModule, toggle),
                PINNED_COMMIT);

        assertThat(result.report().undeclaredCandidateIds()).containsExactly("module:alpha");
        assertThat(result.report().decisions()).extracting(CurationDecision::candidateId, CurationDecision::state, CurationDecision::membershipSource)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("module:alpha", CurationReport.STATE_UNDECLARED, CurationReport.SOURCE_UNDECLARED),
                        org.assertj.core.groups.Tuple.tuple("module:hidden", CurationReport.STATE_UNMODELED, CurationReport.SOURCE_UNMODELED),
                        org.assertj.core.groups.Tuple.tuple("toggle:RateLimit", CurationReport.STATE_UNMODELED, CurationReport.SOURCE_UNMODELED));
        assertThat(result.report().stateCounts()).containsEntry(CurationReport.STATE_UNDECLARED, 1).containsEntry(CurationReport.STATE_UNMODELED, 2);
        assertThat(result.items()).filteredOn(item -> ReportItem.CODE_UNDECLARED_CANDIDATE.equals(item.code())).singleElement().satisfies(item -> {
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.message()).contains("features entry").contains("notModeled");
        });
        assertThat(result.items()).filteredOn(item -> ReportItem.CODE_UNMODELED_ANCHOR.equals(item.code())).hasSize(2)
                .allSatisfy(item -> assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_INFO));
        assertThat(conformant(manifest, result)).isFalse();
    }

    @Test
    void unmodeledAnchorsAloneKeepTheRunConformant() {
        FeatureScopeManifest manifest = manifest(List.of(), List.of(), List.of());
        FeatureCandidate toggle = new FeatureCandidate("toggle:RateLimit", FeatureCandidate.KIND_RUNTIME_TOGGLE, null, null, null, null, null, null, null, null,
                null, null, null, null);

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(toggle), PINNED_COMMIT);

        assertThat(result.report().undeclaredCandidateIds()).isEmpty();
        assertThat(conformant(manifest, result)).isTrue();
    }

    @Test
    void notModeledFeatureShapedModuleIsExcludedWithoutBlocking() {
        FeatureScopeManifest manifest = manifest(List.of(), List.of(), List.of(new NotModeledEntry("module:alpha", "deferred", "Later.")));

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled")), PINNED_COMMIT);

        assertThat(result.includedFeatures()).isEmpty();
        assertThat(result.report().decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.state()).isEqualTo(CurationReport.STATE_EXCLUDE);
            assertThat(decision.reason()).isEqualTo("deferred");
            assertThat(decision.membershipSource()).isEqualTo(CurationReport.SOURCE_NOT_MODELED);
        });
        assertThat(result.items()).isEmpty();
        assertThat(conformant(manifest, result)).isTrue();
    }

    @Test
    void featuresEntryWhoseImpliedAnchorMatchesNoCandidateIsAnOrphanAndBlocking() {
        FeatureScopeManifest manifest = manifest(List.of(feature("missing", "root")), List.of(), List.of());

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled")), PINNED_COMMIT);

        assertThat(result.items()).filteredOn(item -> ReportItem.CODE_MANIFEST_ORPHAN_ANCHOR.equals(item.code())).singleElement().satisfies(item -> {
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("module:missing");
        });
        assertThat(result.includedFeatures()).isEmpty();
        assertThat(result.report().undeclaredCandidateIds()).containsExactly("module:alpha");
        assertThat(conformant(manifest, result)).isFalse();
    }

    @Test
    void reportsConflictWhenSeveralEntriesResolveToOneCandidateAndFirstWins() {
        FeatureScopeManifest manifest = manifest(List.of(feature("alpha", "root")), List.of(), List.of(new NotModeledEntry("AlphaEnabled", "duplicate", null)));

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled")), PINNED_COMMIT);

        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_MANIFEST_CURATION_CONFLICT);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("module:alpha");
        });
        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> assertThat(feature.id()).isEqualTo("alpha"));
        assertThat(result.report().stateCounts()).containsEntry(CurationReport.STATE_INCLUDE, 1).containsEntry(CurationReport.STATE_EXCLUDE, 0);
        assertThat(conformant(manifest, result)).isFalse();
    }

    @Test
    void referencesToTheImplicitRootResolveWhenNoRootIsDeclared() {
        FeatureScopeManifest manifest = new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, List.of(feature("alpha", FeatureScopeManifest.IMPLICIT_ROOT_ID)),
                List.of(), List.of(), List.of(), List.of(), List.of());

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled")), PINNED_COMMIT);

        assertThat(result.items()).isEmpty();
        assertThat(conformant(manifest, result)).isTrue();
    }

    @Test
    void blocksRuntimeToggleMembersWithoutRationale() {
        FeatureEntry toggleSemantics = new FeatureEntry("toggle-one", null, "root", null, null, null, null, List.of(), null, null, null, null);
        FeatureScopeManifest manifest = manifest(List.of(), List.of(new TechnicalEntry("toggle:ToggleOne", toggleSemantics, List.of())), List.of());
        FeatureCandidate toggle = new FeatureCandidate("toggle:ToggleOne", FeatureCandidate.KIND_RUNTIME_TOGGLE, null, null, null, null, null, null, null, null,
                null, null, null, null);

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(toggle), PINNED_COMMIT);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_MANIFEST_CURATION_CONFLICT);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.message()).contains("no rationale");
        });
        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> assertThat(feature.id()).isEqualTo("toggle-one"));
    }

    @Test
    void warnsButAcceptsExcludedRuntimeToggleWithoutReasonOrRationale() {
        FeatureScopeManifest manifest = manifest(List.of(), List.of(), List.of(new NotModeledEntry("toggle:ToggleOne", null, null)));
        FeatureCandidate toggle = new FeatureCandidate("toggle:ToggleOne", FeatureCandidate.KIND_RUNTIME_TOGGLE, null, null, null, null, null, null, null, null,
                null, null, null, null);

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(toggle), PINNED_COMMIT);

        assertThat(result.report().decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.state()).isEqualTo(CurationReport.STATE_EXCLUDE);
            assertThat(decision.reason()).isEqualTo(FeatureScopeManifest.EXCLUSION_REASON_UNSPECIFIED);
        });
        assertThat(result.items()).extracting(ReportItem::code).containsExactlyInAnyOrder(ReportItem.CODE_EXCLUSION_REASON_UNSPECIFIED,
                ReportItem.CODE_EXCLUDED_TOGGLE_RATIONALE_MISSING);
        assertThat(result.items()).allSatisfy(item -> assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_WARNING));
        assertThat(conformant(manifest, result)).isTrue();
    }

    private boolean conformant(FeatureScopeManifest manifest, ScopeCurationService.Result result) {
        return new ManifestConformanceService().evaluate(manifest, result.includedFeatures(), List.of(), result.report(), result.items(), List.of())
                .conformance().conformant();
    }

    private FeatureScopeManifest manifest(List<FeatureEntry> features, List<TechnicalEntry> technical, List<NotModeledEntry> notModeled) {
        return new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, features, technical, notModeled, List.of(ROOT), List.of(), List.of());
    }

    private FeatureEntry feature(String id, String parent) {
        return new FeatureEntry(id, null, parent, null, null, null, null, List.of(), null, null, null, null);
    }

    private FeatureCandidate featureShaped(String id, String conditionClass) {
        return new FeatureCandidate(id, FeatureCandidate.KIND_MODULE_FEATURE, null, null, null, null, null, null, null, conditionClass, null, true, true, null);
    }
}
