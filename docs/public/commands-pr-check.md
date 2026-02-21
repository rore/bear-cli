# bear pr-check

## Purpose

Classify normalized IR deltas versus merge-base for PR governance and boundary-expansion signaling.

## Invocation forms

```text
bear pr-check <ir-file> --project <path> --base <ref>
bear pr-check --all --project <repoRoot> --base <ref> [--blocks <path>] [--only <csv>] [--strict-orphans]
```

## Inputs and flags

- Single mode requires `<ir-file>`, `--project`, and `--base`.
- `<ir-file>` must be repo-relative.
- `--all` mode uses index selection and optional `--blocks`, `--only`, `--strict-orphans`.

## Output schema and ordering guarantees

- Delta lines to `stderr`:
  - `pr-delta: <CLASS>: <CATEGORY>: <CHANGE>: <KEY>`
- Boundary verdict:
  - `stderr`: `pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED`
  - `stdout`: `pr-check: OK: NO_BOUNDARY_EXPANSION`
- Deterministic sort precedence for class, category, change, key.
- Non-zero exits append failure footer as last three `stderr` lines.

## Exit codes emitted

- `0` no boundary-expanding deltas
- `5` boundary-expanding deltas found
- `2` validation failure
- `64` usage failure
- `70` internal failure
- `74` IO or git failure

## Deterministic failure footer

Non-zero exits append:

- `CODE=<enum>`
- `PATH=<locator>`
- `REMEDIATION=<step>`

For aggregated `--all` non-zero failures, footer code is `REPO_MULTI_BLOCK_FAILED`.

## Remediation pointers

- [troubleshooting.md#boundary_expansion](troubleshooting.md#boundary_expansion)
- [troubleshooting.md#io_git](troubleshooting.md#io_git)
- [troubleshooting.md#ir_validation](troubleshooting.md#ir_validation)

## Related

- [MODEL.md](MODEL.md)
- [commands-check.md](commands-check.md)
- [exit-codes.md](exit-codes.md)
- [output-format.md](output-format.md)
- [troubleshooting.md](troubleshooting.md)

