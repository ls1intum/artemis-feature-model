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

    /** Client and server disagree about a module feature constant or runtime toggle enum member. */
    public static final String CODE_CLIENT_SERVER_MIRROR_MISMATCH = "CLIENT_SERVER_MIRROR_MISMATCH";

    /** The curated config key catalog disagrees with the scanned Artemis configuration keys or commit pin. */
    public static final String CODE_CONFIG_KEY_CATALOG_DRIFT = "CONFIG_KEY_CATALOG_DRIFT";

    /** One extractor failed to parse its source; the scan continued without its contribution. */
    public static final String CODE_EXTRACTOR_ERROR = "EXTRACTOR_ERROR";

    /** A server property name constant has no matching server module feature constant, or vice versa. */
    public static final String CODE_MODULE_CONSTANT_ASYMMETRY = "MODULE_CONSTANT_ASYMMETRY";

    /** An extracted candidate is absent from both manifest membership lists, so its scope is undecided. */
    public static final String CODE_UNDECLARED_CANDIDATE = "UNDECLARED_CANDIDATE";

    /** A source annotation exists on a candidate that the manifest does not include. */
    public static final String CODE_ANNOTATED_BUT_UNSCOPED = "ANNOTATED_BUT_UNSCOPED";

    /** A source annotation declares an attribute differently from the manifest entry; the manifest value is used. */
    public static final String CODE_MANIFEST_OVERRIDES_ANNOTATION = "MANIFEST_OVERRIDES_ANNOTATION";

    /** A source annotation could not be joined to an extracted candidate. */
    public static final String CODE_ANNOTATED_ANCHOR_NOT_EXTRACTED = "ANNOTATED_ANCHOR_NOT_EXTRACTED";

    /** A manifest anchor matches no extraction candidate of this scan, or matches more than one. */
    public static final String CODE_MANIFEST_ORPHAN_ANCHOR = "MANIFEST_ORPHAN_ANCHOR";

    /** Manifest entries, annotations, or resolved semantics collide for this scan; the entry needs review. */
    public static final String CODE_MANIFEST_CURATION_CONFLICT = "MANIFEST_CURATION_CONFLICT";

    /** The assembled generated model failed the shared structural integrity validation. */
    public static final String CODE_GENERATED_MODEL_INVALID = "GENERATED_MODEL_INVALID";

    /** The assembled model differs semantically from the resolved manifest contract. */
    public static final String CODE_GENERATED_MODEL_CONFORMANCE_MISMATCH = "GENERATED_MODEL_CONFORMANCE_MISMATCH";

    /** The bundled guided workflow failed its hard reference validation against the generated model. */
    public static final String CODE_GENERATED_WORKFLOW_INVALID = "GENERATED_WORKFLOW_INVALID";

    /** A technical feature of the generated model is visible or configurable for teachers. */
    public static final String CODE_TECHNICAL_FEATURE_ROLE_LEAK = "TECHNICAL_FEATURE_ROLE_LEAK";

    /** An included technical feature provides a capability the bundled deployment profile does not list. */
    public static final String CODE_PROFILE_CAPABILITY_MISMATCH = "PROFILE_CAPABILITY_MISMATCH";

    /** A relation candidate between included features has neither a declared constraint nor an explicit ignore entry. */
    public static final String CODE_RELATION_CANDIDATE_UNDECLARED = "RELATION_CANDIDATE_UNDECLARED";

    /** A manifest constraint references a feature that was not emitted into the generated model. */
    public static final String CODE_DANGLING_GENERATED_CONSTRAINT = "DANGLING_GENERATED_CONSTRAINT";

    /** The guided workflow validation against the generated model produced findings; see guided-workflow-validation.json. */
    public static final String CODE_GUIDED_WORKFLOW_FINDINGS = "GUIDED_WORKFLOW_FINDINGS";

    /** A staged command could not verify or consume the artifact contract required for the run. */
    public static final String CODE_PIPELINE_ARTIFACT_INVALID = "PIPELINE_ARTIFACT_INVALID";

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
