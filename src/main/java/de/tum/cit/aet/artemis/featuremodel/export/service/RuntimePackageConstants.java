package de.tum.cit.aet.artemis.featuremodel.export.service;

/**
 * Shared string constants for the Phase 6 local runtime deployment package. They keep the generated Compose override,
 * helper scripts, README files, and manifest consistent, so the path the start script mounts and the path the override
 * references never drift apart.
 *
 * <p>
 * This phase implements Layer 1 (local Artemis repository runtime) only. Layer 2 (remote Artemis image runtime) is
 * deferred, so no remote-image image tag, database, or stack constants are defined here yet.
 */
public final class RuntimePackageConstants {

    private RuntimePackageConstants() {
    }

    /** Root directory every package file lives under inside the ZIP. */
    public static final String PACKAGE_ROOT_DIR = "artemis-feature-model-deployment-package/";

    /** Download file name for the generated runtime package ZIP. */
    public static final String PACKAGE_ZIP_NAME = "artemis-feature-model-deployment-package.zip";

    /** Package type recorded in the manifest. */
    public static final String PACKAGE_TYPE = "local-runtime-deployment-package";

    /** Package format version recorded in the manifest. */
    public static final String PACKAGE_VERSION = "1.0.0";

    /** Only generation mode in this phase; placeholder values are allowed but reported. */
    public static final String MODE_DEMO = "DEMO";

    /** Runtime mode identifier for the local Artemis repository layer (Layer 1). */
    public static final String RUNTIME_MODE_LOCAL_REPO = "local-repo";

    /** Path of the generated Spring configuration overlay inside the package. */
    public static final String OVERLAY_PACKAGE_PATH = "config/application-feature-model.yml";

    /** Path of the generated {@code .env.example} inside the package (empty values, from Phase 5). */
    public static final String ENV_EXAMPLE_PACKAGE_PATH = "env/.env.example";

    /** Path of the generated demo env file inside the package. */
    public static final String ENV_DEMO_PACKAGE_PATH = "env/.env.demo";

    /** Path of the runtime env file the scripts create and read (never generated into the package). */
    public static final String ENV_FILE_RELATIVE_PATH = "env/.env";

    /** Container path the overlay is mounted to, and that Spring is told to load as an additional config file. */
    public static final String CONTAINER_OVERLAY_PATH = "/opt/artemis/config/application-feature-model.yml";

    /** Spring Boot environment variable used to load the overlay as an additional configuration file. */
    public static final String SPRING_CONFIG_ENV = "SPRING_CONFIG_ADDITIONAL_LOCATION";

    /** Spring config value pointing at the mounted overlay; {@code optional:} tolerates a missing mount. */
    public static final String SPRING_CONFIG_VALUE = "optional:file:" + CONTAINER_OVERLAY_PATH;

    /** Environment variable the start/stop scripts export with the absolute host path of the overlay. */
    public static final String OVERLAY_HOST_PATH_ENV = "FM_OVERLAY_HOST_PATH";

    /** Environment variable the start/stop scripts export with the absolute host path of {@code env/.env}. */
    public static final String ENV_FILE_ENV = "FM_ENV_FILE";

    /** Optional environment variable to override which Artemis Compose file the local-repo script uses. */
    public static final String ARTEMIS_COMPOSE_ENV = "FM_ARTEMIS_COMPOSE_FILE";

    /**
     * Default Artemis Compose file (relative to the Artemis repository) used by the local-repo script. The CI-capable
     * local-VC/local-CI stack is used so that CI-dependent features (for example Hyperion, which hard-requires a CI
     * trigger bean) can start; a plain database-only stack shuts Artemis down when such a feature is enabled.
     */
    public static final String DEFAULT_ARTEMIS_COMPOSE_FILE = "docker/artemis-dev-local-vc-local-ci-mysql.yml";

    /** Compose project name shared by the local-repo start and stop scripts so stop finds the started stack. */
    public static final String COMPOSE_PROJECT_NAME = "artemis-feature-model-local";

    /** Artemis application service name the local-repo override targets. */
    public static final String ARTEMIS_APP_SERVICE = "artemis-app";

    /** Database service name in the CI-capable stack; also the resolvable host used in the datasource URL. */
    public static final String DB_SERVICE = "mysql";

    /** Local runtime database type recorded in the manifest. */
    public static final String DATABASE_TYPE = "mysql";

    /** Container name for our Artemis app, distinct from the stack default so it never collides with an existing one. */
    public static final String CONTAINER_APP_NAME = "artemis-feature-model-local-app";

    /** Container name for our database, distinct from the stack default to avoid name collisions. */
    public static final String CONTAINER_DB_NAME = "artemis-feature-model-local-mysql";

    /** Named volume for Artemis app data, isolated from any existing Artemis dev volumes. */
    public static final String DATA_VOLUME = "artemis-feature-model-local-data";

    /** Named volume for the database, isolated from any existing Artemis dev volumes. */
    public static final String DB_VOLUME = "artemis-feature-model-local-mysqldata";

    /**
     * Datasource URL pointed at the {@code mysql} service by name (not the stack's default {@code artemis-mysql}
     * container host), so our renamed database container still resolves. Artemis creates the database if absent.
     */
    public static final String DATASOURCE_URL = "jdbc:mysql://mysql:3306/Artemis?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true"
            + "&useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC";

    /** URL Artemis is reachable at after a successful local-repo start. */
    public static final String ARTEMIS_LOCAL_URL = "http://localhost:8080";

    /** Path of the local-repo Compose override inside the package. */
    public static final String LOCAL_REPO_OVERRIDE_PACKAGE_PATH = "deployment/local-repo/docker-compose.override.example.yml";

    /** Selection-driven Compose stack generated for models with technical mappings. */
    public static final String TECHNICAL_STACK_PACKAGE_PATH = "deployment/local-repo/artemis-feature-model-stack.yml";

    /** Environment variable through which the generated stack locates the local Artemis checkout. */
    public static final String ARTEMIS_REPO_ENV = "FM_ARTEMIS_REPO";

    /** Package-scoped MySQL service name. */
    public static final String MYSQL_SERVICE = "artemis-feature-model-mysql";

    /** Package-scoped PostgreSQL service name. */
    public static final String POSTGRES_SERVICE = "artemis-feature-model-postgresql";

    /**
     * Abbreviated Artemis commit the Phase 5 profile keys were verified against (see
     * {@code devdocs/plan/phase-5/parameter-alignment-audit.md}). Recorded so a user running Layer 1 against a
     * different local checkout is warned about a possible key mismatch.
     */
    public static final String VERIFIED_ARTEMIS_COMMIT = "b1e27eeaaa";
}
