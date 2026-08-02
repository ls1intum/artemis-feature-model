package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisRuntimeSource;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelection;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;

/** Writes the self-contained remote-image Compose stack for one resolved technical selection. */
@Component
public class RemoteImageStackWriter {

    private static final Set<String> ICL_TOKENS = Set.of("localci", "buildagent", "localvc");

    private static final Set<String> JENKINS_TOKENS = Set.of("jenkins", "localvc");

    /**
     * Writes deterministic Compose YAML without local-checkout references.
     *
     * @param selection resolved technical selection.
     * @param runtimeSource resolved Artemis runtime provenance.
     * @return self-contained Compose YAML.
     */
    public String write(TechnicalSelection selection, ArtemisRuntimeSource runtimeSource) {
        DatabasePlan database = databasePlan(selection);
        CiPlan ci = ciPlan(selection);
        StringBuilder yaml = new StringBuilder();
        appendHeader(yaml, database, ci);
        appendApplication(yaml, database, ci, imageReference(runtimeSource));
        appendDatabase(yaml, database);
        appendNetworkAndVolumes(yaml, database);
        return yaml.toString();
    }

    /**
     * Renders the configured Artemis image reference.
     *
     * @param runtimeSource resolved Artemis runtime source.
     * @return tag reference for {@code latest}, otherwise a digest reference.
     */
    String imageReference(ArtemisRuntimeSource runtimeSource) {
        if ("latest".equals(runtimeSource.imageDigest())) {
            return runtimeSource.imageRepository() + ":latest";
        }
        return runtimeSource.imageRepository() + "@" + runtimeSource.imageDigest();
    }

    /**
     * Resolves database-specific remote stack values.
     *
     * @param selection resolved technical selection.
     * @return database rendering plan.
     */
    private DatabasePlan databasePlan(TechnicalSelection selection) {
        String databaseId = selection.databaseId().orElse("");
        String composeFile = selection.databaseComposeFile().orElse("");
        if ("mysql".equals(databaseId) && "docker/mysql.yml".equals(composeFile)) {
            String url = "jdbc:mysql://" + RuntimePackageConstants.MYSQL_SERVICE
                    + ":3306/Artemis?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useUnicode=true"
                    + "&characterEncoding=utf8&useSSL=false&serverTimezone=UTC";
            return new DatabasePlan("mysql", RuntimePackageConstants.MYSQL_SERVICE, url, null,
                    "artemis-feature-model-local-mysqldata", "/var/lib/mysql");
        }
        if ("postgresql".equals(databaseId) && "docker/postgres.yml".equals(composeFile)) {
            String url = "jdbc:postgresql://" + RuntimePackageConstants.POSTGRES_SERVICE + ":5432/Artemis?sslmode=disable";
            return new DatabasePlan("postgresql", RuntimePackageConstants.POSTGRES_SERVICE, url, "Artemis",
                    "artemis-feature-model-local-postgresdata", "/var/lib/postgresql");
        }
        throw ArtifactGenerationException.unsupportedTechnicalChoice("database", databaseId + ":" + composeFile);
    }

    /**
     * Resolves CI-specific profiles and Docker access.
     *
     * @param selection resolved technical selection.
     * @return CI rendering plan.
     */
    private CiPlan ciPlan(TechnicalSelection selection) {
        Set<String> tokens = new LinkedHashSet<>(selection.springProfileTokens());
        if (ICL_TOKENS.equals(tokens)) {
            return new CiPlan(RuntimeStackWriter.ICL_DOCKER_PROFILES, true, false);
        }
        if (JENKINS_TOKENS.equals(tokens)) {
            return new CiPlan(RuntimeStackWriter.JENKINS_DOCKER_PROFILES, false, true);
        }
        throw ArtifactGenerationException.unsupportedTechnicalProfileTokens(tokens);
    }

    /**
     * Appends deterministic provenance and limitation comments.
     *
     * @param yaml output buffer.
     * @param database database plan.
     * @param ci CI plan.
     */
    private void appendHeader(StringBuilder yaml, DatabasePlan database, CiPlan ci) {
        yaml.append("# Self-contained remote-image stack: ").append(database.id()).append(" database.\n");
        yaml.append("# No Artemis checkout, Git fetch, or checkout-relative Compose file is used.\n");
        if (ci.jenkins()) {
            yaml.append("# WARNING: Jenkins profiles are configured, but this stack intentionally contains no Jenkins service.\n");
        }
        yaml.append("services:\n");
    }

