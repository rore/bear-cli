# BEAR Preview Governance Policy

This document is normative for BEAR Preview governance.

## Purpose

BEAR governance prevents silent boundary expansion during agentic iteration.
Implementation can evolve quickly, but interaction-power changes must be explicit and reviewable.

## Litmus

BEAR is valuable only if introducing new external interaction capability cannot happen silently.

## IR Diff Classes

All IR changes are classified into one of two classes.

### 1. Ordinary

Ordinary changes are auto-allowed under normal workflow.

Examples:
- Add an input field to an operation contract.
- Tighten invariants without widening behavior.
- Remove effects/ports/ops.
- Normalization-only edits with no semantic change.

### 2. Boundary-Expanding

Boundary-expanding changes require explicit visibility and review.

Examples:
- Add/remove an operation entrypoint in `block.operations`.
- Add a new effect port.
- Add new ops to an existing block effect port.
- Widen operation usage (`operations[].uses`).
- Add/change operation idempotency usage.
- Add/change operation invariants.
- Relax/remove invariant constraints.

## Decision Table (Preview)

| Change example | Class | Required action |
| --- | --- | --- |
| Add `input.note` to `ExecuteWithdraw` | ordinary | Standard validate/compile/check flow |
| Add operation `RefundWithdraw` | boundary-expanding | Explicit review signal required |
| Add `ledger.reverse` to operation uses | boundary-expanding | Explicit review signal required |
| Add new `paymentsGateway` port | boundary-expanding | Explicit review signal required |
| Remove `non_negative` invariant from allowed set | boundary-expanding | Explicit review signal required |
| Remove unused `audit` effect | ordinary | Standard validate/compile/check flow |

## Preview Enforcement Requirement

Boundary-expanding changes must produce deterministic, reviewable signals in BEAR workflow and CI.
Detect-and-signal is mandatory in Preview docs and contracts.

## Agentic Operating Model

BEAR is expected to run by default in agent sessions.

1. Explore and locate/create the correct block.
2. Update IR.
3. Run deterministic gates (`validate`, `compile`/`fix`, `check`, `pr-check`).
4. Report boundary summary, including expansion signals when present.

## Scope Guardrails

This policy does not add runtime semantics beyond declared IR contracts.
It defines review/signaling requirements for the current Preview model.