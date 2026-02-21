# bear fix

## Purpose

Repair deterministic BEAR-owned generated artifacts from IR without mutating user-owned implementation files.

## Invocation forms

```text
bear fix <ir-file> --project <path>
bear fix --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]
```

## Inputs and flags

- Single mode: `<ir-file>` and `--project`.
- `--all` mode: index-driven orchestration.
- Optional flags: `--blocks`, `--only`, `--fail-fast`, `--strict-orphans`.

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
- [troubleshooting.md#usage_invalid_args](troubleshooting.md#usage_invalid_args)
- [troubleshooting.md#io_error](troubleshooting.md#io_error)

## Related

- [commands-compile.md](commands-compile.md)
- [commands-check.md](commands-check.md)
- [exit-codes.md](exit-codes.md)
- [output-format.md](output-format.md)
- [troubleshooting.md](troubleshooting.md)

