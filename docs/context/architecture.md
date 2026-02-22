# BEAR Preview Architecture

This document defines current Preview contract-level guarantees and constraints.
For long-horizon motivation and success criteria, see `docs/context/north-star.md`.

## Core Purpose

BEAR is a boundary-governance enforcement layer for agentic backend development.

It exists because:
- agent-generated code can expand capability surfaces silently
- generated artifacts can drift from declared structure
- production workflows need deterministic, independent gates

BEAR uses strict BEAR IR plus deterministic commands to make boundary changes explicit and enforceable.

## Philosophy in Agentic Development

BEAR assumes:
- agents are strong at producing implementation
- governance must not depend on agent reasoning quality

Design stance:
- maximize implementation freedom inside declared boundaries
- minimize trust in hidden reasoning
- shift trust to deterministic, machine-checkable gates

Invariant source of truth:
- `docs/context/invariant-charter.md` is normative for invariant definitions and current enforcement status (`ENFORCED`/`PARTIAL`/`PLANNED`).

## Core Principles

1. Deterministic core
   - validation, normalization, generation, and checks are reproducible.
2. Agent-agnostic CLI
   - BEAR works with Codex/Copilot/manual workflows.
3. Cage, not code style engine
   - BEAR governs structure and boundary surfaces, not coding style.
4. Boundary visibility over silent expansion
   - boundary-expanding changes must produce deterministic, reviewable signals.

## Agentic Process Contract (Preview)

Role split:
- Developer: states domain intent and accepts/rejects boundary-expanding changes.
- Agent: executes BEAR mechanics (IR updates, generation, gates, triage).
- BEAR gates: provide deterministic independent checks.

Expected loop:
1. Prompt in domain language.
2. Agent updates IR and implementation.
3. Agent runs deterministic gates (`validate`, `compile`/`fix`, `check`, `pr-check`).
4. Agent reports implementation summary and explicit boundary-governance signals.

Required property:
- boundary-expanding changes are visible in PR/CI in seconds.
- ordinary changes remain low-friction.

## What BEAR Guarantees in Preview

If a block passes `bear check`:
- IR and generated artifacts are structurally consistent.
- deterministic drift gate is clean.
- active boundary policy gates pass (`UNDECLARED_REACH`, `BOUNDARY_BYPASS`).
- project test stage passes.
- deterministic failure/diagnostic contract holds on non-zero outcomes.

If `bear pr-check` passes:
- no boundary-expanding IR delta is detected against base.

## What BEAR Does Not Guarantee in Preview

- business correctness beyond declared structural constraints
- runtime transaction semantics or cross-port atomicity guarantees
- universal static detection across every possible external API
- runtime sandboxing, IAM policy enforcement, or orchestration behavior

Preview scope caveat:
- undeclared-reach detection is intentionally bounded to covered surfaces.

## Repository Structure (bear-cli)

This repo is a Gradle multi-module project.

- `kernel/`
  - deterministic seed
  - IR parse/validate/normalize and target abstractions
- `app/`
  - CLI orchestration (`validate`, `compile`, `fix`, `check`, `unblock`, `pr-check`)
  - command output/exit contract rendering

## BEAR IR Scope (v1)

A BEAR IR file defines one governed `logic` block.

Current schema and normalization rules are canonical in:
- `docs/context/ir-spec.md`

Operational rule:
- IR is strict, deterministic, and intentionally constrained for enforceability.

## Preview Scope Lock

In scope:
- deterministic `validate`, `compile`, `fix`, `check`, `unblock`, `pr-check`
- deterministic failure envelope (`CODE/PATH/REMEDIATION`)
- deterministic PR boundary-governance signaling
- JVM/Java target in Preview

Out of scope:
- behavior DSL expansion
- runtime policy/sandbox systems
- multi-target production support beyond current Preview target

## Governance Reference

`docs/context/governance.md` is the normative source for IR diff classification and boundary-expansion review semantics.
