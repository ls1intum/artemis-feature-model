package de.tum.cit.aet.artemis.featuremodel.export.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.featuremodel.catalog.domain.FeatureModel;
import de.tum.cit.aet.artemis.featuremodel.export.domain.AnsibleBindingCatalog;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RemoteAnsibleEmissionPlan;
import de.tum.cit.aet.artemis.featuremodel.export.domain.RemoteEnvironmentValues;

/**
 * Writes the remote-ansible package files: the inventory values planned by the pure emission layer plus the
 * self-contained run scaffolding (README, pinned requirements, minimal {@code ansible.cfg}, playbook, preflight
 * script). The package mirrors the structure of the upstream values repository while the collection stays consumed
 * as-is from the pinned fork commit.
 *
 * <p>
 * All content is deterministic for the same input: no timestamps, fixed ordering, and no environment or secret
 * values — every admin-owned or secret value appears exclusively as a {@code lookup('ansible.builtin.env', …)}
 * expression, and the shipped preflight refuses a run with a missing or empty variable.
 */
@Component
public class RemoteAnsibleValuesWriter {

    private final AnsibleBindingCatalog catalog;

    private final RemoteAnsibleEmissionPlanner planner;

    /**
     * Creates the writer over the loaded binding catalog.
     *
     * @param catalogLoader fail-closed loader of the Ansible binding catalog.
     */
    public RemoteAnsibleValuesWriter(AnsibleBindingCatalogLoader catalogLoader) {
        this.catalog = catalogLoader.catalog();
        this.planner = new RemoteAnsibleEmissionPlanner(catalog);
    }

    /**
     * Plans the inventory files for a validated selection through the pure emission layer.
     *
     * @param model active feature model.
     * @param selectedFeatureIds validated selected feature ids.
     * @param environment resolved target identity.
     * @return deterministic emission plan.
     * @throws de.tum.cit.aet.artemis.featuremodel.shared.exception.ArtifactGenerationException if a feature is
     *             unclassified or a selection state is unsupported.
     */
    public RemoteAnsibleEmissionPlan plan(FeatureModel model, Set<String> selectedFeatureIds, RemoteEnvironmentValues environment) {
        return planner.plan(model, selectedFeatureIds, environment);
    }

    /**
     * Builds the package README for the admin consuming the package.
     *
     * @param modelId active feature model id.
     * @param modelVersion active feature model version.
     * @param profileId active deployment profile id.
     * @return README markdown text.
     */
    public String packageReadme(String modelId, String modelVersion, String profileId) {
        return """
                # Artemis Remote Deployment Package (Ansible)

                Generated from feature model `%s` version `%s` and deployment context `%s`, with Ansible binding
                catalog v%s curated against collection commit `%s`.

                This package is **admin-consumable, not deployable**: it contains the complete values and
                orchestration for deploying the selected Artemis variant with the pinned
                `ls1intum.artemis` Ansible collection, but it holds no credentials and connects to nothing.
                Deployment remains a deliberate admin action on an execution environment that provides SSH access
                and the environment values.

                ## Contents

                - `requirements.yml` — the Artemis collection pinned to the exact commit the values were curated against,
                  plus the collections its roles need.
                - `ansible.cfg` — minimal run semantics; `hash_behaviour = merge` is required by the inventory layering.
                - `playbook.yml` — applies the collection's `artemis` and `legal` roles to the `artemistests` group.
                - `inventory/` — group membership wiring and generated values for the selected variant.
                - `preflight.sh` — the environment gate, static checks, and `ansible-playbook --syntax-check`; never
                  connects to a host.
                - `metadata/` — package manifest, layered readiness, every environment reference, and the selected features.

                ## Environment values

                No environment value — identity or secret, dummy or real — is stored in this package. Every value is
                referenced as a `lookup('ansible.builtin.env', …)` expression that Ansible resolves on the control
                node at run time; `metadata/env-references.json` lists each variable with its consuming value and the
                file referencing it. Provide the variables where the playbook runs:

                - **Locally**: export the full set in the shell before running the preflight and the playbook.
                - **GitHub Actions** (execution-plane stage): provision the same names as Actions secrets; the
                  workflow injects them into the run environment.

                Ownership of the values:

                - **Identity values** (`TESTSERVER_NAME`, `SERVER_HOSTNAME`, `ARTEMIS_EMAIL_TEST`, the operator
                  names, the certificate paths): admin-owned inputs describing the target environment.
                - **Deployment-internal secrets**: both ends live inside this deployment, so self-generated random
                  values are fully functional. Generate them once:

                  ```bash
                  export ARTEMIS_DATABASE_PASSWORD=$(openssl rand -base64 48)
                  export ARTEMIS_INTERNAL_ADMIN_PASSWORD=$(openssl rand -base64 48)
                  export ARTEMIS_JHIPSTER_JWT=$(openssl rand -base64 64 | tr -d '\\n')
                  ```

                - **Integration secrets** (Iris, Athena, LTI, Sharing, Hyperion — when selected): these authenticate
                  against an external service and must come from that service's operator.

                **Keep the generated set stable.** Store the values once (for example as GitHub Actions secrets) and
                reuse them for every deploy of the same target: a database applies its credentials only on first
                initialization, so regenerating `ARTEMIS_DATABASE_PASSWORD` against an existing data volume locks
                Artemis out with an access-denied loop instead of rotating the password.

                Production note: a secret manager such as HashiCorp Vault stays compatible with this channel —
                resolve the managed secrets into the environment of the run (or replace the generated lookup
                expressions with your manager's lookup plugin). The package itself standardizes on plain environment
                variables and requires no Vault server.

                ## Before running

                1. Add your connection line to the empty target group in `inventory/hosts`, for example
                   `<host> ansible_user=<user> ansible_ssh_private_key_file=<key>`.
                2. Make sure the target host provides Docker, git, and the `acl` package — the collection installs none
                   of them, and POSIX ACLs are needed wherever Ansible hands a file to the unprivileged artemis user.
                3. Install the collections: `ansible-galaxy collection install -r requirements.yml`; the `ansible`
                   meta-package already ships the non-Artemis collections. Add `ansible-galaxy role install
                   geerlingguy.docker` if your target still needs Docker provisioned.
                4. Export the environment values (previous section).
                5. Run `./preflight.sh`. It fails fast on a missing or empty environment variable and on syntax
                   problems.

                ## Deploying

                ```bash
                ./preflight.sh
                ansible-playbook -i inventory/hosts playbook.yml
                ```

                The deployed Artemis version is set by `artemis_version` in
                `inventory/group_vars/artemistests_common_config.yml` (baseline: `develop`). Telemetry reporting is
                disabled by default (`artemis_telemetry_enabled: false`); review the generated values before pointing
                a deployment at any shared infrastructure.

                ## Lifecycle boundary

                Generation proved: the selection is valid, every feature is classified against the binding catalog,
                the values are generated, and environment values appear only as lookup expressions. It did **not**
                prove the inventory renders or boots — that is the preflight's and the admin's job. See
                `metadata/remote-readiness.json`.
                """.formatted(modelId, modelVersion, profileId, catalog.catalogVersion(), catalog.collectionPin());
    }

