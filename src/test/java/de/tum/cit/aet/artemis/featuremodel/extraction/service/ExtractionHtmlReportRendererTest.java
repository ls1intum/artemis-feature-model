package de.tum.cit.aet.artemis.featuremodel.extraction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.CurationReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ExtractionReport;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;

/** Covers standalone rendering, escaping, and sensitive local-value sanitization. */
class ExtractionHtmlReportRendererTest {

    @Test
    void rendersDependencyFreeReportAndSanitizesDynamicValues() {
        CurationReport curation = new CurationReport(2, "0123456789abcdef0123456789abcdef01234567", Map.of("include", 0, "exclude", 0, "undeclared", 0),
                Map.of(), List.of(), List.of());
        List<ReportItem> items = List.of(ReportItem.error(ReportItem.CODE_EXTRACTOR_ERROR, "<scanner>",
                "Failed at /Users/developer/Artemis/private.yml token=top-secret-value"));
        ExtractionReport report = new ExtractionReport(ExtractionReport.CURRENT_SCHEMA_VERSION, ExtractionReport.STATUS_FAIL,
                "0123456789abcdef0123456789abcdef01234567", "sha256:manifest", curation, Map.of(), Map.of("error", 1),
                Map.of(ReportItem.CODE_EXTRACTOR_ERROR, 1), items);

        String html = new String(new ExtractionHtmlReportRenderer().render(report), StandardCharsets.UTF_8);

        assertThat(html).contains("<!doctype html>", "Overall verdict: FAIL", "&lt;scanner&gt;", "[path]", "token=[redacted]");
        assertThat(html).doesNotContain("/Users/developer", "top-secret-value", "<script", "src=\"http");
    }
}
