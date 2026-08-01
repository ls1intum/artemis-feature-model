package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.util.List;

/**
 * Verdict on whether the manifest describes the scanned source completely. A run may only assemble and publish a
 * model when every discovered candidate and every relation between included features has an explicit decision, every
 * manifest entry still resolves, no two decisions collide, and every extractor actually ran. The lists stay complete
 * even when the verdict fails, so one run names every gap a maintainer has to close.
 *
 * @param conformant true only when every list below is empty.
 * @param undeclaredCandidates candidates without an include or exclude decision, sorted.
 * @param undeclaredRelations relation candidates between included features without a constraint or ignore entry, sorted.
 * @param unresolvedAnchors manifest anchors that match no candidate or several candidates, sorted.
 * @param conflictingDecisions candidates or ids with colliding manifest decisions or unresolved references, sorted.
 * @param extractorFailures scans that failed, so the source facts of this run are incomplete, sorted.
 */
public record ManifestConformance(boolean conformant, List<String> undeclaredCandidates, List<String> undeclaredRelations, List<String> unresolvedAnchors,
        List<String> conflictingDecisions, List<String> extractorFailures) {

    /**
     * Normalizes the finding lists to immutable copies.
     */
    public ManifestConformance {
        undeclaredCandidates = undeclaredCandidates == null ? List.of() : List.copyOf(undeclaredCandidates);
        undeclaredRelations = undeclaredRelations == null ? List.of() : List.copyOf(undeclaredRelations);
        unresolvedAnchors = unresolvedAnchors == null ? List.of() : List.copyOf(unresolvedAnchors);
        conflictingDecisions = conflictingDecisions == null ? List.of() : List.copyOf(conflictingDecisions);
        extractorFailures = extractorFailures == null ? List.of() : List.copyOf(extractorFailures);
    }

    /**
     * Derives the verdict from the collected findings.
     *
     * @param undeclaredCandidates candidates without a manifest decision.
     * @param undeclaredRelations relation candidates without a decision.
     * @param unresolvedAnchors manifest anchors that no longer resolve to exactly one candidate.
     * @param conflictingDecisions colliding decisions and unresolved semantic references.
     * @param extractorFailures failed scans.
     * @return conformance verdict that is conformant only when no finding was collected.
     */
    public static ManifestConformance from(List<String> undeclaredCandidates, List<String> undeclaredRelations, List<String> unresolvedAnchors,
            List<String> conflictingDecisions, List<String> extractorFailures) {
        boolean conformant = undeclaredCandidates.isEmpty() && undeclaredRelations.isEmpty() && unresolvedAnchors.isEmpty() && conflictingDecisions.isEmpty()
                && extractorFailures.isEmpty();
        return new ManifestConformance(conformant, undeclaredCandidates, undeclaredRelations, unresolvedAnchors, conflictingDecisions, extractorFailures);
    }

    /**
     * Summarizes the verdict for a command line message.
     *
     * @return one line naming each non-empty finding group.
     */
    public String describeFindings() {
        StringBuilder description = new StringBuilder();
        appendGroup(description, "undeclared candidates", undeclaredCandidates);
        appendGroup(description, "undeclared relations", undeclaredRelations);
        appendGroup(description, "unresolved anchors", unresolvedAnchors);
        appendGroup(description, "conflicting decisions", conflictingDecisions);
        appendGroup(description, "extractor failures", extractorFailures);
        return description.toString();
    }

    /**
     * Appends one non-empty finding group to the summary.
     *
     * @param description summary being assembled.
     * @param label human-readable group name.
     * @param subjects subjects of the group.
     */
    private void appendGroup(StringBuilder description, String label, List<String> subjects) {
        if (subjects.isEmpty()) {
            return;
        }
        if (!description.isEmpty()) {
            description.append("; ");
        }
        description.append(subjects.size()).append(' ').append(label).append(" (").append(String.join(", ", subjects)).append(')');
    }
}
