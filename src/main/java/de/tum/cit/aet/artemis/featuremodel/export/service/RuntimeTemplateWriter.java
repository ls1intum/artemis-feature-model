package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.export.domain.EnvironmentRequirement;
import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelection;
import de.tum.cit.aet.artemis.featuremodel.export.domain.ArtemisRuntimeSource;

/**
 * Writes the static and near-static text files that turn the generated configuration artifacts into a local runtime
 * deployment package: the package README, the demo/README env files, and the Layer 1 (local Artemis repository)
 * Compose override and its README.
 *
 * <p>
 * All content is deterministic for the same input. Shared paths and environment variable names are the literal values
 * of the constants in {@link RuntimePackageConstants}; a drift-guard test keeps these files and the helper scripts in
 * sync with those constants. The local Docker package supports both a supplied Artemis checkout and a self-contained
 * remote-image stack.
 */
@Component
public class RuntimeTemplateWriter {

    /**
     * Builds the package README for the two local-docker runtime paths. The sections follow the order in which a user
     * needs them: supported environments, the quick start, and the variables come first, background and checks
     * afterwards.
     *
     * @param modelId active feature model id.
     * @param modelVersion active feature model version.
     * @param profileId active deployment profile id.
     * @param profileVersion active deployment profile version.
     * @param selection resolved technical selection.
     * @param runtimeSource resolved Artemis runtime image.
     * @param environmentRequirements environment requirements of the package.
     * @return package README.
     */
    public String packageReadme(String modelId, String modelVersion, String profileId, String profileVersion,
            TechnicalSelection selection, ArtemisRuntimeSource runtimeSource, List<EnvironmentRequirement> environmentRequirements) {
        String database = selection.databaseId().orElse("mysql");
        String databaseComposeFile = selection.databaseComposeFile().orElse("docker/mysql.yml");
        String ciProvider = selection.ciProviderId().orElse("integrated-code-lifecycle");
        boolean jenkins = "jenkins".equals(ciProvider);
        List<String> sections = List.of(
                readmeIntroduction(modelId, modelVersion, profileId, profileVersion, database, ciProvider, jenkins),
                readmeSupportedEnvironments(databaseComposeFile, jenkins),
                readmeQuickStart(),
                readmeVariables(environmentRequirements, jenkins),
                readmeRuntime(database, databaseComposeFile, runtimeSource),
                readmePackageChecks());
        return String.join("\n", sections);
    }

    /**
     * Builds the README title and a summary of what the package runs, including the Jenkins limitation.
     *
     * @param modelId active feature model id.
     * @param modelVersion active feature model version.
     * @param profileId active deployment profile id.
     * @param profileVersion active deployment profile version.
     * @param database selected database id.
     * @param ciProvider selected CI provider id.
     * @param jenkins whether Jenkins is the selected CI provider.
     * @return introduction markdown.
     */
    private String readmeIntroduction(String modelId, String modelVersion, String profileId, String profileVersion, String database, String ciProvider,
            boolean jenkins) {
        String jenkinsWarning = jenkins ? """

                > **Jenkins limitation:** this package configures the Jenkins profiles but includes no Jenkins service, so
                > the `jenkins-stack-available` check in `metadata/runtime-checks.json` fails on purpose. Creating
                > programming exercises and running builds need a separately managed Jenkins instance.
                """ : "";
        return """
                # Artemis Feature Model — Local Docker Deployment Package

                Generated from feature model `%s` version `%s` and deployment context `%s` version `%s` in DEMO mode.

                This package runs Artemis locally with your feature selection, the `%s` database, and the `%s` CI
                provider. Use it to check that Artemis starts with the generated configuration. It is not a production
                deployment and never contains plaintext secrets.
                """.formatted(modelId, modelVersion, profileId, profileVersion, database, ciProvider) + jenkinsWarning;
    }

