package de.tum.cit.aet.artemis.featuremodel.export.service;

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
     * Builds the package README for the two local-docker runtime paths.
     *
     * @param modelId active feature model id.
     * @param modelVersion active feature model version.
     * @param profileId active deployment profile id.
     * @param profileVersion active deployment profile version.
     * @param selection resolved technical selection.
     * @param runtimeSource resolved Artemis runtime image.
     * @return package README.
     */
    public String packageReadme(String modelId, String modelVersion, String profileId, String profileVersion,
            TechnicalSelection selection, ArtemisRuntimeSource runtimeSource) {
        String database = selection.databaseId().orElse("mysql");
        String ciProvider = selection.ciProviderId().orElse("integrated-code-lifecycle");
        String jenkinsWarning = "jenkins".equals(ciProvider)
                ? "\n> **Jenkins limitation:** profiles and configuration are generated, but no Jenkins service is included; the readiness check fails deliberately.\n"
                : "";
        return """
                # Artemis Feature Model — Local Docker Deployment Package

                Generated from feature model `%s` version `%s` and deployment context `%s` version `%s` in DEMO mode.
                The selected database is `%s`; the selected CI provider is `%s`.

                This package is for local validation, not production. It never writes plaintext secrets.

                ## Quick Start

                Use a local Artemis checkout when you provide one path argument:

                ```bash
                bash scripts/start-demo.sh /absolute/path/to/Artemis
                ```

                Use the self-contained remote-image stack when you provide no argument:

                ```bash
                bash scripts/start-demo.sh
                ```

                Both forms prepare `env/.env` non-destructively and use the stable Compose project name
                `artemis-feature-model-local`. Stop either package project with `./scripts/stop.sh`; add `--volumes`
                only when you intentionally want to destroy its local data.

                ## Runtime image

                - Image repository: `%s`
                - Original image digest: `%s`

                The value `latest` renders `%s:latest`. It is a mutable tag, so a later start can run a newer
                Artemis build. Every other non-empty value renders an exact digest reference in the form
                `%s@<imageDigest>`. Package generation never contacts the registry or resolves `latest`.

                `deployment/remote-image/artemis-feature-model-stack.yml` directly declares Artemis and the selected
                database. Remote startup does not clone Git repositories, fetch files, download upstream `.env` files,
                or read a local Artemis checkout. The local-repo path continues to extend the supplied checkout's
                Compose definitions.

                ## Host support and Docker socket

                Linux with Docker Engine and macOS with Docker Desktop are supported. Windows is supported only from a
                WSL2 distribution with Docker Desktop WSL integration and Linux containers enabled; Native PowerShell,
                Command Prompt, Git Bash, and Windows containers are not supported.

                Integrated Code Lifecycle mounts `/var/run/docker.sock`. On Linux the start scripts derive
                `FM_DOCKER_GID` from the socket; on macOS they use `0`. Docker Desktop Enhanced Container Isolation
                requires an explicit socket-mount exception. Mounting the socket grants broad control over Docker.
                %s
                ## Package checks

                Run `./scripts/validate-package.sh` before startup. Review `metadata/package-manifest.json`,
                `metadata/runtime-checks.json`, `metadata/static-config-validation.json`, and
                `metadata/generation-report.json` for provenance, warnings, and validation results.
                """.formatted(modelId, modelVersion, profileId, profileVersion, database, ciProvider, runtimeSource.imageRepository(),
                runtimeSource.imageDigest(), runtimeSource.imageRepository(), runtimeSource.imageRepository(), jenkinsWarning);
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
