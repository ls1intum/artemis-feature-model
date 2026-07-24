package de.tum.cit.aet.artemis.featuremodel.export.service;

import org.springframework.stereotype.Component;

/**
 * Writes the Bash helper scripts (macOS/Linux) for the local runtime deployment package. The scripts reduce manual
 * steps: preparing the env file, validating the package, and starting/stopping Artemis from a local checkout (Layer 1).
 *
 * <p>
 * Each script uses {@code set -euo pipefail}, prints clear errors, avoids destructive operations, and never overwrites
 * {@code env/.env} or removes volumes unless explicitly requested. The literal project name, env var names, and Compose
 * file mirror {@link RuntimePackageConstants}; a drift-guard test keeps them in sync. The remote-image scripts are
 * deferred with the remote-image layer.
 */
@Component
public class RuntimeScriptWriter {

    /**
     * Builds {@code start-demo.sh}, the single-command DEMO entry point: it makes the package scripts executable (ZIP
     * archives do not preserve the executable bit), creates {@code env/.env} with DEMO values via
     * {@code prepare-env.sh --demo} (an existing {@code env/.env} is kept), and delegates to
     * {@code start-local-repo.sh}. Invoked with {@code bash scripts/start-demo.sh /path/to/Artemis} so no prior
     * {@code chmod} is needed.
     *
     * @return {@code start-demo.sh} content.
     */
    public String startDemoScript() {
        return """
                #!/usr/bin/env bash
                set -euo pipefail

                SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

                if [ "$#" -lt 1 ] || [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
                  echo "Usage: bash $(basename "$0") /path/to/Artemis"
                  echo "  One-command DEMO start: makes the package scripts executable, creates env/.env"
                  echo "  with DEMO placeholder values (an existing env/.env is kept), and starts the"
                  echo "  local Artemis repository stack via scripts/start-local-repo.sh."
                  exit 0
                fi

                chmod +x "$SCRIPT_DIR"/*.sh
                "$SCRIPT_DIR/prepare-env.sh" --demo
                exec "$SCRIPT_DIR/start-local-repo.sh" "$@"
                """;
    }

    /**
     * Builds {@code prepare-env.sh}, which creates {@code env/.env} non-destructively from the example or demo file.
     *
     * @return {@code prepare-env.sh} content.
     */
    public String prepareEnvScript() {
        return """
                #!/usr/bin/env bash
                set -euo pipefail

                SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
                PACKAGE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

                DEMO=false
                FORCE=false
                for arg in "$@"; do
                  case "$arg" in
                    --demo) DEMO=true ;;
                    --force) FORCE=true ;;
                    -h|--help)
                      echo "Usage: $(basename "$0") [--demo] [--force]"
                      echo "  Creates env/.env from env/.env.example (or env/.env.demo with --demo)."
                      echo "  Does not overwrite an existing env/.env unless --force is given."
                      exit 0 ;;
                    *)
                      echo "ERROR: unknown argument: $arg" >&2
                      exit 1 ;;
                  esac
                done

                ENV_FILE="$PACKAGE_ROOT/env/.env"
                if [ "$DEMO" = true ]; then
                  SOURCE_FILE="$PACKAGE_ROOT/env/.env.demo"
                else
                  SOURCE_FILE="$PACKAGE_ROOT/env/.env.example"
                fi

                if [ ! -f "$SOURCE_FILE" ]; then
                  echo "ERROR: source env file not found: $SOURCE_FILE" >&2
                  exit 1
                fi

                if [ -f "$ENV_FILE" ] && [ "$FORCE" != true ]; then
                  echo "env/.env already exists; leaving it unchanged (use --force to overwrite)."
                  exit 0
                fi

                cp "$SOURCE_FILE" "$ENV_FILE"
                echo "Wrote env/.env from $(basename "$SOURCE_FILE")."
                if [ "$DEMO" = true ]; then
                  echo "WARNING: env/.env now contains DEMO placeholder values. Do not use them for a real deployment."
                fi
                """;
    }

