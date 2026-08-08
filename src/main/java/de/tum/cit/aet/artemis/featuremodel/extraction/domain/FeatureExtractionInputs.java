package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Every filesystem input of the extraction commands, resolved once at the command boundary. The Artemis checkout is
 * the only machine-specific input: it is never committed anywhere in this repository and is resolved from the
 * {@code --artemis-path} option, which the build fills from the Gradle {@code artemisPath} property, and otherwise
 * from the {@code ARTEMIS_PATH} environment variable. All other inputs are repository-relative and passed explicitly
 * by the build.
 *
 * @param artemisCheckout local Artemis checkout to scan, or null when the command does not read Artemis.
 * @param manifestFile scope manifest that pins the source commit and decides membership.
 * @param authoredWorkflowFile authored lean guided workflow.
 * @param deploymentProfileFile bundled deployment profile used for the capability cross-checks.
 * @param outputRoot root directory of all extraction runs.
 */
public record FeatureExtractionInputs(Path artemisCheckout, Path manifestFile, Path authoredWorkflowFile, Path deploymentProfileFile, Path outputRoot) {

    /** Option carrying the local Artemis checkout path. */
    public static final String OPTION_ARTEMIS_PATH = "artemis-path";

    /** Option carrying the scope manifest path. */
    public static final String OPTION_MANIFEST = "manifest";

    /** Option carrying the authored guided workflow path. */
    public static final String OPTION_AUTHORED_WORKFLOW = "authored-workflow";

    /** Option carrying the deployment profile path. */
    public static final String OPTION_DEPLOYMENT_PROFILE = "deployment-profile";

    /** Option carrying the extraction output root. */
    public static final String OPTION_OUTPUT_ROOT = "output-root";

    /** Environment variable consulted when no Artemis checkout option is given. */
    public static final String ARTEMIS_PATH_ENVIRONMENT_VARIABLE = "ARTEMIS_PATH";

    /**
     * Resolves the inputs of one command invocation.
     *
     * @param options parsed command options.
     * @param environment environment variable lookup.
     * @return resolved inputs; the Artemis checkout is null when neither option nor environment variable is set.
     * @throws IllegalArgumentException if a required repository-relative input is missing.
     */
    public static FeatureExtractionInputs resolve(Map<String, String> options, UnaryOperator<String> environment) {
        return new FeatureExtractionInputs(resolveArtemisCheckout(options, environment), requiredPath(options, OPTION_MANIFEST),
                requiredPath(options, OPTION_AUTHORED_WORKFLOW), requiredPath(options, OPTION_DEPLOYMENT_PROFILE), requiredPath(options, OPTION_OUTPUT_ROOT));
    }

    /**
     * Returns the Artemis checkout of a command that reads Artemis sources.
     *
     * @return local Artemis checkout path.
     * @throws IllegalStateException if no checkout is configured, naming both supported ways to configure one.
     */
    public Path requireArtemisCheckout() {
        if (artemisCheckout == null) {
            throw new IllegalStateException("No local Artemis checkout is configured. Pass -PartemisPath=<checkout> to the Gradle task, set it in your user-level "
                    + "gradle.properties, or export " + ARTEMIS_PATH_ENVIRONMENT_VARIABLE + "=<checkout>.");
        }
        return artemisCheckout;
    }

    /**
     * Applies the documented Artemis checkout precedence: explicit option first, environment variable second.
     *
     * @param options parsed command options.
     * @param environment environment variable lookup.
     * @return resolved checkout path, or null when neither source configures one.
     */
    private static Path resolveArtemisCheckout(Map<String, String> options, UnaryOperator<String> environment) {
        String configured = options.get(OPTION_ARTEMIS_PATH);
        if (configured == null || configured.isBlank()) {
            configured = environment.apply(ARTEMIS_PATH_ENVIRONMENT_VARIABLE);
        }
        return configured == null || configured.isBlank() ? null : Path.of(configured);
    }

    /**
     * Reads a required path option.
     *
     * @param options parsed command options.
     * @param option option name without the leading dashes.
     * @return option value as a path.
     * @throws IllegalArgumentException if the option is missing or blank.
     */
    private static Path requiredPath(Map<String, String> options, String option) {
        String value = options.get(option);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option --" + option + ".");
        }
        return Path.of(value);
    }
}
