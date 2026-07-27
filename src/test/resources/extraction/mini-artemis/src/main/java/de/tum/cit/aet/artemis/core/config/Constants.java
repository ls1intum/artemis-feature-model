package de.tum.cit.aet.artemis.core.config;

/**
 * Synthetic constants covering every anchor shape the extractor accepts.
 */
public final class Constants {

    /**
     * The name of the module feature used for Alpha functionality.
     */
    public static final String MODULE_FEATURE_ALPHA = "alpha";

    /**
     * The name of the module feature used for the Gamma submodule.
     */
    public static final String MODULE_FEATURE_GAMMA = "gamma";

    /**
     * The name of the module feature used for Beta functionality.
     */
    public static final String FEATURE_BETA = "beta";

    /**
     * The name of the module feature indicating Beta is required for extra features.
     */
    public static final String FEATURE_BETA_EXTRA = "beta-extra";

    /**
     * The name of the property used to enable or disable the Alpha module.
     */
    public static final String ALPHA_ENABLED_PROPERTY_NAME = "artemis.alpha.enabled";

    /**
     * The name of the property used to enable or disable the Gamma submodule of Alpha.
     */
    public static final String GAMMA_ENABLED_PROPERTY_NAME = "artemis.alpha.gamma.enabled";

    /**
     * The name of the property used to enable or disable Beta functionality.
     */
    public static final String BETA_ENABLED_PROPERTY_NAME = "artemis.user-management.beta.enabled";

    /**
     * The name of the Spring profile used for the first continuous integration variant.
     */
    public static final String PROFILE_CIONE = "cione";

    /**
     * The name of the Spring profile used for the build agent variant.
     */
    public static final String PROFILE_AGENT = "agentx";

    /**
     * The name of the Spring profile used for the Jenkins variant.
     */
    public static final String PROFILE_JENKINS = "jenkins";

    /**
     * Combined profile expression; not a literal and therefore skipped by the extractor.
     */
    public static final String PROFILE_COMBINED = PROFILE_CIONE + " & " + PROFILE_AGENT;

    private Constants() {
    }
}
