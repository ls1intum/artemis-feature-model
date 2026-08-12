package de.tum.cit.aet.artemis.featuremodel.extraction.domain;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Every filesystem input of the extraction commands, resolved once at the command boundary. The Artemis checkout is
 * the only machine-specific input: it is never committed anywhere in this repository and is resolved from the
 * {@code --artemis-path} option, which the build fills from the Gradle {@code artemisPath} property, and otherwise
 * from the {@code ARTEMIS_PATH} environment variable. All other inputs are repository-relative and passed explicitly
 * by the build.
 *
 * @param artemisCheckout local Artemis checkout supplying the derived source revision, or null when unconfigured.
 * @param manifestFile scope manifest that decides membership.
 * @param authoredWorkflowFile authored lean guided workflow.
 * @param deploymentProfileFile bundled deployment profile used for the capability cross-checks.
 * @param outputRoot root directory of all extraction runs.
 * @param expectedArtemisSha externally supplied immutable revision the checkout must be at — a CI validation pin or a
 *            dispatch input — or null when the derived revision stands on its own.
 */
public record FeatureExtractionInputs(Path artemisCheckout, Path manifestFile, Path authoredWorkflowFile, Path deploymentProfileFile, Path outputRoot,
        String expectedArtemisSha) {

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

    /** Option carrying the externally expected Artemis revision. */
    public static final String OPTION_EXPECTED_ARTEMIS_SHA = "expected-artemis-sha";

    /** Environment variable consulted when no Artemis checkout option is given. */
    public static final String ARTEMIS_PATH_ENVIRONMENT_VARIABLE = "ARTEMIS_PATH";

    /** An expectation must be one immutable commit: branch names, tags, and abbreviated hashes are rejected. */
    private static final Pattern FULL_COMMIT_SHA = Pattern.compile("[0-9a-f]{40}");

    /**
     * Validates that a present expectation is one immutable commit, so a mutable ref can never become a run identity.
     */
    public FeatureExtractionInputs {
        if (expectedArtemisSha != null && !FULL_COMMIT_SHA.matcher(expectedArtemisSha).matches()) {
            throw new IllegalArgumentException("--" + OPTION_EXPECTED_ARTEMIS_SHA + " must be a full 40-character lowercase Git commit hash, but was '"
                    + expectedArtemisSha + "'. Branch names, tags, and abbreviated hashes select a moving source and are not accepted.");
        }
    }

    /**
     * Creates inputs without an expected revision, for callers whose derived revision stands on its own.
     *
     * @param artemisCheckout local Artemis checkout, or null when unconfigured.
     * @param manifestFile scope manifest.
     * @param authoredWorkflowFile authored lean guided workflow.
     * @param deploymentProfileFile bundled deployment profile.
     * @param outputRoot root directory of all extraction runs.
     */
    public FeatureExtractionInputs(Path artemisCheckout, Path manifestFile, Path authoredWorkflowFile, Path deploymentProfileFile, Path outputRoot) {
        this(artemisCheckout, manifestFile, authoredWorkflowFile, deploymentProfileFile, outputRoot, null);
    }

    /**
     * Resolves the inputs of one command invocation.
     *
     * @param options parsed command options.
     * @param environment environment variable lookup.
     * @return resolved inputs; the Artemis checkout is null when neither option nor environment variable is set.
     * @throws IllegalArgumentException if a required repository-relative input is missing or the expected revision is
     *             not one immutable commit.
     */
    public static FeatureExtractionInputs resolve(Map<String, String> options, UnaryOperator<String> environment) {
        return new FeatureExtractionInputs(resolveArtemisCheckout(options, environment), requiredPath(options, OPTION_MANIFEST),
                requiredPath(options, OPTION_AUTHORED_WORKFLOW), requiredPath(options, OPTION_DEPLOYMENT_PROFILE), requiredPath(options, OPTION_OUTPUT_ROOT),
                optionalValue(options, OPTION_EXPECTED_ARTEMIS_SHA));
    }

    /**
     * Returns the Artemis checkout of a command that derives the source revision or reads Artemis sources.
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

    /**
     * Reads an optional textual option.
     *
     * @param options parsed command options.
     * @param option option name without the leading dashes.
     * @return option value, or null when absent or blank.
     */
    private static String optionalValue(Map<String, String> options, String option) {
        String value = options.get(option);
        return value == null || value.isBlank() ? null : value;
    }
}
