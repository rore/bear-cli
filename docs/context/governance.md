# BEAR Preview Governance Policy

This document is normative for BEAR Preview governance.
For full invariant catalog and enforcement status, see `docs/context/invariant-charter.md`.

## Purpose

BEAR governance prevents silent boundary expansion during agentic iteration.
Implementation logic can evolve quickly, but changes that widen external interaction power must be explicit, deterministic, and easy to review.

## Litmus

BEAR is valuable only if introducing new external interaction capability cannot happen silently.

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

## Decision Table (Preview)

| Change example | Class | Required action |
| --- | --- | --- |
| Add `ledger.reverse` op to existing `ledger` port | ordinary | Standard validate/compile/check flow |
| Add a new `paymentsGateway` port | boundary-expanding | Explicit review signal required |
| Change output `balance` precision only | ordinary | Standard validate/compile/check flow |
| Remove `non_negative` invariant | boundary-expanding | Explicit review signal required |
| Remove unused `audit` effect | ordinary | Standard validate/compile/check flow |
| Add filesystem write effect surface | boundary-expanding | Explicit review signal required |

## Preview Enforcement Requirement

Boundary-expanding changes must produce deterministic, reviewable signals in BEAR workflow and CI.
Detect-and-signal is mandatory in Preview docs and contracts.

## Agentic Operating Model

BEAR is expected to run by default in agent sessions.
Developers stay domain-focused; agents handle BEAR mechanics:

1. Explore and locate/create the correct block.
2. Update IR.
3. Run deterministic gates (`validate`, `compile`/`fix`, `check`, `pr-check`).
4. Report boundary summary, including expansion signals when present.

## Scope Guardrails

This policy does not expand IR expressiveness and does not add a BEAR runtime.
It defines review/signaling requirements for the current Preview model.
