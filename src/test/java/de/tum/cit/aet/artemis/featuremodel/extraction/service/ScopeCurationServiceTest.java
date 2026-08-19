package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractedAnnotation;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConceptualNode;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.IncludeEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.repository.LocalArtemisSourceRepository;
import de.tum.cit.aet.artemis.featuremodel.extraction.source.SourceScanResult;

/** Covers candidate membership, source-symbol resolution, annotation precedence, and unscoped diagnostics. */
class ScopeCurationServiceTest {

    private static final Path ANNOTATED_FIXTURE = Path.of("src/test/resources/extraction/annotated-artemis");

    private static final String PINNED_COMMIT = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee";

    @Test
    void keepsManifestSemanticsWhenAnAnnotationContradictsThem() throws Exception {
        SourceScanResult<List<ExtractedAnnotation>> annotationScan = new ArtemisFeatureAnnotationScan()
                .scan(new LocalArtemisSourceRepository(ANNOTATED_FIXTURE));
        assertThat(annotationScan.diagnostics()).isEmpty();
        assertThat(annotationScan.facts()).hasSize(4);
        assertThat(annotationScan.facts()).anySatisfy(annotation -> {
            assertThat(annotation.anchor()).isEqualTo("de.tum.cit.aet.artemis.alpha.config.AlphaEnabled");
            assertThat(annotation.semantics().id()).isEqualTo("annotated-alpha");
            assertThat(annotation.semantics().requiresCapabilities()).containsExactly("annotation-service", "annotation-secret");
        });
        assertThat(annotationScan.facts()).extracting(ExtractedAnnotation::anchor).contains("MODULE_FEATURE_FIELD_ALPHA", "toggle:ToggleField");

        List<FeatureCandidate> candidates = List.of(module("module:alpha", "AlphaEnabled"), module("module:beta", "BetaEnabled"));
        IncludeEntry include = new IncludeEntry("de.tum.cit.aet.artemis.alpha.config.AlphaEnabled", "manifest-alpha", "manifest-group", null, null, null, null, null,
                null, List.of("manifest-service"), List.of(), List.of(), null, null, null, null);
        FeatureScopeManifest manifest = new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, List.of(include), List.of(),
                List.of(new ConceptualNode("manifest-group", null, "group", null, null, null, null, null, null),
                        new ConceptualNode("annotation-group", null, "group", null, null, null, null, null, null)),
                List.of(), List.of(), List.of());

