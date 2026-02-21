# bear compile

## Purpose

Compile one validated IR into deterministic BEAR-owned generated artifacts for a target project.

## Invocation forms

```text
bear compile <ir-file> --project <path>
```

## Inputs and flags

- `<ir-file>`: required IR YAML path.
- `--project <path>`: required project root.
- Args must match exact command form.

## Output schema and ordering guarantees

- Success: `compiled: OK` to `stdout`, exit `0`.
- Non-zero: deterministic diagnostic lines to `stderr`, then failure footer.
- Compile preserves user-owned impl files and regenerates BEAR-owned artifacts deterministically.

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
- [troubleshooting.md#usage_invalid_args](troubleshooting.md#usage_invalid_args)
- [troubleshooting.md#io_error](troubleshooting.md#io_error)

## Related

- [MODEL.md](MODEL.md)
- [commands-check.md](commands-check.md)
- [commands-fix.md](commands-fix.md)
- [output-format.md](output-format.md)
- [troubleshooting.md](troubleshooting.md)

