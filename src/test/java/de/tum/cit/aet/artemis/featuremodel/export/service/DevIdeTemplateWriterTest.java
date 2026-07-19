package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DevIdeTemplateWriterTest {

    private static final String CI_ACTIVE_PROFILES = "artemis,localci,localvc,scheduling,buildagent,core,dev,feature-model,local";

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
    void writesAReadmeCoveringOverlayPlacementRunConfigImportAndSecretPlaceholders() {
        String readme = writer.devIdeReadme("artemis-functional-features", "1.0.0", "default-artemis-profile", CI_ACTIVE_PROFILES,
                List.of("ARTEMIS_IRIS_SECRET_TOKEN"));

        // The overlay is copied under its original name; the feature-model profile loads it, no rename or merge.
        assertThat(readme).contains("src/main/resources/config/application-feature-model.yml").contains("keep the file name").contains("`feature-model` Spring profile")
                .contains("application-local.yml").contains("SPRING_CONFIG_ADDITIONAL_LOCATION").contains(".idea/runConfigurations/")
                .contains(CI_ACTIVE_PROFILES).contains("`ARTEMIS_IRIS_SECRET_TOKEN`").contains("env/.env.example").contains("DEMO");
        assertThat(readme).doesNotContain("env:ARTEMIS");
    }
}
