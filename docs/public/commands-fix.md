# bear fix

## Purpose

Repair deterministic BEAR-owned generated artifacts from IR without mutating user-owned implementation files.

## Quick use

Canonical invocation:

```text
bear fix <ir-file> --project <path>
```

Success looks like:
- `fix: OK` and exit `0`

Main failure classes:
- validation/config (`exit 2`)
- usage/internal/IO (`64/70/74`)

## Invocation forms

```text
bear fix <ir-file> --project <path> [--index <path>]
bear fix --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]
```

## Missing index envelope (`--all`)

If `bear.blocks.yaml` is missing, all `--all` commands (`compile`, `check`, `fix`, `pr-check`) fail with the same validation envelope on `stderr` and exit `2`:

```text
index: VALIDATION_ERROR: INDEX_REQUIRED_MISSING: bear.blocks.yaml: project=.
CODE=INDEX_REQUIRED_MISSING
PATH=bear.blocks.yaml
REMEDIATION=Create bear.blocks.yaml or run non---all command
```

## Inputs and flags

Single mode:
- `<ir-file>` and `--project` are required.
- `--index <path>` is an optional override for index path.
- for `kind=block`, single-file fix resolves index path as: explicit `--index` if provided, else `<project>/bear.blocks.yaml`; then validates normalized `(ir, projectRoot)` tuple membership before generation/repair.

All-mode:
- `--all` runs index-driven orchestration.
- optional flags: `--blocks`, `--only`, `--fail-fast`, `--strict-orphans`.

## Output schema and ordering guarantees

- Single mode success: `fix: OK` to `stdout`, exit `0`.
- `--all` mode: deterministic block sections and summary.
- Non-zero exits append deterministic failure footer.

## Exit codes emitted

- `0` success
- `2` validation/config failure
- `64` usage failure
- `70` internal failure
- `74` IO failure

## Deterministic failure footer

Non-zero exits append:

- `CODE=<enum>`
- `PATH=<locator>`
- `REMEDIATION=<step>`

For aggregated `--all` non-zero failures, footer code is `REPO_MULTI_BLOCK_FAILED`.

## Remediation pointers

- [troubleshooting.md#ir_validation](troubleshooting.md#ir_validation)
- [troubleshooting.md#block_port_index_required](troubleshooting.md#block_port_index_required)
- [troubleshooting.md#usage_invalid_args](troubleshooting.md#usage_invalid_args)
- [troubleshooting.md#io_error](troubleshooting.md#io_error)

## Related

- [commands-compile.md](commands-compile.md)
- [commands-check.md](commands-check.md)
- [exit-codes.md](exit-codes.md)
- [output-format.md](output-format.md)
- [troubleshooting.md](troubleshooting.md)



