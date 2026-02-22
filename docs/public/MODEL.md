# Model

Role of this page: operating model and vocabulary only.

For rationale see [FOUNDATIONS.md](FOUNDATIONS.md). For guarantees/signals see [ENFORCEMENT.md](ENFORCEMENT.md).

## Vocabulary

- `block`: one governed unit of logic.
- `IR`: YAML declaration of block contract, effects, and selected semantics.
- `compile`: deterministic generation of BEAR-owned artifacts.
- `check`: deterministic local gate for drift, boundary rules, and tests.
- `pr-check`: deterministic base-vs-head governance classification.
- `unblock`: clears `check` blocked marker after lock/bootstrap IO failures.
- `determinism`: same inputs produce the same output lines, files, and exit behavior.
- `agent loop`: BEAR command sequence executed by the agent while implementing specs.
- `developer visibility`: PR/CI signals BEAR emits for review and governance.

## Agent execution model

1. Agent updates block boundaries and contract in IR from project specs.
2. Agent runs `compile` (or `fix`) to materialize deterministic generated artifacts.
3. Agent runs `check` to enforce sync and policy.
4. Agent runs `pr-check` to classify boundary changes against base.

Core flow: `IR -> compile -> check`

Governance flow: `pr-check --base <ref>`

## Developer visibility model

- PR signal: `pr-check` marks boundary-expanding vs ordinary changes.
- CI signal: deterministic exit codes and output shape support stable gates.
- Local signal: deterministic ordering and paths make failures fast to triage.

## Related

- [INDEX.md](INDEX.md)
- [ENFORCEMENT.md](ENFORCEMENT.md)
- [commands-check.md](commands-check.md)
- [commands-unblock.md](commands-unblock.md)
- [commands-pr-check.md](commands-pr-check.md)
