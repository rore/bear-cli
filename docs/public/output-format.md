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
- wiring examples (canonical path form):
  - `drift: CHANGED: build/generated/bear/wiring/<block>.wiring.json`
  - `drift: MISSING_BASELINE: build/generated/bear/wiring/<block>.wiring.json`

`pr-check` delta lines:

- `pr-delta: <CLASS>: <CATEGORY>: <CHANGE>: <KEY>`

`pr-check` boundary-bypass lines:

- `pr-check: BOUNDARY_BYPASS: RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: <relative/path>: KIND=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: <interfaceFqcn> -> <implClassFqcn>`
- `pr-check: BOUNDARY_BYPASS: RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: <relative/path>: KIND=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: <implClassFqcn> -> <sortedGeneratedPackageCsv>`
- `pr-check: BOUNDARY_BYPASS: RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: <relative/path>: KIND=MARKER_MISUSED_OUTSIDE_SHARED: <implClassFqcn>`

`pr-check` governance signal lines (informational):

- `pr-check: GOVERNANCE: MULTI_BLOCK_PORT_IMPL_ALLOWED: <relative/path>: <implClassFqcn> -> <sortedGeneratedPackageCsv>`

Common `check` policy lines:

- `check: HYGIENE_UNEXPECTED_PATHS: <relative/path>`
- `check: UNDECLARED_REACH: <relative/path>: <surface>`
- `check: BOUNDARY_BYPASS: RULE=<rule>: <relative/path>: <detail>`
- `check: INFO: CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION: projectRoot=<root>: reason=no_selected_blocks_with_impl_allowedDeps`

## Ordering guarantees

`check` single mode order:

1. baseline manifest diagnostics
2. boundary signal lines
3. drift lines
4. containment lines (including informational containment-skip line when applicable)
5. strict-hygiene lines (if enabled)
6. undeclared-reach lines
7. boundary-bypass lines
8. test failure or timeout output
9. failure footer

`pr-check` delta lines are deterministically sorted by class, category, change, and key.
`pr-check` port-impl containment findings are deterministically sorted by `path`, then `rule`, then `detail`.
`pr-check` governance signal lines are deterministically sorted by `path`, then `implClassFqcn`, then `sortedGeneratedPackageCsv`.

Wiring drift diagnostics:
- one line per `(reason, path)` for wiring files (no duplicate path variants).
- wiring-detail reason rank is frozen as:
  1. `MISSING_BASELINE`
  2. `REMOVED`
  3. `CHANGED`
  4. `ADDED`

`check --all` block section note:
- for `PASS` blocks, `DETAIL:` is emitted only when non-blank contextual detail is present.

## Related

- [exit-codes.md](exit-codes.md)
- [commands-check.md](commands-check.md)
- [commands-unblock.md](commands-unblock.md)
- [commands-pr-check.md](commands-pr-check.md)
- [commands-validate.md](commands-validate.md)
- [troubleshooting.md](troubleshooting.md)

