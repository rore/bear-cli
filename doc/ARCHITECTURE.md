# BEAR v0 Architecture

This document defines v0 contract-level guarantees and constraints.
For long-horizon motivation and success criteria, see `doc/NORTH_STAR.md`.

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

## Philosophy in Agentic Development
BEAR assumes agents are highly capable at producing implementation, but weakly bounded unless structure is enforced.

The design stance is:
- maximize implementation freedom inside a declared cage
- minimize trust required in agent internal reasoning
- shift trust to deterministic, independent gates

In practical terms:
- BEAR does not try to make intent perfect before coding.
- BEAR makes boundary changes explicit and reviewable.
- BEAR treats ordinary internal evolution as fast-path work.
- BEAR treats boundary expansion as governance-sensitive work.
- BEAR governs side-effect surfaces, not general library usage.

Core litmus:
- If an agent can add new external interaction capability without a small, obvious, deterministic signal, BEAR is not doing its job.

Invariant source of truth:
- `doc/INVARIANT_CHARTER.md` is normative for invariant definitions and current enforcement status (`ENFORCED`/`PARTIAL`/`PLANNED`).

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

## Agentic Process Contract (v0)
BEAR is expected to be default-on in agent sessions.

Role split:
- Developer: states domain intent and accepts/rejects boundary changes.
- Agent: handles BEAR mechanics (IR updates, generation, gate execution, and reporting).
- BEAR gates: provide deterministic, independent checks.

Expected operating loop:
1. Prompt in domain language.
2. Agent explores code and locates/creates affected block IR.
3. Agent applies IR and code updates.
4. Agent runs deterministic gates (`validate`, `compile`/`check`, project tests as applicable).
5. Agent reports:
   - implementation summary
   - boundary summary
   - explicit boundary-expansion signals when present

Required property:
- boundary-expanding changes must be visible in workflow output and reviewable in seconds.
- ordinary changes should remain low-friction.

## What BEAR Guarantees in v0
If a block passes `bear check`, then:
- it respects declared input/output contract structure
- it cannot call undeclared capability operations, via generated structured port interfaces
- it satisfies declared invariant templates under generated tests
- it respects declared idempotency semantics under generated tests
- generated artifacts are unchanged from deterministic regeneration (drift detection)
- boundary-governance signals are deterministic for covered v0 expansion cases
- covered undeclared-reach bypass patterns fail deterministically (`UNDECLARED_REACH`, exit `6`) in preview scope

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
- v0 undeclared-reach static detection is intentionally scoped to covered preview surfaces, not every possible external API.

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
