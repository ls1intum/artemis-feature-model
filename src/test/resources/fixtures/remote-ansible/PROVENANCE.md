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

Deliberate deltas versus the lab's tracked files, per the two defined delta
classes of the composer design:

1. Secret material: the lab keeps generated dummy values in an untracked
   `artemislocal/secrets.yml` and references them via indirection variables;
   the generated package emits Vault lookup expressions instead. This affects
   `artemislocal-secrets.yml` (package-own expectation). The lab's
   `artemistests_local_vc_ci.yml` additionally retains the removed
   `build_agent_git_credentials` block as commented-out incident history;
   the generated file omits it entirely.
2. Package-only content with no lab counterpart is asserted by its own
   expectations, not by lab diff (`hosts` in this directory reflects the
   generated wiring: the host line comes from the environment input, and only
   wired group sections are emitted; the lab file additionally keeps empty
   `artemistests_postgres`/`artemistests_iris` sections and its concrete SSH
   connection line, which is environment-repository state).

Additionally, the two lab-only header comment lines of
`artemistests_common_config.yml` (referencing the lab's evidence documents) are
not part of the generated output and were stripped during derivation.

The `artemistests_without_exam.yml` and `artemistests_without_tutorialgroup.yml`
fixtures are the lab's tracked module-toggle group files (added with the
collection-fork validation evidence, state as of lab commit `2040df8`): the
hand-written values of the reduced variant validated on the lab VM against the
fork's `artemis_modules` off-switches
(`evidence/collection-fork-validation-2026-08-25.md`).

Updating these fixtures is a deliberate, reviewed act: re-derive from the lab
inventory and record the new lab commit here.
