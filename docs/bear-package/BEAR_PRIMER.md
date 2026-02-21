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
  - replay-safe wrapper behavior keyed by declared input material (`key` or `keyFromInputs`).
- Invariant:
  - structural output rule (`non_negative`, `non_empty`, `equals`, `one_of`).
  - v1 supports `non_negative` (and v1.2 extends this structural set with `non_empty`, `equals`, `one_of`).

## Source of Truth for IR

- Use `.bear/agent/doc/IR_QUICKREF.md` for schema/capability rules.
- Use `.bear/agent/doc/IR_EXAMPLES.md` for minimal valid examples.
- Do not infer IR by reverse engineering CLI binaries.

## Semantics Decision Rule (v1.2)

BEAR enforces a semantic only when it is:
- wrapper-enforceable from declared inputs/outputs/ports
- free of hidden context unless explicitly declared
- deterministic and target-implementable
- defined by a frozen, testable contract

Why idempotency is included:
- key material, store port, side-effect boundary, and replay payload are declared in IR.

Why invariants are limited:
- they are output-level structural checks that are deterministic and boundary-checkable.

Out of scope:
- business-policy inference
- undeclared context semantics
- cross-port transaction atomicity

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
