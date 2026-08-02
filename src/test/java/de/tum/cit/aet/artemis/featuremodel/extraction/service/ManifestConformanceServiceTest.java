package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.ConstraintEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest.IgnoredRelationEntry;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ManifestConformance;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.RelationCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ResolvedFeatureScope;

/** Covers the blocking conformance gate: every candidate and every included-feature relation needs a decision. */
class ManifestConformanceServiceTest {

    private static final String ARTEMIS_COMMIT = "aaaaaaaabbbbbbbbccccccccddddddddeeeeeeee";

    private final ManifestConformanceService conformanceService = new ManifestConformanceService();

    @Test
    void passesWhenEveryCandidateAndRelationHasADecision() {
        ManifestConformanceService.Result result = evaluate(manifest(List.of(), List.of()), List.of(), curation(List.of()), List.of(), List.of());

        assertThat(result.conformance().conformant()).isTrue();
        assertThat(result.items()).isEmpty();
        assertThat(result.conformance().describeFindings()).isEmpty();
    }

    @Test
    void failsAndNamesAnUndeclaredCandidate() {
        ManifestConformanceService.Result result = evaluate(manifest(List.of(), List.of()), List.of(), curation(List.of("module:new")), List.of(), List.of());

        assertThat(result.conformance().conformant()).isFalse();
        assertThat(result.conformance().undeclaredCandidates()).containsExactly("module:new");
        assertThat(result.conformance().describeFindings()).contains("module:new");
    }

    @Test
    void failsAndNamesAnUndeclaredRelationBetweenIncludedFeatures() {
        ManifestConformanceService.Result result = evaluate(manifest(List.of(), List.of()), List.of(relation()), curation(List.of()), List.of(), List.of());

        assertThat(result.conformance().conformant()).isFalse();
        assertThat(result.conformance().undeclaredRelations()).containsExactly("relation:AlphaWithBeta");
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo(ReportItem.CODE_RELATION_CANDIDATE_UNDECLARED);
            assertThat(item.severity()).isEqualTo(ReportItem.SEVERITY_ERROR);
            assertThat(item.message()).contains("alpha").contains("beta").contains("ignoredRelations");
        });
    }

    @Test
    void acceptsARelationCoveredByADeclaredConstraint() {
        List<ConstraintEntry> constraints = List.of(new ConstraintEntry("alpha-requires-beta", "requires", "alpha", "beta", null));

        ManifestConformanceService.Result result = evaluate(manifest(constraints, List.of()), List.of(relation()), curation(List.of()), List.of(), List.of());

        assertThat(result.conformance().conformant()).isTrue();
        assertThat(result.items()).isEmpty();
    }

    @Test
    void acceptsARelationExplicitlyIgnoredWithARationale() {
        List<IgnoredRelationEntry> ignored = List.of(new IgnoredRelationEntry("relation:AlphaWithBeta", "Independent modules; the condition only guards glue code."));

        ManifestConformanceService.Result result = evaluate(manifest(List.of(), ignored), List.of(relation()), curation(List.of()), List.of(), List.of());

        assertThat(result.conformance().conformant()).isTrue();
        assertThat(result.items()).isEmpty();
    }

    @Test
    void ignoresRelationsThatTouchAnExcludedCandidate() {
        RelationCandidate outsideScope = new RelationCandidate("relation:AlphaWithGhost", "requires", "module:alpha", List.of("module:alpha", "module:ghost"),
                true, "AlphaWithGhostEnabled", "candidate", "alpha AND ghost");

        ManifestConformanceService.Result result = evaluate(manifest(List.of(), List.of()), List.of(outsideScope), curation(List.of()), List.of(), List.of());

        assertThat(result.conformance().conformant()).isTrue();
    }

    @Test
    void failsOnOrphanAnchorsConflictsAndExtractorFailures() {
        List<ReportItem> curationItems = List.of(ReportItem.error(ReportItem.CODE_MANIFEST_ORPHAN_ANCHOR, "module:gone", "Anchor no longer resolves."),
                ReportItem.error(ReportItem.CODE_MANIFEST_CURATION_CONFLICT, "module:alpha", "Two entries claim this candidate."));
        List<ReportItem> scanItems = List.of(ReportItem.error(ReportItem.CODE_EXTRACTOR_ERROR, "backend constants", "Scan failed."));

        ManifestConformanceService.Result result = evaluate(manifest(List.of(), List.of()), List.of(), curation(List.of()), curationItems, scanItems);

        assertThat(result.conformance().conformant()).isFalse();
        assertThat(result.conformance().unresolvedAnchors()).containsExactly("module:gone");
        assertThat(result.conformance().conflictingDecisions()).containsExactly("module:alpha");
        assertThat(result.conformance().extractorFailures()).containsExactly("backend constants");
        assertThat(result.conformance().describeFindings()).contains("unresolved anchors").contains("conflicting decisions").contains("extractor failures");
    }

    /**
     * Evaluates conformance for the two included fixture features.
     *
     * @param manifest scope manifest under test.
     * @param relationCandidates relation candidates the scan discovered.
     * @param curation curation section of the run.
     * @param curationItems curation diagnostics.
     * @param scanItems scan diagnostics.
     * @return conformance result.
     */
    private ManifestConformanceService.Result evaluate(FeatureScopeManifest manifest, List<RelationCandidate> relationCandidates, CurationReport curation,
            List<ReportItem> curationItems, List<ReportItem> scanItems) {
        return conformanceService.evaluate(manifest, includedFeatures(), relationCandidates, curation, curationItems, scanItems);
    }

    /**
     * Creates a manifest with the given constraints and ignore entries.
     *
     * @param constraints declared constraints.
     * @param ignoredRelations declared ignore entries.
     * @return scope manifest.
     */
    private FeatureScopeManifest manifest(List<ConstraintEntry> constraints, List<IgnoredRelationEntry> ignoredRelations) {
        return new FeatureScopeManifest(FeatureScopeManifest.CURRENT_VERSION, ARTEMIS_COMMIT, "latest", List.of(), List.of(), List.of(), constraints, ignoredRelations,
                List.of());
    }

    /**
     * Creates a curation section with the given undeclared candidates.
     *
     * @param undeclaredCandidateIds candidates without a manifest decision.
     * @return curation section.
     */
    private CurationReport curation(List<String> undeclaredCandidateIds) {
        return new CurationReport(FeatureScopeManifest.CURRENT_VERSION, ARTEMIS_COMMIT, Map.of(), Map.of(), undeclaredCandidateIds, List.of());
    }

    /**
     * Creates the two included features the relation candidates connect.
     *
     * @return resolved include semantics.
     */
    private List<ResolvedFeatureScope> includedFeatures() {
        return List.of(
                new ResolvedFeatureScope("module:alpha", "alpha", null, "root", "module", "optional", null, null, 1, List.of(), List.of(), List.of(), null, null,
                        null, "manifest"),
                new ResolvedFeatureScope("module:beta", "beta", null, "root", "module", "optional", null, null, 2, List.of(), List.of(), List.of(), null, null,
                        null, "manifest"));
    }

    /**
     * Creates a relation candidate between the two included fixture features.
     *
     * @return relation candidate.
     */
    private RelationCandidate relation() {
        return new RelationCandidate("relation:AlphaWithBeta", "requires", null, List.of("module:alpha", "module:beta"), false, "AlphaWithBetaEnabled",
                "candidate", "alpha AND beta");
    }
}