    /**
     * Builds the supported host environments, their prerequisites, and the Docker socket note of Integrated Code
     * Lifecycle.
     *
     * @param databaseComposeFile database Compose file that a local checkout must provide.
     * @param jenkins whether Jenkins is the selected CI provider; the Jenkins stacks mount no Docker socket.
     * @return supported environments markdown.
     */
    private String readmeSupportedEnvironments(String databaseComposeFile, boolean jenkins) {
        String dockerSocket = jenkins ? "" : """

                ### Docker socket access

                Integrated Code Lifecycle runs builds as containers on your Docker daemon, so the Artemis container
                mounts `/var/run/docker.sock`. The mount gives Artemis broad control over Docker; use this package only
                for local development and validation. Docker Desktop Enhanced Container Isolation blocks the mount
                unless you allow it for the Artemis image.

                If builds report Docker socket permission errors, set `FM_DOCKER_GID` as described in
                [Script settings](#script-settings).
                """;
        return """
                ## Supported environments

                | Host | Supported setup |
                | --- | --- |
                | Linux | Docker Engine |
                | macOS | Docker Desktop |
                | Windows | Docker Desktop with WSL integration and Linux containers, running every command inside a WSL2 distribution |

                Native PowerShell, Command Prompt, Git Bash, and Windows containers are not supported. On Windows,
                keeping the package and any Artemis checkout in the WSL file system is recommended.

                Every environment also needs:

                - Docker Compose v2 or later, so that `docker compose version` succeeds.
                - Free host ports `8080` for Artemis and `5005` for remote debugging.
                - Network access to pull Docker images.
                - Enough disk space for the Docker images and the package volumes.

                To run your own Artemis checkout instead of the published image, the checkout needs `docker/artemis.yml`,
                `%s`, and its repository-root `.env`. This path also publishes the database port defined in `%s`.
                """.formatted(databaseComposeFile, databaseComposeFile) + dockerSocket;
    }

    /**
     * Builds the quick start: start either runtime path, follow the startup, open Artemis, and stop it.
     *
     * @return quick start markdown.
     */
    private String readmeQuickStart() {
        return """
                ## Quick start

                Run every command from this extracted package directory.

                1. Start Artemis. Without an argument, the package runs the published Artemis image and needs no
                   checkout:

                   ```bash
                   bash scripts/start-demo.sh
                   ```

                   To run your local Artemis checkout instead, pass its path:

                   ```bash
                   bash scripts/start-demo.sh /absolute/path/to/Artemis
                   ```

                   The script makes the package scripts executable, creates `env/.env` with DEMO values if it does not
                   exist yet, and starts Artemis in the background.

                2. Follow the startup. The first start can take several minutes while Docker pulls images and Artemis
                   prepares its database:

                   ```bash
                   docker compose -p artemis-feature-model-local logs -f artemis-app
                   ```

                   Artemis is ready when the log shows `Started ArtemisApp`. Press `Ctrl+C` to stop following the log.

                3. Open http://localhost:8080.

                4. Stop Artemis when you are done. Its data stays in Docker volumes for the next start:

                   ```bash
                   bash scripts/stop.sh
                   ```

                   To delete the database and Artemis data as well, run `bash scripts/stop.sh --volumes`.
                """;
    }

    /**
     * Builds the variables section: the Artemis settings in {@code env/.env}, the Jenkins connection note, and the
     * optional shell variables of the start scripts.
     *
     * @param environmentRequirements environment requirements of the package.
     * @param jenkins whether Jenkins is the selected CI provider.
     * @return variables markdown.
     */
    private String readmeVariables(List<EnvironmentRequirement> environmentRequirements, boolean jenkins) {
        List<String> subsections = new ArrayList<>();
        subsections.add("## Variables\n");
        subsections.add(readmeArtemisSettings(environmentRequirements));
        if (jenkins) {
            subsections.add(readmeJenkinsConnection());
        }
        subsections.add(readmeScriptSettings(jenkins));
        return String.join("\n", subsections);
    }