    /**
     * Builds {@code validate-package.sh}, which checks required files, overlay env leaks, placeholder declarations, and
     * the static config validation verdict. It does not require {@code jq}.
     *
     * @return {@code validate-package.sh} content.
     */
    public String validatePackageScript() {
        return """
                #!/usr/bin/env bash
                set -euo pipefail

                SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
                PACKAGE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
                cd "$PACKAGE_ROOT"

                STATUS=0

                REQUIRED_FILES=(
                  "README.md"
                  "config/application-feature-model.yml"
                  "env/.env.example"
                  "env/.env.demo"
                  "env/README.md"
                  "metadata/selected-features.json"
                  "metadata/deployment-profile-summary.json"
                  "metadata/generation-report.json"
                  "metadata/package-manifest.json"
                  "metadata/runtime-checks.json"
                  "metadata/static-config-validation.json"
                  "deployment/local-repo/docker-compose.override.example.yml"
                  "deployment/local-repo/README.md"
                  "scripts/prepare-env.sh"
                  "scripts/start-demo.sh"
                  "scripts/validate-package.sh"
                  "scripts/start-local-repo.sh"
                  "scripts/stop-local-repo.sh"
                  "scripts/print-runtime-summary.sh"
                )

                echo "Checking required files..."
                for file in "${REQUIRED_FILES[@]}"; do
                  if [ -f "$file" ]; then
                    echo "  OK   $file"
                  else
                    echo "  MISS $file"
                    STATUS=1
                  fi
                done

                OVERLAY="config/application-feature-model.yml"
                echo "Checking overlay for raw env: values..."
                if [ -f "$OVERLAY" ] && grep -Eq 'env:[A-Za-z_]' "$OVERLAY"; then
                  echo "  FAIL overlay contains a raw env: value (secret leak)"
                  STATUS=1
                else
                  echo "  OK   no raw env: values in overlay"
                fi

                echo "Checking that overlay placeholders are declared in env/.env.example..."
                if [ -f "$OVERLAY" ] && [ -f "env/.env.example" ]; then
                  MISSING=0
                  for var in $(grep -oE '[$][{][A-Z0-9_]+[}]' "$OVERLAY" | tr -d '${}' | sort -u); do
                    if grep -q "^$var=" "env/.env.example"; then
                      echo "  OK   $var"
                    else
                      echo "  MISS $var is not declared in env/.env.example"
                      MISSING=1
                      STATUS=1
                    fi
                  done
                  if [ "$MISSING" = 0 ]; then
                    echo "  All overlay placeholders are declared."
                  fi
                fi

                STATIC_REPORT="metadata/static-config-validation.json"
                echo "Checking static config validation result..."
                if [ -f "$STATIC_REPORT" ] && grep -Eq '"overallStatus"[[:space:]]*:[[:space:]]*"PASS"' "$STATIC_REPORT"; then
                  echo "  OK   static config validation reported PASS"
                else
                  echo "  FAIL static config validation did not report PASS (see $STATIC_REPORT)"
                  STATUS=1
                fi

                echo ""
                if [ "$STATUS" = 0 ]; then
                  echo "validate-package: PASS"
                else
                  echo "validate-package: FAIL" >&2
                fi
                exit "$STATUS"
                """;
    }

    /**
     * Builds the package validator and requires the generated stack for a technical model.
     *
     * @param technicalStack whether the package contains a selection-driven stack.
     * @return validator script.
     */
    public String validatePackageScript(boolean technicalStack) {
        String script = validatePackageScript();
        if (!technicalStack) {
            return script;
        }
        String stackEntry = "                  \"deployment/local-repo/artemis-feature-model-stack.yml\"\n";
        String insertionPoint = "                  \"deployment/local-repo/docker-compose.override.example.yml\"\n";
        return script.replace(insertionPoint, stackEntry + insertionPoint);
    }

