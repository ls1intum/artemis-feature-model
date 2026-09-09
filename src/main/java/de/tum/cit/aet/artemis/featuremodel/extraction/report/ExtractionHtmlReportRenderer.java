package de.tum.cit.aet.artemis.featuremodel.extraction.report;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ConfigDerivationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ConfigDerivationReport.MemberConfigDerivation;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport.CurationDecision;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureCandidate;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.FeatureScopeManifest;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;

/**
 * Renders the consolidated extraction verdict as one dependency-free, sanitized HTML artifact.
 *
 * <p>
 * The document is composed from one private method per section, each returning a text block completed with
 * {@code formatted}, so the page structure stays readable in the source. The stylesheet is a classpath resource that is
 * concatenated verbatim and never passed through {@code formatted}: a literal {@code %} in a declaration such as
 * {@code width:100%} would otherwise throw. Interactivity is CSS-only — the output carries no script, no external asset,
 * and no timestamp, so two runs on the same commit produce byte-identical bytes.
 *
 * <p>
 * Every value that originates in scanned Artemis source passes through {@link #escape(String)}, which redacts secrets
 * and absolute paths before escaping markup. Internal constants such as link targets are inserted verbatim, because the
 * sanitizer would rewrite their slashes.
 */
public class ExtractionHtmlReportRenderer {

    /** Classpath location of the stylesheet inlined into every rendered report. */
    private static final String STYLESHEET_RESOURCE = "/feature-model/extraction/report.css";

    /** Severity render order; errors always precede warnings, which always precede infos. */
    private static final List<String> SEVERITY_ORDER = List.of(ReportItem.SEVERITY_ERROR, ReportItem.SEVERITY_WARNING, ReportItem.SEVERITY_INFO);

    /** Plural filter labels per severity. */
    private static final Map<String, String> SEVERITY_LABELS = Map.of(ReportItem.SEVERITY_ERROR, "Errors", ReportItem.SEVERITY_WARNING, "Warnings",
            ReportItem.SEVERITY_INFO, "Info");

    /** Column order of the candidate decision matrix. */
    private static final List<String> DECISION_STATES = CurationReport.STATES;

    /**
     * Preferred row order of the candidate decision matrix, from the most feature-like kind to the most infrastructural
     * one. Kinds outside this list follow in the sorted order of the report, so the matrix stays deterministic if a new
     * candidate kind appears.
     */
    private static final List<String> KIND_ORDER = List.of(FeatureCandidate.KIND_MODULE_FEATURE, FeatureCandidate.KIND_SPRING_PROFILE,
            FeatureCandidate.KIND_RUNTIME_TOGGLE, FeatureCandidate.KIND_INFRASTRUCTURE);

    /** Placeholder for an attribute the run did not resolve. */
    private static final String NOT_AVAILABLE = "—";

    /** Characters of a commit hash kept in the status bar; the full value stays in the tooltip. */
    private static final int SHORT_COMMIT_LENGTH = 10;

    /** Characters of a digest kept in the status bar; the full value stays in the tooltip. */
    private static final int SHORT_DIGEST_LENGTH = 12;

    /** Inline verdict icon of a passing run, as a data URI so the page references no external asset. */
    private static final String FAVICON_PASS = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3E"
            + "%3Ccircle cx='8' cy='8' r='7' fill='%230f7b3f'/%3E%3C/svg%3E";

    /** Inline verdict icon of a failing run, as a data URI so the page references no external asset. */
    private static final String FAVICON_FAIL = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3E"
            + "%3Ccircle cx='8' cy='8' r='7' fill='%23b3261e'/%3E%3C/svg%3E";