    /**
     * Describes the environment variables the Artemis container reads from {@code env/.env} and how to fill them.
     *
     * @param environmentRequirements environment requirements of the package.
     * @return Artemis settings markdown.
     */
    private String readmeArtemisSettings(List<EnvironmentRequirement> environmentRequirements) {
        if (environmentRequirements.isEmpty()) {
            return """
                    ### Artemis settings in `env/.env`

                    Both runtime paths pass `env/.env` to the Artemis container. This selection needs no variables, so
                    `env/.env` can stay empty.
                    """;
        }
        return """
                ### Artemis settings in `env/.env`

                Both runtime paths pass `env/.env` to the Artemis container. This selection needs these variables:

                | Variable | Feature | Secret |
                | --- | --- | --- |
                %s

                `start-demo.sh` fills them with the DEMO values from `env/.env.demo`, such as
                `%s` or `%s`. DEMO values let Artemis start, but a selected external
                service works only with real values. To provide real values, create `env/.env` from `env/.env.example`,
                which lists every variable with an empty value and its configuration key, and fill it in before you start
                Artemis:

                ```bash
                bash scripts/prepare-env.sh
                ```

                Neither script replaces an existing `env/.env`. Run `bash scripts/prepare-env.sh --force` to recreate it
                from `env/.env.example`, or add `--demo` to copy the DEMO values instead. Do not commit `env/.env`.
                """.formatted(readmeVariableRows(environmentRequirements), DemoDefaultValues.DEMO_PLACEHOLDER, DemoDefaultValues.DEMO_URL);
    }

    /**
     * Renders one table row per environment variable, sorted by name and without duplicates.
     *
     * @param environmentRequirements environment requirements of the package.
     * @return markdown table rows separated by line breaks.
     */
    private String readmeVariableRows(List<EnvironmentRequirement> environmentRequirements) {
        List<String> rows = new ArrayList<>();
        Set<String> writtenNames = new HashSet<>();
        for (EnvironmentRequirement requirement : environmentRequirements.stream().sorted(Comparator.comparing(EnvironmentRequirement::name)).toList()) {
            if (writtenNames.add(requirement.name())) {
                String secret = requirement.secret() ? "yes" : "no";
                rows.add("| `" + requirement.name() + "` | " + requirement.featureName() + " | " + secret + " |");
            }
        }
        return String.join("\n", rows);
    }

    /**
     * Explains how a Jenkins package reaches the separately managed Jenkins instance.
     *
     * @return Jenkins connection markdown.
     */
    private String readmeJenkinsConnection() {
        return """
                ### Jenkins connection

                This package contains no Jenkins service. Point the Jenkins variables in `env/.env` at a Jenkins instance
                that the Artemis container can reach. Inside the container, `localhost` is Artemis itself; with Docker
                Desktop, `host.docker.internal` reaches services on your host.
                """;
    }

    /**
     * Describes the optional shell variables the start scripts read and the path variables they set themselves.
     *
     * @param jenkins whether Jenkins is the selected CI provider; the Jenkins stacks use no Docker socket group.
     * @return script settings markdown.
     */
    private String readmeScriptSettings(boolean jenkins) {
        List<String> rows = new ArrayList<>();
        if (!jenkins) {
            rows.add("| `FM_DOCKER_GID` | `0` on macOS, the socket group on Linux | Docker socket group for Integrated Code Lifecycle builds. |");
        }
        rows.add("| `FM_ARTEMIS_ENV_FILE` | `.env` in the checkout | Local checkout only: Compose values such as database image versions. |");
        rows.add("| `FM_ARTEMIS_COMPOSE_FILE` | the generated local-repo stack "
                + "| Local checkout only: replacement stack, absolute or relative to the checkout. |");
        return """
                ### Script settings

                The start scripts read these optional shell variables:

                | Variable | Default | Purpose |
                | --- | --- | --- |
                %s

                Set a variable for a single start, for example
                `FM_ARTEMIS_ENV_FILE=/path/to/.env bash scripts/start-demo.sh /absolute/path/to/Artemis`. The scripts
                also set `FM_OVERLAY_HOST_PATH`, `FM_ENV_FILE`, and, for a local checkout, `FM_ARTEMIS_REPO` for Docker
                Compose; you need these only when you run `docker compose` by hand.
                """.formatted(String.join("\n", rows));
    }

