package de.tum.cit.aet.artemis.featuremodel.export.service;

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
 * All content is deterministic for the same input: no timestamps, fixed ordering, and no secret values — secrets
 * appear exclusively as {@code lookup('hashi_vault', …)} expressions.
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
     * @param environment resolved environment values.
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
     * @param plan emission plan of the package.
     * @return README markdown text.
     */
    public String packageReadme(String modelId, String modelVersion, String profileId, RemoteAnsibleEmissionPlan plan) {
        long pendingInputs = plan.environmentStates().stream().filter(state -> RemoteAnsibleEmissionPlan.ENVIRONMENT_PENDING.equals(state.status())).count();
        String environmentNote = pendingInputs == 0
                ? "All environment values were provided at generation time."
                : pendingInputs + " environment value(s) are still `REPLACE_ME_*` placeholders; fill them in before running the preflight (it fails fast on placeholders).";
        return """
                # Artemis Remote Deployment Package (Ansible)

                Generated from feature model `%s` version `%s` and deployment context `%s`, with Ansible binding
                catalog v%s curated against collection commit `%s`.

                This package is **admin-consumable, not deployable**: it contains the complete values and
                orchestration for deploying the selected Artemis variant with the pinned
                `ls1intum.artemis` Ansible collection, but it holds no credentials and connects to nothing.
                Deployment remains a deliberate admin action on an execution environment that provides SSH access
                and secret material.

                ## Contents

                - `requirements.yml` — the Artemis collection pinned to the exact commit the values were curated against,
                  plus the collections its roles and the vault lookups need.
                - `ansible.cfg` — minimal run semantics; `hash_behaviour = merge` is required by the inventory layering.
                - `playbook.yml` — applies the collection's `artemis` and `legal` roles to the `artemistests` group.
                - `inventory/` — group membership wiring and generated values for the selected variant.
                - `preflight.sh` — static checks and `ansible-playbook --syntax-check`; never connects to a host.
                - `metadata/` — package manifest, layered readiness, every vault reference, and the selected features.

                ## Before running

                1. Fill in remaining values: %s
                2. Complete the host line in `inventory/hosts` with your connection details, for example
                   `<host> ansible_user=<user> ansible_ssh_private_key_file=<key>`.
                3. Make sure the target host provides Docker, git, and the `acl` package — the collection installs none
                   of them, and POSIX ACLs are needed wherever Ansible hands a file to the unprivileged artemis user.
                4. Install the collections: `ansible-galaxy collection install -r requirements.yml`, and the
                   `hvac` Python package for the vault lookup (`pip install hvac`); the `ansible` meta-package already
                   ships the non-Artemis collections. Add `ansible-galaxy role install geerlingguy.docker` if your
                   target still needs Docker provisioned.
                5. Provide secret material (next section).
                6. Run `./preflight.sh`. It fails fast on unresolved placeholders and syntax problems.

                ## Secrets

                No secret value — dummy or real — is stored in this package. Every secret is referenced as a
                `lookup('hashi_vault', …)` expression; `metadata/vault-references.json` lists each referenced path,
                field, and consuming variable. To use Vault, configure the `community.hashi_vault.hashi_vault` lookup
                environment (`ANSIBLE_HASHI_VAULT_ADDR`, `ANSIBLE_HASHI_VAULT_TOKEN` or your auth method) and create
                the listed secrets.

                Without Vault, replace the lookup expressions in the generated values files with values you manage
                yourself, following the ownership of each value:

                - **Deployment-internal secrets** (database password, internal admin password, JWT secret): both ends
                  live inside this deployment, so self-generated random values are fully functional (for example
                  `openssl rand -base64 48`; use `openssl rand -base64 64` for the JWT secret).
                - **Integration secrets** (Iris, Athena, LTI, Sharing, Hyperion): these authenticate against an
                  external service and must come from that service's operator.
                - **Identity values** (hostname, operator identity, certificate paths): admin-owned inputs, already
                  materialized in the generated values or left as placeholders.

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
                the values are generated, and secrets appear only as references. It did **not** prove the inventory
                renders or boots — that is the preflight's and the admin's job. See `metadata/remote-readiness.json`.
                """.formatted(modelId, modelVersion, profileId, catalog.catalogVersion(), catalog.collectionPin(), environmentNote);
    }

    /**
     * Builds the requirements file: the Artemis collection pinned to the curated commit plus the collections its
     * roles use, which a collection cannot declare as dependencies itself, and the vault lookup collection.
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
                  # Provides the hashi_vault lookup used for every secret reference (needs the hvac Python package).
                  - name: community.hashi_vault
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
     * Builds the preflight script: fail fast on unresolved placeholders, verify the pinned collection is installed,
     * and run the playbook syntax check. The script never connects to a host and never uses {@code --diff}.
     *
     * @return preflight shell script text.
     */
    public String preflightScript() {
        return """
                #!/usr/bin/env bash
                # Preflight for the generated remote-ansible package: static checks and a syntax check
                # only. This script never connects to a host and never applies changes.
                set -euo pipefail
                cd "$(dirname "$0")"

                echo "Checking for unresolved REPLACE_ME_ placeholders..."
                if grep -rn "REPLACE_ME_" inventory ansible.cfg playbook.yml requirements.yml; then
                    echo "ERROR: unresolved placeholders found. Fill in the values listed above (see README.md)." >&2
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
                """;
    }
}
