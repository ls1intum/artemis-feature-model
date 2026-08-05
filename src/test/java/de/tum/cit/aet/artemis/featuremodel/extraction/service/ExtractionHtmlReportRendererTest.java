package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport.CurationDecision;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;

/**
 * Covers the standalone rendering invariants of the extraction report: no script, no external asset, no absolute path
 * or secret, and no timestamp; and the presentation contract that every computed field the report carries is rendered
 * and that findings are ordered by severity rather than by diagnostic code.
 */
class ExtractionHtmlReportRendererTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    private static final String MANIFEST_DIGEST = "sha256:1a2b3c4d5e6f7890abcdef1234567890abcdef1234567890abcdef1234567890";

    @Test
    void rendersDependencyFreeReportAndSanitizesDynamicValues() {
        List<ReportItem> items = List.of(ReportItem.error(ReportItem.CODE_EXTRACTOR_ERROR, "<scanner>",
                "Failed at /Users/developer/Artemis/private.yml token=top-secret-value"));
        ExtractionReport report = report(ExtractionReport.STATUS_FAIL, curation(List.of()), items);

        String html = render(report);

        assertThat(html).contains("<!doctype html>", "FAIL", "&lt;scanner&gt;", "[path]", "token=[redacted]");
        assertThat(html).doesNotContain("/Users/developer", "top-secret-value", "<script", "src=\"http");
    }

    @Test
    void carriesTheVerdictAndShortCommitInTheTitleAndStatusBar() {
        ExtractionReport report = report(ExtractionReport.STATUS_PASS, curation(List.of(included("module:alpha"))), List.of());

        String html = render(report);

        assertThat(html).contains("<title>PASS · 0123456789 · Feature extraction report</title>");
        assertThat(html).contains("<span class=\"verdict ok\">PASS</span>", "commit <code>0123456789</code>", "manifest <code>1a2b3c4d5e6f</code>",
                "<span>manifest v2</span>");
    }

    @Test
    void quantifiesTheVerdictWithSeverityAndCandidateTiles() {
        List<ReportItem> items = List.of(ReportItem.warning(ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY, "alpha", "Asymmetric constants."),
                ReportItem.info(ReportItem.CODE_CLIENT_SERVER_MIRROR_MISMATCH, "beta", "Mirror mismatch."));
        ExtractionReport report = report(ExtractionReport.STATUS_PASS, curation(List.of(included("module:alpha"), excluded("configkey:beta", "deferred"))), items);

        String html = render(report);

        assertThat(html).contains("<span class=\"kpi-value\">0</span><span class=\"kpi-label\">Errors</span>",
                "<span class=\"kpi-value\">1</span><span class=\"kpi-label\">Warnings</span>",
                "<span class=\"kpi-value\">1</span><span class=\"kpi-label\">Info</span>",
                "<span class=\"kpi-value\">0</span><span class=\"kpi-label\">Undeclared</span>",
                "<span class=\"kpi-value\">2</span><span class=\"kpi-label\">Candidates</span>");
    }

    @Test
    void rendersErrorsBeforeWarningsBeforeInfosRegardlessOfCodeOrder() {
        List<ReportItem> items = List.of(ReportItem.warning(ReportItem.CODE_CLIENT_SERVER_MIRROR_MISMATCH, "beta", "Mirror mismatch."),
                ReportItem.info(ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY, "gamma", "Asymmetric constants."),
                ReportItem.error(ReportItem.CODE_UNDECLARED_CANDIDATE, "module:delta", "Undecided scope."));
        ExtractionReport report = report(ExtractionReport.STATUS_FAIL, curation(List.of(undeclared("module:delta"))), items);

        String html = render(report);

        assertThat(html.indexOf("data-severity=\"error\"")).isLessThan(html.indexOf("data-severity=\"warning\""));
        assertThat(html.indexOf("data-severity=\"warning\"")).isLessThan(html.indexOf("data-severity=\"info\""));
    }

    @Test
    void promotesFindingsAboveTheCandidateInventory() {
        List<ReportItem> items = List.of(ReportItem.warning(ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY, "alpha", "Asymmetric constants."));
        ExtractionReport report = report(ExtractionReport.STATUS_PASS, curation(List.of(included("module:alpha"))), items);

        String html = render(report);

        assertThat(html.indexOf("id=\"findings\"")).isLessThan(html.indexOf("id=\"decisions\""));
    }

    @Test
    void statesTheMeaningOfADiagnosticCodeOncePerGroup() {
        String meaning = "Server enabled property constants and module feature constants are asymmetric.";
        List<ReportItem> items = List.of(ReportItem.warning(ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY, "alpha", "First subject."),
                ReportItem.warning(ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY, "beta", "Second subject."));
        ExtractionReport report = new ExtractionReport(ExtractionReport.CURRENT_SCHEMA_VERSION, ExtractionReport.STATUS_PASS, COMMIT, MANIFEST_DIGEST,
                curation(List.of(included("module:alpha"))), Map.of(ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY, meaning),
                Map.of(ReportItem.SEVERITY_WARNING, 2), Map.of(ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY, 2), items);

        String html = render(report);

        assertThat(html).containsOnlyOnce("<p class=\"code-doc\">" + meaning + "</p>");
        assertThat(html).contains("First subject.", "Second subject.");
    }

    @Test
    void offersACssOnlySeverityFilterWithAnEmptyStatePerSeverity() {
        List<ReportItem> items = List.of(ReportItem.warning(ReportItem.CODE_MODULE_CONSTANT_ASYMMETRY, "alpha", "Asymmetric constants."));
        ExtractionReport report = report(ExtractionReport.STATUS_PASS, curation(List.of(included("module:alpha"))), items);

        String html = render(report);

        assertThat(html).contains("id=\"sev-all\"", "id=\"sev-error\"", "id=\"sev-warning\"", "id=\"sev-info\"");
        assertThat(html).contains("<p class=\"empty-state\" data-for=\"error\">No error findings in this run.</p>",
                "<p class=\"empty-state\" data-for=\"info\">No info findings in this run.</p>");
        assertThat(html).doesNotContain("data-for=\"warning\"", "<script");
    }

    @Test
    void rendersTheKindByStateMatrixAndTheSemanticSourceOfIncludedCandidates() {
        List<CurationDecision> decisions = List.of(included("module:alpha"), excluded("configkey:beta", "internal-mechanism"),
                excluded("configkey:gamma", "internal-mechanism"), excluded("profile:delta", "deferred"));
        ExtractionReport report = report(ExtractionReport.STATUS_PASS, curation(decisions), List.of());

        String html = render(report);

        assertThat(html).contains("<caption>Candidate decisions by kind</caption>");
        assertThat(html).contains("<tr><th scope=\"row\"><code>module-feature</code></th><td class=\"num\">1</td>");
        assertThat(html).contains("<tr><th scope=\"row\"><code>config-key</code></th><td class=\"num zero\">0</td><td class=\"num\">2</td>");
        assertThat(html).contains("<span class=\"tag\">manifest</span>");
        assertThat(html).contains("<summary><code>internal-mechanism</code> <span class=\"count\">2</span></summary>");
    }

    @Test
    void rendersTheSatisfiedStateWhenCurationLeftNothingUndeclared() {
        ExtractionReport report = report(ExtractionReport.STATUS_PASS, curation(List.of(included("module:alpha"))), List.of());

        String html = render(report);

        assertThat(html).contains("No undeclared candidates", "curation is complete for this commit");
        assertThat(html).doesNotContain("Undeclared candidates <span");
    }

    @Test
    void rendersTheBlockingBlockWhenCandidatesAreUndeclared() {
        ExtractionReport report = report(ExtractionReport.STATUS_FAIL, curation(List.of(included("module:alpha"), undeclared("module:beta"))), List.of());

        String html = render(report);

        assertThat(html).contains("<div class=\"callout bad compact\">", "Undeclared candidates <span class=\"count\">1</span>", "module:beta");
        assertThat(html).doesNotContain("No undeclared candidates");
    }

    @Test
    void statesThatARunWithoutCurationClassifiedNoCandidate() {
        ExtractionReport report = report(ExtractionReport.STATUS_FAIL, curation(List.of()), List.of());

        String html = render(report);

        assertThat(html).contains("No candidate decisions recorded", "did not reach manifest curation");
        assertThat(html).doesNotContain("No undeclared candidates", "curation is complete for this commit");
    }

    /**
     * Renders a report to text.
     *
     * @param report report to render.
     * @return rendered HTML.
     */
    private String render(ExtractionReport report) {
        return new String(new ExtractionHtmlReportRenderer().render(report), StandardCharsets.UTF_8);
    }

    /**
     * Builds a report whose counts are derived from its items, as the assembler derives them.
     *
     * @param status overall verdict.
     * @param curation curation section.
     * @param items diagnostics of the run.
     * @return report ready to render.
     */
    private ExtractionReport report(String status, CurationReport curation, List<ReportItem> items) {
        Map<String, Integer> severityCounts = new LinkedHashMap<>();
        Map<String, Integer> codeCounts = new LinkedHashMap<>();
        for (ReportItem item : items) {
            severityCounts.merge(item.severity(), 1, Integer::sum);
            codeCounts.merge(item.code(), 1, Integer::sum);
        }
        return new ExtractionReport(ExtractionReport.CURRENT_SCHEMA_VERSION, status, COMMIT, MANIFEST_DIGEST, curation, Map.of(), severityCounts, codeCounts,
                items);
    }

    /**
     * Builds a curation section whose counts are derived from its decisions, as the curation service derives them.
     *
     * @param decisions candidate decisions of the run.
     * @return curation section.
     */
    private CurationReport curation(List<CurationDecision> decisions) {
        Map<String, Integer> stateCounts = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> countsByKind = new LinkedHashMap<>();
        List<String> undeclaredIds = decisions.stream().filter(decision -> ScopeCurationService.STATE_UNDECLARED.equals(decision.state()))
                .map(CurationDecision::candidateId).toList();
        for (String state : List.of(ScopeCurationService.STATE_INCLUDE, ScopeCurationService.STATE_EXCLUDE, ScopeCurationService.STATE_UNDECLARED)) {
            stateCounts.put(state, 0);
        }
        for (CurationDecision decision : decisions) {
            stateCounts.merge(decision.state(), 1, Integer::sum);
            countsByKind.computeIfAbsent(decision.candidateKind(), ignored -> new LinkedHashMap<>(stateCountTemplate())).merge(decision.state(), 1, Integer::sum);
        }
        return new CurationReport(2, COMMIT, stateCounts, countsByKind, undeclaredIds, decisions);
    }

    /**
     * Creates the zero-initialized state counts every kind carries.
     *
     * @return state counts initialized to zero.
     */
    private Map<String, Integer> stateCountTemplate() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(ScopeCurationService.STATE_INCLUDE, 0);
        counts.put(ScopeCurationService.STATE_EXCLUDE, 0);
        counts.put(ScopeCurationService.STATE_UNDECLARED, 0);
        return counts;
    }

    private CurationDecision included(String candidateId) {
        return new CurationDecision(candidateId, FeatureCandidate.KIND_MODULE_FEATURE, ScopeCurationService.STATE_INCLUDE, "alpha-feature", null, "manifest");
    }

    private CurationDecision excluded(String candidateId, String reason) {
        String kind = candidateId.startsWith("profile:") ? FeatureCandidate.KIND_SPRING_PROFILE : FeatureCandidate.KIND_CONFIG_KEY;
        return new CurationDecision(candidateId, kind, ScopeCurationService.STATE_EXCLUDE, null, reason, null);
    }

    private CurationDecision undeclared(String candidateId) {
        return new CurationDecision(candidateId, FeatureCandidate.KIND_MODULE_FEATURE, ScopeCurationService.STATE_UNDECLARED, null, null, null);
    }
}
