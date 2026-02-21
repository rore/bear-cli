# Exit Codes

## Registry

- `0`: pass
- `2`: validation or semantic/config contract failure
- `3`: drift failure (`check`)
- `4`: project test failure or timeout (`check`)
- `5`: boundary expansion detected (`pr-check`)
- `6`: boundary policy failure in `check` (`UNDECLARED_REACH` or `BOUNDARY_BYPASS`)
- `64`: usage or argument failure
- `70`: internal or unexpected failure
- `74`: IO or git failure

## When codes appear

- `validate`: `0,2,64,70,74`
- `compile`: `0,2,64,70,74`
- `fix`: `0,2,64,70,74`
- `check`: `0,2,3,4,6,64,70,74`
- `pr-check`: `0,2,5,64,70,74`

## `--all` aggregation

`check --all` and `pr-check --all` aggregate by severity rank, not numeric max.

- `check --all` rank: `70 > 74 > 64 > 2 > 3 > 6 > 4 > 0`
- `pr-check --all` rank: `70 > 74 > 64 > 2 > 5 > 0`

Global non-zero footer in aggregated failures uses:

- `CODE=REPO_MULTI_BLOCK_FAILED`
- `PATH=bear.blocks.yaml`

## Related

- `output-format.md`
- `commands-check.md`
- `commands-pr-check.md`
- `troubleshooting.md`
- `CONTRACTS.md`
