package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelection;
import de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException;

/**
 * Writes the selection-driven local-docker Compose stack.
 *
 * <p>
 * The writer receives an already resolved {@link TechnicalSelection}. It owns the exact Docker profile order and
 * renders the shipped {@code extends} pattern without traversing or interpreting the feature model.
 */
@Component
public class RuntimeStackWriter {

    /** Docker profile order mirrored from {@code dev-local-vc-local-ci.env}, without the inert sharing token. */
    static final String ICL_DOCKER_PROFILES = "artemis,scheduling,localci,localvc,buildagent,core,dev,docker";

    /**
     * Jenkins Docker profile order derived from the shipped IDE configuration. Artemis ships no matching Docker
     * configuration; the local smoke verdict is recorded separately.
     */
    static final String JENKINS_DOCKER_PROFILES = "jenkins,localvc,artemis,scheduling,core,dev,docker";

    private static final Set<String> ICL_TOKENS = Set.of("localci", "buildagent", "localvc");

    private static final Set<String> JENKINS_TOKENS = Set.of("jenkins", "localvc");

    /**
     * Writes an extends-based stack for one complete technical selection.
     *
     * @param selection resolved technical selection.
     * @return deterministic Compose YAML.
     * @throws ArtifactGenerationException if the database or profile-token combination is unsupported.
     */
    public String write(TechnicalSelection selection) {
        DatabasePlan database = databasePlan(selection);
        CiPlan ci = ciPlan(selection);

        StringBuilder yaml = new StringBuilder();
        appendHeader(yaml, database, ci);
        appendArtemisService(yaml, database, ci);
        appendDatabaseService(yaml, database);
        appendNetworkAndVolumes(yaml, database);
        return yaml.toString();
    }

    /**
     * Resolves the database-specific rendering values.
     *
     * @param selection resolved technical selection.
     * @return database rendering plan.
     */
    private DatabasePlan databasePlan(TechnicalSelection selection) {
        String databaseId = selection.databaseId().orElse("");
        String composeFile = selection.databaseComposeFile().orElse("");
        if ("mysql".equals(databaseId) && "docker/mysql.yml".equals(composeFile)) {
            return new DatabasePlan("mysql", RuntimePackageConstants.MYSQL_SERVICE, composeFile,
                    "jdbc:mysql://" + RuntimePackageConstants.MYSQL_SERVICE
                            + ":3306/Artemis?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useUnicode=true"
                            + "&characterEncoding=utf8&useSSL=false&serverTimezone=UTC",
                    null, "/var/lib/mysql", "artemis-feature-model-local-mysqldata");
        }
        if ("postgresql".equals(databaseId) && "docker/postgres.yml".equals(composeFile)) {
            return new DatabasePlan("postgres", RuntimePackageConstants.POSTGRES_SERVICE, composeFile,
                    "jdbc:postgresql://" + RuntimePackageConstants.POSTGRES_SERVICE + ":5432/Artemis?sslmode=disable",
                    "Artemis", "/var/lib/postgresql", "artemis-feature-model-local-postgresdata");
        }
        throw ArtifactGenerationException.unsupportedTechnicalChoice("database", databaseId + ":" + composeFile);
    }

    /**
     * Resolves the CI-specific profile order and Docker access.
     *
     * @param selection resolved technical selection.
     * @return CI rendering plan.
     */
    private CiPlan ciPlan(TechnicalSelection selection) {
        Set<String> tokens = new LinkedHashSet<>(selection.springProfileTokens());
        if (ICL_TOKENS.equals(tokens)) {
            return new CiPlan(ICL_DOCKER_PROFILES, true, false);
        }
        if (JENKINS_TOKENS.equals(tokens)) {
            return new CiPlan(JENKINS_DOCKER_PROFILES, false, true);
        }
        throw ArtifactGenerationException.unsupportedTechnicalProfileTokens(tokens);
    }

