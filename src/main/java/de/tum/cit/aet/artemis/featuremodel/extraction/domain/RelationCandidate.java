package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * One relation candidate derived from a composite {@code *Enabled} Spring condition. Relation candidates are never
 * enforced in this phase: they stay in status {@code candidate} until a later curation phase decides about them.
 *
 * @param id stable relation candidate id derived from the condition class, for example {@code relation:AtlasMLEnabled}.
 * @param type relation type; composite conditions always yield {@code requires}-style candidates.
 * @param sourceCandidateId candidate id owning the requirement when the direction is derivable, otherwise null.
 * @param memberCandidateIds sorted candidate ids of all modules joined by the composite condition.
 * @param directed true when the condition structure implies a direction, false for plain conjunctions.
 * @param conditionClass simple name of the composite condition class the relation was derived from.
 * @param status lifecycle status; always {@code candidate} in this phase.
 * @param detail human-readable description of the observed conjunction.
 */
public record RelationCandidate(String id, String type, String sourceCandidateId, List<String> memberCandidateIds, boolean directed, String conditionClass,
        String status, String detail) {

    public static final String TYPE_REQUIRES = "requires";

    public static final String STATUS_CANDIDATE = "candidate";

    /**
     * Creates a relation candidate and normalizes the member list to an immutable copy.
     *
     * @param id stable relation candidate id.
     * @param type relation type.
     * @param sourceCandidateId source candidate id or null.
     * @param memberCandidateIds member candidate ids.
     * @param directed whether the relation direction is derivable.
     * @param conditionClass condition class simple name.
     * @param status lifecycle status.
     * @param detail human-readable detail.
     */
    public RelationCandidate {
        memberCandidateIds = memberCandidateIds == null ? List.of() : List.copyOf(memberCandidateIds);
    }
}
