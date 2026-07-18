package de.tum.cit.aet.artemis.featuremodel.selection.domain;

/**
 * One soft diagnostic about the guided workflow. Findings never fail a request: they surface coverage gaps,
 * capability typos, template inconsistencies, and unfinished scaffold prose that would otherwise break silently,
 * both in the running app (logged) and in the extraction generation report.
 *
 * @param severity finding severity, one of {@code warning} or {@code info}.
 * @param code stable finding code, one of the {@code CODE_*} constants.
 * @param subject feature id, capability id, template id, or option id the finding is about.
 * @param message human-readable explanation.
 */
public record GuidedWorkflowFinding(String severity, String code, String subject, String message) {

    public static final String SEVERITY_WARNING = "warning";

    public static final String SEVERITY_INFO = "info";

    /** A selectable functional model feature is not selected by any guided decision option. */
    public static final String CODE_COVERAGE_GAP = "GUIDED_WORKFLOW_COVERAGE_GAP";

    /** A model group with selectable functional children is not represented by a final review group. */
    public static final String CODE_REVIEW_GROUP_GAP = "GUIDED_WORKFLOW_REVIEW_GROUP_GAP";

    /** A model feature requires a capability id that no known deployment profile provides. */
    public static final String CODE_UNKNOWN_CAPABILITY = "GUIDED_WORKFLOW_UNKNOWN_CAPABILITY";

    /** A use-case template both selects and deselects the same feature. */
    public static final String CODE_TEMPLATE_CONFLICT = "GUIDED_WORKFLOW_TEMPLATE_CONFLICT";

    /** The default template carries preset selections instead of deferring to the backend-derived defaults. */
    public static final String CODE_DEFAULT_TEMPLATE_PRESET = "GUIDED_WORKFLOW_DEFAULT_TEMPLATE_PRESET";

    /** An option still carries scaffold TODO prose and needs human-authored text. */
    public static final String CODE_STUB_PROSE = "GUIDED_WORKFLOW_STUB_PROSE";

    /**
     * Creates a warning finding.
     *
     * @param code stable finding code.
     * @param subject finding subject.
     * @param message human-readable explanation.
     * @return finding with severity {@code warning}.
     */
    public static GuidedWorkflowFinding warning(String code, String subject, String message) {
        return new GuidedWorkflowFinding(SEVERITY_WARNING, code, subject, message);
    }
}