    /**
     * Describes how both runtime paths start Artemis, how they load the generated configuration, and which Artemis
     * image they run.
     *
     * @param database selected database id.
     * @param databaseComposeFile database Compose file that the local checkout path extends.
     * @param runtimeSource resolved Artemis runtime image.
     * @return runtime markdown.
     */
    private String readmeRuntime(String database, String databaseComposeFile, ArtemisRuntimeSource runtimeSource) {
        return """
                ## How the package runs Artemis

                Both runtime paths start the Docker Compose project `artemis-feature-model-local` with their own container
                names and volumes, so a normal Artemis development setup stays untouched. Both paths use this project,
                so run only one of them at a time.

                - **Published image:** `deployment/remote-image/artemis-feature-model-stack.yml` defines Artemis and the
                  `%s` database directly. Starting it reads no Artemis checkout and fetches no files besides Docker
                  images.
                - **Local checkout:** `deployment/local-repo/artemis-feature-model-stack.yml` extends `docker/artemis.yml`
                  and `%s` from your checkout. `deployment/local-repo/docker-compose.override.example.yml` adds the
                  package configuration, and the checkout's `.env` provides Compose values such as database image
                  versions.

                Both paths mount `config/application-feature-model.yml` read-only into the Artemis container and set
                `SPRING_CONFIG_ADDITIONAL_LOCATION`, so Spring Boot loads the generated configuration on top of the
                Artemis defaults.

                ### Artemis image

                %s

                A local checkout runs the image that its `docker/artemis.yml` defines. Package generation never contacts
                the registry.
                """.formatted(database, databaseComposeFile, readmeImageDescription(runtimeSource));
    }

    /**
     * Describes the Artemis image of the published-image path.
     *
     * @param runtimeSource resolved Artemis runtime image.
     * @return image description for the mutable latest tag or a pinned digest.
     */
    private String readmeImageDescription(ArtemisRuntimeSource runtimeSource) {
        String imageReference = "`" + runtimeSource.imageReference() + "`";
        if (runtimeSource.usesLatestTag()) {
            return "The published-image path runs " + imageReference + " and pulls it on every start.\n"
                    + "`latest` is a mutable tag, so a later start can run a newer Artemis build.";
        }
        return "The published-image path runs the pinned image " + imageReference + ",\nso every start runs the same Artemis build.";
    }

    /**
     * Builds the package check instructions and an overview of the generated metadata files.
     *
     * @return package checks markdown.
     */
    private String readmePackageChecks() {
        return """
                ## Package checks

                Check the package without starting Artemis:

                ```bash
                bash scripts/validate-package.sh
                ```

                The script confirms that every package file exists, that the generated configuration contains no raw
                `env:` secret references, that `env/.env.example` declares every `${VARIABLE}` placeholder, and that the
                static configuration-key validation passed.

                The `metadata/` directory records how the package was generated:

                | File | Content |
                | --- | --- |
                | `package-manifest.json` | Package type, runtime paths, Artemis image, technical selection, and readiness. |
                | `runtime-checks.json` | Checks that ran during generation. |
                | `static-config-validation.json` | Result of the configuration-key check, including the Artemis commit the key catalog was verified against. |
                | `generation-report.json` | Selected features, environment requirements, and warnings. |
                | `selected-features.json` | Selected features. |
                | `deployment-profile-summary.json` | Deployment profile used for generation. |
                """;
    }

    /**
     * Builds the demo env file with catalog-typed, visibly fake values for every environment requirement. Clearly
     * labeled as demo-only so it is never mistaken for a real secret store.
     *
     * @param environmentRequirements environment requirements of the package.
     * @return {@code .env.demo} text.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException if a catalog-keyed requirement has no catalog entry or its
     *             demo value does not match the catalog type.
     */
    public String envDemo(List<EnvironmentRequirement> environmentRequirements) {
        StringBuilder builder = new StringBuilder();
        builder.append("# DEMO ONLY — dummy local values. UNSAFE for production; do not commit real secrets.\n");
        builder.append("# scripts/prepare-env.sh --demo copies this file to env/.env for local validation.\n");
        Set<String> writtenNames = new HashSet<>();
        for (EnvironmentRequirement requirement : environmentRequirements.stream().sorted(Comparator.comparing(EnvironmentRequirement::name)).toList()) {
            if (writtenNames.add(requirement.name())) {
                builder.append(requirement.name()).append("=").append(DemoDefaultValues.valueFor(requirement)).append("\n");
            }
        }
        return builder.toString();
    }

    /**
     * Builds the env directory README explaining the three env files and secret-handling expectations.
     *
     * @return env README markdown text.
     */
    public String envReadme() {
        return """
                # Environment files

                - `.env.example` — every environment variable the overlay references, with **empty** values. Copy it to
                  `.env` and fill in real values for a real run.
                - `.env.demo` — the same variables with **dummy** local values (`demo-change-me`). For local validation
                  only; never use these for a real deployment.
                - `.env` — the file the helper scripts actually load. It is created by `scripts/prepare-env.sh` from either
                  `.env.example` or `.env.demo` and is not shipped in the package.

                Secrets must never be committed or used in production from this package. Values here are placeholders or
                dummy demo tokens; provide real secrets securely through your deployment environment.
                """;
    }

