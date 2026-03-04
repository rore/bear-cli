# bear compile

## Purpose

Compile validated IR into deterministic BEAR-owned generated artifacts for one block or all indexed blocks.

## Invocation forms

```text
bear compile <ir-file> --project <path> [--index <path>]
bear compile --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]
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

Single-block:
- `<ir-file>`: required IR YAML path.
- `--project <path>`: required project root.
- `--index <path>`: optional override for index path.
- when `kind=block` effects are present, single-file compile resolves index path as: explicit `--index` if provided, else `<project>/bear.blocks.yaml`; then validates normalized `(ir, projectRoot)` tuple membership before generation.

All-mode:
- `--all`: enables index-managed multi-block compile.
- `--project <repoRoot>`: required repo root containing index/project roots.
- `--blocks <path>`: optional override for index path (`bear.blocks.yaml` by default).
- `--only <csv>`: optional block-name filter from index.
- `--fail-fast`: stop compiling additional blocks after first fail.
- `--strict-orphans`: enforce repo-wide orphan/legacy marker checks.

Identity and selection:
- all-mode uses the same index/block identity resolver as other `--all` commands.
- unknown `--only` names fail with deterministic usage error.

## Output schema and ordering guarantees

Single-block success:
- `compiled: OK` to `stdout`, exit `0`.

All-mode success:
- deterministic per-block sections followed by:
  - `SUMMARY`
  - total/checked/passed/failed/skipped
  - `FAIL_FAST_TRIGGERED`
  - `EXIT_CODE`

Failure:
- deterministic diagnostic lines to `stderr`, then failure footer.
- all-mode non-zero footer uses:
  - `CODE=REPO_MULTI_BLOCK_FAILED`
  - `PATH=bear.blocks.yaml`

Compile always preserves user-owned impl files and regenerates BEAR-owned artifacts deterministically.
Generated wiring manifests include containment roots:
- `blockRootSourceDir`
- `governedSourceRoots` (always includes block root and reserved root `src/main/java/blocks/_shared`)

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

See [output-format.md](output-format.md).

## Remediation pointers

- [troubleshooting.md#ir_validation](troubleshooting.md#ir_validation)
- [troubleshooting.md#block_port_index_required](troubleshooting.md#block_port_index_required)
- [troubleshooting.md#manifest_invalid](troubleshooting.md#manifest_invalid)
- [troubleshooting.md#usage_invalid_args](troubleshooting.md#usage_invalid_args)
- [troubleshooting.md#io_error](troubleshooting.md#io_error)

## Related

- [MODEL.md](MODEL.md)
- [commands-check.md](commands-check.md)
- [commands-fix.md](commands-fix.md)
- [output-format.md](output-format.md)
- [troubleshooting.md](troubleshooting.md)


