# bear pr-check

## Purpose

Run deterministic PR governance checks:
- classify normalized IR deltas versus merge-base (boundary expansion signaling)
- enforce generated-port implementation containment boundaries

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
- Port-impl containment lines (when violated):
  - `pr-check: BOUNDARY_BYPASS: RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: <relative/path>: KIND=PORT_IMPL_EXTERNAL_BINDING: <interfaceFqcn> <- <implClassFqcn>`
- Boundary verdict:
  - `stderr`: `pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED`
  - `stdout`: `pr-check: OK: NO_BOUNDARY_EXPANSION`
- Deterministic sort precedence for class, category, change, key.
- Port-impl containment findings are deterministically sorted by interface FQCN, impl class FQCN, and path.
- Non-zero exits append failure footer as last three `stderr` lines.

Implementation note:
- `pr-check` acquires wiring manifests using deterministic temp staging + wiring-only generation.
- It does not require full compile output to be present in project working tree.

## Exit codes emitted

- `0` no boundary-expanding deltas
- `5` boundary-expanding deltas found
- `6` boundary bypass (`CODE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT`)
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
- [troubleshooting.md#port_impl_outside_governed_root](troubleshooting.md#port_impl_outside_governed_root)
- [troubleshooting.md#io_git](troubleshooting.md#io_git)
- [troubleshooting.md#ir_validation](troubleshooting.md#ir_validation)

## Related

- [MODEL.md](MODEL.md)
- [commands-check.md](commands-check.md)
- [exit-codes.md](exit-codes.md)
- [output-format.md](output-format.md)
- [troubleshooting.md](troubleshooting.md)

