package de.tum.cit.aet.artemis.featuremodel.extraction.source;

import java.nio.file.Path;
import java.util.List;

/**
 * Version-controlled Artemis source conventions understood by the extractor. These values describe the upstream
 * source tree and symbols; they are deliberately not runtime configuration or manifest-authored policy.
 */
public final class ArtemisSourceConventions {

    private ArtemisSourceConventions() {
    }

    /** Checkout-relative source roots scanned for facts or evidence. */
    public static final class Roots {

        /** Backend production Java sources. */
        public static final String JAVA = "src/main/java";

        /** Backend production resources. */
        public static final String RESOURCES = "src/main/resources";

        /** Spring application configuration resources. */
        public static final String CONFIG = RESOURCES + "/config";

        /** Complete frontend source root. */
        public static final String WEBAPP = "src/main/webapp";

        /** Frontend application sources. */
        public static final String WEBAPP_APP = WEBAPP + "/app";

        /** English frontend translations. */
        public static final String ENGLISH_I18N = WEBAPP + "/i18n/en";

        /** Top-level Docker Compose inputs. */
        public static final String DOCKER = "docker";

        /** Roots eligible for curated evidence relocation checks. */
        public static final List<String> EVIDENCE = List.of(JAVA, RESOURCES, WEBAPP, DOCKER);

        private Roots() {
        }
    }

    /** Known source files whose preferred location may move within one verified fallback root. */
    public static final class Files {

        /** Backend constants class. */
        public static final SourceFileTarget BACKEND_CONSTANTS = target("backend constants",
                Roots.JAVA + "/de/tum/cit/aet/artemis/core/config/Constants.java", Roots.JAVA, "Constants.java");

        /** Backend module configuration helper. */
        public static final SourceFileTarget CONFIG_HELPER = target("configuration helper",
                Roots.JAVA + "/de/tum/cit/aet/artemis/core/config/ArtemisConfigHelper.java", Roots.JAVA, "ArtemisConfigHelper.java");

        /** Backend runtime feature enum. */
        public static final SourceFileTarget BACKEND_FEATURE_ENUM = target("backend feature enum",
                Roots.JAVA + "/de/tum/cit/aet/artemis/core/service/feature/Feature.java", Roots.JAVA, "Feature.java");

        /** Frontend module and profile constants. */
        public static final SourceFileTarget FRONTEND_CONSTANTS = target("frontend constants", Roots.WEBAPP_APP + "/app.constants.ts", Roots.WEBAPP,
                "app.constants.ts");

        /** Frontend runtime feature-toggle enum service. */
        public static final SourceFileTarget FRONTEND_TOGGLE_SERVICE = target("frontend feature-toggle service",
                Roots.WEBAPP_APP + "/foundation/feature-toggle/feature-toggle.service.ts", Roots.WEBAPP, "feature-toggle.service.ts");

        /** Administrator feature display component. */
        public static final SourceFileTarget ADMIN_FEATURE_COMPONENT = target("administrator feature component",
                Roots.WEBAPP_APP + "/admin/features/admin-feature-toggle.component.ts", Roots.WEBAPP, "admin-feature-toggle.component.ts");

        /** English feature text resource. */
        public static final SourceFileTarget FEATURE_I18N = target("English feature translations", Roots.ENGLISH_I18N + "/featureToggles.json",
                Roots.ENGLISH_I18N, "featureToggles.json");

        /** Jenkins Compose file used as profile evidence. */
        public static final String JENKINS_COMPOSE = Roots.DOCKER + "/jenkins.yml";

        /** Preferred application default file with highest precedence. */
        public static final String APPLICATION_CORE = Roots.CONFIG + "/application-core.yml";

        /** General application default file with second precedence. */
        public static final String APPLICATION = Roots.CONFIG + "/application.yml";

        private Files() {
        }

        /**
         * Returns the conventional profile-specific Spring configuration path.
         *
         * @param profileId Spring profile id.
         * @return checkout-relative configuration path.
         */
        public static String profileConfiguration(String profileId) {
            return Roots.CONFIG + "/application-" + profileId + Naming.YAML_SUFFIX;
        }

