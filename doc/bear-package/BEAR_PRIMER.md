# BEAR Primer

Purpose:
- Give a first-time isolated agent enough context to work correctly with BEAR in any backend project.

## What BEAR Is

BEAR is deterministic boundary governance for backend logic blocks.
You declare structure in IR, generate deterministic artifacts, implement only user-owned logic, and gate with BEAR commands.

## Core Concepts

- Block:
  - one backend logic unit with explicit contract and allowed effects.
- IR:
  - BEAR YAML (`spec/*.bear.yaml`) declaring block boundary.
- Contract:
  - input/output fields for the block API.
- Effects:
  - declared capability ports and allowed ops.
- Idempotency:
  - replay-safe behavior keyed by an input field.
- Invariant:
  - structural output rule (v1 supports `non_negative`).

## Source of Truth for IR

- Use `doc/IR_QUICKREF.md` for schema/capability rules.
- Use `doc/IR_EXAMPLES.md` for minimal valid examples.
- Do not infer IR by reverse engineering CLI binaries.

## IR-First Rule

Update IR before implementation when a feature changes:
- contract inputs/outputs
- effect ports/ops
- idempotency wiring
- invariants

If no IR exists (greenfield), create initial IR first.

## Generated vs Editable

Do not edit:
- `build/generated/bear/**`

Edit:
- `src/main/java/**/<BlockName>Impl.java`
- `src/test/java/**`
- repo-owned IR/docs/scripts
