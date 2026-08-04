package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;

/** Renders the consolidated extraction verdict as one dependency-free, sanitized HTML artifact. */
class ExtractionHtmlReportRenderer {

    /**
     * Renders a report without exposing absolute paths or markup supplied by diagnostics.
     *
     * @param report consolidated JSON report.
     * @return UTF-8 HTML bytes.
     */
    byte[] render(ExtractionReport report) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">")
                .append("<title>Feature extraction report</title><style>")
                .append("body{font:15px system-ui,sans-serif;max-width:1100px;margin:2rem auto;padding:0 1rem;color:#172033}")
                .append("h1,h2{color:#102a43}table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccd6e0;padding:.45rem;text-align:left}")
                .append(".pass{color:#137333}.fail,.error{color:#b3261e}.warning{color:#8a4b08}code{overflow-wrap:anywhere}")
                .append("</style></head><body><h1>Feature extraction report</h1>");
        html.append("<p class=\"").append(escape(report.status())).append("\"><strong>Overall verdict: ")
                .append(escape(report.status().toUpperCase())).append("</strong></p>");
        section(html, "Source and manifest provenance");
        html.append("<dl><dt>Artemis commit</dt><dd><code>").append(escape(report.artemisCommit())).append("</code></dd>")
                .append("<dt>Manifest digest</dt><dd><code>").append(escape(report.manifestDigest())).append("</code></dd></dl>");
        section(html, "Candidate decisions");
        decisionTable(html, report.curation());
        section(html, "Blocking reasons and artifact validation");
        itemTable(html, report.items());
        section(html, "Relation, constraint, workflow, catalog, profile, and snapshot validation");
        html.append("<p>All structured findings are listed above. A passing verdict means every deterministic delivery gate completed successfully.</p>");
        section(html, "Release delta");
        html.append("<p>No previous validated snapshot baseline was configured; release delta is informational and was skipped.</p>");
        section(html, "Raw artifacts");
        html.append("<ul><li><a href=\"extraction-report.json\">extraction-report.json</a></li>")
                .append("<li><a href=\"../model/manifest-conformance-report.json\">manifest-conformance-report.json</a></li>")
                .append("<li><a href=\"../workflow/guided-workflow-validation.json\">guided-workflow-validation.json</a></li></ul>")
                .append("</body></html>\n");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void decisionTable(StringBuilder html, CurationReport curation) {
        html.append("<p>Include: ").append(count(curation, ScopeCurationService.STATE_INCLUDE)).append("; exclude: ")
                .append(count(curation, ScopeCurationService.STATE_EXCLUDE)).append("; undeclared: ")
                .append(count(curation, ScopeCurationService.STATE_UNDECLARED)).append(".</p>")
                .append("<table><thead><tr><th>Candidate</th><th>Kind</th><th>Decision</th><th>Generated id / reason</th></tr></thead><tbody>");
        for (CurationReport.CurationDecision decision : curation.decisions()) {
            String detail = decision.curatedId() != null ? decision.curatedId() : decision.reason();
            html.append("<tr><td><code>").append(escape(decision.candidateId())).append("</code></td><td>")
                    .append(escape(decision.candidateKind())).append("</td><td>").append(escape(decision.state())).append("</td><td>")
                    .append(escape(detail)).append("</td></tr>");
        }
        html.append("</tbody></table>");
    }

    private void itemTable(StringBuilder html, List<ReportItem> items) {
        if (items.isEmpty()) {
            html.append("<p>No findings.</p>");
            return;
        }
        html.append("<table><thead><tr><th>Severity</th><th>Code</th><th>Subject</th><th>Message</th></tr></thead><tbody>");
        for (ReportItem item : items) {
            html.append("<tr><td class=\"").append(escape(item.severity())).append("\">").append(escape(item.severity())).append("</td><td><code>")
                    .append(escape(item.code())).append("</code></td><td>").append(escape(item.subject())).append("</td><td>")
                    .append(escape(item.message())).append("</td></tr>");
        }
        html.append("</tbody></table>");
    }

    private int count(CurationReport curation, String state) {
        return curation.stateCounts() == null ? 0 : curation.stateCounts().getOrDefault(state, 0);
    }

    private void section(StringBuilder html, String title) {
        html.append("<h2>").append(escape(title)).append("</h2>");
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("(?i)(password|token|secret)=\\S+", "$1=[redacted]")
                .replaceAll("(?:[A-Za-z]:\\\\|/)[^\\s<>]+", "[path]");
        return sanitized.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
