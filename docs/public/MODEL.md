# Model

## Vocabulary

- `block`: one governed unit of logic.
- `IR`: YAML declaration of block contract, effects, and selected semantics.
- `compile`: deterministic generation of BEAR-owned artifacts.
- `check`: deterministic local gate for drift, boundary rules, and tests.
- `pr-check`: deterministic base-vs-head governance classification.
- `determinism`: same inputs produce the same output lines, files, and exit behavior.

## Mental model

1. Declare block boundaries and contract in IR.
2. Compile to deterministic generated artifacts.
3. Enforce sync and policy with `check`.
4. Use `pr-check` to classify PR boundary changes.

Core flow:

`IR -> compile -> check`

Governance flow:

`pr-check --base <ref>`

## Related

- `QUICKSTART.md`
- `CONTRACTS.md`
- `commands-compile.md`
- `commands-check.md`
- `commands-pr-check.md`