    /**
     * Builds {@code start-local-repo.sh}, which starts Artemis from a local checkout with the overlay layered on top.
     *
     * @return {@code start-local-repo.sh} content.
     */
    public String startLocalRepoScript() {
        return """
                #!/usr/bin/env bash
                set -euo pipefail

                SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
                PACKAGE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

                if [ "$#" -lt 1 ] || [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
                  echo "Usage: $(basename "$0") /path/to/Artemis"
                  echo "  Starts Artemis from a local checkout with the generated overlay layered on top."
                  echo "  Uses the CI-capable local-VC/local-CI (MySQL) stack so any selection can start."
                  echo "  Override the Artemis Compose file with FM_ARTEMIS_COMPOSE_FILE (default docker/artemis-dev-local-vc-local-ci-mysql.yml)."
                  exit 0
                fi

                ARTEMIS_REPO="$1"

                command -v docker >/dev/null 2>&1 || { echo "ERROR: docker is not installed or not on PATH." >&2; exit 1; }
                docker compose version >/dev/null 2>&1 || { echo "ERROR: 'docker compose' is not available (Docker Compose v2 required)." >&2; exit 1; }

                if [ ! -d "$ARTEMIS_REPO" ]; then
                  echo "ERROR: Artemis repository path does not exist: $ARTEMIS_REPO" >&2
                  exit 1
                fi

                ARTEMIS_DOCKER_DIR="$ARTEMIS_REPO/docker"
                if [ ! -f "$ARTEMIS_DOCKER_DIR/artemis.yml" ]; then
                  echo "ERROR: $ARTEMIS_REPO does not look like an Artemis repository (missing docker/artemis.yml)." >&2
                  exit 1
                fi

                COMPOSE_FILE_REL="${FM_ARTEMIS_COMPOSE_FILE:-docker/artemis-dev-local-vc-local-ci-mysql.yml}"
                ARTEMIS_COMPOSE_FILE="$ARTEMIS_REPO/$COMPOSE_FILE_REL"
                if [ ! -f "$ARTEMIS_COMPOSE_FILE" ]; then
                  echo "ERROR: Artemis Compose file not found: $ARTEMIS_COMPOSE_FILE" >&2
                  echo "       Set FM_ARTEMIS_COMPOSE_FILE to a Compose file that exists under the Artemis repository." >&2
                  exit 1
                fi

                ENV_FILE="$PACKAGE_ROOT/env/.env"
                if [ ! -f "$ENV_FILE" ]; then
                  echo "ERROR: env/.env not found. Run ./scripts/prepare-env.sh --demo first." >&2
                  exit 1
                fi

                OVERRIDE_FILE="$PACKAGE_ROOT/deployment/local-repo/docker-compose.override.example.yml"

                # Artemis resolves image versions (e.g. POSTGRES_VERSION) from its repo-root .env during Compose
                # interpolation. --project-directory points at docker/, where no .env lives, so pass it explicitly.
                ARTEMIS_ENV_FILE="${FM_ARTEMIS_ENV_FILE:-$ARTEMIS_REPO/.env}"

                # Inject absolute host paths so the override does not depend on Compose's relative-path resolution.
                export FM_OVERLAY_HOST_PATH="$PACKAGE_ROOT/config/application-feature-model.yml"
                export FM_ENV_FILE="$ENV_FILE"

                echo "Starting Artemis from $ARTEMIS_REPO with the generated overlay..."
                echo "  Compose file: $ARTEMIS_COMPOSE_FILE"
                echo "  Overlay:      $FM_OVERLAY_HOST_PATH"
                echo "  Env file:     $FM_ENV_FILE"
                echo "  Artemis env:  $ARTEMIS_ENV_FILE"
                echo "  Note: this is the CI-capable MySQL stack; the first start pulls/builds images and can take a while."

                if [ -f "$ARTEMIS_ENV_FILE" ]; then
                  docker compose -p artemis-feature-model-local \\
                    --project-directory "$ARTEMIS_DOCKER_DIR" \\
                    --env-file "$ARTEMIS_ENV_FILE" \\
                    -f "$ARTEMIS_COMPOSE_FILE" \\
                    -f "$OVERRIDE_FILE" \\
                    up -d
                else
                  echo "WARNING: Artemis env file not found: $ARTEMIS_ENV_FILE" >&2
                  echo "         The Artemis stack may fail to resolve image versions (e.g. POSTGRES_VERSION)." >&2
                  echo "         Set FM_ARTEMIS_ENV_FILE if the Artemis interpolation .env lives elsewhere." >&2
                  docker compose -p artemis-feature-model-local \\
                    --project-directory "$ARTEMIS_DOCKER_DIR" \\
                    -f "$ARTEMIS_COMPOSE_FILE" \\
                    -f "$OVERRIDE_FILE" \\
                    up -d
                fi

                echo ""
                echo "Artemis is starting. Once healthy it will be available at http://localhost:8080"
                echo "Check status:  docker compose -p artemis-feature-model-local ps"
                echo "View logs:     docker compose -p artemis-feature-model-local logs -f artemis-app"
                echo "Stop:          ./scripts/stop-local-repo.sh $ARTEMIS_REPO"
                """;
    }

