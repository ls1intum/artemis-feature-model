# Remote-Ansible Golden Fixture Provenance

These files are the expected output of the remote-ansible deployment package for the two requests in
`RemoteAnsibleGoldenFixtureTest`: the full PostgreSQL selection with the target name `artemis-local`, and the same
selection without `exam` and `tutorialgroup`.

They are derived from a hand-written single-node inventory for the Ansible collection at the pinned commit, which
was deployed successfully to a test server. That reference inventory is not part of this repository. Because the
package expresses every host, identity, and secret value as an environment lookup, the fixtures fall into two
classes.

## Structural files

Files that carry no environment value match the reference inventory:

- `artemistests_postgres.yml` byte for byte, including the explicit port 5432.
- `artemistests_local_vc_ci.yml`. It configures no shared `build_agent_git_credentials` pair, because Artemis
  refuses that pair on a node that runs local CI (`LocalVCBuildAgentCredentialsValidator`); build agents
  authenticate per job instead.
- `artemistests_without_exam.yml` and `artemistests_without_tutorialgroup.yml` line for line; the generated files
  end without a trailing newline. They switch modules off through the collection's `artemis_modules` variable, as
  validated on the test server.
- The group wiring (`:children` sections) of `hosts`.

## Package-own expectations

Files that carry environment values are rendered from the binding catalog, so they cannot match hand-written
values:

- `artemislocal-main.yml` and `artemistests_common_config.yml` express identity values as
  `lookup('ansible.builtin.env', …)` expressions.
- `artemislocal-secrets.yml` expresses every secret as an environment lookup.
- `hosts` leaves the target-group section empty: a host entry cannot be a lookup, so the execution environment owns
  the connection line. It also wires `artemistests_without_atlas`, which the reference inventory does not need:
  Atlas is enabled by default in the Artemis image, so a selection without Atlas must switch it off explicitly.

## Collection pin

The fixtures match the collection pin of the binding catalog. The pinned collection deploys PostgreSQL only, so
there is no MySQL fixture.

## Updating the fixtures

Updating a fixture is a deliberate, reviewed act. Re-derive the structural files from a reference inventory that
was deployed successfully against the new collection pin, and re-derive the package-own expectations from the
binding catalog. Record in this file what changed and why.
