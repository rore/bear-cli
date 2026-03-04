# bear unblock

## Purpose

Clear `check` blocked marker after lock/bootstrap IO failures so deterministic check gates can be retried.

## Quick use

Canonical invocation:

```text
bear unblock --project <path>
```

Success looks like:
- `unblock: OK` and exit `0`

Main failure classes:
- usage/internal (`64/70`)
- IO/lock (`74`)

## Invocation forms

```text
bear unblock --project <path>
```

## Inputs and flags

- `--project <path>`: required project root.
- Marker target is `<project>/build/bear/check.blocked.marker`.
- Marker is advisory; `check` and `check --all` still run fresh gates.
- Missing or invalid args are usage errors.

## Output schema and ordering guarantees

- Success (marker removed or already absent): `unblock: OK` to `stdout`, exit `0`.
- Locked marker: deterministic diagnostics to `stderr`, then failure footer.
- Non-zero exits append failure footer as last three `stderr` lines.

## Exit codes emitted

- `0` success
- `64` usage failure
- `70` internal failure
- `74` IO failure (including `UNBLOCK_LOCKED`)

## Deterministic failure footer

Non-zero exits append:

- `CODE=<enum>`
- `PATH=<locator>`
- `REMEDIATION=<step>`

See [output-format.md](output-format.md).

## Remediation pointers

- [troubleshooting.md#unblock_locked](troubleshooting.md#unblock_locked)
- [troubleshooting.md#io_error](troubleshooting.md#io_error)
- [troubleshooting.md#usage_invalid_args](troubleshooting.md#usage_invalid_args)

## Related

- [commands-check.md](commands-check.md)
- [exit-codes.md](exit-codes.md)
- [output-format.md](output-format.md)
- [troubleshooting.md](troubleshooting.md)

