# Exit Codes

## Registry

- `0`: pass
- `2`: validation or semantic/config contract failure
- `3`: drift failure (`check`)
- `4`: project test failure or timeout (`check`)
- `5`: boundary expansion detected (`pr-check`)
- `6`: boundary policy failure (`check` and `pr-check`)
  - `check`: `UNDECLARED_REACH`, `BOUNDARY_BYPASS`, `HYGIENE_UNEXPECTED_PATHS`
  - `pr-check`: `PORT_IMPL_OUTSIDE_GOVERNED_ROOT`
- `64`: usage or argument failure
- `70`: internal or unexpected failure
- `74`: IO or git failure

## When codes appear

- `validate`: `0,2,64,70,74`
- `compile`: `0,2,64,70,74`
- `fix`: `0,2,64,70,74`
- `check`: `0,2,3,4,6,64,70,74`
- `unblock`: `0,64,70,74`
- `pr-check`: `0,2,5,6,64,70,74`

## `--all` aggregation

`check --all`, `compile --all`, `fix --all`, and `pr-check --all` aggregate by severity rank, not numeric max.
This severity ranking is part of the frozen Preview contract.

- `check --all` rank: `70 > 74 > 64 > 2 > 3 > 6 > 4 > 0`
- `compile --all` rank: `70 > 74 > 64 > 2 > 0`
- `fix --all` rank: `70 > 74 > 64 > 2 > 0`
- `pr-check --all` rank: `70 > 74 > 64 > 2 > 5 > 6 > 0`

Global non-zero footer in aggregated failures uses:

- `CODE=REPO_MULTI_BLOCK_FAILED`
- `PATH=bear.blocks.yaml`

## Exit `2` bucket

`exit=2` is the configuration/schema failure bucket. It is not IR-only.

Common code values in this bucket:
- `IR_VALIDATION`
- `MANIFEST_INVALID`
- `POLICY_INVALID`

Notable mappings:
- `exit 4` includes `INVARIANT_VIOLATION`

## Related

- [output-format.md](output-format.md)
- [commands-check.md](commands-check.md)
- [commands-unblock.md](commands-unblock.md)
- [commands-pr-check.md](commands-pr-check.md)
- [troubleshooting.md](troubleshooting.md)
- [CONTRACTS.md](CONTRACTS.md)
