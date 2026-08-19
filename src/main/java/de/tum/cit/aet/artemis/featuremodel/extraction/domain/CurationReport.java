package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;
import java.util.Map;

/**
 * Manifest classification section of the extraction report.
 *
 * @param manifestVersion loaded manifest version.
 * @param artemisCommitSha source revision derived from the verified checkout.
 * @param stateCounts total counts for include, exclude, and undeclared.
 * @param countsByCandidateKind state counts grouped by extraction candidate kind.
 * @param undeclaredCandidateIds ids without a manifest decision, sorted by candidate id.
 * @param decisions all candidate decisions, with undeclared entries first and ids sorted within each state.
 */
public record CurationReport(int manifestVersion, String artemisCommitSha, Map<String, Integer> stateCounts,
        Map<String, Map<String, Integer>> countsByCandidateKind, List<String> undeclaredCandidateIds, List<CurationDecision> decisions) {

    /** Persisted state key of a candidate the manifest includes. */
    public static final String STATE_INCLUDE = "include";

    /** Persisted state key of a candidate the manifest excludes. */
    public static final String STATE_EXCLUDE = "exclude";

    /** Persisted state key of a candidate the manifest never decided about. */
    public static final String STATE_UNDECLARED = "undeclared";

    /**
     * Normalizes report collections to immutable copies.
     */
    public CurationReport {
        undeclaredCandidateIds = undeclaredCandidateIds == null ? List.of() : List.copyOf(undeclaredCandidateIds);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }

    /**
     * Classification of one extracted candidate.
     *
     * @param candidateId namespaced candidate id.
     * @param candidateKind extraction candidate kind.
     * @param state include, exclude, or undeclared.
     * @param curatedId curated model id for included entries, otherwise null.
     * @param reason exclusion reason code, otherwise null.
     * @param semanticSource manifest or annotation for included entries, otherwise null.
     */
    public record CurationDecision(String candidateId, String candidateKind, String state, String curatedId, String reason, String semanticSource) {
    }
}
