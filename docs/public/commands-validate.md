# bear validate

## Purpose

Validate one IR file, enforce schema and semantic rules, and print canonical normalized YAML on success.

## Quick use

Canonical invocation:

```text
bear validate <ir-file>
```

Success looks like:
- canonical normalized YAML on `stdout` and exit `0`

Main failure classes:
- validation (`exit 2`)
- usage/internal/IO (`64/70/74`)

## Invocation forms

```text
bear validate <ir-file>
```

## Inputs and flags

- `<ir-file>`: required path to a single IR YAML file.
- Extra or missing args are usage errors.
- validate checks IR schema/semantic consistency for the file itself.
- `block.kind` remains `logic`; cross-block semantics are declared by `port.kind=block` entries and resolved in index-aware command paths.
- cross-block graph resolution (for `kind=block` target lookup and cycle checks) is enforced in index-aware compile/check/pr-check paths.

## Output schema and ordering guarantees

- Success: canonical normalized YAML to `stdout`, exit `0`.
- Validation failure: deterministic validation line to `stderr`, then failure footer.
- Footer is always the last three `stderr` lines on non-zero exits.

## Exit codes emitted

- `0` success
- `2` validation failure
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
- [troubleshooting.md#usage_invalid_args](troubleshooting.md#usage_invalid_args)
- [troubleshooting.md#io_error](troubleshooting.md#io_error)

## Related

- [commands-compile.md](commands-compile.md)
- [exit-codes.md](exit-codes.md)
- [output-format.md](output-format.md)
- [troubleshooting.md](troubleshooting.md)