    /**
     * Appends provenance and Jenkins caveat comments.
     *
     * @param yaml output buffer.
     * @param database database plan.
     * @param ci CI plan.
     */
    private void appendHeader(StringBuilder yaml, DatabasePlan database, CiPlan ci) {
        yaml.append("# Generated feature-model stack: ").append(database.id()).append(" database.\n");
        yaml.append("# Extends the matching files in the local Artemis checkout through ${FM_ARTEMIS_REPO}.\n");
        if (ci.jenkins()) {
            yaml.append("# WARNING: Jenkins profiles are configured, but this stack intentionally contains no Jenkins service.\n");
            yaml.append("# The profile order has no shipped Docker mirror and remains subject to the recorded V4 smoke verdict.\n");
        }
        yaml.append("services:\n");
    }

    /**
     * Appends the Artemis application service.
     *
     * @param yaml output buffer.
     * @param database database plan.
     * @param ci CI plan.
     */
    private void appendArtemisService(StringBuilder yaml, DatabasePlan database, CiPlan ci) {
        yaml.append("    artemis-app:\n");
        yaml.append("        extends:\n");
        yaml.append("            file: \"${FM_ARTEMIS_REPO}/docker/artemis.yml\"\n");
        yaml.append("            service: artemis-app\n");
        yaml.append("        container_name: artemis-feature-model-local-app\n");
        yaml.append("        ports:\n");
        yaml.append("            - \"8080:8080\"\n");
        yaml.append("            - \"5005:5005\"\n");
        yaml.append("        environment:\n");
        yaml.append("            SPRING_PROFILES_ACTIVE: \"").append(ci.profiles()).append("\"\n");
        yaml.append("            SPRING_DATASOURCE_URL: \"").append(database.datasourceUrl()).append("\"\n");
        if (database.datasourceUsername() != null) {
            yaml.append("            SPRING_DATASOURCE_USERNAME: \"").append(database.datasourceUsername()).append("\"\n");
        }
        if (ci.dockerAccess()) {
            yaml.append("            ").append(RuntimePackageConstants.VERSION_CONTROL_URL_ENV).append(": \"")
                    .append(RuntimePackageConstants.LOCAL_VERSION_CONTROL_URL).append("\"\n");
        }
        yaml.append("        depends_on:\n");
        yaml.append("            ").append(database.service()).append(":\n");
        yaml.append("                condition: service_healthy\n");
        if (ci.dockerAccess()) {
            yaml.append("        group_add:\n");
            yaml.append("            - \"${").append(RuntimePackageConstants.DOCKER_GID_ENV).append(":-999}\"\n");
            yaml.append("        volumes:\n");
            yaml.append("            - /var/run/docker.sock:/var/run/docker.sock\n");
        }
    }

    /**
     * Appends the selected database service.
     *
     * @param yaml output buffer.
     * @param database database plan.
     */
    private void appendDatabaseService(StringBuilder yaml, DatabasePlan database) {
        yaml.append("    ").append(database.service()).append(":\n");
        yaml.append("        extends:\n");
        yaml.append("            file: \"${FM_ARTEMIS_REPO}/").append(database.composeFile()).append("\"\n");
        yaml.append("            service: ").append(database.id()).append("\n");
        yaml.append("        container_name: artemis-feature-model-local-").append(database.id()).append("\n");
        yaml.append("        volumes:\n");
        yaml.append("            - \"").append(database.volume()).append(":").append(database.volumeTarget()).append("\"\n");
    }

    /**
     * Appends package-scoped network and volume definitions.
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

    /** Database-specific rendering data. */
    private record DatabasePlan(String id, String service, String composeFile, String datasourceUrl, String datasourceUsername,
            String volumeTarget, String volume) {
    }

    /** CI-specific rendering data. */
    private record CiPlan(String profiles, boolean dockerAccess, boolean jenkins) {
    }
}