    /**
     * Builds the Layer 1 Compose override that layers the overlay onto an existing Artemis stack. The literal env var
     * names, container path, and Spring config setting mirror {@link RuntimePackageConstants} and the helper scripts.
     *
     * @return docker-compose override YAML text.
     */
    public String localRepoOverride() {
        return """
                # Local-repo runtime override (Layer 1) — DEMO / local validation only.
                #
                # scripts/start-local-repo.sh combines this override with the CI-capable Artemis local-VC/local-CI stack
                # (docker/artemis-dev-local-vc-local-ci-mysql.yml), so CI-dependent features such as Hyperion can start. It
                # only layers the generated Spring configuration overlay onto the existing artemis-app service; it does not
                # redefine the Artemis stack.
                #
                # It uses its own container names and named volumes so it never collides with, or writes into, an existing
                # local Artemis dev environment. The database host is pinned to the "mysql" service name so the renamed
                # database container still resolves. Running CI builds (not startup) additionally needs the Docker socket
                # the base stack already mounts.
                #
                # Host paths are injected as absolute values through the FM_OVERLAY_HOST_PATH and FM_ENV_FILE environment
                # variables that the start script exports, so this file works regardless of the directory Docker Compose
                # resolves relative paths from.
                services:
                    artemis-app:
                        container_name: artemis-feature-model-local-app
                        volumes:
                            - "${FM_OVERLAY_HOST_PATH}:/opt/artemis/config/application-feature-model.yml:ro"
                            - "artemis-feature-model-local-data:/opt/artemis/data"
                        env_file:
                            - "${FM_ENV_FILE}"
                        environment:
                            SPRING_CONFIG_ADDITIONAL_LOCATION: "optional:file:/opt/artemis/config/application-feature-model.yml"
                            SPRING_DATASOURCE_URL: "jdbc:mysql://mysql:3306/Artemis?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC"
                    mysql:
                        container_name: artemis-feature-model-local-mysql
                        volumes:
                            - "artemis-feature-model-local-mysqldata:/var/lib/mysql"
                volumes:
                    artemis-feature-model-local-data:
                        name: artemis-feature-model-local-data
                    artemis-feature-model-local-mysqldata:
                        name: artemis-feature-model-local-mysqldata
                """;
    }

    /**
     * Builds the overlay-only override used on top of a generated technical stack.
     *
     * @return technical-model Compose override.
     */
    public String technicalLocalRepoOverride() {
        return """
                # Layers package-owned configuration onto the selection-driven stack.
                services:
                    artemis-app:
                        volumes:
                            - "${FM_OVERLAY_HOST_PATH}:/opt/artemis/config/application-feature-model.yml:ro"
                        env_file:
                            - "${FM_ENV_FILE}"
                        environment:
                            SPRING_CONFIG_ADDITIONAL_LOCATION: "optional:file:/opt/artemis/config/application-feature-model.yml"
                """;
    }