    /**
     * Appends the directly declared Artemis application service.
     *
     * @param yaml output buffer.
     * @param database database plan.
     * @param ci CI plan.
     * @param imageReference rendered Artemis image reference.
     */
    private void appendApplication(StringBuilder yaml, DatabasePlan database, CiPlan ci, String imageReference) {
        yaml.append("    artemis-app:\n");
        yaml.append("        image: \"").append(imageReference).append("\"\n");
        yaml.append("        container_name: artemis-feature-model-local-app\n");
        yaml.append("        pull_policy: always\n");
        yaml.append("        ports:\n");
        yaml.append("            - \"8080:8080\"\n");
        yaml.append("            - \"5005:5005\"\n");
        yaml.append("        env_file:\n");
        yaml.append("            - \"${FM_ENV_FILE}\"\n");
        yaml.append("        environment:\n");
        yaml.append("            SPRING_PROFILES_ACTIVE: \"").append(ci.profiles()).append("\"\n");
        yaml.append("            SPRING_DATASOURCE_URL: \"").append(database.datasourceUrl()).append("\"\n");
        if (database.datasourceUsername() != null) {
            yaml.append("            SPRING_DATASOURCE_USERNAME: \"").append(database.datasourceUsername()).append("\"\n");
        }
        yaml.append("            ").append(RuntimePackageConstants.SPRING_CONFIG_ENV).append(": \"")
                .append(RuntimePackageConstants.SPRING_CONFIG_VALUE).append("\"\n");
        yaml.append("            ").append(RuntimePackageConstants.VERSION_CONTROL_URL_ENV).append(": \"")
                .append(RuntimePackageConstants.LOCAL_VERSION_CONTROL_URL).append("\"\n");
        yaml.append("        depends_on:\n");
        yaml.append("            ").append(database.service()).append(":\n");
        yaml.append("                condition: service_healthy\n");
        if (ci.dockerAccess()) {
            yaml.append("        group_add:\n");
            yaml.append("            - \"${FM_DOCKER_GID:-999}\"\n");
        }
        yaml.append("        volumes:\n");
        yaml.append("            - artemis-data:/opt/artemis/data\n");
        yaml.append("            - \"${FM_OVERLAY_HOST_PATH}:").append(RuntimePackageConstants.CONTAINER_OVERLAY_PATH).append(":ro\"\n");
        if (ci.dockerAccess()) {
            yaml.append("            - /var/run/docker.sock:/var/run/docker.sock\n");
        }
        yaml.append("        networks:\n");
        yaml.append("            - artemis\n");
    }

    /**
     * Appends the selected package-scoped database service.
     *
     * @param yaml output buffer.
     * @param database database plan.
     */
    private void appendDatabase(StringBuilder yaml, DatabasePlan database) {
        yaml.append("    ").append(database.service()).append(":\n");
        yaml.append("        container_name: artemis-feature-model-local-").append(database.id()).append("\n");
        if ("mysql".equals(database.id())) {
            appendMySql(yaml);
        }
        else {
            appendPostgreSql(yaml);
        }
        yaml.append("        volumes:\n");
        yaml.append("            - \"").append(database.volume()).append(":").append(database.volumeTarget()).append("\"\n");
        yaml.append("        networks:\n");
        yaml.append("            - artemis\n");
    }

    /**
     * Appends the minimal MySQL image, environment, and health settings.
     *
     * @param yaml output buffer.
     */
    private void appendMySql(StringBuilder yaml) {
        yaml.append("        image: \"docker.io/library/mysql:9.7.0\"\n");
        yaml.append("        environment:\n");
        yaml.append("            MYSQL_ALLOW_EMPTY_PASSWORD: \"yes\"\n");
        yaml.append("            MYSQL_DATABASE: \"Artemis\"\n");
        yaml.append("        command: mysqld --lower_case_table_names=1 --tls-version='' --character_set_server=utf8mb4 --collation-server=utf8mb4_unicode_ci --explicit_defaults_for_timestamp --max_connections=100000\n");
        yaml.append("        cap_add:\n");
        yaml.append("            - SYS_NICE\n");
        yaml.append("        healthcheck:\n");
        yaml.append("            test: [\"CMD-SHELL\", \"mysqladmin ping -h 127.0.0.1 -u root --silent\"]\n");
        appendHealthTiming(yaml);
    }

    /**
     * Appends the minimal PostgreSQL image, environment, and health settings.
     *
     * @param yaml output buffer.
     */
    private void appendPostgreSql(StringBuilder yaml) {
        yaml.append("        image: \"docker.io/library/postgres:18.4-alpine\"\n");
        yaml.append("        user: postgres\n");
        yaml.append("        command: [\"postgres\", \"-c\", \"max_connections=10000\"]\n");
        yaml.append("        environment:\n");
        yaml.append("            POSTGRES_HOST_AUTH_METHOD: \"trust\"\n");
        yaml.append("            POSTGRES_USER: \"Artemis\"\n");
        yaml.append("            POSTGRES_DB: \"Artemis\"\n");
        yaml.append("        healthcheck:\n");
        yaml.append("            test: [\"CMD-SHELL\", \"pg_isready -U Artemis -d Artemis\"]\n");
        appendHealthTiming(yaml);
        yaml.append("        shm_size: \"256m\"\n");
    }

    /**
     * Appends database health-check timing shared by both database services.
     *
     * @param yaml output buffer.
     */
    private void appendHealthTiming(StringBuilder yaml) {
        yaml.append("            interval: 5s\n");
        yaml.append("            timeout: 3s\n");
        yaml.append("            retries: 30\n");
        yaml.append("            start_period: 300s\n");
    }

    /**
     * Appends package-scoped network and volume declarations.
     *
     * @param yaml output buffer.
     * @param database database plan.
     */
    private void appendNetworkAndVolumes(StringBuilder yaml, DatabasePlan database) {
        yaml.append("networks:\n");
        yaml.append("    artemis:\n");
        yaml.append("        name: artemis-feature-model-local\n");
        yaml.append("volumes:\n");
        yaml.append("    artemis-data:\n");
        yaml.append("        name: artemis-feature-model-local-data\n");
        yaml.append("    ").append(database.volume()).append(":\n");
        yaml.append("        name: ").append(database.volume()).append("\n");
    }

    private record DatabasePlan(String id, String service, String datasourceUrl, String datasourceUsername, String volume, String volumeTarget) {
    }

    private record CiPlan(String profiles, boolean dockerAccess, boolean jenkins) {
    }
}
