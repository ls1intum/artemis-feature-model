package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** Covers the documented input resolution: option before environment, and no committed machine-specific checkout path. */
class FeatureExtractionInputsTest {

    private static final String PROPERTY_CHECKOUT = "/property/artemis";

    private static final String ENVIRONMENT_CHECKOUT = "/environment/artemis";

    @Test
    void prefersTheExplicitOptionOverTheEnvironmentVariable() {
        Map<String, String> options = requiredOptions();
        options.put(FeatureExtractionInputs.OPTION_ARTEMIS_PATH, PROPERTY_CHECKOUT);

        FeatureExtractionInputs inputs = FeatureExtractionInputs.resolve(options, environmentWithCheckout());

        assertThat(inputs.requireArtemisCheckout()).isEqualTo(Path.of(PROPERTY_CHECKOUT));
    }

    @Test
    void fallsBackToTheEnvironmentVariable() {
        FeatureExtractionInputs inputs = FeatureExtractionInputs.resolve(requiredOptions(), environmentWithCheckout());

        assertThat(inputs.requireArtemisCheckout()).isEqualTo(Path.of(ENVIRONMENT_CHECKOUT));
    }

    @Test
    void failsWithAnActionableMessageWhenNoCheckoutIsConfigured() {
        FeatureExtractionInputs inputs = FeatureExtractionInputs.resolve(requiredOptions(), variable -> null);

        assertThat(inputs.artemisCheckout()).isNull();
        assertThatThrownBy(inputs::requireArtemisCheckout).isInstanceOf(IllegalStateException.class).hasMessageContaining("-PartemisPath")
                .hasMessageContaining(FeatureExtractionInputs.ARTEMIS_PATH_ENVIRONMENT_VARIABLE);
    }

    @Test
    void rejectsAMissingRepositoryRelativeInput() {
        Map<String, String> options = requiredOptions();
        options.remove(FeatureExtractionInputs.OPTION_MANIFEST);

        assertThatThrownBy(() -> FeatureExtractionInputs.resolve(options, variable -> null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--" + FeatureExtractionInputs.OPTION_MANIFEST);
    }

    @Test
    void noBuildFileOrExtractionSourceCarriesAMachineSpecificCheckoutPath() throws Exception {
        List<Path> inspectedFiles = Stream
                .concat(Stream.of(Path.of("build.gradle"), Path.of("gradle.properties")), listJavaSources(Path.of("src/main/java")).stream())
                .filter(Files::isRegularFile).toList();

        assertThat(inspectedFiles).isNotEmpty();
        for (Path file : inspectedFiles) {
            assertThat(Files.readString(file)).as("%s must not contain a developer home directory path", file).doesNotContain("/Users/");
        }
    }

    /**
     * Lists all Java sources below a directory.
     *
     * @param directory source directory.
     * @return Java source files.
     * @throws Exception if the directory cannot be traversed.
     */
    private List<Path> listJavaSources(Path directory) throws Exception {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    /**
     * Creates the repository-relative options every command receives from the build.
     *
     * @return mutable required option map.
     */
    private Map<String, String> requiredOptions() {
        Map<String, String> options = new HashMap<>();
        options.put(FeatureExtractionInputs.OPTION_MANIFEST, "manifest.yml");
        options.put(FeatureExtractionInputs.OPTION_AUTHORED_WORKFLOW, "guided-workflow.json");
        options.put(FeatureExtractionInputs.OPTION_DEPLOYMENT_PROFILE, "profile.json");
        options.put(FeatureExtractionInputs.OPTION_CURATED_MODEL, "feature-model.json");
        options.put(FeatureExtractionInputs.OPTION_BOOTSTRAP_CATALOG, "catalog.json");
        options.put(FeatureExtractionInputs.OPTION_OUTPUT_ROOT, "build/feature-extraction");
        return options;
    }

    /**
     * Creates an environment lookup that configures an Artemis checkout.
     *
     * @return environment lookup with the checkout variable set.
     */
    private UnaryOperator<String> environmentWithCheckout() {
        return variable -> FeatureExtractionInputs.ARTEMIS_PATH_ENVIRONMENT_VARIABLE.equals(variable) ? ENVIRONMENT_CHECKOUT : null;
    }
}
