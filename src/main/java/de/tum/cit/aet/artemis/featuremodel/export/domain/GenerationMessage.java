package de.tum.cit.aet.artemis.featuremodel.export.domain;

/**
 * A single warning, error, or informational note produced during artifact generation.
 *
 * @param severity message severity, one of {@link #WARNING}, {@link #INFO}, or {@link #ERROR}.
 * @param featureId feature the message relates to, or {@code null} for model-level messages.
 * @param parameter configuration path or profile key the message relates to, or {@code null}.
 * @param message human-readable message.
 */
public record GenerationMessage(String severity, String featureId, String parameter, String message) {

    /** Severity for issues that do not block generation but must be resolved before real deployment. */
    public static final String WARNING = "warning";

    /** Severity for neutral notes that do not affect deployment readiness. */
    public static final String INFO = "info";

    /** Severity reserved for blocking failures; generation throws before producing a report, so reports stay error-free. */
    public static final String ERROR = "error";

    /**
     * Creates a warning message.
     *
     * @param featureId related feature id, or {@code null}.
     * @param parameter related configuration path or profile key, or {@code null}.
     * @param message human-readable message.
     * @return warning message.
     */
    public static GenerationMessage warning(String featureId, String parameter, String message) {
        return new GenerationMessage(WARNING, featureId, parameter, message);
    }

    /**
     * Creates an informational message.
     *
     * @param featureId related feature id, or {@code null}.
     * @param message human-readable message.
     * @return informational message.
     */
    public static GenerationMessage info(String featureId, String message) {
        return new GenerationMessage(INFO, featureId, null, message);
    }
}
