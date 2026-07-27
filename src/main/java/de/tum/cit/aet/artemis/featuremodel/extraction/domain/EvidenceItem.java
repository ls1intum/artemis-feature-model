package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * One piece of source evidence backing a candidate or relation candidate. Anchors are identified by symbol; the file
 * path and line only document where the symbol was seen at scan time.
 *
 * @param candidateId id of the candidate or relation candidate the evidence belongs to.
 * @param kind evidence kind, for example {@code backend-constant}, {@code condition-class}, or {@code yaml-default}.
 * @param file path of the evidence file relative to the Artemis checkout root.
 * @param line 1-based line number of the evidence, or null when the source format has no useful line resolution.
 * @param symbol symbol observed at the evidence location, or null when the evidence is file-level.
 * @param detail optional human-readable detail, or null.
 */
public record EvidenceItem(String candidateId, String kind, String file, Integer line, String symbol, String detail) {

    public static final String KIND_BACKEND_CONSTANT = "backend-constant";

    public static final String KIND_BACKEND_ENUM = "backend-enum";

    public static final String KIND_BACKEND_ENUMERATION = "backend-enumeration";

    public static final String KIND_CONFIG_HELPER_ACCESSOR = "config-helper-accessor";

    public static final String KIND_CONDITION_CLASS = "condition-class";

    public static final String KIND_YAML_DEFAULT = "yaml-default";

    public static final String KIND_FRONTEND_CONSTANT = "frontend-constant";

    public static final String KIND_FRONTEND_ENUM = "frontend-enum";

    public static final String KIND_ADMIN_PAGE = "admin-page";

    public static final String KIND_I18N = "i18n";

    public static final String KIND_PROFILE_YAML = "profile-yaml";

    public static final String KIND_COMPOSE_FILE = "compose-file";

    public static final String KIND_USAGE_FEATURE_TOGGLE = "usage-feature-toggle";

    public static final String KIND_USAGE_TEMPLATE = "usage-template";

    public static final String KIND_USAGE_CONDITIONAL = "usage-conditional";
}
