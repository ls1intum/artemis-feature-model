package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisRuntimeSource;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelection;

class RuntimeTemplateWriterTest {

    private final RuntimeTemplateWriter writer = new RuntimeTemplateWriter();

    @Test
    void packageReadmeLeadsWithSupportedEnvironmentsAndTheQuickStart() {
        String readme = readme(iclSelection());

        assertThat(readme).startsWith("# Artemis Feature Model — Local Docker Deployment Package\n");
        assertThat(sectionHeadings(readme)).containsExactly("## Supported environments", "## Quick start", "## Runtime image", "## Package checks");
    }

    @Test
    void packageReadmeExplainsDockerSocketAccessForIntegratedCodeLifecycle() {
        String readme = readme(iclSelection());

        assertThat(readme).contains("### Docker socket access", "/var/run/docker.sock", "Enhanced Container Isolation", "FM_DOCKER_GID")
                .doesNotContain("Jenkins limitation");
    }

    @Test
    void packageReadmeWarnsAboutTheMissingJenkinsServiceBeforeTheSections() {
        String readme = readme(jenkinsSelection());

        assertThat(readme).contains("**Jenkins limitation:**", "jenkins-stack-available").doesNotContain("### Docker socket access");
        assertThat(readme.indexOf("Jenkins limitation")).isLessThan(readme.indexOf("## Supported environments"));
    }

    @Test
    void packageReadmeNamesTheDatabaseComposeFileALocalCheckoutNeeds() {
        assertThat(readme(jenkinsSelection())).contains("`docker/postgres.yml`, and its repository-root `.env`");
    }

    private String readme(TechnicalSelection selection) {
        ArtemisRuntimeSource runtimeSource = new ArtemisRuntimeSource("ghcr.io/ls1intum/artemis", "latest");
        return writer.packageReadme("model", "1.0.0", "profile", "2.0.0", selection, runtimeSource);
    }

    private TechnicalSelection iclSelection() {
        return new TechnicalSelection(List.of("localci", "buildagent", "localvc"), Optional.of("docker/mysql.yml"), Optional.of("mysql"),
                Optional.of("integrated-code-lifecycle"));
    }

    private TechnicalSelection jenkinsSelection() {
        return new TechnicalSelection(List.of("jenkins", "localvc"), Optional.of("docker/postgres.yml"), Optional.of("postgresql"), Optional.of("jenkins"));
    }

    private List<String> sectionHeadings(String readme) {
        return readme.lines().filter(line -> line.startsWith("## ")).toList();
    }
}
