package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * One diagnostic entry of the extraction report. The {@code code} strings are a stable contract for later automation
 * phases and must not be renamed casually.
 *
 * @param severity item severity, one of {@code error}, {@code warning}, or {@code info}.
 * @param code stable diagnostic code, one of the {@code CODE_*} constants.
 * @param subject candidate id, curated feature id, config key, or symbol the item is about.
 * @param message human-readable explanation.
 */
public record ReportItem(String severity, String code, String subject, String message) {

    public static final String SEVERITY_ERROR = "error";

    public static final String SEVERITY_WARNING = "warning";

    public static final String SEVERITY_INFO = "info";

    /** A candidate found in Artemis has no matching feature in the active curated model. */
    public static final String CODE_NEW_CANDIDATE_NOT_IN_MODEL = "NEW_CANDIDATE_NOT_IN_MODEL";

    /** A curated feature references a config key or condition class that no longer exists in Artemis. */
    public static final String CODE_CURATED_ANCHOR_MISSING = "CURATED_ANCHOR_MISSING";

    /** A curated file or line evidence reference no longer matches the scanned Artemis sources. */
    public static final String CODE_CURATED_EVIDENCE_STALE = "CURATED_EVIDENCE_STALE";

    /** A curated feature has no config-key or condition-class anchor; expected for conceptual aggregates. */
    public static final String CODE_UNANCHORED_CURATED_FEATURE = "UNANCHORED_CURATED_FEATURE";

    /** Frontend and backend disagree about a module feature constant or runtime toggle enum member. */
    public static final String CODE_FE_BE_MIRROR_MISMATCH = "FE_BE_MIRROR_MISMATCH";

    /** The curated config key catalog disagrees with the scanned Artemis configuration keys or commit pin. */
    public static final String CODE_CONFIG_KEY_CATALOG_DRIFT = "CONFIG_KEY_CATALOG_DRIFT";

    /** One extractor failed to parse its source; the scan continued without its contribution. */
    public static final String CODE_EXTRACTOR_ERROR = "EXTRACTOR_ERROR";

    /** A backend property name constant has no matching backend module feature constant, or vice versa. */
    public static final String CODE_MODULE_CONSTANT_ASYMMETRY = "MODULE_CONSTANT_ASYMMETRY";

    /** An extracted candidate is absent from both manifest membership lists. */
    public static final String CODE_PENDING_SCOPE_DECISION = "PENDING_SCOPE_DECISION";

    /** A source annotation exists on a candidate that the manifest does not include. */
    public static final String CODE_ANNOTATED_BUT_UNSCOPED = "ANNOTATED_BUT_UNSCOPED";

    /** Source annotation semantics take precedence over redundant semantics in the include entry. */
    public static final String CODE_ANNOTATION_OVERRIDES_MANIFEST = "ANNOTATION_OVERRIDES_MANIFEST";

    /** A source annotation could not be joined to an extracted candidate. */
    public static final String CODE_ANNOTATED_ANCHOR_NOT_EXTRACTED = "ANNOTATED_ANCHOR_NOT_EXTRACTED";

    /** The scope manifest commit pin differs from the scanned Artemis commit. */
    public static final String CODE_MANIFEST_COMMIT_MISMATCH = "MANIFEST_COMMIT_MISMATCH";

    /**
     * Creates an error item.
     *
     * @param code stable diagnostic code.
     * @param subject item subject.
     * @param message human-readable explanation.
     * @return report item with severity {@code error}.
     */
    public static ReportItem error(String code, String subject, String message) {
        return new ReportItem(SEVERITY_ERROR, code, subject, message);
    }

    /**
     * Creates a warning item.
     *
     * @param code stable diagnostic code.
     * @param subject item subject.
     * @param message human-readable explanation.
     * @return report item with severity {@code warning}.
     */
    public static ReportItem warning(String code, String subject, String message) {
        return new ReportItem(SEVERITY_WARNING, code, subject, message);
    }

    /**
     * Creates an info item.
     *
     * @param code stable diagnostic code.
     * @param subject item subject.
     * @param message human-readable explanation.
     * @return report item with severity {@code info}.
     */
    public static ReportItem info(String code, String subject, String message) {
        return new ReportItem(SEVERITY_INFO, code, subject, message);
    }
}
