package de.tum.cit.aet.artemis.featuremodel.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.featuremodel.export.domain.EnvironmentRequirement;

class EnvExampleWriterTest {

    private final EnvExampleWriter writer = new EnvExampleWriter();

    @Test
    void ordersMappingRequirementsByFeatureAndVariableBeforePackageOnlyRequirements() {
        String env = writer.write(List.of(packageOnly("ZZ_PACKAGE_VALUE", false), mapping("iris", "Iris (Pyris)", "artemis.iris.url", "url", false),
                mapping("athena", "Athena", "artemis.athena.secret", "string", true)));

        int athenaIndex = env.indexOf("ARTEMIS_ATHENA_SECRET=");
        int irisIndex = env.indexOf("ARTEMIS_IRIS_URL=");
        int packageIndex = env.indexOf("ZZ_PACKAGE_VALUE=");
        assertThat(athenaIndex).isLessThan(irisIndex);
        assertThat(irisIndex).isLessThan(packageIndex);
    }

    @Test
    void writesCommentedEmptyAssignmentsWithConfigKeyAndType() {
        String env = writer.write(List.of(mapping("iris", "Iris (Pyris)", "artemis.iris.url", "url", false)));

        assertThat(env).isEqualTo("""
                # Iris (Pyris)
                # Config key: artemis.iris.url
                # Type: url
                ARTEMIS_IRIS_URL=
                """);
    }

    @Test
    void marksSecretRequirementsInsteadOfTypingThem() {
        String env = writer.write(List.of(mapping("iris", "Iris (Pyris)", "artemis.iris.secret-token", "string", true)));

        assertThat(env).contains("# SECRET — obtain from the deployment secret store").doesNotContain("# Type:");
        assertThat(env).contains("ARTEMIS_IRIS_SECRET_TOKEN=");
    }

    @Test
    void describesPackageOnlyRequirementsByPurposeAndSource() {
        String env = writer.write(List.of(packageOnly("ZZ_PACKAGE_VALUE", true)));

        assertThat(env).contains("# Required by the runtime package.").contains("# Provided by: runtime-package").contains("ZZ_PACKAGE_VALUE=");
    }

    @Test
    void deduplicatesRequirementsWithTheSameVariableName() {
        String env = writer.write(List.of(mapping("iris", "Iris (Pyris)", "artemis.iris.url", "url", false),
                mapping("iris", "Iris (Pyris)", "artemis.iris.url", "url", false)));

        assertThat(env.lines().filter(line -> line.equals("ARTEMIS_IRIS_URL=")).count()).isEqualTo(1);
    }

    @Test
    void returnsEmptyStringForNoRequirements() {
        assertThat(writer.write(List.of())).isEmpty();
    }

    private EnvironmentRequirement mapping(String featureId, String featureName, String configKey, String catalogType, boolean secret) {
        String name = configKey.toUpperCase().replaceAll("[^A-Z0-9]+", "_");
        return new EnvironmentRequirement(name, featureId, featureName, configKey, catalogType, secret, EnvironmentRequirement.SOURCE_ARTIFACT_MAPPING,
                "Value for configuration key '" + configKey + "' required by " + featureName + ".");
    }

    private EnvironmentRequirement packageOnly(String name, boolean secret) {
        return new EnvironmentRequirement(name, "jenkins", "Jenkins", null, null, secret, EnvironmentRequirement.SOURCE_RUNTIME_PACKAGE,
                "Required by the runtime package.");
    }
}
