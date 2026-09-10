# Remote-Ansible Golden Fixture Provenance

These fixtures are derived from the tracked hand-written inventory of the
`artemis-feature-model-deploy-lab` repository at commit
`2040df8` ("Close out module-toggle validation: pin the fork patch and drop
the refused build-agent credentials", 2026-08-27). That commit carries the
2026-08-26 removal of the `build_agent_git_credentials` block from
`artemistests_local_vc_ci.yml`: Artemis develop reversed the build-agent
credential contract on 2026-08-25 (`LocalVCBuildAgentCredentialsValidator`
refuses the shared pair on a node running local CI), so the previously
required block became a startup refusal. See the lab's
`evidence/incident-2026-08-26-build-agent-credentials-refused.md` (and its
mirror `evidence/incident-2026-08-15-build-agent-git-password.md` for the
retired contract these fixtures used to bake in). Every disposition behind
these values was verified on the live lab VM; see
`evidence/transformation-table.md` in the lab repository.

## Environment-channel evolution (2026-08-30)

The package's environment channel moved from baked values and Vault lookup
expressions to `lookup('ansible.builtin.env', …)` expressions over the
user-provisioned environment-variable names of
`devdocs/plan/deployment/ansible-remote/gitops/ansible-package-github-secrets-mapping.txt`
(binding catalog v2). This split the fixtures into two classes:

1. **Package-own expectations** — files that carried baked environment
   values or Vault expressions can no longer match the lab's hand-written
   values byte-for-byte; the lab files remain historical evidence of the
   values that booted the lab VM. Affected: `artemislocal-main.yml` and
   `artemistests_common_config.yml` (identity lines are now env lookups),
   `artemislocal-secrets.yml` (env lookups instead of Vault lookups), and
   `hosts` (the target-group section is now empty — a host entry cannot be
   a lookup, so the connection line is owned by the execution environment;
   the lab file keeps its concrete SSH connection line plus its inert empty
   `artemistests_postgres`/`artemistests_iris` sections).
2. **Structural files** — files that never carried an environment value
   are unchanged by the switch and keep matching the lab's tracked files at
   commit `2040df8`: `artemistests_mysql.yml` and
   `artemistests_postgres.yml` byte-for-byte,
   `artemistests_local_vc_ci.yml` except the lab's commented-out
   `build_agent_git_credentials` incident-history block (a pre-existing,
   documented delta), `artemistests_without_exam.yml` and
   `artemistests_without_tutorialgroup.yml` line-for-line (the generated
   files end without a trailing newline), and the group wiring
   (`:children` sections) of `hosts`.

Additionally, the two lab-only header comment lines of
`artemistests_common_config.yml` (referencing the lab's evidence documents) are
not part of the generated output and were stripped during derivation.

The `artemistests_without_exam.yml` and `artemistests_without_tutorialgroup.yml`
fixtures are the lab's tracked module-toggle group files (added with the
collection-fork validation evidence, state as of lab commit `2040df8`): the
hand-written values of the reduced variant validated on the lab VM against the
fork's `artemis_modules` off-switches
(`evidence/collection-fork-validation-2026-08-25.md`).

The `artemistests_without_atlas` section of `hosts` has no lab counterpart:
`artemis.atlas.enabled` defaults to true inside the Artemis image, so a
selection without atlas must emit an explicit off-switch, while the
hand-written lab inventory (which predates the selection semantics) simply
runs with the image default. The group's values file is asserted by its own
package expectation, not by lab diff.

Updating these fixtures is a deliberate, reviewed act: re-derive the
structural class from the lab inventory and record the new lab commit here;
re-derive the package-own class from the binding catalog and the mapping
file.
