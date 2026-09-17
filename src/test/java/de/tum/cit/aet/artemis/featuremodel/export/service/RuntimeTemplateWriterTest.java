package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisRuntimeSource;
import de.tum.cit.aet.artemis.featuremodel.export.domain.EnvironmentRequirement;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelection;

class RuntimeTemplateWriterTest {

    private final RuntimeTemplateWriter writer = new RuntimeTemplateWriter();

    @Test
    void packageReadmeLeadsWithSupportedEnvironmentsTheQuickStartAndTheVariables() {
        String readme = readme(iclSelection());

        assertThat(readme).startsWith("# Artemis Feature Model — Local Docker Deployment Package\n");
        assertThat(sectionHeadings(readme)).containsExactly("## Supported environments", "## Quick start", "## Variables",
                "## How the package runs Artemis", "## Package checks");
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

    @Test
    void packageReadmeListsEachEnvironmentVariableOnceByNameWithItsFeatureAndSecretFlag() {
        List<EnvironmentRequirement> requirements = List.of(requirement("ARTEMIS_IRIS_URL", "Iris", false),
                requirement("ARTEMIS_ATHENA_SECRET", "Athena", true), requirement("ARTEMIS_IRIS_URL", "Iris", false));

        String readme = readme(iclSelection(), requirements);

        String table = """
                | Variable | Feature | Secret |
                | --- | --- | --- |
                | `ARTEMIS_ATHENA_SECRET` | Athena | yes |
                | `ARTEMIS_IRIS_URL` | Iris | no |

                """;
        assertThat(readme).contains(table, "`demo-change-me`", "`https://feature-model-demo.invalid`", "bash scripts/prepare-env.sh");
    }

    @Test
    void packageReadmeStatesWhenTheSelectionNeedsNoEnvironmentVariables() {
        String readme = readme(iclSelection(), List.of());

        assertThat(readme).contains("This selection needs no variables").doesNotContain("| Variable | Feature | Secret |");
    }

    @Test
    void packageReadmeListsTheDockerSocketGroupOnlyForIntegratedCodeLifecycle() {
        String iclReadme = readme(iclSelection());
        String jenkinsReadme = readme(jenkinsSelection());

        assertThat(iclReadme).contains("### Script settings", "| `FM_DOCKER_GID` |", "| `FM_ARTEMIS_ENV_FILE` |", "| `FM_ARTEMIS_COMPOSE_FILE` |")
                .doesNotContain("### Jenkins connection");
        assertThat(jenkinsReadme).contains("### Jenkins connection", "| `FM_ARTEMIS_ENV_FILE` |").doesNotContain("| `FM_DOCKER_GID` |");
    }

    @Test
    void packageReadmeDescribesTheLatestTagAsMutableAndADigestAsPinned() {
        String latestReadme = readme(iclSelection(), List.of(), "latest");
        String pinnedReadme = readme(iclSelection(), List.of(), "sha256:abc123");

        assertThat(latestReadme).contains("runs `ghcr.io/ls1intum/artemis:latest` and pulls it on every start", "`latest` is a mutable tag");
        assertThat(pinnedReadme).contains("runs the pinned image `ghcr.io/ls1intum/artemis@sha256:abc123`").doesNotContain("mutable tag");
    }

    private String readme(TechnicalSelection selection) {
        return readme(selection, List.of(requirement("ARTEMIS_ATLAS_CHAT_MODEL", "Atlas", false)));
    }

    private String readme(TechnicalSelection selection, List<EnvironmentRequirement> requirements) {
        return readme(selection, requirements, "latest");
    }

    private String readme(TechnicalSelection selection, List<EnvironmentRequirement> requirements, String imageDigest) {
        ArtemisRuntimeSource runtimeSource = new ArtemisRuntimeSource("ghcr.io/ls1intum/artemis", imageDigest);
        return writer.packageReadme("model", "1.0.0", "profile", "2.0.0", selection, runtimeSource, requirements);
    }

    private EnvironmentRequirement requirement(String name, String featureName, boolean secret) {
        return new EnvironmentRequirement(name, "feature", featureName, null, null, secret, EnvironmentRequirement.SOURCE_RUNTIME_PACKAGE, "purpose");
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
