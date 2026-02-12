# BEAR v0 Governance Policy

This document is normative for BEAR v0 governance.

## Purpose

BEAR governance exists to prevent silent boundary expansion during agentic iteration.
Implementation logic can evolve quickly, but changes that widen external interaction power must be explicit, deterministic, and easy to review.

## Litmus

BEAR is valuable only if introducing a new external interaction capability cannot happen silently.

## IR Diff Classes

All IR changes are classified into one of two classes.

### 1. Ordinary

Ordinary changes are auto-allowed under normal workflow.

Examples:
- Add an operation under an already declared effect/port boundary.
- Refine contract field types within the same boundary.
- Strengthen invariants.
- Remove effects or narrow existing scope.
- Normalization-only edits with no semantic boundary change.

### 2. Boundary-Expanding

Boundary-expanding changes require explicit visibility and review.

Examples:
- Introduce interaction with a new external system/category.
- Widen capability scope of an existing external interaction.
- Add new effect surfaces not previously declared.
- Relax or remove invariants that previously constrained behavior.

## Decision Table (v0)

| Change example | Class | Required action |
| --- | --- | --- |
| Add `ledger.reverse` op to existing `ledger` port | ordinary | Standard validate/compile/check flow |
| Add a new `paymentsGateway` port | boundary-expanding | Explicit review signal required |
| Change output `balance` type precision only | ordinary | Standard validate/compile/check flow |
| Remove `non_negative` invariant | boundary-expanding | Explicit review signal required |
| Remove unused `audit` effect | ordinary | Standard validate/compile/check flow |
| Add filesystem write effect surface | boundary-expanding | Explicit review signal required |

## v0 Enforcement Requirement

For v0, boundary-expanding changes must produce deterministic, reviewable signals in BEAR workflow.
Detect-and-signal is mandatory in v0 docs and roadmap.
Full static hard-blocking (forbidden APIs/dependency firewalls) is future hardening unless explicitly delivered.

## Agentic Operating Model

BEAR is expected to run by default in agent sessions.
Developers stay domain-focused; agents handle BEAR mechanics:

1. Explore and locate/create the correct block.
2. Update IR.
3. Run validate -> compile/check gates.
4. Report boundary summary, including expansion signals when present.

## Scope Guardrails

This governance policy does not expand v0 IR expressiveness and does not add a BEAR runtime.
It defines review and signaling requirements for the existing v0 model.