        private static SourceFileTarget target(String description, String preferredPath, String fallbackRoot, String fileName) {
            return new SourceFileTarget(description, preferredPath, fallbackRoot, fileName);
        }
    }

    /** Stable upstream symbols used to verify targets or join scan facts. */
    public static final class Symbols {

        /** Backend and frontend module constant prefix. */
        public static final String MODULE_FEATURE_PREFIX = "MODULE_FEATURE_";

        /** Backend and frontend profile constant prefix. */
        public static final String PROFILE_CONSTANT_PREFIX = "PROFILE_";

        /** Backend configuration helper type. */
        public static final String CONFIG_HELPER_TYPE = "ArtemisConfigHelper";

        /** Backend enabled-feature enumeration method. */
        public static final String ENABLED_FEATURES_METHOD = "getEnabledFeatures";

        /** Spring condition interface. */
        public static final String CONDITION_INTERFACE = "Condition";

        /** Backend runtime feature enum type. */
        public static final String BACKEND_FEATURE_ENUM = "Feature";

        /** Frontend runtime feature enum type. */
        public static final String FRONTEND_FEATURE_ENUM = "FeatureToggle";

        /** Frontend admin-page documentation key prefix for runtime toggles. */
        public static final String FRONTEND_TOGGLE_REFERENCE_PREFIX = FRONTEND_FEATURE_ENUM + ".";

        /** Opt-in extraction annotation simple name. */
        public static final String ARTEMIS_FEATURE_ANNOTATION = "ArtemisFeature";

        private Symbols() {
        }
    }

    /** Naming rules shared by scanners and candidate assembly. */
    public static final class Naming {

        /** Java source suffix. */
        public static final String JAVA_SUFFIX = ".java";

        /** HTML template suffix. */
        public static final String HTML_SUFFIX = ".html";

        /** YAML source suffix. */
        public static final String YAML_SUFFIX = ".yml";

        /** General configuration property-constant suffix. */
        public static final String PROPERTY_CONSTANT_SUFFIX = "_PROPERTY_NAME";

        /** Enabled configuration property-constant suffix. */
        public static final String ENABLED_PROPERTY_CONSTANT_SUFFIX = "_ENABLED" + PROPERTY_CONSTANT_SUFFIX;

        /** Spring condition class suffix. */
        public static final String CONDITION_CLASS_SUFFIX = "Enabled";

        /** Spring condition source-file suffix. */
        public static final String CONDITION_FILE_SUFFIX = CONDITION_CLASS_SUFFIX + ".java";

        /** Application configuration file prefix. */
        public static final String APPLICATION_FILE_PREFIX = "application";

        private Naming() {
        }
    }

    /**
     * One convention-owned source target with an exact path and a bounded fallback search.
     *
     * @param description human-readable target name used in diagnostics.
     * @param preferredPath checkout-relative exact path.
     * @param fallbackRoot checkout-relative root for name-based fallback.
     * @param fileName exact fallback file name.
     */
    public record SourceFileTarget(String description, String preferredPath, String fallbackRoot, String fileName) {

        /**
         * Validates that every descriptor path stays checkout-relative and within its fallback root.
         *
         * @throws IllegalArgumentException if the descriptor is blank, absolute, escaping, or internally
         *             inconsistent.
         */
        public SourceFileTarget {
            requireRelativePath("preferred path", preferredPath);
            requireRelativePath("fallback root", fallbackRoot);
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("A source target requires a description.");
            }
            if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
                throw new IllegalArgumentException("A source target file name must be one checkout-relative name.");
            }
            String normalizedPreferred = preferredPath.replace('\\', '/');
            String normalizedRoot = fallbackRoot.replace('\\', '/');
            if (!normalizedPreferred.startsWith(normalizedRoot + "/")) {
                throw new IllegalArgumentException("Source target preferred path must stay under its fallback root.");
            }
        }

        private static void requireRelativePath(String label, String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Source target " + label + " must not be blank.");
            }
            Path path = Path.of(value);
            if (path.isAbsolute() || path.normalize().startsWith("..")) {
                throw new IllegalArgumentException("Source target " + label + " must stay within the checkout.");
            }
        }
    }
}
