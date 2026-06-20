## Version Control Guidelines

- Use `feature/...` branches for implementation phases.
- Keep commits small and reviewable.
- Use concise imperative commit messages, scoped where useful.
- Commit Gradle wrapper files and npm lock files.
- Do not hand-edit `package-lock.json` unless resolving a targeted lockfile issue.
- Do not commit generated build outputs, `node_modules`, `.angular`, or local IDE files.
- Do not revert user changes unless the user explicitly asks.

Recommended commit message examples:

```text
chore: add server feature model store
test: cover mandatory feature validation
docs: add phase 3 server api plan
```