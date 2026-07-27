package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Writes the dev-ide mode files: the IntelliJ IDEA run configuration XML and the developer README.
 *
 * <p>
 * The run configuration mirrors the structure of the run configurations the Artemis repository ships under
 * {@code .idea/runConfigurations/} ({@code SpringBootApplicationConfigurationType}, main class
 * {@code de.tum.cit.aet.artemis.ArtemisApp}, module {@code Artemis.main}), with the {@code ACTIVE_PROFILES} value
 * derived from the feature selection. Output is deterministic: fixed attribute order, no timestamps, and no secret
 * values — secrets stay {@code ${VARIABLE}} placeholders in the overlay and are supplied through the developer's
 * environment.
 */
@Component
public class DevIdeTemplateWriter {

    /** IntelliJ display name of the generated run configuration. */
    static final String RUN_CONFIGURATION_NAME = "Artemis Server (Feature Model Selection)";

    /** Artemis application main class, as in the shipped Artemis run configurations. */
    static final String ARTEMIS_MAIN_CLASS = "de.tum.cit.aet.artemis.ArtemisApp";

    /** IntelliJ module of the Artemis main source set, as in the shipped Artemis run configurations. */
    static final String ARTEMIS_MODULE_NAME = "Artemis.main";

    /**
     * VM parameters mirrored from the shipped Artemis dev run configuration ({@code Artemis_Server__Dev__BuildAgent_LocalCI_.xml}):
     * the {@code --add-exports}/{@code --add-opens} flags Artemis needs at runtime on current JDKs.
     */
    private static final String VM_PARAMETERS = "-XX:+ShowCodeDetailsInExceptionMessages -Duser.country=US -Duser.language=en -Xmx4g "
            + "-XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8 --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED "
            + "--add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED "
            + "--add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED "
            + "--add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED "
            + "--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.management/sun.management=ALL-UNNAMED "
            + "--add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED";

    /**
     * Builds the IntelliJ run configuration XML with the derived active profiles. The inserted values are internal
     * constants and a comma-separated profile list, so no XML escaping is required.
     *
     * @param activeProfiles comma-separated Spring profiles derived from the selection.
     * @return deterministic run configuration XML text.
     */
    public String runConfigurationXml(String activeProfiles) {
        return """
                <component name="ProjectRunConfigurationManager">
                  <configuration default="false" name="%s" type="SpringBootApplicationConfigurationType" factoryName="Spring Boot">
                    <option name="ACTIVE_PROFILES" value="%s" />
                    <option name="ALTERNATIVE_JRE_PATH" value="25" />
                    <option name="ALTERNATIVE_JRE_PATH_ENABLED" value="true" />
                    <module name="%s" />
                    <option name="SHORTEN_COMMAND_LINE" value="ARGS_FILE" />
                    <option name="SPRING_BOOT_MAIN_CLASS" value="%s" />
                    <option name="VM_PARAMETERS" value="%s" />
                    <method v="2">
                      <option name="Gradle.BeforeRunTask" enabled="false" tasks="build" externalProjectPath="$PROJECT_DIR$" vmOptions="" scriptParameters="-x webapp -x compileTest -x test -x jacocoTestCoverageVerification -x spotlessCheck -x checkstyleMain -x checkstyleTest" />
                    </method>
                  </configuration>
                </component>
                """.formatted(RUN_CONFIGURATION_NAME, activeProfiles, ARTEMIS_MODULE_NAME, ARTEMIS_MAIN_CLASS, VM_PARAMETERS);
    }

    /**
     * Builds the demo defaults file for the overlay's {@code ${VARIABLE}} placeholders — the dev-ide counterpart of
     * the local-docker package's {@code env/.env.demo}. Each required environment variable becomes a top-level
     * property with a dummy value, loaded through the {@code feature-model-demo} profile, so a DEMO run resolves all
     * placeholders without manual environment setup. Real environment variables rank above config files in Spring
     * Boot's property precedence and override these dummies; the values are never real secrets.
     *
     * @param requiredEnvVars environment variable names the overlay references.
     * @return deterministic demo defaults YAML text.
     */
    public String demoEnvDefaultsYaml(List<String> requiredEnvVars) {
        StringBuilder builder = new StringBuilder();
        builder.append("# DEMO ONLY — dummy defaults for the ${VARIABLE} placeholders in application-feature-model.yml.\n");
        builder.append("# Loaded via the feature-model-demo profile so a demo run starts without manual environment setup.\n");
        builder.append("# Real environment variables override these values. UNSAFE for production; never put real secrets here.\n");
        for (String name : requiredEnvVars) {
            builder.append(name).append(": demo-change-me\n");
        }
        return builder.toString();
    }

