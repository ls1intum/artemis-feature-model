package de.tum.cit.aet.artemis.featuremodel.extraction.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport.CurationDecision;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotation;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotationSemantics;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConceptualNode;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.FeatureEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.NotModeledEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ProvisionalEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.TechnicalEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;

/** Covers annotation, provisional, and technical membership, the tiered gate, and every curation diagnostic. */
class ScopeCurationServiceTest {

    private static final String PINNED_COMMIT = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee";

    private static final String ALPHA_FILE = "src/main/java/de/tum/cit/aet/artemis/alpha/config/AlphaEnabled.java";

    private static final ConceptualNode ROOT = new ConceptualNode("root", null, "root", null, null, null, null, null, null);

    @Test
    void annotationGrantsMembershipAndTheFeaturesEntrySuppliesTheSemantics() {
        FeatureScopeManifest manifest = manifest(List.of(feature("alpha", "root", List.of("alpha-service"))), List.of(), List.of(), List.of());

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled")),
                List.of(annotation("de.tum.cit.aet.artemis.alpha.config.AlphaEnabled", "alpha")), PINNED_COMMIT);

        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> {
            assertThat(feature.id()).isEqualTo("alpha");
            assertThat(feature.parent()).isEqualTo("root");
            assertThat(feature.kind()).isEqualTo("module");
            assertThat(feature.optionality()).isEqualTo(FeatureScopeManifest.OPTIONALITY_OPTIONAL);
            assertThat(feature.requiresCapabilities()).containsExactly("alpha-service");
            assertThat(feature.membershipSource()).isEqualTo(CurationReport.SOURCE_ANNOTATION);
        });
        assertThat(result.report().decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.state()).isEqualTo(CurationReport.STATE_INCLUDE);
            assertThat(decision.curatedId()).isEqualTo("alpha");
            assertThat(decision.membershipSource()).isEqualTo(CurationReport.SOURCE_ANNOTATION);
        });
        assertThat(result.items()).isEmpty();
    }

    @Test
    void provisionalEntryCarriesMembershipUntilTheAnnotationLands() {
        FeatureScopeManifest manifest = manifest(List.of(feature("alpha", "root", List.of())), List.of(new ProvisionalEntry("module:alpha", "alpha")),
                List.of(), List.of());

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled")), List.of(),
                PINNED_COMMIT);

        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> {
            assertThat(feature.id()).isEqualTo("alpha");
            assertThat(feature.membershipSource()).isEqualTo(CurationReport.SOURCE_PROVISIONAL);
        });
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_PROVISIONAL_MEMBERSHIP);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_INFO);
            assertThat(item.subject()).isEqualTo("module:alpha");
        });
    }

    @Test
    void annotationWinsOverAMatchingProvisionalEntryWhichBecomesRedundant() {
        FeatureScopeManifest manifest = manifest(List.of(feature("alpha", "root", List.of())), List.of(new ProvisionalEntry("module:alpha", "alpha")),
                List.of(), List.of());

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled")),
                List.of(annotation("AlphaEnabled", "alpha")), PINNED_COMMIT);

        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> assertThat(feature.membershipSource()).isEqualTo(CurationReport.SOURCE_ANNOTATION));
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_PROVISIONAL_REDUNDANT);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_WARNING);
            assertThat(item.message()).contains("module:alpha", ALPHA_FILE + ":3", "remove the entry");
        });
        assertThat(conformant(manifest, result)).isTrue();
    }

    @Test
    void provisionalIdDifferingFromTheAnnotationIdIsAConflict() {
        FeatureScopeManifest manifest = manifest(List.of(feature("alpha", "root", List.of()), feature("alpha-renamed", "root", List.of())),
                List.of(new ProvisionalEntry("module:alpha", "alpha")), List.of(), List.of());

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled")),
                List.of(annotation("AlphaEnabled", "alpha-renamed")), PINNED_COMMIT);

        assertThat(result.items()).extracting(ReportItem::code).containsExactlyInAnyOrder(ReportItem.CODE_MANIFEST_CURATION_CONFLICT,
                ReportItem.CODE_PROVISIONAL_REDUNDANT, ReportItem.CODE_MANIFEST_FEATURE_UNKNOWN);
        assertThat(result.items()).filteredOn(item -> ReportItem.CODE_MANIFEST_CURATION_CONFLICT.equals(item.code())).singleElement()
                .satisfies(item -> assertThat(item.message()).contains("'alpha'", "'alpha-renamed'", "must match"));
        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> assertThat(feature.id()).isEqualTo("alpha-renamed"));
        assertThat(conformant(manifest, result)).isFalse();
    }

    @Test
    void technicalEntryGrantsMembershipWithInlineSemantics() {
        FeatureEntry technicalSemantics = new FeatureEntry("tech-a", null, "root", "feature", null, FeatureScopeManifest.CATEGORY_TECHNICAL, "enabled", 1,
                List.of(), List.of("tech-capability"), List.of(), List.of(), "Tech A", null, null, null);
        FeatureScopeManifest manifest = manifest(List.of(), List.of(), List.of(new TechnicalEntry("infra:tech-a", technicalSemantics)), List.of());
        FeatureCandidate infrastructure = new FeatureCandidate("infra:tech-a", FeatureCandidate.KIND_INFRASTRUCTURE, null, null, null, null, null, null, null,
                null, null, null, null, null);

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(infrastructure), List.of(), PINNED_COMMIT);

        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> {
            assertThat(feature.id()).isEqualTo("tech-a");
            assertThat(feature.kind()).isEqualTo("feature");
            assertThat(feature.category()).isEqualTo(FeatureScopeManifest.CATEGORY_TECHNICAL);
            assertThat(feature.providesCapabilities()).containsExactly("tech-capability");
            assertThat(feature.name()).isEqualTo("Tech A");
            assertThat(feature.membershipSource()).isEqualTo(CurationReport.SOURCE_TECHNICAL);
        });
        assertThat(result.items()).isEmpty();
    }

    @Test
    void annotatedCandidateListedInNotModeledIsBlocking() {
        FeatureScopeManifest manifest = manifest(List.of(), List.of(), List.of(), List.of(new NotModeledEntry("module:alpha", "deferred", "Later.")));

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled")),
                List.of(annotation("AlphaEnabled", "alpha")), PINNED_COMMIT);

        assertThat(result.report().decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.state()).isEqualTo(CurationReport.STATE_EXCLUDE);
            assertThat(decision.membershipSource()).isEqualTo(CurationReport.SOURCE_NOT_MODELED);
        });
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_NOT_MODELED_ANCHOR_ANNOTATED);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
        });
        assertThat(conformant(manifest, result)).isFalse();
    }

    @Test
    void undecidedFeatureShapedModuleBlocksWhileOtherUndecidedAnchorsOnlyInform() {
        FeatureScopeManifest manifest = manifest(List.of(), List.of(), List.of(), List.of());
        FeatureCandidate hiddenModule = new FeatureCandidate("module:hidden", FeatureCandidate.KIND_MODULE_FEATURE, null, null, null, null, null, null, null,
                "HiddenEnabled", null, false, false, null);
        FeatureCandidate toggle = new FeatureCandidate("toggle:RateLimit", FeatureCandidate.KIND_RUNTIME_TOGGLE, null, null, null, null, null, null, null, null,
                null, null, null, null);

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled"), hiddenModule, toggle),
                List.of(), PINNED_COMMIT);

        assertThat(result.report().undeclaredCandidateIds()).containsExactly("module:alpha");
        assertThat(result.report().decisions()).extracting(CurationDecision::candidateId, CurationDecision::state, CurationDecision::membershipSource)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("module:alpha", CurationReport.STATE_UNDECLARED, CurationReport.SOURCE_UNDECLARED),
                        org.assertj.core.groups.Tuple.tuple("module:hidden", CurationReport.STATE_UNMODELED, CurationReport.SOURCE_UNMODELED),
                        org.assertj.core.groups.Tuple.tuple("toggle:RateLimit", CurationReport.STATE_UNMODELED, CurationReport.SOURCE_UNMODELED));
        assertThat(result.report().stateCounts()).containsEntry(CurationReport.STATE_UNDECLARED, 1).containsEntry(CurationReport.STATE_UNMODELED, 2);
        assertThat(result.items()).filteredOn(item -> ReportItem.CODE_UNDECLARED_CANDIDATE.equals(item.code())).singleElement()
                .satisfies(item -> assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR));
        assertThat(result.items()).filteredOn(item -> ReportItem.CODE_UNMODELED_ANCHOR.equals(item.code())).hasSize(2)
                .allSatisfy(item -> assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_INFO));
        assertThat(conformant(manifest, result)).isFalse();
    }

    @Test
    void unmodeledAnchorsAloneKeepTheRunConformant() {
        FeatureScopeManifest manifest = manifest(List.of(), List.of(), List.of(), List.of());
        FeatureCandidate toggle = new FeatureCandidate("toggle:RateLimit", FeatureCandidate.KIND_RUNTIME_TOGGLE, null, null, null, null, null, null, null, null,
                null, null, null, null);

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(toggle), List.of(), PINNED_COMMIT);

        assertThat(result.report().undeclaredCandidateIds()).isEmpty();
        assertThat(conformant(manifest, result)).isTrue();
    }

    @Test
    void memberWithoutFeaturesEntryIsUnplacedAndBlocking() {
        FeatureScopeManifest manifest = manifest(List.of(), List.of(), List.of(), List.of());

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled")),
                List.of(annotation("AlphaEnabled", "alpha")), PINNED_COMMIT);

        assertThat(result.includedFeatures()).isEmpty();
        assertThat(result.report().decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.state()).isEqualTo(CurationReport.STATE_INCLUDE);
            assertThat(decision.curatedId()).isEqualTo("alpha");
        });
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_ANNOTATED_FEATURE_UNPLACED);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.message()).contains("features[id=alpha]");
        });
        assertThat(conformant(manifest, result)).isFalse();
    }

    @Test
    void featuresEntryWithoutAMemberIsUnknownAndBlocking() {
        FeatureScopeManifest manifest = manifest(List.of(feature("ghost", "root", List.of())), List.of(), List.of(), List.of());

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(), List.of(), PINNED_COMMIT);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_MANIFEST_FEATURE_UNKNOWN);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("ghost");
        });
        assertThat(conformant(manifest, result)).isFalse();
    }

    @Test
    void annotationThatMatchesNoCandidateIsBlocking() {
        FeatureScopeManifest manifest = manifest(List.of(), List.of(), List.of(), List.of());

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(), List.of(annotation("GhostEnabled", "ghost")), PINNED_COMMIT);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_ANNOTATED_ANCHOR_NOT_EXTRACTED);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("GhostEnabled");
        });
        assertThat(conformant(manifest, result)).isFalse();
    }

    @Test
    void reportsOrphanManifestAnchorAndKeepsCurating() {
        FeatureScopeManifest manifest = manifest(List.of(feature("missing", "root", List.of())), List.of(new ProvisionalEntry("module:missing", "missing")),
                List.of(), List.of());

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled")), List.of(),
                PINNED_COMMIT);

        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_MANIFEST_ORPHAN_ANCHOR);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("module:missing");
        });
        assertThat(result.includedFeatures()).isEmpty();
        assertThat(result.report().undeclaredCandidateIds()).containsExactly("module:alpha");
    }

    @Test
    void reportsConflictWhenSeveralEntriesResolveToOneCandidateAndFirstWins() {
        FeatureScopeManifest manifest = manifest(List.of(feature("alpha", "root", List.of())), List.of(new ProvisionalEntry("module:alpha", "alpha")),
                List.of(), List.of(new NotModeledEntry("AlphaEnabled", "duplicate", null)));

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(featureShaped("module:alpha", "AlphaEnabled")), List.of(),
                PINNED_COMMIT);

        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_MANIFEST_CURATION_CONFLICT);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("module:alpha");
        });
        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> assertThat(feature.id()).isEqualTo("alpha"));
        assertThat(result.report().stateCounts()).containsEntry(CurationReport.STATE_INCLUDE, 1).containsEntry(CurationReport.STATE_EXCLUDE, 0);
    }

    @Test
    void blocksRuntimeToggleMembersWithoutRationale() {
        FeatureScopeManifest manifest = manifest(List.of(feature("toggle-one", "root", List.of())), List.of(), List.of(), List.of());
        FeatureCandidate toggle = new FeatureCandidate("toggle:ToggleOne", FeatureCandidate.KIND_RUNTIME_TOGGLE, null, null, null, null, null, null, null, null,
                null, null, null, null);

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(toggle), List.of(annotation("toggle:ToggleOne", "toggle-one")),
                PINNED_COMMIT);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_MANIFEST_CURATION_CONFLICT);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.message()).contains("no rationale");
        });
        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> assertThat(feature.id()).isEqualTo("toggle-one"));
    }

    @Test
    void warnsButAcceptsExcludedRuntimeToggleWithoutReasonOrRationale() {
        FeatureScopeManifest manifest = manifest(List.of(), List.of(), List.of(), List.of(new NotModeledEntry("toggle:ToggleOne", null, null)));
        FeatureCandidate toggle = new FeatureCandidate("toggle:ToggleOne", FeatureCandidate.KIND_RUNTIME_TOGGLE, null, null, null, null, null, null, null, null,
                null, null, null, null);

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(toggle), List.of(), PINNED_COMMIT);

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

    private FeatureScopeManifest manifest(List<FeatureEntry> features, List<ProvisionalEntry> provisional, List<TechnicalEntry> technical,
            List<NotModeledEntry> notModeled) {
        return new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, features, provisional, technical, notModeled, List.of(ROOT), List.of(), List.of(),
                List.of());
    }

    private FeatureEntry feature(String id, String parent, List<String> requiresCapabilities) {
        return new FeatureEntry(id, null, parent, null, null, null, null, null, requiresCapabilities, List.of(), List.of(), List.of(), null, null, null, null);
    }

    private ExtractedAnnotation annotation(String anchor, String id) {
        return new ExtractedAnnotation(anchor, new ExtractedAnnotationSemantics(id, List.of()), ALPHA_FILE, 3);
    }

    private FeatureCandidate featureShaped(String id, String conditionClass) {
        return new FeatureCandidate(id, FeatureCandidate.KIND_MODULE_FEATURE, null, null, null, null, null, null, null, conditionClass, null, true, true, null);
    }
}
