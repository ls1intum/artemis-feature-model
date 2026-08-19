package de.tum.cit.aet.artemis.featuremodel.extraction.scan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import de.tum.cit.aet.artemis.featuremodel.extraction.domain.EvidenceItem;
import de.tum.cit.aet.artemis.featuremodel.extraction.domain.ReportItem;

/** Invocation-local evidence and diagnostic collection shared by the cohesive candidate-family assemblers. */
final class CandidateAssemblyContext {

    private final List<EvidenceItem> evidence = new ArrayList<>();

    private final List<ReportItem> items = new ArrayList<>();

    /**
     * Records one evidence item using the established null conventions.
     *
     * @param candidateId candidate or relation candidate id.
     * @param kind evidence kind.
     * @param file checkout-relative path.
     * @param line 1-based line, or null.
     * @param symbol observed symbol, or null.
     * @param detail human-readable detail, or null.
     */
    void addEvidence(String candidateId, String kind, String file, Integer line, String symbol, String detail) {
        evidence.add(new EvidenceItem(candidateId, kind, file, line, symbol, detail));
    }

    /**
     * Records one structural diagnostic in collaborator invocation order.
     *
     * @param item diagnostic to append.
     */
    void addItem(ReportItem item) {
        items.add(item);
    }

    /**
     * Returns evidence in the persisted deterministic order.
     *
     * @return sorted immutable evidence.
     */
    List<EvidenceItem> sortedEvidence() {
        List<EvidenceItem> sorted = new ArrayList<>(evidence);
        sorted.sort(Comparator.comparing(EvidenceItem::candidateId).thenComparing(item -> item.file() == null ? "" : item.file())
                .thenComparing(item -> item.line() == null ? Integer.MAX_VALUE : item.line()).thenComparing(EvidenceItem::kind)
                .thenComparing(item -> item.symbol() == null ? "" : item.symbol()));
        return List.copyOf(sorted);
    }

    /**
     * Returns structural diagnostics in their established assembly order.
     *
     * @return immutable diagnostics.
     */
    List<ReportItem> items() {
        return List.copyOf(items);
    }
}