    /**
     * Builds the dev-ide developer README explaining how to apply the overlay, import the run configuration, and
     * provide secret values.
     *
     * @param modelId active feature model id.
     * @param modelVersion active feature model version.
     * @param profileId active deployment profile id.
     * @param activeProfiles comma-separated Spring profiles derived from the selection.
     * @param requiredEnvVars environment variable names the overlay references.
     * @return README markdown text.
     */
    public String devIdeReadme(String modelId, String modelVersion, String profileId, String activeProfiles, List<String> requiredEnvVars) {
        String envVarList = requiredEnvVars.isEmpty() ? "- (none — the overlay references no environment variables)"
                : String.join("\n", requiredEnvVars.stream().map(name -> "- `" + name + "`").toList());
        return """
                # Artemis Feature Model — IDE Development Setup (dev-ide)

                Generated from feature model `%s` version `%s` and deployment context `%s` in DEMO mode.

                This package configures a local **IntelliJ IDEA** development run of Artemis for your selected feature
                set. It contains no runtime and deploys nothing: you run Artemis from your own local Artemis checkout.

                ## 1. Apply the configuration overlay

                Copy both files from this package's `config/` directory into your Artemis checkout's
                `src/main/resources/config/` — **keep the file names**:

                - `application-feature-model.yml` — the generated configuration overlay, loaded through the
                  `feature-model` Spring profile the run configuration activates; no renaming or merging is needed.
                - `application-feature-model-demo.yml` — DEMO dummy defaults for the `${VARIABLE}` placeholders the
                  overlay references, loaded through the `feature-model-demo` profile, so the run starts without
                  manual environment setup (the dev-ide counterpart of the local runtime package's `env/.env.demo`).

                Your own `src/main/resources/config/application-local.yml` (if you maintain one) is loaded after the
                overlay and keeps the final say for machine-specific settings.

                Do not commit the copied files: Artemis' `.gitignore` covers only `application-local*.yml`. Consider
                adding both file names to `.git/info/exclude` in your checkout.

                Alternatively, leave the overlay where it is and point Spring Boot at it by adding the environment
                variable `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/absolute/path/to/config/application-feature-model.yml`
                to the run configuration.

                ## 2. Import the run configuration

                Copy `intellij/runConfigurations/Artemis_Server__Feature_Model_Selection_.xml` into the
                `.idea/runConfigurations/` directory of your Artemis checkout and restart IntelliJ IDEA (or reload the
                project). The configuration appears as "Artemis Server (Feature Model Selection)".

                Its active Spring profiles are derived from your selection:

                ```text
                %s
                ```

                If your selection includes CI-dependent features (Programming, Hyperion), the `localci`, `localvc`,
                and `buildagent` profiles are included, because those features require a CI trigger bean at runtime.
                The profile order mirrors the run configurations Artemis ships and is semantic (`buildagent` must
                stay before `core`); do not reorder it.

                ## 3. Provide secret values

                The overlay never contains plaintext secrets; it references them as `${VARIABLE}` placeholders. For a
                DEMO run nothing is required: `application-feature-model-demo.yml` supplies dummy defaults for every
                placeholder. To use real service values, set the variables listed in `env/.env.example` in the run
                configuration's environment (Run → Edit Configurations → Environment variables) — environment
                variables rank above config files in Spring Boot's property precedence and override the demo defaults:

                %s

                ## Notes

                - This is a DEMO artifact: placeholder values (for example `*.example.com` URLs) must be replaced with
                  real service values; see `metadata/generation-report.json`.
                - `metadata/static-config-validation.json` records the static check of every overlay key against the
                  Artemis config key catalog.
                """.formatted(modelId, modelVersion, profileId, activeProfiles, envVarList);
    }
}