    /**
     * Builds the local-repo README explaining the override, the env vars the start script injects, and the caveats.
     *
     * @return local-repo README markdown text.
     */
    public String localRepoReadme() {
        return """
                # Local repository runtime (Layer 1)

                This directory holds the Docker Compose override used when running Artemis from a **local checkout**.

                ## CI-capable stack

                The default Artemis Compose file is `docker/artemis-dev-local-vc-local-ci-mysql.yml` — the local-VC/local-CI
                stack (profiles `localci,localvc,buildagent`) backed by **MySQL**. This is deliberate: CI-dependent features
                such as Hyperion hard-require a CI trigger bean at startup, so a database-only stack would shut Artemis down
                when they are enabled. As a result any feature selection can start here. Trade-offs: the stack is heavier and
                the first start is slower than a database-only one, and it uses MySQL rather than PostgreSQL.

                ## How it is used

                `scripts/start-local-repo.sh /path/to/Artemis` runs, from the Artemis `docker/` directory:

                ```bash
                docker compose -p artemis-feature-model-local \\
                  --project-directory /path/to/Artemis/docker \\
                  --env-file /path/to/Artemis/.env \\
                  -f /path/to/Artemis/docker/artemis-dev-local-vc-local-ci-mysql.yml \\
                  -f <this-package>/deployment/local-repo/docker-compose.override.example.yml \\
                  up -d
                ```

                The Artemis Compose file can be changed with the `FM_ARTEMIS_COMPOSE_FILE` environment variable (default
                `docker/artemis-dev-local-vc-local-ci-mysql.yml`). Note the override pins a MySQL datasource, so a different
                database stack would also need the override adapted.

                The `--env-file` points at the Artemis repo-root `.env`, which Artemis uses to resolve image versions during
                Compose interpolation (for example `MYSQL_VERSION`). Because `--project-directory` is the `docker/`
                directory, that `.env` is not picked up automatically, so the start script passes it explicitly. Override
                its location with `FM_ARTEMIS_ENV_FILE` if your Artemis `.env` lives elsewhere.

                ## What the override does

                - Layers only onto the `artemis-app` service; it does not redefine the Artemis stack.
                - Uses its **own container names** (`artemis-feature-model-local-app`, `artemis-feature-model-local-mysql`)
                  and **own named volumes**, so it never collides with, or writes into, an existing local Artemis dev
                  environment.
                - Mounts `config/application-feature-model.yml` read-only into the container at
                  `/opt/artemis/config/application-feature-model.yml`.
                - Loads `env/.env` into the container.
                - Sets `SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/opt/artemis/config/application-feature-model.yml`
                  so Spring Boot loads the overlay as an additional configuration file.
                - Pins `SPRING_DATASOURCE_URL` to the `mysql` **service** name (not the stack default `artemis-mysql`
                  container host), so the renamed database container still resolves.

                ## Path handling

                Docker Compose resolves relative bind-mount paths against the project directory (the first Compose file's
                directory), not against this override. To avoid that pitfall, the start script exports absolute host paths
                as `FM_OVERLAY_HOST_PATH` (the overlay) and `FM_ENV_FILE` (the env file), which this override references.
                Run the scripts from the package; do not invoke Compose with this override by hand unless you export those
                variables yourself.

                ## Caveats

                - This is an **example** override and may need adaptation to your Artemis version.
                - The overlay only adds feature configuration; it does not provide a complete Artemis runtime configuration.
                - Startup / config-loading is validated, not full functionality. Optional external services (Iris, Athena,
                  Theia, Apollon, Sharing) may be placeholders; and running actual CI builds (for example Hyperion code
                  generation) needs the Docker socket the base stack mounts, which on macOS may need group/permission tweaks.
                """;
    }

    /**
     * Builds selection-aware local-repository instructions.
     *
     * @param selection resolved technical selection.
     * @return local-repository README.
     */
    public String technicalLocalRepoReadme(TechnicalSelection selection) {
        String database = selection.databaseId().orElseThrow();
        String databaseFile = selection.databaseComposeFile().orElseThrow();
        String ciProvider = selection.ciProviderId().orElseThrow();
        String ciNote = technicalCiNote(ciProvider);
        return """
                # Local repository runtime

                `artemis-feature-model-stack.yml` applies `%s` with `%s`. It extends the local checkout's
                `docker/artemis.yml` and `%s`; the adjacent override only mounts the generated overlay and environment.

                The start script exports `FM_ARTEMIS_REPO`, `FM_OVERLAY_HOST_PATH`, and `FM_ENV_FILE`, then composes both
                package files. `FM_ARTEMIS_COMPOSE_FILE` remains an explicit escape hatch.

                %s
                """.formatted(database, ciProvider, databaseFile, ciNote);
    }

    /**
     * Describes the local-docker CI-specific behavior.
     *
     * @param ciProvider selected CI provider.
     * @return CI note.
     */
    private String technicalCiNote(String ciProvider) {
        if ("jenkins".equals(ciProvider)) {
            return "**Warning:** no Jenkins service is generated. This selection is configuration-complete but not "
                    + "DEMO-bootable as a Jenkins stack.";
        }
        return "The integrated code lifecycle stack mounts the Docker socket and adds the Docker group for local CI builds.";
    }
}
