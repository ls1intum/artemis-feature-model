package de.tum.cit.aet.artemis.featuremodel.extraction.source;

import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;

/**
 * One explicit scanner result path for discovered facts and diagnostics. A whole-scanner failure carries fallback
 * facts and {@code wholeScannerFailed=true}; scanners that isolate individual file failures keep their remaining facts
 * and attach diagnostics without marking the complete scanner failed.
 *
 * @param facts discovered facts or the scanner's empty fallback.
 * @param diagnostics scanner diagnostics in discovery order.
 * @param wholeScannerFailed whether the scanner invocation failed before it could return its normal fact set.
 * @param <T> scanner fact type.
 */
public record SourceScanResult<T>(T facts, List<ReportItem> diagnostics, boolean wholeScannerFailed) {

    /**
     * Creates a successful scanner result without diagnostics.
     *
     * @param facts discovered facts.
     * @param <T> scanner fact type.
     * @return successful source-scan result.
     */
    public static <T> SourceScanResult<T> success(T facts) {
        return new SourceScanResult<>(facts, List.of(), false);
    }

    /**
     * Creates a scanner result that retained facts while isolating diagnostics.
     *
     * @param facts discovered facts.
     * @param diagnostics isolated diagnostics.
     * @param <T> scanner fact type.
     * @return source-scan result with retained facts.
     */
    public static <T> SourceScanResult<T> withDiagnostics(T facts, List<ReportItem> diagnostics) {
        return new SourceScanResult<>(facts, List.copyOf(diagnostics), false);
    }

    /**
     * Creates a failed whole-scanner result with its empty fallback facts.
     *
     * @param fallback empty scanner facts.
     * @param diagnostic controlled failure diagnostic.
     * @param <T> scanner fact type.
     * @return failed source-scan result.
     */
    public static <T> SourceScanResult<T> failure(T fallback, ReportItem diagnostic) {
        return new SourceScanResult<>(fallback, List.of(diagnostic), true);
    }

    /** Normalizes diagnostics to an immutable list. */
    public SourceScanResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
