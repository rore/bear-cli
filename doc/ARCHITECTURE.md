# BEAR v0 Architecture

## Core Purpose
BEAR is a boundary-governance enforcement layer for agentic development.

It exists because:
- Agent-generated code is non-deterministic.
- Boundary expansion can happen silently.
- Production systems need deterministic, independent gates.

BEAR uses a rigid, machine-checkable intermediate representation (BEAR IR) to:
- constrain allowed block interaction surfaces
- compile deterministic structural boundaries and tests
- detect drift and surface governance-relevant diffs

BEAR is not a full verifier.
BEAR does not simulate infrastructure behavior.
BEAR is not a behavior DSL.
BEAR is not primarily a spec-refinement tool.

## Core Principles
1. Deterministic core
   - Validation, normalization, and code generation are deterministic and reproducible.
2. Agent-agnostic
   - BEAR works with Copilot, Codex, or no agent at all.
   - Agent instructions stay outside BEAR core.
3. Cage, not code
   - BEAR generates boundaries (ports), skeletons, and test gates.
   - Business logic lives in a separate implementation file.
4. Minimal enforceable semantics
   - v0 enforces only a narrow, mechanically testable surface.
5. Boundary visibility over silent expansion
   - Ordinary IR evolution is expected.
   - Boundary-expanding changes must be explicit and reviewable.
   - Governance classification is defined in `doc/GOVERNANCE.md`.

## What BEAR Guarantees in v0
If a block passes `bear check`, then:
- it respects declared input/output contract structure
- it cannot call undeclared capability operations, via generated structured port interfaces
- it satisfies declared invariant templates under generated tests
- it respects declared idempotency semantics under generated tests
- generated artifacts are unchanged from deterministic regeneration (drift detection)
- boundary-governance signals are deterministic for covered v0 expansion cases

## What BEAR Does Not Guarantee in v0
- business correctness beyond declared invariants
- real database semantics
- concurrency correctness
- transaction semantics
- runtime enforcement beyond test harness
- general behavioral verification
- full static isolation of arbitrary implementation calls outside generated ports

Important caveat:
- v0 structural boundaries are strong at the generated surface.
- v0 is not yet full static enforcement against every possible call pattern inside user impl code.

BEAR v0 is structural enforcement plus deterministic guardrails.
Idempotency in v0 means deterministic replay safety in the test harness, not concurrency-safe duplicate handling.

## Repository Structure (bear-cli)
This repo is a Gradle multi-module project.

- `kernel/`
  - trusted seed
  - BEAR IR parsing, strict validation, deterministic normalization, target abstractions
  - must not depend on generated code
- `app/`
  - CLI wrapper exposing `bear validate`, `bear compile`, `bear check`
  - depends on `kernel`
  - contains no LLM logic

## BEAR IR v0 Scope
A BEAR IR file defines a single logic block.

Model:
- `version` with only `v0` allowed
- `block.name`
- `block.kind` with only `logic` allowed in v0
- `block.contract.inputs` and `block.contract.outputs`
- `block.effects.allow` as structured ports (`port` + `ops[]`), not free strings
- `block.idempotency` with key referencing an input field and explicit `store.port/getOp/putOp`
- `block.invariants` with only `kind: non_negative` plus `field: <outputField>`

IR must be:
- strictly validated
- deterministically normalized
- rejected on unknown keys or invalid references
- intentionally limited in expressive power

Reference: `doc/IR_SPEC.md`.

## v0 Scope Lock
In scope:
- JVM (Java) target only
- single logic block per IR file
- structured ports for effects

Out of scope:
- capability blocks in IR
- block-to-block graph modeling/composition
- behavior DSL
- requires/ensures language
- state delta modeling
- infrastructure simulation
- spec -> IR lowering automation
- embedded LLM logic in BEAR core
- multi-target support

## Target Scope (v0)
Only one target is supported:
- JVM target generating Java sources and JUnit 5 tests for Gradle projects

## Enforcement Pipeline
1. Natural language spec -> BEAR IR (agent-assisted or manual)
2. BEAR IR -> skeleton + structured ports + tests (deterministic compile)
3. Implementation file authored by agent/human
4. `bear check` runs:
   - validate
   - compile
   - deterministic drift/governance signal checks
   - project tests

Two-file approach:
- generated skeleton is non-editable
- implementation file is editable

## Demo Scope (bear-account-demo)
The v0 proof is:
- naive Withdraw implementation fails `bear check`
- corrected Withdraw implementation passes `bear check`

If work does not move toward this proof, it is scope drift.

## Definition of Done (v0)
v0 is done when:
1. `bear validate` deterministically validates and normalizes BEAR IR.
2. `bear compile` deterministically generates JVM artifacts:
   - non-editable skeleton
   - structured port interfaces derived from declared effects
   - JUnit tests for idempotency (if declared) and `non_negative`
3. Two-file enforcement is active (skeleton regenerated, impl preserved).
4. `bear check --project <path>` fails deterministically on violations.
5. Demo proves naive Withdraw fails and corrected Withdraw passes.

## Future Ideas
Keep future ideas in `doc/FUTURE.md`. Do not implement them in v0.

## Governance Reference
`doc/GOVERNANCE.md` is the normative source for IR diff classification and boundary-expansion review semantics in v0.