    /**
     * Builds the selection-driven start script while preserving the frozen curated-model script.
     *
     * @param technicalStack whether the package contains a generated stack.
     * @return start script.
     */
    public String startLocalRepoScript(boolean technicalStack) {
        if (!technicalStack) {
            return startLocalRepoScript();
        }
        return """
                #!/usr/bin/env bash
                set -euo pipefail

                SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
                PACKAGE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

                if [ "$#" -lt 1 ] || [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
                  echo "Usage: $(basename "$0") /path/to/Artemis"
                  echo "  Starts the selection-driven package stack."
                  echo "  Override its default with FM_ARTEMIS_COMPOSE_FILE."
                  exit 0
                fi

                ARTEMIS_REPO="$1"
                ARTEMIS_DOCKER_DIR="$ARTEMIS_REPO/docker"
                if [ ! -f "$ARTEMIS_DOCKER_DIR/artemis.yml" ]; then
                  echo "ERROR: $ARTEMIS_REPO does not look like an Artemis repository." >&2
                  exit 1
                fi

                command -v docker >/dev/null 2>&1 || { echo "ERROR: docker is not installed or not on PATH." >&2; exit 1; }
                docker compose version >/dev/null 2>&1 || { echo "ERROR: Docker Compose v2 is required." >&2; exit 1; }

                DEFAULT_STACK="$PACKAGE_ROOT/deployment/local-repo/artemis-feature-model-stack.yml"
                if [ -n "${FM_ARTEMIS_COMPOSE_FILE:-}" ]; then
                  if [[ "$FM_ARTEMIS_COMPOSE_FILE" = /* ]]; then
                    STACK_FILE="$FM_ARTEMIS_COMPOSE_FILE"
                  else
                    STACK_FILE="$ARTEMIS_REPO/$FM_ARTEMIS_COMPOSE_FILE"
                  fi
                else
                  STACK_FILE="$DEFAULT_STACK"
                fi
                if [ ! -f "$STACK_FILE" ]; then
                  echo "ERROR: Compose stack not found: $STACK_FILE" >&2
                  exit 1
                fi

                ENV_FILE="$PACKAGE_ROOT/env/.env"
                if [ ! -f "$ENV_FILE" ]; then
                  echo "ERROR: env/.env not found. Run ./scripts/prepare-env.sh --demo first." >&2
                  exit 1
                fi

                export FM_ARTEMIS_REPO="$ARTEMIS_REPO"
                export FM_OVERLAY_HOST_PATH="$PACKAGE_ROOT/config/application-feature-model.yml"
                export FM_ENV_FILE="$ENV_FILE"
                OVERRIDE_FILE="$PACKAGE_ROOT/deployment/local-repo/docker-compose.override.example.yml"
                ARTEMIS_ENV_FILE="${FM_ARTEMIS_ENV_FILE:-$ARTEMIS_REPO/.env}"

                COMPOSE_ARGS=(
                  -p artemis-feature-model-local
                  --project-directory "$ARTEMIS_DOCKER_DIR"
                )
                if [ -f "$ARTEMIS_ENV_FILE" ]; then
                  COMPOSE_ARGS+=(--env-file "$ARTEMIS_ENV_FILE")
                fi
                COMPOSE_ARGS+=(-f "$STACK_FILE" -f "$OVERRIDE_FILE")

                echo "Starting selection-driven Artemis stack..."
                echo "  Stack:   $STACK_FILE"
                echo "  Overlay: $FM_OVERLAY_HOST_PATH"
                docker compose "${COMPOSE_ARGS[@]}" up -d

                echo "Artemis is starting at http://localhost:8080"
                """;
    }

    /**
     * Builds {@code stop-local-repo.sh}, which stops the local-repo stack and keeps volumes unless {@code --volumes}.
     *
     * @return {@code stop-local-repo.sh} content.
     */
    public String stopLocalRepoScript() {
        return """
                #!/usr/bin/env bash
                set -euo pipefail

                REMOVE_VOLUMES=false
                for arg in "$@"; do
                  case "$arg" in
                    --volumes) REMOVE_VOLUMES=true ;;
                    -h|--help)
                      echo "Usage: $(basename "$0") [/path/to/Artemis] [--volumes]"
                      echo "  Stops the local-repo Artemis stack started by start-local-repo.sh."
                      echo "  The Artemis path is optional; teardown is by Compose project name."
                      echo "  --volumes also removes named volumes (DESTROYS local Artemis data)."
                      exit 0 ;;
                    *) ;;
                  esac
                done

                command -v docker >/dev/null 2>&1 || { echo "ERROR: docker is not installed or not on PATH." >&2; exit 1; }
                docker compose version >/dev/null 2>&1 || { echo "ERROR: 'docker compose' is not available (Docker Compose v2 required)." >&2; exit 1; }

                if [ "$REMOVE_VOLUMES" = true ]; then
                  echo "WARNING: removing named volumes; local Artemis data will be lost."
                  docker compose -p artemis-feature-model-local down --volumes
                else
                  docker compose -p artemis-feature-model-local down
                fi

                echo "Stopped the local-repo Artemis stack."
                """;
    }

