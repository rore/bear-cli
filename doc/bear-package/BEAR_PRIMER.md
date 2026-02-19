# BEAR Primer (Package)

Purpose:
- Give a first-time isolated agent enough BEAR context to start correctly.

## What BEAR Is

BEAR is a deterministic boundary-governance workflow for backend logic blocks.
You declare block structure and allowed external interactions in IR, generate deterministic code/tests, implement only user-owned logic, and use `bear check` as the gate.

## Core Terms

- Block:
  - a unit of backend logic with explicit contract and allowed effects.
- IR:
  - BEAR YAML declaration (`spec/*.bear.yaml`) for a block.
- Contract:
  - input/output fields for the block API.
- Effects:
  - declared capability ports and operations the block is allowed to call.
- Idempotency:
  - replay-safe behavior keyed by a request field.
- Invariant:
  - declared rule that must hold for output (v0 includes `non_negative`).

## IR-First Rule (Practical)

Update IR before implementation whenever a feature changes:
- external reach/capability
- contract inputs/outputs
- idempotency/invariant declarations

If no IR exists yet (greenfield), create the first IR file before expecting the gate to pass.

## Generated vs Editable

Do not edit:
- `build/generated/bear/**`

Edit:
- user implementation under `src/main/java/**/<BlockName>Impl.java`
- tests under `src/test/java/**`
- IR/spec/workflow docs/scripts in repo-owned paths

## Tiny IR Example Fragment

```yaml
version: v0
block:
  name: Withdraw
  kind: logic
  contract:
    inputs:
      - name: accountId
        type: string
```
