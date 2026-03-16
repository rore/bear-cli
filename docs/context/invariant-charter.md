# BEAR Invariant Charter

This document is the normative invariant catalog for BEAR preview.

Purpose:
- BEAR enforces structural containment of backend logic in agent-generated systems.
- BEAR does not control implementation style or business logic.
- BEAR enforces architectural truths that must always hold.

Scope caveat for preview:
- Broad charter claims ("all external reach") are enforced for covered preview surfaces.
- Preview does not claim universal static detection across every possible external API.
- Enforcement status is labeled explicitly to avoid over-claiming.

Status labels:
- `ENFORCED`: mechanically enforced in current preview contract
- `PARTIAL`: enforced for preview-defined covered surfaces only
- `PLANNED`: not yet mechanically enforced; roadmap target

## 1. Structural Authority Is Explicit

### 1.1 External integrations must be declared
Requirement:
- A block may only interact with external systems through declared ports.
Status:
- `ENFORCED` at generated structural surface.
- `PARTIAL` for undeclared-reach static detection (covered preview surfaces only).
Mechanized by:
- IR validation, deterministic generation, `check` undeclared-reach gate.

### 1.2 No undeclared side effects
Requirement:
- Any attempt to reach infrastructure outside declared capabilities must fail deterministically.
Status:
- `PARTIAL` in preview (covered JVM direct HTTP client surfaces).
Mechanized by:
- `bear check` undeclared-reach detection and fail-fast exit contract.

### 1.3 Boundary changes are visible
Requirement:
- Additions/removals/signature changes to ports/effects/contracts must be surfaced explicitly in PR/CI output.
Status:
- `ENFORCED` for current IR-classified boundary deltas.
Mechanized by:
- `bear pr-check` deterministic delta classification and boundary verdict.

## 2. Structure Is Deterministic

### 2.1 Canonical generation
Requirement:
- Generated artifacts are reproducible; compile twice with unchanged IR yields zero diff.
Status:
- `ENFORCED`.
Mechanized by:
- deterministic normalization + deterministic generator + golden tests.

### 2.2 No generated artifact drift
Requirement:
- Generated files must match canonical output exactly; drift fails the build.
Status:
- `ENFORCED`.
Mechanized by:
- `bear check` drift compare and deterministic diff categories.

### 2.3 Two-file ownership
Requirement:
- Generated artifacts are BEAR-owned and overwriteable; implementation files are never overwritten by BEAR.
Status:
- `ENFORCED`.
Mechanized by:
- compile ownership model (`build/generated/bear` regenerated, user impl preserved).

## 3. Violations Fail Fast

### 3.1 No silent violations
Requirement:
- Structural invariant breaches are build failures, not warnings.
Status:
- `ENFORCED` for active preview invariants.
Mechanized by:
- non-zero exit semantics for validation, drift, test, boundary (pr-check), undeclared reach.

### 3.2 Actionable failures
Requirement:
- Every non-zero exit includes:
  - stable `CODE`
  - violating `PATH`
  - clear `REMEDIATION`
Status:
- `ENFORCED`.
Mechanized by:
- standardized failure envelope contract across `validate`, `compile`, `check`, `pr-check`.

## 4. Architectural Shape Is Protected, Implementation Is Free

### 4.1 BEAR enforces architectural invariants, not coding style
Status:
- `ENFORCED`.

### 4.2 Logic inside a block is unconstrained, provided:
- it does not expand declared boundaries
- it does not bypass declared integration points
- it respects deterministic structure
Status:
- `ENFORCED` as product intent.
- `PARTIAL` for static bypass detection beyond preview-covered undeclared-reach surfaces.

## 5. Rules Must Be Executable

### 5.1 No structural rule may exist only in documentation
Requirement:
- Invariants are enforced mechanically by IR validation, generation, structural checks in the agent loop, or PR/CI gates.
Status:
- `ENFORCED` for preview invariants.
- `PLANNED` for post-preview hardening invariants.

## 6. Scope Discipline

BEAR does not:
- orchestrate agents
- enforce runtime sandboxing
- manage IAM or policy gateways
- evaluate semantic correctness of business logic

Status:
- `ENFORCED` as scope boundary.

Summary principle:
- The bear can move fast inside the cage.
- The cage defines its authority.
- The cage cannot be bypassed silently.

## Preview Invariants (Must Be Enforced)

1. Explicit external reach only. `PARTIAL` in preview static detection scope.
2. Boundary delta visibility. `ENFORCED` in `pr-check`.
3. Deterministic generation. `ENFORCED`.
4. No generated artifact drift. `ENFORCED`.
5. Two-file ownership. `ENFORCED`.
6. Fail-fast structural violations. `ENFORCED`.
7. Actionable failure envelope. `ENFORCED`.

## Priority 2 Invariants (Next Layer)

8. Dependency direction invariant. `PLANNED`.
9. No cross-domain leakage. `PLANNED`.
10. Structural layout stability. `PLANNED`.
11. Symmetric contract delta. `PLANNED`.
12. Regression encoding invariant. `PLANNED`.

## Priority 3 Invariants (Only If Scope Expands)

13. Multi-block composition rules. `PLANNED`.
14. Capability minimization enforcement. `PLANNED`.
15. Side-effect taxonomy enforcement. `PLANNED`.
16. Boundary shrink protection. `PLANNED`.

## Explicitly Out of Scope

Do not add:
- runtime permission enforcement
- policy engines
- sandboxing
- agent orchestration invariants
- LLM grader invariants
- style linting

These belong to other layers.

