package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DevIdeTemplateWriterTest {

    private static final String CI_ACTIVE_PROFILES = "artemis,localci,localvc,scheduling,buildagent,core,dev,feature-model,feature-model-demo,local";

    private final DevIdeTemplateWriter writer = new DevIdeTemplateWriter();

    @Test
    void writesADeterministicSpringBootRunConfiguration() {
        String first = writer.runConfigurationXml(CI_ACTIVE_PROFILES);
        String second = writer.runConfigurationXml(CI_ACTIVE_PROFILES);

        assertThat(first).isEqualTo(second);
        assertThat(first).contains("type=\"SpringBootApplicationConfigurationType\"").contains("factoryName=\"Spring Boot\"")
                .contains("<option name=\"SPRING_BOOT_MAIN_CLASS\" value=\"de.tum.cit.aet.artemis.ArtemisApp\" />").contains("<module name=\"Artemis.main\" />")
                .contains("name=\"Artemis Server (Feature Model Selection)\"");
    }

    @Test
    void writesTheDerivedActiveProfilesIntoTheRunConfiguration() {
        String xml = writer.runConfigurationXml(CI_ACTIVE_PROFILES);

        assertThat(xml).contains("<option name=\"ACTIVE_PROFILES\" value=\"" + CI_ACTIVE_PROFILES + "\" />");
    }

    @Test
    void neverWritesSecretValuesIntoTheRunConfiguration() {
        String xml = writer.runConfigurationXml(CI_ACTIVE_PROFILES);

        // The run configuration carries no environment values at all: secrets stay ${VARIABLE} placeholders in the
        // overlay and are supplied through the developer's environment.
        assertThat(xml).doesNotContain("env:").doesNotContain("${ARTEMIS").doesNotContain("secret").doesNotContain("SECRET");
    }

    @Test
    void writesDeterministicDemoDefaultsForEveryRequiredPlaceholder() {
        List<String> requiredEnvVars = List.of("ARTEMIS_ATHENA_SECRET", "ARTEMIS_IRIS_SECRET_TOKEN");

        String first = writer.demoEnvDefaultsYaml(requiredEnvVars);
        String second = writer.demoEnvDefaultsYaml(requiredEnvVars);

        assertThat(first).isEqualTo(second);
        assertThat(first).contains("ARTEMIS_ATHENA_SECRET: demo-change-me").contains("ARTEMIS_IRIS_SECRET_TOKEN: demo-change-me").contains("DEMO ONLY")
                .contains("feature-model-demo");
        assertThat(first).doesNotContain("env:");
    }

    @Test
    void writesAReadmeCoveringOverlayPlacementRunConfigImportAndSecretPlaceholders() {
        String readme = writer.devIdeReadme("artemis-functional-features", "1.0.0", "default-artemis-profile", CI_ACTIVE_PROFILES,
                List.of("ARTEMIS_IRIS_SECRET_TOKEN"));

        // The config files are copied under their original names; the feature-model profiles load them directly.
        assertThat(readme).contains("src/main/resources/config/").contains("keep the file names").contains("`feature-model` Spring profile")
                .contains("application-feature-model-demo.yml").contains("application-local.yml").contains("SPRING_CONFIG_ADDITIONAL_LOCATION")
                .contains(".idea/runConfigurations/").contains(CI_ACTIVE_PROFILES).contains("`ARTEMIS_IRIS_SECRET_TOKEN`").contains("env/.env.example")
                .contains("DEMO");
        assertThat(readme).doesNotContain("env:ARTEMIS");
    }
}