    /**
     * Builds the requirements file: the Artemis collection pinned to the curated commit plus the collections its
     * roles use, which a collection cannot declare as dependencies itself.
     *
     * @return requirements YAML text.
     */
    public String requirementsYml() {
        return """
                ---
                collections:
                  - name: https://github.com/JTNing/artemis-ansible-collection.git
                    type: git
                    version: %s
                  # Collections the artemis and legal roles use but cannot declare as collection dependencies.
                  - name: ansible.posix
                  - name: community.crypto
                  - name: community.general
                """.formatted(catalog.collectionPin());
    }

    /**
     * Builds the minimal Ansible configuration. Two settings are load-bearing and mirror the upstream values
     * repository: {@code hash_behaviour = merge}, because the inventory layering relies on merged dictionaries so
     * membership changes switch variants without value duplication, and {@code pipelining = True}, because the
     * collection escalates to the unprivileged artemis user and pipelined modules need no remote temporary files —
     * without it those tasks fail on targets that lack POSIX ACL support ({@code setfacl}).
     *
     * @return ansible.cfg text.
     */
    public String ansibleCfg() {
        return """
                [defaults]
                inventory = inventory/hosts
                hash_behaviour = merge
                display_skipped_hosts = false
                interpreter_python = auto_silent
                retry_files_enabled = False

                [ssh_connection]
                pipelining = True
                """;
    }

    /**
     * Builds the playbook, mirroring the upstream test-server playbook: the collection's artemis and legal roles
     * with full system setup.
     *
     * @return playbook YAML text.
     */
    public String playbookYml() {
        return """
                ---
                - name: Setup
                  hosts: %s

                  roles:
                    - role: ls1intum.artemis.artemis
                      tags: artemis
                      vars:
                        setup_system: true

                    - role: ls1intum.artemis.legal
                      tags: legal
                """.formatted(RemoteEnvironmentValues.RESERVED_GROUP);
    }

    /**
     * Builds the preflight script: fail fast when a referenced environment variable is unset or empty — the
     * collection's own variable checks pass an empty string, so this gate is the fail-closed guard of the environment
     * channel — then verify the pinned collection is installed and run the playbook syntax check. The script never
     * connects to a host and never applies changes.
     *
     * @param requiredEnvironmentVariables sorted environment-variable names the generated files reference.
     * @return preflight shell script text.
     */
    public String preflightScript(List<String> requiredEnvironmentVariables) {
        return """
                #!/usr/bin/env bash
                # Preflight for the generated remote-ansible package: the environment gate, static checks, and a
                # syntax check only. This script never connects to a host and never applies changes.
                set -euo pipefail
                cd "$(dirname "$0")"

                # Every environment variable the generated values reference; see metadata/env-references.json.
                required_environment_variables="
                %s
                "

                echo "Checking required environment variables..."
                missing=0
                for name in ${required_environment_variables}; do
                    if [ -z "${!name:-}" ]; then
                        echo "ERROR: required environment variable ${name} is not set or is empty." >&2
                        missing=1
                    fi
                done
                if [ "${missing}" -ne 0 ]; then
                    echo "ERROR: export the values listed above before running the playbook (see README.md)." >&2
                    exit 1
                fi

                echo "Checking the pinned collection is installed..."
                if ! ansible-galaxy collection list 2>/dev/null | grep -q "ls1intum.artemis"; then
                    echo "ERROR: the ls1intum.artemis collection is not installed. Run: ansible-galaxy collection install -r requirements.yml" >&2
                    exit 1
                fi

                echo "Running the playbook syntax check..."
                ansible-playbook --syntax-check -i inventory/hosts playbook.yml

                echo "Preflight passed. The package is consumable; deployment remains an admin action."
                """.formatted(String.join("\n", requiredEnvironmentVariables));
    }
}