    /**
     * Builds teardown for the generated stack while preserving the frozen curated-model script.
     *
     * @param technicalStack whether the package contains a generated stack.
     * @return stop script.
     */
    public String stopLocalRepoScript(boolean technicalStack) {
        if (!technicalStack) {
            return stopLocalRepoScript();
        }
        return """
                #!/usr/bin/env bash
                set -euo pipefail

                SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
                PACKAGE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

                if [ "$#" -lt 1 ] || [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
                  echo "Usage: $(basename "$0") /path/to/Artemis [--volumes]"
                  exit 0
                fi

                ARTEMIS_REPO="$1"
                REMOVE_VOLUMES=false
                if [ "${2:-}" = "--volumes" ]; then
                  REMOVE_VOLUMES=true
                fi

                export FM_ARTEMIS_REPO="$ARTEMIS_REPO"
                export FM_OVERLAY_HOST_PATH="$PACKAGE_ROOT/config/application-feature-model.yml"
                export FM_ENV_FILE="$PACKAGE_ROOT/env/.env"
                STACK_FILE="$PACKAGE_ROOT/deployment/local-repo/artemis-feature-model-stack.yml"
                OVERRIDE_FILE="$PACKAGE_ROOT/deployment/local-repo/docker-compose.override.example.yml"
                COMPOSE_ARGS=(
                  -p artemis-feature-model-local
                  --project-directory "$ARTEMIS_REPO/docker"
                  -f "$STACK_FILE"
                  -f "$OVERRIDE_FILE"
                )

                if [ "$REMOVE_VOLUMES" = true ]; then
                  echo "WARNING: removing named volumes; local Artemis data will be lost."
                  docker compose "${COMPOSE_ARGS[@]}" down --volumes
                else
                  docker compose "${COMPOSE_ARGS[@]}" down
                fi
                """;
    }

    /**
     * Builds {@code print-runtime-summary.sh}, which prints a quick overview and the recommended next steps.
     *
     * @return {@code print-runtime-summary.sh} content.
     */
    public String printRuntimeSummaryScript() {
        return """
                #!/usr/bin/env bash
                set -euo pipefail

                SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
                PACKAGE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

                echo "Artemis Feature Model — Local Runtime Deployment Package"
                echo "========================================================"
                echo ""
                echo "Supported runtime mode:"
                echo "  - Layer 1: local Artemis repository (scripts/start-local-repo.sh)"
                echo ""
                echo "Package metadata:"
                echo "  - metadata/package-manifest.json"
                echo "  - metadata/runtime-checks.json"
                echo "  - metadata/static-config-validation.json"
                echo "  - metadata/generation-report.json"
                echo ""
                if [ -f "$PACKAGE_ROOT/env/.env" ]; then
                  echo "Environment: env/.env is present."
                else
                  echo "Environment: env/.env is missing. Run ./scripts/prepare-env.sh --demo first."
                fi

                MANIFEST="$PACKAGE_ROOT/metadata/package-manifest.json"
                if command -v jq >/dev/null 2>&1 && [ -f "$MANIFEST" ]; then
                  echo ""
                  echo "Manifest summary:"
                  jq '{packageType, packageVersion, mode, supportedRuntimeModes, readiness}' "$MANIFEST"
                fi

                echo ""
                echo "Next steps:"
                echo "  1. chmod +x scripts/*.sh"
                echo "  2. ./scripts/prepare-env.sh --demo"
                echo "  3. ./scripts/validate-package.sh"
                echo "  4. ./scripts/start-local-repo.sh /path/to/Artemis"
                echo "  5. open http://localhost:8080"
                echo "  6. ./scripts/stop-local-repo.sh /path/to/Artemis"
                """;
    }
}
