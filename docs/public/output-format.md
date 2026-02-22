# Output Format

## Non-zero failure footer

Every non-zero exit in `validate`, `compile`, `fix`, `check`, `unblock`, and `pr-check` must end with:

```text
CODE=<enum>
PATH=<locator>
REMEDIATION=<deterministic-step>
```

Contract:

- emitted exactly once
- last three `stderr` lines
- no `stderr` output after `REMEDIATION=...`

## `PATH` locator rules

- Locator may be repo-relative path or stable pseudo-path token.
- Absolute filesystem paths are not allowed.
- Path separators are normalized (`/`).

## Delta and diagnostic line formats

`check` drift lines:

- `drift: ADDED: <relative/path>`
- `drift: REMOVED: <relative/path>`
- `drift: CHANGED: <relative/path>`
- `drift: MISSING_BASELINE: build/generated/bear (...)`

`pr-check` delta lines:

- `pr-delta: <CLASS>: <CATEGORY>: <CHANGE>: <KEY>`

Common `check` policy lines:

- `check: UNDECLARED_REACH: <relative/path>: <surface>`
- `check: BOUNDARY_BYPASS: RULE=<rule>: <relative/path>: <detail>`

## Ordering guarantees

`check` single mode order:

1. baseline manifest diagnostics
2. boundary signal lines
3. drift lines
4. containment lines
5. undeclared-reach lines
6. boundary-bypass lines
7. test failure or timeout output
8. failure footer

`pr-check` delta lines are deterministically sorted by class, category, change, and key.

## Related

- [exit-codes.md](exit-codes.md)
- [commands-check.md](commands-check.md)
- [commands-unblock.md](commands-unblock.md)
- [commands-pr-check.md](commands-pr-check.md)
- [commands-validate.md](commands-validate.md)
- [troubleshooting.md](troubleshooting.md)

