package de.tum.cit.aet.artemis.featuremodel.selection.domain;

/**
 * One graded diagnostic about the guided workflow. Findings never fail a request in the running app: they surface
 * coverage gaps, capability typos, template inconsistencies, lifecycle states, and unfinished prose that would
 * otherwise break silently, both in the running app (logged) and in the extraction generation report. Severity is
 * assigned together with the code at the diagnostic source and is the single delivery-gating axis: {@code error}
 * findings make an extraction run ineligible, while {@code warning} and {@code info} findings publish.
 *
 * @param severity finding severity, one of {@code error}, {@code warning}, or {@code info}.
 * @param code stable finding code, one of the {@code CODE_*} constants.
 * @param subject feature id, capability id, template id, or option id the finding is about.
 * @param message human-readable explanation.
 */
public record GuidedWorkflowFinding(String severity, String code, String subject, String message) {

    public static final String SEVERITY_ERROR = "error";

    public static final String SEVERITY_WARNING = "warning";

    public static final String SEVERITY_INFO = "info";

    /** A selectable functional model feature is not selected by any published guided decision option. */
    public static final String CODE_COVERAGE_GAP = "GUIDED_WORKFLOW_COVERAGE_GAP";

    /** A model group with selectable functional children is not represented by a final review group. */
    public static final String CODE_REVIEW_GROUP_GAP = "GUIDED_WORKFLOW_REVIEW_GROUP_GAP";

    /** A model feature requires a capability id that no known deployment profile provides. */
    public static final String CODE_UNKNOWN_CAPABILITY = "GUIDED_WORKFLOW_UNKNOWN_CAPABILITY";

    /** A use-case template both selects and deselects the same feature. */
    public static final String CODE_TEMPLATE_CONFLICT = "GUIDED_WORKFLOW_TEMPLATE_CONFLICT";

    /** A use-case template presets a feature that no published guided decision option covers. */
    public static final String CODE_TEMPLATE_UNCOVERED_PRESET = "GUIDED_WORKFLOW_TEMPLATE_UNCOVERED_PRESET";

    /** The default template carries preset selections instead of deferring to the backend-derived defaults. */
    public static final String CODE_DEFAULT_TEMPLATE_PRESET = "GUIDED_WORKFLOW_DEFAULT_TEMPLATE_PRESET";

    /** A published option or its decision still carries TODO or empty required prose; the runtime omits it. */
    public static final String CODE_STUB_PROSE = "GUIDED_WORKFLOW_STUB_PROSE";

    /** An option declares no lifecycle status; only payloads generated before the lifecycle may omit it. */
    public static final String CODE_MISSING_STATUS = "GUIDED_WORKFLOW_MISSING_STATUS";

    /** A draft option exists; it is reported for visibility and never served. */
    public static final String CODE_DRAFT_OPTION = "GUIDED_WORKFLOW_DRAFT_OPTION";

    /** A draft option references a feature unknown to the model; it hardens to an error on publication. */
    public static final String CODE_DRAFT_UNKNOWN_REFERENCE = "GUIDED_WORKFLOW_DRAFT_UNKNOWN_REFERENCE";

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

    /**
     * Creates an error finding that makes an extraction run ineligible for delivery.
     *
     * @param code stable finding code.
     * @param subject finding subject.
     * @param message human-readable explanation.
     * @return finding with severity {@code error}.
     */
    public static GuidedWorkflowFinding error(String code, String subject, String message) {
        return new GuidedWorkflowFinding(SEVERITY_ERROR, code, subject, message);
    }

    /**
     * Checks whether this finding blocks delivery.
     *
     * @return true if the severity is {@code error}.
     */
    public boolean isError() {
        return SEVERITY_ERROR.equals(severity);
    }
}