    /**
     * Renders a report without exposing absolute paths or markup supplied by diagnostics.
     *
     * @param report consolidated JSON report.
     * @return UTF-8 HTML bytes.
     */
    public byte[] render(ExtractionReport report) {
        return document(report).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Assembles the complete document from its sections.
     *
     * @param report consolidated JSON report.
     * @return rendered HTML text.
     */
    private String document(ExtractionReport report) {
        boolean passed = ExtractionReport.STATUS_PASS.equals(report.status());
        String verdict = report.status().toUpperCase(Locale.ROOT);
        String title = "%s · %s · Feature extraction report".formatted(verdict, shortCommit(report.artemisCommit()));
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title>
                <link rel="icon" href="%s">
                <style>
                %s
                </style>
                </head>
                <body>
                %s<main>
                %s%s%s%s%s%s</main>
                </body>
                </html>
                """.formatted(escape(title), passed ? FAVICON_PASS : FAVICON_FAIL, stylesheet(), statusBar(report, verdict, passed), summaryTiles(report),
                findingsSection(report), decisionsSection(report.curation()), configDerivationSection(report.configDerivation()), releaseDeltaSection(),
                rawArtifactsSection());
    }

    /**
     * Renders the sticky status bar: the verdict and the provenance that identifies which inputs produced this run.
     * Commit and digest are shortened for scanning and carry their full value as a tooltip.
     *
     * @param report consolidated JSON report.
     * @param verdict upper-case overall verdict.
     * @param passed whether the run is eligible for delivery.
     * @return status bar markup.
     */
    private String statusBar(ExtractionReport report, String verdict, boolean passed) {
        return """
                <header class="statusbar">
                <span class="verdict %s">%s</span>
                <div class="statusbar-text">
                <h1>Feature extraction report</h1>
                <p class="provenance"><span title="%s">commit <code>%s</code></span><span title="%s">manifest <code>%s</code></span>\
                <span>manifest v%s</span></p>
                </div>
                <nav class="jump"><a href="#findings">Findings</a><a href="#decisions">Decisions</a><a href="#configuration">Configuration</a>\
                <a href="#raw">Raw</a></nav>
                </header>
                """.formatted(passed ? "ok" : "bad", escape(verdict), escape(report.artemisCommit()), escape(shortCommit(report.artemisCommit())),
                escape(report.manifestDigest()), escape(shortDigest(report.manifestDigest())), report.curation().manifestVersion());
    }

    /**
     * Renders the summary tiles that quantify the verdict: the diagnostic counts the run produced, the number of
     * candidates that block publication, and the size of the scanned candidate set.
     *
     * @param report consolidated JSON report.
     * @return summary tile markup.
     */
    private String summaryTiles(ExtractionReport report) {
        Map<String, Integer> severityCounts = report.severityCounts();
        int errors = countOf(severityCounts, ReportItem.SEVERITY_ERROR);
        int warnings = countOf(severityCounts, ReportItem.SEVERITY_WARNING);
        int infos = countOf(severityCounts, ReportItem.SEVERITY_INFO);
        int undeclared = countOf(report.curation().stateCounts(), CurationReport.STATE_UNDECLARED);
        String tiles = tile("Errors", errors, errors == 0 ? "ok" : "bad") + tile("Warnings", warnings, warnings == 0 ? "ok" : "warn")
                + tile("Info", infos, infos == 0 ? "ok" : "info") + tile("Undeclared", undeclared, undeclared == 0 ? "ok" : "bad")
                + tile("Candidates", report.curation().decisions().size(), "neutral");
        return """
                <section class="kpis" aria-label="Run summary">
                %s</section>
                """.formatted(tiles);
    }

    /**
     * Renders one summary tile.
     *
     * @param label tile caption.
     * @param value counted value.
     * @param tone tile tone class.
     * @return tile markup.
     */
    private String tile(String label, int value, String tone) {
        return """
                <div class="kpi %s"><span class="kpi-value">%s</span><span class="kpi-label">%s</span></div>
                """.formatted(tone, value, label);
    }

    /**
     * Renders the findings, grouped by severity and then by diagnostic code so that the meaning of a code is stated
     * once per group instead of once per item. The severity filter is a set of radio inputs the stylesheet acts on, so
     * the section stays interactive without script.
     *
     * @param report consolidated JSON report.
     * @return findings section markup.
     */
    private String findingsSection(ExtractionReport report) {
        Map<String, Integer> severityCounts = report.severityCounts();
        String groups = findingGroups(report);
        String body = groups.isEmpty() ? """
                <p class="empty-state always">No findings.</p>
                """ : groups + severityEmptyStates(severityCounts);
        String radios = renderEach(SEVERITY_ORDER, severity -> """
                <input type="radio" name="sev" id="sev-%s" class="filter-state">
                """.formatted(severity));
        return """
                <section id="findings">
                <h2>Findings <span class="count">%s</span></h2>
                <input type="radio" name="sev" id="sev-all" class="filter-state" checked>
                %s<div class="segmented" role="group" aria-label="Filter findings by severity">
                <label for="sev-all">All <span class="count">%s</span></label>
                %s</div>
                <div class="finding-list">
                %s</div>
                </section>
                """.formatted(report.items().size(), radios, report.items().size(), severityLabels(severityCounts), body);
    }

    /**
     * Groups the items of every severity by code, errors first, and renders one block per group.
     *
     * @param report consolidated JSON report.
     * @return concatenated finding group markup, empty when the run produced no findings.
     */
    private String findingGroups(ExtractionReport report) {
        StringBuilder groups = new StringBuilder();
        for (String severity : SEVERITY_ORDER) {
            List<ReportItem> ofSeverity = new ArrayList<>(report.items().stream().filter(item -> severity.equals(item.severity())).toList());
            ofSeverity.sort(Comparator.comparing(ReportItem::code).thenComparing(ReportItem::subject).thenComparing(ReportItem::message));
            Map<String, List<ReportItem>> byCode = new LinkedHashMap<>();
            for (ReportItem item : ofSeverity) {
                byCode.computeIfAbsent(item.code(), ignored -> new ArrayList<>()).add(item);
            }
            byCode.forEach((code, entries) -> groups.append(findingGroup(severity, code, codeMeaning(report, code), entries)));
        }
        return groups.toString();
    }

    /**
     * Renders one severity and code group with its documented meaning and its subjects.
     *
     * @param severity severity of every item in the group.
     * @param code diagnostic code of every item in the group.
     * @param meaning documented meaning of the code, or null if the report documents no meaning.
     * @param entries items of the group.
     * @return finding group markup.
     */
    private String findingGroup(String severity, String code, String meaning, List<ReportItem> entries) {
        String documentation = meaning == null ? "" : """
                <p class="code-doc">%s</p>
                """.formatted(escape(meaning));
        String rows = renderEach(entries, entry -> """
                <li><code class="subject">%s</code><span class="msg">%s</span></li>
                """.formatted(escape(entry.subject()), escape(entry.message())));
        return """
                <article class="fgroup" data-severity="%s">
                <h3><span class="chip %s">%s</span><code>%s</code><span class="count">%s</span></h3>
                %s<ul class="fitems">
                %s</ul>
                </article>
                """.formatted(severity, severity, escape(severity), escape(code), entries.size(), documentation, rows);
    }

    /**
     * Renders the severity filter labels. A severity without findings is muted but stays selectable, so the filter
     * always offers the same choices.
     *
     * @param severityCounts item counts per severity.
     * @return filter label markup.
     */
    private String severityLabels(Map<String, Integer> severityCounts) {
        return renderEach(SEVERITY_ORDER, severity -> """
                <label for="sev-%s"%s>%s <span class="count">%s</span></label>
                """.formatted(severity, countOf(severityCounts, severity) == 0 ? " class=\"zero\"" : "", SEVERITY_LABELS.get(severity),
                countOf(severityCounts, severity)));
    }

    /**
     * Renders the per-severity empty state that the stylesheet reveals when a severity without findings is selected, so
     * filtering never resolves to a blank panel.
     *
     * @param severityCounts item counts per severity.
     * @return empty state markup for every severity without findings.
     */
    private String severityEmptyStates(Map<String, Integer> severityCounts) {
        List<String> empty = SEVERITY_ORDER.stream().filter(severity -> countOf(severityCounts, severity) == 0).toList();
        return renderEach(empty, severity -> """
                <p class="empty-state" data-for="%s">No %s findings in this run.</p>
                """.formatted(severity, severity));
    }

    /**
     * Renders the membership outcome: the kind-by-state matrix, the undeclared candidates that block the run, the
     * members grouped by membership source, the excluded candidates, and the informational unmodeled anchors. A run
     * that failed before curation carries no decisions at all, which is stated instead of being rendered as a complete
     * curation with zero findings.
     *
     * @param curation manifest curation section of the report.
     * @return candidate decision section markup.
     */
    private String decisionsSection(CurationReport curation) {
        List<CurationDecision> decisions = curation.decisions();
        if (decisions.isEmpty()) {
            return """
                    <section id="decisions">
                    <h2>Candidate decisions</h2>
                    <div class="callout compact">
                    <h3>No candidate decisions recorded</h3>
                    <p>This run did not reach manifest curation, so no extracted candidate was classified. The findings above state why.</p>
                    </div>
                    </section>
                    """;
        }
        return """
                <section id="decisions">
                <h2>Candidate decisions <span class="count">%s</span></h2>
                %s%s%s%s%s</section>
                """.formatted(decisions.size(), decisionMatrix(curation), undeclaredBlock(decisionsWithState(decisions, CurationReport.STATE_UNDECLARED)),
                includedGroup(decisionsWithState(decisions, CurationReport.STATE_INCLUDE)),
                excludedGroup(decisionsWithState(decisions, CurationReport.STATE_EXCLUDE)),
                unmodeledGroup(decisionsWithState(decisions, CurationReport.STATE_UNMODELED)));
    }

    /**
     * Renders the candidate kind by decision state matrix, which is the compact form of the decision inventory.
     *
     * @param curation manifest curation section of the report.
     * @return matrix table markup.
     */
    private String decisionMatrix(CurationReport curation) {
        Map<String, Map<String, Integer>> byKind = curation.countsByCandidateKind();
        Map<String, Integer> totals = curation.stateCounts();
        String headCells = renderEach(DECISION_STATES, state -> "<th scope=\"col\">%s</th>".formatted(state));
        String rows = renderEach(orderedKinds(byKind), kind -> matrixRow(kind, byKind.get(kind)));
        String footCells = renderEach(DECISION_STATES, state -> "<td class=\"num total\">%s</td>".formatted(countOf(totals, state)));
        int grandTotal = DECISION_STATES.stream().mapToInt(state -> countOf(totals, state)).sum();
        return """
                <div class="scroll-x">
                <table class="matrix">
                <caption>Candidate decisions by kind</caption>
                <thead><tr><th scope="col">Candidate kind</th>%s<th scope="col">total</th></tr></thead>
                <tbody>
                %s</tbody>
                <tfoot><tr><th scope="row">total</th>%s<td class="num total">%s</td></tr></tfoot>
                </table>
                </div>
                """.formatted(headCells, rows, footCells, grandTotal);
    }

    /**
     * Renders one matrix row for a candidate kind.
     *
     * @param kind candidate kind.
     * @param counts decision state counts of that kind.
     * @return matrix row markup.
     */
    private String matrixRow(String kind, Map<String, Integer> counts) {
        String cells = renderEach(DECISION_STATES, state -> {
            int value = countOf(counts, state);
            return "<td class=\"num%s\">%s</td>".formatted(value == 0 ? " zero" : "", value);
        });
        int total = DECISION_STATES.stream().mapToInt(state -> countOf(counts, state)).sum();
        return """
                <tr><th scope="row"><code>%s</code></th>%s<td class="num total">%s</td></tr>
                """.formatted(escape(kind), cells, total);
    }

    /**
     * Orders the candidate kinds of the matrix: the known kinds in their declared order, then any further kind in the
     * sorted order of the report, so an unknown kind is rendered rather than dropped.
     *
     * @param byKind state counts grouped by candidate kind.
     * @return candidate kinds in render order.
     */
    private List<String> orderedKinds(Map<String, Map<String, Integer>> byKind) {
        List<String> ordered = new ArrayList<>(KIND_ORDER.stream().filter(byKind::containsKey).toList());
        byKind.keySet().stream().filter(kind -> !ordered.contains(kind)).forEach(ordered::add);
        return ordered;
    }

    /**
     * Renders the undeclared candidates as a blocking callout, or the explicit satisfied state when curation ran and
     * left nothing undecided.
     *
     * @param undeclared candidates without a manifest decision.
     * @return undeclared block markup.
     */
    private String undeclaredBlock(List<CurationDecision> undeclared) {
        if (undeclared.isEmpty()) {
            return """
                    <div class="callout ok compact">
                    <h3><span class="chip ok">none</span> No undeclared candidates</h3>
                    <p>Every candidate Artemis presents as a feature carries an annotation or a manifest decision, so curation is complete for this commit.</p>
                    </div>
                    """;
        }
        return """
                <div class="callout bad compact">
                <h3><span class="chip bad">undeclared</span> Undeclared candidates <span class="count">%s</span></h3>
                <p>Artemis presents these modules as features, but neither an @ArtemisFeature annotation nor a manifest entry decides about them. \
                The run is blocked until each one is annotated, declared provisional, or listed in notModeled.</p>
                <div class="scroll-x"><table>
                <thead><tr><th scope="col">Candidate</th><th scope="col">Kind</th></tr></thead>
                <tbody>
                %s</tbody>
                </table></div>
                </div>
                """.formatted(undeclared.size(), renderEach(undeclared, this::candidateRow));
    }

    /**
     * Renders the members, open by default because they are what the run delivers. The membership column states
     * whether an annotation, a provisional entry, or a technical entry declared the membership.
     *
     * @param included members of the generated model.
     * @return included disclosure markup.
     */
    private String includedGroup(List<CurationDecision> included) {
        String rows = renderEach(included, decision -> """
                <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td><td><span class="tag">%s</span></td></tr>
                """.formatted(escape(decision.candidateId()), escape(decision.candidateKind()), escape(decision.curatedId()),
                decision.membershipSource() == null ? NOT_AVAILABLE : escape(decision.membershipSource())));
        return """
                <details class="group" open>
                <summary><span class="chip ok">include</span> Included features <span class="count">%s</span></summary>
                <div class="scroll-x"><table>
                <thead><tr><th scope="col">Candidate</th><th scope="col">Kind</th><th scope="col">Generated id</th>\
                <th scope="col">Membership</th></tr></thead>
                <tbody>
                %s</tbody>
                </table></div>
                </details>
                """.formatted(included.size(), rows);
    }

    /**
     * Renders the excluded candidates collapsed and grouped by exclusion reason, largest group first. They are the
     * least volatile part of the report and would otherwise dominate the page.
     *
     * @param excluded candidates the manifest excludes.
     * @return excluded disclosure markup.
     */
    private String excludedGroup(List<CurationDecision> excluded) {
        Map<String, List<CurationDecision>> byReason = new TreeMap<>();
        for (CurationDecision decision : excluded) {
            String reason = decision.reason() == null ? FeatureScopeManifest.EXCLUSION_REASON_UNSPECIFIED : decision.reason();
            byReason.computeIfAbsent(reason, ignored -> new ArrayList<>()).add(decision);
        }
        List<Map.Entry<String, List<CurationDecision>>> ordered = new ArrayList<>(byReason.entrySet());
        Comparator<Map.Entry<String, List<CurationDecision>>> bySizeThenReason = Comparator
                .<Map.Entry<String, List<CurationDecision>>> comparingInt(entry -> entry.getValue().size()).reversed().thenComparing(Map.Entry::getKey);
        ordered.sort(bySizeThenReason);
        return """
                <details class="group">
                <summary><span class="chip skip">exclude</span> Excluded candidates <span class="count">%s</span>\
                <span class="summary-hint">grouped by reason</span></summary>
                %s</details>
                """.formatted(excluded.size(), renderEach(ordered, entry -> excludedReasonGroup(entry.getKey(), entry.getValue())));
    }

    /**
     * Renders one exclusion reason group. The reason code is manifest-authored and rendered as written, because the
     * extractor defines no closed set of reasons.
     *
     * @param reason exclusion reason code.
     * @param entries candidates excluded for that reason.
     * @return nested disclosure markup.
     */
    private String excludedReasonGroup(String reason, List<CurationDecision> entries) {
        return """
                <details class="group nested">
                <summary><code>%s</code> <span class="count">%s</span></summary>
                <div class="scroll-x"><table>
                <thead><tr><th scope="col">Candidate</th><th scope="col">Kind</th></tr></thead>
                <tbody>
                %s</tbody>
                </table></div>
                </details>
                """.formatted(escape(reason), entries.size(), renderEach(entries, this::candidateRow));
    }

    /**
     * Renders the candidates nobody decided about that Artemis does not present as features. They never block the
     * run and stay outside the model until someone annotates or declares them; the group is omitted when empty.
     *
     * @param unmodeled undecided candidates that are not feature-shaped.
     * @return unmodeled disclosure markup, empty when there are none.
     */
    private String unmodeledGroup(List<CurationDecision> unmodeled) {
        if (unmodeled.isEmpty()) {
            return "";
        }
        return """
                <details class="group">
                <summary><span class="chip info">unmodeled</span> Unmodeled anchors <span class="count">%s</span>\
                <span class="summary-hint">no decision; informational</span></summary>
                <div class="scroll-x"><table>
                <thead><tr><th scope="col">Candidate</th><th scope="col">Kind</th></tr></thead>
                <tbody>
                %s</tbody>
                </table></div>
                </details>
                """.formatted(unmodeled.size(), renderEach(unmodeled, this::candidateRow));
    }

    /**
     * Renders a candidate identity row shared by the undeclared, excluded, and unmodeled tables.
     *
     * @param decision candidate decision.
     * @return table row markup.
     */
    private String candidateRow(CurationDecision decision) {
        return """
                <tr><td><code>%s</code></td><td>%s</td></tr>
                """.formatted(escape(decision.candidateId()), escape(decision.candidateKind()));
    }

    /**
     * Renders the per-member configuration-key resolutions: every key the precedence merge considered, with its
     * origin, decision, secret flag, and evidence references. Members without any key resolution are summarized in
     * one line instead of rendering empty tables. A run that failed before derivation carries no section data, which
     * is stated explicitly.
     *
     * @param configDerivation configuration-derivation section of the report, or null.
     * @return configuration section markup.
     */
    private String configDerivationSection(ConfigDerivationReport configDerivation) {
        if (configDerivation == null) {
            return """
                    <section id="configuration">
                    <h2>Configuration derivation</h2>
                    <p class="muted">This run did not reach configuration derivation; the findings above state why.</p>
                    </section>
                    """;
        }
        List<MemberConfigDerivation> withKeys = configDerivation.members().stream().filter(member -> !member.keys().isEmpty()).toList();
        List<MemberConfigDerivation> withoutKeys = configDerivation.members().stream().filter(member -> member.keys().isEmpty()).toList();
        String emptyMembers = withoutKeys.isEmpty() ? "" : """
                <p class="muted">No configuration keys were declared or derived for: %s.</p>
                """.formatted(escape(withoutKeys.stream().map(MemberConfigDerivation::featureId).collect(Collectors.joining(", "))));
        return """
                <section id="configuration">
                <h2>Configuration derivation <span class="count">%s</span></h2>
                <p class="muted">Per functional member, every configuration key the precedence merge considered: declared by the \
                @ArtemisFeature annotation, confirmed or rejected by the manifest, derived from guarded Artemis structure, or listed as a tunable.</p>
                %s%s</section>
                """.formatted(configDerivation.members().size(), renderEach(withKeys, this::memberDerivationGroup), emptyMembers);
    }

    /**
     * Renders one member's key resolutions as a collapsible table.
     *
     * @param member member key resolutions.
     * @return nested disclosure markup.
     */
    private String memberDerivationGroup(MemberConfigDerivation member) {
        String rows = renderEach(member.keys(), resolution -> """
                <tr><td><code>%s</code></td><td><span class="tag">%s</span></td><td><span class="tag">%s</span></td><td>%s</td><td>%s</td><td>%s</td></tr>
                """.formatted(escape(resolution.key()), escape(resolution.origin()), escape(resolution.decision()), resolution.secret() ? "secret" : "",
                evidenceReferences(resolution.evidence()), resolution.detail() == null ? "" : escape(resolution.detail())));
        return """
                <details class="group nested">
                <summary><code>%s</code> <span class="count">%s</span></summary>
                <div class="scroll-x"><table>
                <thead><tr><th scope="col">Key</th><th scope="col">Origin</th><th scope="col">Decision</th><th scope="col">Secret</th>\
                <th scope="col">Evidence</th><th scope="col">Note</th></tr></thead>
                <tbody>
                %s</tbody>
                </table></div>
                </details>
                """.formatted(escape(member.featureId()), member.keys().size(), rows);
    }

    /**
     * Renders evidence items as short file references. Only the file name and line are shown, mirroring the model's
     * evidence reference style, so the sanitizer never sees a slash-separated path.
     *
     * @param evidence evidence items of one key resolution.
     * @return escaped evidence references separated by spaces.
     */
    private String evidenceReferences(List<EvidenceItem> evidence) {
        return renderEach(evidence, item -> {
            String fileName = item.file() == null ? "" : item.file().substring(item.file().lastIndexOf('/') + 1);
            String reference = item.line() == null ? fileName : fileName + ":" + item.line();
            return """
                    <code>%s</code>
                    """.formatted(escape(reference));
        });
    }

    /**
     * Renders the release delta section.
     *
     * @return release delta section markup.
     */
    private String releaseDeltaSection() {
        return """
                <section id="delta">
                <h2>Release delta</h2>
                <p class="muted">No previous validated snapshot baseline was configured; release delta is informational and was skipped.</p>
                </section>
                """;
    }

    /**
     * Renders the links to the machine-readable artifacts of this run. The targets are internal constants and are
     * therefore inserted without sanitization, which would rewrite their slashes.
     *
     * @return raw artifact section markup.
     */
    private String rawArtifactsSection() {
        return """
                <section id="raw">
                <h2>Raw artifacts</h2>
                <ul class="file-links">
                <li><a href="extraction-report.json"><code>extraction-report.json</code></a>\
                <span class="muted">Consolidated diagnostics, counts, and code contract</span></li>
                <li><a href="../model/manifest-conformance-report.json"><code>manifest-conformance-report.json</code></a>\
                <span class="muted">Source-to-manifest conformance bound to generated identifiers</span></li>
                <li><a href="../workflow/guided-workflow-validation.json"><code>guided-workflow-validation.json</code></a>\
                <span class="muted">Coverage, capability, and consistency findings</span></li>
                <li><a href="release-delta-report.json"><code>release-delta-report.json</code></a>\
                <span class="muted">Comparison against a deployed baseline</span></li>
                </ul>
                <p class="muted">Paths are relative to this run directory. Machine-specific paths and secrets are never rendered.</p>
                </section>
                """;
    }

    /**
     * Reads the stylesheet resource. It is concatenated verbatim so that literal percent signs in CSS declarations
     * never reach a format string.
     *
     * @return stylesheet text.
     * @throws IllegalStateException if the resource is missing from the classpath.
     * @throws UncheckedIOException if the resource cannot be read.
     */
    private String stylesheet() {
        try (InputStream resource = ExtractionHtmlReportRenderer.class.getResourceAsStream(STYLESHEET_RESOURCE)) {
            if (resource == null) {
                throw new IllegalStateException("The extraction report stylesheet " + STYLESHEET_RESOURCE + " is missing from the classpath.");
            }
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
        catch (IOException exception) {
            throw new UncheckedIOException("The extraction report stylesheet " + STYLESHEET_RESOURCE + " could not be read.", exception);
        }
    }

    /**
     * Maps every value through a per-row template and joins the results, which is the only repetition mechanism of this
     * renderer.
     *
     * @param <T> rendered value type.
     * @param values values to render, in render order.
     * @param rowTemplate template applied to one value.
     * @return concatenated markup.
     */
    private <T> String renderEach(Collection<T> values, Function<T, String> rowTemplate) {
        return values.stream().map(rowTemplate).collect(Collectors.joining());
    }

    /**
     * Selects the decisions of one curation state, preserving the deterministic order of the report.
     *
     * @param decisions all candidate decisions.
     * @param state curation state to select.
     * @return decisions in that state.
     */
    private List<CurationDecision> decisionsWithState(List<CurationDecision> decisions, String state) {
        return decisions.stream().filter(decision -> state.equals(decision.state())).toList();
    }

    /**
     * Reads the documented meaning of a diagnostic code.
     *
     * @param report consolidated JSON report.
     * @param code diagnostic code.
     * @return documented meaning, or null if the report documents none.
     */
    private String codeMeaning(ExtractionReport report, String code) {
        return report.codes() == null ? null : report.codes().get(code);
    }

    /**
     * Reads one count, treating an absent key or map as zero.
     *
     * @param counts count map, possibly null.
     * @param key counted key.
     * @return counted value, or zero.
     */
    private int countOf(Map<String, Integer> counts, String key) {
        return counts == null ? 0 : counts.getOrDefault(key, 0);
    }

    /**
     * Shortens a commit hash for the status bar.
     *
     * @param commit full commit hash.
     * @return leading characters of the hash.
     */
    private String shortCommit(String commit) {
        return commit == null ? "" : commit.substring(0, Math.min(SHORT_COMMIT_LENGTH, commit.length()));
    }

    /**
     * Shortens a digest for the status bar, dropping its algorithm prefix.
     *
     * @param digest full digest, optionally prefixed with its algorithm.
     * @return leading characters of the digest body.
     */
    private String shortDigest(String digest) {
        if (digest == null) {
            return "";
        }
        int separator = digest.indexOf(':');
        String body = separator < 0 ? digest : digest.substring(separator + 1);
        return body.substring(0, Math.min(SHORT_DIGEST_LENGTH, body.length()));
    }

    /**
     * Redacts secrets and absolute paths, then escapes markup. This is the only path for a value that originates in
     * scanned Artemis source.
     *
     * @param value untrusted value, possibly null.
     * @return sanitized and escaped value.
     */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("(?i)(password|token|secret)=\\S+", "$1=[redacted]")
                .replaceAll("(?:[A-Za-z]:\\\\|/)[^\\s<>]+", "[path]");
        return sanitized.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
