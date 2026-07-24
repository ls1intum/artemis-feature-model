package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Writes the static and near-static text files that turn the Phase 5 configuration artifacts into a local runtime
 * deployment package: the package README, the demo/README env files, and the Layer 1 (local Artemis repository)
 * Compose override and its README.
 *
 * <p>
 * All content is deterministic for the same input. Shared paths and environment variable names are the literal values
 * of the constants in {@link RuntimePackageConstants}; a drift-guard test keeps these files and the helper scripts in
 * sync with those constants. Only Layer 1 files are produced in this phase; the remote-image layer is deferred.
 */
@Component
public class RuntimeTemplateWriter {

    /**
     * Builds the package-level README describing the local runtime package and how to use its Layer 1 mode.
     *
     * @param modelId active feature model id.
     * @param modelVersion active feature model version.
     * @param profileId active deployment profile id.
     * @param profileVersion active deployment profile version.
     * @return README markdown text.
     */
    public String packageReadme(String modelId, String modelVersion, String profileId, String profileVersion) {
        return """
                # Artemis Feature Model — Local Runtime Deployment Package

                Generated from feature model `%s` version `%s` and deployment context `%s` version `%s` in DEMO mode.

                This package wraps the Level 1 configuration artifacts (see `metadata/generation-report.json`) with local
                runtime templates, helper scripts, and package metadata. Its goal is **local validation**: confirming that
                Artemis starts with the generated Spring configuration overlay mounted and loaded. It is **not** a
                production deployment.

                ## What this is not

                - Not a production deployment. Placeholder values may be present; secrets are never written as plaintext.
                - Not a replacement for normal Artemis configuration. `config/application-feature-model.yml` is applied as
                  an **additional** Spring configuration file on top of the Artemis configuration stack.
                - Not a guarantee that optional external services (Iris, Athena, Theia, Apollon, Sharing) work locally.

                ## Runtime modes

                This package currently supports one local validation mode:

                - **Layer 1 — Local Artemis Repository Runtime** (`deployment/local-repo/`, `scripts/start-local-repo.sh`).
                  Uses an existing local Artemis checkout and its Docker Compose setup.

                A second mode (Layer 2 — Remote Artemis Image Runtime, running without an Artemis checkout by pulling a
                pinned Artemis image) is planned for a later increment and is not included here.

                ## Quick start (DEMO)

                One command starts the Layer 1 stack in DEMO mode:

                ```bash
                bash scripts/start-demo.sh /path/to/Artemis
                ```

                It makes the package scripts executable (ZIP archives do not preserve the executable bit), creates
                `env/.env` with DEMO placeholder values (an existing `env/.env` is kept unchanged), and starts the
                local Artemis repository stack. Stop it later with `./scripts/stop-local-repo.sh /path/to/Artemis`.

                ## Manual path: Layer 1 step by step

                Use the individual scripts instead when you want real service values in `env/.env` or more control:

                ```bash
                chmod +x scripts/*.sh           # ZIP archives do not preserve the executable bit
                ./scripts/prepare-env.sh        # or --demo for placeholder values
                ./scripts/start-local-repo.sh /path/to/Artemis
                # ... validate, then stop:
                ./scripts/stop-local-repo.sh /path/to/Artemis
                ```

                `start-local-repo.sh` verifies Docker and Docker Compose, checks that the given path looks like an Artemis
                repository, and layers `deployment/local-repo/docker-compose.override.example.yml` onto the Artemis Compose
                stack. It uses the CI-capable local-VC/local-CI stack (MySQL) so any selection — including CI-dependent
                features such as Hyperion — can start; the first start is therefore slower and heavier than a database-only
                stack. The override mounts the overlay read-only and tells Spring Boot to load it, using its own container
                names and volumes so it does not disturb an existing local Artemis dev environment. Artemis then starts at
                http://localhost:8080.

                > The overlay keys were verified against Artemis commit `%s`. A local checkout at a very different
                > commit may not match every key; review `metadata/generation-report.json` and `metadata/runtime-checks.json`.

                ## Package contents

                - `config/application-feature-model.yml` — the generated Spring configuration overlay.
                - `env/` — environment files (`.env.example`, `.env.demo`) and their README.
                - `metadata/` — selected features, deployment-profile summary, generation report, package manifest,
                  runtime checks, and the static config validation report.
                - `deployment/local-repo/` — the local-repo Compose override and its README.
                - `scripts/` — helper scripts described above plus `validate-package.sh` and `print-runtime-summary.sh`.

                Run `./scripts/validate-package.sh` to check the package structure and `./scripts/print-runtime-summary.sh`
                for a quick overview.
                """.formatted(modelId, modelVersion, profileId, profileVersion, RuntimePackageConstants.VERIFIED_ARTEMIS_COMMIT);
    }

    /**
     * Builds the demo env file with dummy values for every referenced environment variable. Clearly labeled as
     * demo-only so it is never mistaken for a real secret store.
     *
     * @param environmentVariableNames referenced environment variable names, in stable order.
     * @return {@code .env.demo} text.
     */
    public String envDemo(List<String> environmentVariableNames) {
        StringBuilder builder = new StringBuilder();
        builder.append("# DEMO ONLY — dummy local values. UNSAFE for production; do not commit real secrets.\n");
        builder.append("# scripts/prepare-env.sh --demo copies this file to env/.env for local validation.\n");
        for (String name : environmentVariableNames) {
            builder.append(name).append("=demo-change-me\n");
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
}
