package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Membership classification section of the extraction report.
 *
 * @param manifestVersion loaded manifest version.
 * @param artemisCommitSha source revision derived from the verified checkout.
 * @param stateCounts total counts for include, exclude, undeclared, and unmodeled.
 * @param countsByCandidateKind state counts grouped by extraction candidate kind.
 * @param undeclaredCandidateIds feature-shaped candidates without a decision, sorted by candidate id; they block the run.
 * @param decisions all candidate decisions, with undeclared entries first and ids sorted within each state.
 */
public record CurationReport(int manifestVersion, String artemisCommitSha, Map<String, Integer> stateCounts,
        Map<String, Map<String, Integer>> countsByCandidateKind, List<String> undeclaredCandidateIds, List<CurationDecision> decisions) {

    /** Persisted state key of a member of the generated model. */
    public static final String STATE_INCLUDE = "include";

    /** Persisted state key of a candidate the manifest lists in notModeled. */
    public static final String STATE_EXCLUDE = "exclude";

    /** Persisted state key of a feature-shaped candidate nobody decided about; blocks the run. */
    public static final String STATE_UNDECLARED = "undeclared";

    /** Persisted state key of a candidate without a decision that Artemis does not present as a feature; informational. */
    public static final String STATE_UNMODELED = "unmodeled";

    /** Membership source of a member declared by its {@code @ArtemisFeature} annotation. */
    public static final String SOURCE_ANNOTATION = "annotation";

    /** Membership source of a member declared by a provisional manifest entry. */
    public static final String SOURCE_PROVISIONAL = "provisional";

    /** Membership source of a member declared by a technical manifest entry. */
    public static final String SOURCE_TECHNICAL = "technical";

    /** Membership source of an excluded candidate. */
    public static final String SOURCE_NOT_MODELED = "notModeled";

    /** Membership source of a blocking undecided candidate. */
    public static final String SOURCE_UNDECLARED = "undeclared";

    /** Membership source of an informational undecided candidate. */
    public static final String SOURCE_UNMODELED = "unmodeled";

    /** All persisted states in report order. */
    public static final List<String> STATES = List.of(STATE_INCLUDE, STATE_EXCLUDE, STATE_UNDECLARED, STATE_UNMODELED);

    /**
     * Normalizes report collections to immutable copies.
     */
    public CurationReport {
        undeclaredCandidateIds = undeclaredCandidateIds == null ? List.of() : List.copyOf(undeclaredCandidateIds);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }

    /**
     * Creates a state count map with every state present, so report consumers see explicit zeros.
     *
     * @return mutable count map initialized to zero in report order.
     */
    public static Map<String, Integer> zeroStateCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        STATES.forEach(state -> counts.put(state, 0));
        return counts;
    }

    /**
     * Classification of one extracted candidate.
     *
     * @param candidateId namespaced candidate id.
     * @param candidateKind extraction candidate kind.
     * @param state include, exclude, undeclared, or unmodeled.
     * @param curatedId feature id of a member, otherwise null.
     * @param reason exclusion reason code, otherwise null.
     * @param membershipSource one of the {@code SOURCE_*} constants: what declared the membership or the lack of it.
     */
    public record CurationDecision(String candidateId, String candidateKind, String state, String curatedId, String reason, String membershipSource) {
    }
}
