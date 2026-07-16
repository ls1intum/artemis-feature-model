package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;
import java.util.Map;

/**
 * Manifest classification section of the extraction report.
 *
 * @param manifestVersion loaded manifest version.
 * @param verifiedAgainstArtemisCommit commit recorded by the manifest.
 * @param stateCounts total counts for include, exclude, and pending.
 * @param countsByCandidateKind state counts grouped by extraction candidate kind.
 * @param pendingCandidateIds pending ids sorted by candidate id.
 * @param decisions all candidate decisions, with pending entries first and ids sorted within each state.
 */
public record CurationReport(int manifestVersion, String verifiedAgainstArtemisCommit, Map<String, Integer> stateCounts,
        Map<String, Map<String, Integer>> countsByCandidateKind, List<String> pendingCandidateIds, List<CurationDecision> decisions) {

    /**
     * Normalizes report collections to immutable copies.
     */
    public CurationReport {
        pendingCandidateIds = pendingCandidateIds == null ? List.of() : List.copyOf(pendingCandidateIds);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }

    /**
     * Classification of one extracted candidate.
     *
     * @param candidateId namespaced candidate id.
     * @param candidateKind extraction candidate kind.
     * @param state include, exclude, or pending.
     * @param curatedId curated model id for included entries, otherwise null.
     * @param reason exclusion reason code, otherwise null.
     * @param semanticSource manifest or annotation for included entries, otherwise null.
     */
    public record CurationDecision(String candidateId, String candidateKind, String state, String curatedId, String reason, String semanticSource) {
    }
}