        List<ExtractedAnnotation> conditionAnnotations = annotationScan.facts().stream()
                .filter(annotation -> annotation.anchor().endsWith("Enabled")).toList();
        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, candidates, conditionAnnotations, PINNED_COMMIT);

        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> {
            assertThat(feature.id()).isEqualTo("manifest-alpha");
            assertThat(feature.group()).isEqualTo("manifest-group");
            assertThat(feature.requiresCapabilities()).containsExactly("manifest-service");
            assertThat(feature.name()).as("the annotation still fills a name the manifest leaves open").isEqualTo("Annotated Alpha");
            assertThat(feature.semanticSource()).isEqualTo("annotation");
            assertThat(feature.optionality()).isEqualTo(FeatureScopeManifest.OPTIONALITY_OPTIONAL);
        });
        assertThat(result.report().undeclaredCandidateIds()).containsExactly("module:beta");
        assertThat(result.report().decisions().getFirst().state()).isEqualTo(CurationReport.STATE_UNDECLARED);
        assertThat(result.items()).extracting(ReportItem::code).contains(ReportItem.CODE_MANIFEST_OVERRIDES_ANNOTATION,
                ReportItem.CODE_ANNOTATED_BUT_UNSCOPED, ReportItem.CODE_UNDECLARED_CANDIDATE);
        assertThat(result.items()).filteredOn(item -> ReportItem.CODE_MANIFEST_OVERRIDES_ANNOTATION.equals(item.code())).singleElement().satisfies(item -> {
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_WARNING);
            assertThat(item.message()).contains("id", "group", "requiresCapabilities").contains("the manifest value is used");
        });
    }

    @Test
    void reportsOrphanManifestAnchorAndKeepsCurating() {
        IncludeEntry orphan = new IncludeEntry("module:missing", "missing", null, null, null, null, null, null, null, List.of(), List.of(), List.of(), null, null, null, null);
        FeatureScopeManifest manifest = new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, List.of(orphan), List.of(), List.of(), List.of(), List.of(), List.of());

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(module("module:alpha", "AlphaEnabled")), List.of(), PINNED_COMMIT);

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
        IncludeEntry byId = new IncludeEntry("module:alpha", "alpha", null, null, null, null, null, null, null, List.of(), List.of(), List.of(), null, null, null, null);
        FeatureScopeManifest.ExcludeEntry bySymbol = new FeatureScopeManifest.ExcludeEntry("AlphaEnabled", "duplicate", null);
        FeatureScopeManifest manifest = new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, List.of(byId), List.of(bySymbol), List.of(), List.of(), List.of(), List.of());

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(module("module:alpha", "AlphaEnabled")), List.of(), PINNED_COMMIT);

        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_MANIFEST_CURATION_CONFLICT);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.subject()).isEqualTo("module:alpha");
        });
        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> assertThat(feature.id()).isEqualTo("alpha"));
        assertThat(result.report().stateCounts()).containsEntry(CurationReport.STATE_INCLUDE, 1).containsEntry(CurationReport.STATE_EXCLUDE, 0);
    }

    @Test
    void blocksIncludedRuntimeToggleEntriesWithoutRationale() {
        IncludeEntry toggleWithoutRationale = new IncludeEntry("toggle:ToggleOne", "toggle-one", null, null, null, null, null, null, null, List.of(), List.of(), List.of(), null, null, null, null);
        FeatureScopeManifest manifest = new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, List.of(toggleWithoutRationale), List.of(), List.of(), List.of(), List.of(), List.of());
        FeatureCandidate toggle = new FeatureCandidate("toggle:ToggleOne", FeatureCandidate.KIND_RUNTIME_TOGGLE, null, null, null, null, null, null, null, null, null,
                null, null, null);

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(toggle), List.of(), PINNED_COMMIT);

        assertThat(result.items()).anySatisfy(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_MANIFEST_CURATION_CONFLICT);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.message()).contains("no rationale");
        });
        assertThat(result.includedFeatures()).singleElement().satisfies(feature -> assertThat(feature.id()).isEqualTo("toggle-one"));
    }

    @Test
    void warnsButAcceptsExcludedRuntimeToggleWithoutReasonOrRationale() {
        FeatureScopeManifest.ExcludeEntry excludedToggle = new FeatureScopeManifest.ExcludeEntry("toggle:ToggleOne", null, null);
        FeatureScopeManifest manifest = new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, List.of(), List.of(excludedToggle), List.of(), List.of(), List.of(),
                List.of());
        FeatureCandidate toggle = new FeatureCandidate("toggle:ToggleOne", FeatureCandidate.KIND_RUNTIME_TOGGLE, null, null, null, null, null, null, null, null, null,
                null, null, null);

        ScopeCurationService.Result result = new ScopeCurationService().curate(manifest, List.of(toggle), List.of(), PINNED_COMMIT);
        ManifestConformanceService.Result conformance = new ManifestConformanceService().evaluate(manifest, result.includedFeatures(), List.of(), result.report(),
                result.items(), List.of());

        assertThat(result.report().decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.state()).isEqualTo(CurationReport.STATE_EXCLUDE);
            assertThat(decision.reason()).isEqualTo(FeatureScopeManifest.EXCLUSION_REASON_UNSPECIFIED);
        });
        assertThat(result.items()).extracting(ReportItem::code).containsExactlyInAnyOrder(ReportItem.CODE_EXCLUSION_REASON_UNSPECIFIED,
                ReportItem.CODE_EXCLUDED_TOGGLE_RATIONALE_MISSING);
        assertThat(result.items()).allSatisfy(item -> assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_WARNING));
        assertThat(conformance.conformance().conformant()).isTrue();
    }

    private FeatureCandidate module(String id, String conditionClass) {
        return new FeatureCandidate(id, FeatureCandidate.KIND_MODULE_FEATURE, null, null, null, null, null, null, null, conditionClass, null, null, null, null);
    }
}
