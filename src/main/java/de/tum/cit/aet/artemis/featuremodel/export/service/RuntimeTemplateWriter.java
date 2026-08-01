package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.List;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.export.domain.TechnicalSelection;

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
     * Builds the selection-aware package README while preserving the frozen curated-model output.
     *
     * @param modelId active feature model id.
     * @param modelVersion active feature model version.
     * @param profileId active deployment profile id.
     * @param profileVersion active deployment profile version.
     * @param selection resolved technical selection.
     * @return package README.
     */
    public String packageReadme(String modelId, String modelVersion, String profileId, String profileVersion,
            TechnicalSelection selection) {
        if (selection.isEmpty()) {
            return packageReadme(modelId, modelVersion, profileId, profileVersion);
        }
        String database = selection.databaseId().orElseThrow();
        String databaseFile = selection.databaseComposeFile().orElseThrow();
        String ciProvider = selection.ciProviderId().orElseThrow();
        String ciGuide = packageCiGuide(ciProvider);
        return """
                # Artemis Feature Model — Local Runtime Deployment Package

                Generated from feature model `%s` version `%s` and deployment context `%s` version `%s` in DEMO mode.

                This package combines the generated Spring configuration overlay with a selection-driven Docker Compose
                stack and helper scripts. Its purpose is **local validation**: confirming that Artemis starts with the
                selected database, CI profile family, and feature configuration. It is **not** a production deployment.

                ## Selected technical stack

                The generated Compose stack applies the selected technical axes:

                - Database: `%s`
                - Database Compose source: `%s`
                - CI provider: `%s`
                - Stack: `deployment/local-repo/artemis-feature-model-stack.yml`

                %s

                ## Supported host environments

                - **Linux with Docker Engine:** supported when the Docker daemon exposes
                  `unix:///var/run/docker.sock`.
                - **macOS with Docker Desktop:** supported with Linux containers. The start script uses
                  `FM_DOCKER_GID=0` for the Docker Desktop socket.
                - **Windows with Docker Desktop:** supported only through a WSL2 distribution with WSL integration and
                  Linux containers enabled. Run the Bash scripts inside WSL; keeping the package and Artemis checkout
                  in the WSL filesystem is recommended.

                Native PowerShell, Command Prompt, Git Bash, and Windows containers are not supported. Docker Desktop
                for Linux is also not supported by the ICL stack's default socket handling because it normally exposes
                a per-user socket instead of `/var/run/docker.sock`.

                When Integrated Code Lifecycle is selected, the Artemis container mounts the Docker socket so it can
                create build containers. Docker Desktop Enhanced Container Isolation blocks this mount by default
                unless the Artemis image is allowed explicitly. Mounting the socket grants broad control over the local
                Docker daemon, so use this package only for local development and validation.

                ## What this package does not provide

                - Production-ready credentials, TLS, backups, monitoring, or high availability.
                - Working implementations of optional external services such as Iris, Athena, Theia, Apollon, or Sharing.
                - A standalone remote-image runtime. The package still needs a local Artemis checkout because its stack
                  extends Compose services from that checkout.

                ## Prerequisites

                Before starting, make sure you have:

                1. Docker with Docker Compose v2 (`docker compose version`).
                2. A local Artemis checkout containing `docker/artemis.yml`, `%s`, and a repository-root `.env`.
                3. Free host ports `8080` for Artemis and `5005` for remote debugging.
                4. Enough disk space for the Artemis image, the selected database image, build images, and named volumes.

                Run every command below from this extracted package directory. Use an absolute path for the Artemis
                checkout when possible.

                ## Step 1 — Inspect and validate the package

                Make the scripts executable, print the generated summary, and run the static package checks:

                ```bash
                chmod +x scripts/*.sh
                ./scripts/print-runtime-summary.sh
                ./scripts/validate-package.sh
                ```

                `validate-package.sh` checks required files, secret placeholder declarations, and the static Artemis
                configuration-key verdict. It does not contact Docker, Artemis, the database, or an external CI server.
                Review these files if a check fails:

                - `metadata/generation-report.json`
                - `metadata/runtime-checks.json`
                - `metadata/static-config-validation.json`

                ## Step 2A — Quick start with DEMO values

                For local startup testing, run:

                ```bash
                bash scripts/start-demo.sh /absolute/path/to/Artemis
                ```

                This command:

                1. makes all package scripts executable;
                2. copies `env/.env.demo` to `env/.env` if `env/.env` does not already exist; and
                3. starts the selection-driven Compose stack in the background.

                DEMO values such as `demo-change-me` are intentionally unsafe. They are suitable only for local startup
                validation. If `env/.env` already exists, the script preserves it. To deliberately recreate it from the
                latest DEMO template, run:

                ```bash
                ./scripts/prepare-env.sh --demo --force
                ```

                `--force` overwrites `env/.env`, so review or back up any values you want to keep.

                ## Step 2B — Start with your own integration values

                Use this path when selected features connect to real services:

                ```bash
                ./scripts/prepare-env.sh
                # Edit env/.env and replace every empty value.
                ./scripts/validate-package.sh
                ./scripts/start-local-repo.sh /absolute/path/to/Artemis
                ```

                `prepare-env.sh` copies `env/.env.example` and never overwrites an existing `env/.env` unless `--force`
                is provided. The generated Spring overlay contains `${VARIABLE}` references; Docker loads their values
                from `env/.env`. Do not commit that file or use DEMO credentials outside local testing.

                ## Step 3 — Follow startup and verify Artemis

                The first start can take several minutes while Docker pulls images, PostgreSQL/MySQL initializes, and
                Artemis applies database migrations. Check the stack and follow the application logs with:

                ```bash
                docker compose -p artemis-feature-model-local ps
                docker compose -p artemis-feature-model-local logs -f artemis-app
                ```

                Successful startup includes a log entry containing `Started ArtemisApp`. Then open:

                - Artemis: http://localhost:8080
                - Readiness endpoint: http://localhost:8080/management/health/readiness

                The database health check only proves that the database accepts connections. Artemis can still be
                starting, applying migrations, or waiting for a selected external integration.

                ## Step 4 — Stop or reset the stack

                Stop containers while keeping the package-owned database and Artemis data volumes:

                ```bash
                ./scripts/stop-local-repo.sh /absolute/path/to/Artemis
                ```

                To remove the named volumes as well:

                ```bash
                ./scripts/stop-local-repo.sh /absolute/path/to/Artemis --volumes
                ```

                The second command permanently deletes the local database and Artemis data created by this package.

                ## How the generated stack is assembled

                `artemis-feature-model-stack.yml` extends `docker/artemis.yml` and `%s` from the local Artemis checkout.
                `docker-compose.override.example.yml` then:

                - mounts `config/application-feature-model.yml` read-only at
                  `/opt/artemis/config/application-feature-model.yml`;
                - loads `env/.env` into the Artemis container; and
                - sets `SPRING_CONFIG_ADDITIONAL_LOCATION` so Spring loads the generated overlay.

                The start script exports absolute package and checkout paths before invoking Compose. The stack uses the
                project name `artemis-feature-model-local`, package-specific container names, and package-specific named
                volumes to avoid colliding with a normal Artemis development stack.

                The Artemis repository-root `.env` is used separately for Compose interpolation, including image-version
                variables such as `POSTGRES_VERSION` or `MYSQL_VERSION`. Set `FM_ARTEMIS_ENV_FILE` when that file is stored
                elsewhere.

                ## Environment files

                - `env/.env.example` lists required variables with empty values.
                - `env/.env.demo` contains dummy local values.
                - `env/.env` is the runtime file created by `prepare-env.sh`; it is not included in the ZIP.

                The package never writes real plaintext secrets. Values in `.env.demo` are explicit dummy placeholders,
                not generated credentials.

                ## Supported overrides

                - `FM_ARTEMIS_ENV_FILE=/path/to/.env` selects the Artemis Compose interpolation file.
                - `FM_ARTEMIS_COMPOSE_FILE=/path/to/stack.yml` replaces the generated stack explicitly.
                - `FM_DOCKER_GID=<gid>` overrides the Docker socket group used by the integrated code lifecycle stack.

                Prefer the generated stack unless you are intentionally adapting the package to a different Artemis
                checkout. An override must still define compatible `artemis-app` and database services.

                ## Troubleshooting

                ### `env/.env` is missing or contains stale variables

                Run `./scripts/prepare-env.sh --demo` for DEMO values or `./scripts/prepare-env.sh` for empty real-service
                values. If the file came from an older package, use `--force` only after reviewing the existing secrets.

                ### An image name is empty or Docker reports `invalid reference format`

                Confirm that the Artemis checkout contains its repository-root `.env`. If it lives elsewhere, export
                `FM_ARTEMIS_ENV_FILE` before starting.

                ### Port `8080` or `5005` is already allocated

                Stop the conflicting local stack before starting this package. The generated stack deliberately uses
                fixed development ports so its URLs and debugging instructions remain deterministic.

                ### Artemis starts but a selected external feature fails

                DEMO values only satisfy configuration placeholders. Replace the corresponding values in `env/.env` and,
                where the generated overlay contains a literal endpoint, update the deployment profile and regenerate the
                package. Use `metadata/generation-report.json` to trace each generated value to its selected feature.

                ### The local checkout is incompatible

                The generated keys were verified against Artemis commit `%s`. A substantially different checkout can
                rename configuration keys, Compose services, or environment variables. Review
                `metadata/static-config-validation.json` and regenerate against a matching snapshot.

                ## Package contents

                - `config/application-feature-model.yml` — generated Spring configuration overlay.
                - `env/` — example, DEMO, and environment-file instructions.
                - `metadata/` — selected features, deployment profile, generation report, runtime checks, and manifest.
                - `deployment/local-repo/artemis-feature-model-stack.yml` — selected database and CI profile stack.
                - `deployment/local-repo/docker-compose.override.example.yml` — package overlay and environment mount.
                - `deployment/local-repo/README.md` — lower-level Compose details.
                - `scripts/` — environment preparation, validation, start, stop, and summary helpers.
                """.formatted(modelId, modelVersion, profileId, profileVersion, database, databaseFile, ciProvider, ciGuide,
                databaseFile, databaseFile, RuntimePackageConstants.VERIFIED_ARTEMIS_COMMIT);
    }

    /**
     * Describes the runtime requirements of the selected CI provider.
     *
     * @param ciProvider selected CI provider.
     * @return CI-specific package instructions.
     */
    private String packageCiGuide(String ciProvider) {
        if ("jenkins".equals(ciProvider)) {
            return """
                    ### External Jenkins required

                    > **Jenkins limitation:** this package configures the Artemis Jenkins profile but contains no Jenkins
                    > service. Artemis can start, but creating programming exercises and executing builds require a
                    > separately managed Jenkins instance.

                    The Jenkins URL must be reachable **from the Artemis container**. `localhost` inside that container
                    refers to Artemis itself, not to the Docker host. Typical addresses are `http://jenkins:8080` for a
                    Jenkins service on the same Docker network or `http://host.docker.internal:8082` for Jenkins running
                    on Docker Desktop's host. Configure matching Jenkins credentials, notification tokens, and LocalVC
                    credentials before testing programming-exercise creation.
                    """;
        }
        return """
                ### Integrated Code Lifecycle

                No separate CI service is required. Artemis creates build containers through the mounted host Docker
                socket. The start script derives `FM_DOCKER_GID` automatically (`0` on macOS; the socket group on Linux).
                If builds report Docker socket permission errors, set `FM_DOCKER_GID` explicitly and restart the stack.
                Build images still need to be available locally or pullable by the host Docker daemon.
                """;
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
