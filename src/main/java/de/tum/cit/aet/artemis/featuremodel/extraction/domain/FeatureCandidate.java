package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

/**
 * One extracted feature candidate. Candidates are discovery output only: they never enter the generated model
 * without a manifest include decision, and namespaced ids keep the anchor kind explicit.
 *
 * @param id namespaced candidate id, for example {@code module:iris}, {@code toggle:Exports},
 *            {@code profile:localci}, or {@code infra:mysql}.
 * @param kind candidate kind, one of the {@code KIND_*} constants.
 * @param name human-readable name from Artemis i18n, or null when Artemis has none.
 * @param description human-readable description from Artemis i18n, or null when Artemis has none.
 * @param disableWarning disable warning text from Artemis i18n, only present for runtime toggles.
 * @param configKey backing configuration key, or null when the candidate has no configuration anchor.
 * @param defaultValue default value of the configuration key found in the scanned Artemis YAML defaults, or null.
 * @param serverConstant server constant symbol declaring the anchor, or null.
 * @param clientConstant client constant or enum symbol mirroring the anchor, or null.
 * @param serverConditionClass simple name of the backing Spring condition class, or null.
 * @param springProfile Spring profile id for profile candidates, or null.
 * @param enumeratedByServer true when the module id is returned by {@code ArtemisConfigHelper.getEnabledFeatures},
 *            null for candidates where the enumeration does not apply.
 * @param displayedOnAdminPage true when the admin Features page displays this candidate, null when the display
 *            membership does not apply to the candidate kind.
 * @param documentationUrl documentation link curated on the admin Features page, or null.
 */
public record FeatureCandidate(String id, String kind, String name, String description, String disableWarning, String configKey, Object defaultValue,
        String serverConstant, String clientConstant, String serverConditionClass, String springProfile, Boolean enumeratedByServer, Boolean displayedOnAdminPage,
        String documentationUrl) {

    public static final String KIND_MODULE_FEATURE = "module-feature";

    public static final String KIND_RUNTIME_TOGGLE = "runtime-toggle";

    public static final String KIND_SPRING_PROFILE = "spring-profile";

    public static final String KIND_INFRASTRUCTURE = "infrastructure";

    public static final String NAMESPACE_MODULE = "module:";

    public static final String NAMESPACE_TOGGLE = "toggle:";

    public static final String NAMESPACE_PROFILE = "profile:";

    public static final String NAMESPACE_INFRASTRUCTURE = "infra:";
}
