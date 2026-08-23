# Remote-Ansible Golden Fixture Provenance

These fixtures are derived from the tracked hand-written inventory of the
`artemis-feature-model-deploy-lab` repository at commit
`2d0385ff089e304abb523a23daa524683413a741` ("minimal setup with lab values",
2026-08-15) **including its 2026-08-15 uncommitted working-tree deltas**
(`build_agent_git_credentials` in `artemistests_local_vc_ci.yml`; see the lab's
`evidence/incident-2026-08-15-build-agent-git-password.md` and transformation
table section 10). Every disposition behind these values was verified on the
live lab VM; see `evidence/transformation-table.md` in the lab repository.

Deliberate deltas versus the lab's tracked files, per the two defined delta
classes of the composer design:

1. Secret material: the lab keeps generated dummy values in an untracked
   `artemislocal/secrets.yml` and references them via indirection variables;
   the generated package emits Vault lookup expressions instead. This affects
   `artemislocal-secrets.yml` (package-own expectation) and exactly one line of
   `artemistests_local_vc_ci.yml` (`build_agent_git_credentials.password`:
   `{{ lab_build_agent_git_password }}` in the lab, a `hashi_vault` lookup here).
2. Package-only content with no lab counterpart is asserted by its own
   expectations, not by lab diff (`hosts` in this directory reflects the
   generated wiring: the host line comes from the environment input, and only
   wired group sections are emitted; the lab file additionally keeps empty
   `artemistests_postgres`/`artemistests_iris` sections and its concrete SSH
   connection line, which is environment-repository state).

Additionally, the two lab-only header comment lines of
`artemistests_common_config.yml` (referencing the lab's evidence documents) are
not part of the generated output and were stripped during derivation.

Updating these fixtures is a deliberate, reviewed act: re-derive from the lab
inventory and record the new lab commit here.
