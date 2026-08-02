package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisRuntimeSource;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelection;

class RemoteImageStackWriterTest {

    private final RemoteImageStackWriter writer = new RemoteImageStackWriter();

    @Test
    void latestUsesTagSyntaxInAMySqlIclStack() {
        String stack = writer.write(selection("mysql", "docker/mysql.yml", List.of("localci", "buildagent", "localvc")), source("latest"));

        assertThat(stack).contains("image: \"ghcr.io/ls1intum/artemis:latest\"")
                .contains("image: \"docker.io/library/mysql:9.7.0\"")
                .contains("/var/run/docker.sock:/var/run/docker.sock", "${FM_DOCKER_GID:-999}")
                .doesNotContain("FM_ARTEMIS_REPO", "extends:");
    }

    @Test
    void exactDigestUsesDigestSyntaxInAPostgreSqlIclStack() {
        String stack = writer.write(selection("postgresql", "docker/postgres.yml", List.of("localci", "buildagent", "localvc")),
                source("sha256:abc123"));

        assertThat(stack).contains("image: \"ghcr.io/ls1intum/artemis@sha256:abc123\"")
                .contains("image: \"docker.io/library/postgres:18.4-alpine\"")
                .contains("jdbc:postgresql://artemis-feature-model-postgresql:5432/Artemis?sslmode=disable")
                .contains("POSTGRES_USER: \"Artemis\"");
    }

    @Test
    void jenkinsStackKeepsTheLimitationAndAddsNoJenkinsService() {
        String stack = writer.write(selection("mysql", "docker/mysql.yml", List.of("jenkins", "localvc")), source("latest"));

        assertThat(stack).contains("WARNING: Jenkins profiles are configured", RuntimeStackWriter.JENKINS_DOCKER_PROFILES)
                .doesNotContain("    jenkins:", "/var/run/docker.sock:/var/run/docker.sock", "group_add:");
    }

    @Test
    void outputIsDeterministic() {
        TechnicalSelection selection = selection("mysql", "docker/mysql.yml", List.of("localci", "buildagent", "localvc"));

        assertThat(writer.write(selection, source("latest"))).isEqualTo(writer.write(selection, source("latest")));
    }

    private TechnicalSelection selection(String databaseId, String composeFile, List<String> profiles) {
        return new TechnicalSelection(profiles, Optional.of(composeFile), Optional.of(databaseId),
                Optional.of(profiles.contains("jenkins") ? "jenkins" : "integrated-code-lifecycle"));
    }

    private ArtemisRuntimeSource source(String imageDigest) {
        return new ArtemisRuntimeSource("commit", "ghcr.io/ls1intum/artemis", imageDigest);
    }
}
