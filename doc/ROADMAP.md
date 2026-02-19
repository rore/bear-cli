# BEAR Target Roadmap

This roadmap expresses the broader target direction for BEAR.
For current execution order and milestone sequencing, see `doc/ROADMAP_V0.md` (historical filename; now post-v0 execution tracker).
For current v0 contract details, see `doc/ARCHITECTURE.md` and `doc/GOVERNANCE.md`.

## Status Snapshot

- v0 core is complete.
- active work is post-v0 milestone execution (currently M1.1 governance signal hardening and preview contract hardening).
- target milestone is `Preview Release` (reached via M1 -> M1.1 -> preview).
- this file is strategic; do not treat it as the step-by-step execution tracker.

## Why This Roadmap

BEAR is successful only if agent speed is paired with structural control:
- agents can move fast inside blocks
- new external power cannot be introduced silently
- boundary expansion is explicit and reviewable
- deterministic build/test gates enforce declared structure
- developers are not burdened with IR micromanagement

## Harness Engineering Addendum (Preview)

This is additive to the existing roadmap, not a replacement.

Strategic position:
- BEAR is a deterministic structural containment layer for agent-generated code.
- BEAR is a compile/CI gate, not a full agent harness platform.
- Reliability comes from deterministic structure + mechanical enforcement, not model trust.

Primary preview failure modes to target:
- silent boundary expansion
- drift/entropy in generated artifacts
- undeclared escape paths that bypass declared boundaries

Preview additions that are now mandatory:
1. Actionable failures as a product requirement.
  - output must be minimal, stable, greppable, and include explicit remediation
  - applies to every non-zero path, including usage/IO/git/internal failures
2. Demo-grade no-undeclared-reach enforcement.
  - deterministically catch at least one realistic boundary bypass class
3. Stable exit semantics as a CI contract.
  - one numeric registry across commands
  - contract tests enforce failure-envelope coverage

Preview invariant alignment:
- preview invariant source of truth is `doc/INVARIANT_CHARTER.md`
- preview "must enforce" set is the charter preview list
- broad charter claims use explicit scope caveat for preview static detection coverage

Position in the wider stack:
- In scope: static/CI structural enforcement.
- Out of scope for preview: runtime orchestration, sandboxing, policy gateways, eval platforms.
- Scope rationale: fastest falsifiable preview with current team capacity.

Preview success is falsifiable only if all are true:
- implementation/refactor freedom remains high inside blocks
- boundary expansion is deterministic and review-visible in PR/CI
- undeclared reach attempts for covered surfaces fail deterministically with remediation
- workflow stays low-friction with one canonical CI command and scoped self-hosting (clean clone + normal wrapper flow, no bespoke ritual)

## Execution Layering

Two roadmap files are intentional:
- `doc/ROADMAP.md` (this file): long-horizon strategy and phase intent.
- `doc/ROADMAP_V0.md`: near-term execution order, milestone definitions, and done criteria.

## Phase 1 - Deterministic Core (current foundation)

Goal: IR and generation are stable and trustworthy.

Deliver:
- strict IR validation
- canonical normalization
- deterministic canonical emission
- drift detection
- stable exit-code contract
- golden fixtures/conformance corpus

Success criteria:
- IR diffs are stable
- schema/semantic failures are deterministic
- normalization has no silent behavior shifts
- `bear check` is reliable and predictable

Current status:
- directionally complete; maintain and harden as baseline

## Phase 2 - Real Structural Enforcement (critical)

Goal: declared effects/ports map to mechanical build enforcement.

Initial JVM enforcement targets:
- block modules cannot import integration modules directly
- forbidden API/symbol checks (for example: network/filesystem/reflection surfaces)
- only declared capability interfaces are visible to block logic
- build fails on forbidden references
- failures are actionable and deterministic for agents

CI must fail if:
- code uses undeclared external surfaces
- implementation diverges from declared structural boundaries

Success criteria:
- an agent cannot introduce a new HTTP call, filesystem write, or DB client usage without corresponding declared boundary change plus passing enforcement checks

Notes:
- start on JVM first
- cross-language parity comes after JVM maturity

Phase 2 explicit deliverable:
- define and freeze a minimal side-effect taxonomy and JVM enforcement mapping
- apply principle: side-effect gating, not library gating
  - libraries are generally allowed
  - external reach/escape-hatch surfaces are governed
  - side effects should flow through declared capability ports

## Phase 3 - Boundary Expansion Classification

Goal: make power expansion visible and reviewable.

Deliver:
- deterministic IR diff classification:
  - ordinary change
  - boundary expansion
- deterministic CI/report output that labels boundary expansion clearly
- optional governance hooks (for example CODEOWNERS/approval on boundary-expanding diffs)
- PR/base-branch boundary diff mode (`bear pr-check` or equivalent):
  - compare current branch boundary surface against base branch
  - emit deterministic boundary deltas (ports/ops/effects/contract/invariants)
  - mark PRs with explicit boundary-expansion status for approval workflows
- normalize exit-code classes for stale/boundary paths so CI signals are stable (`drift`/`boundary` are not ambiguously reported as generic failures)

Success criteria:
- when a block gains external power, reviewers can see it in seconds

## Phase 4 - Agent-Native Integration

Goal: BEAR becomes default operating mode in agentic development.

Deliver:
- repo-level bootstrap context for agents
- explicit agent protocol:
  - update IR when boundary/contract changes require it
  - run validate -> check (drift + tests)
  - do not widen effects silently
  - report human-readable boundary summary/diff

Success criteria:
- developers work in domain terms
- agents operate BEAR-aware by default
- BEAR does not require constant manual IR babysitting

## Phase 5 - Controlled Behavioral Visibility (optional, high value)

This phase starts only after structural enforcement is strong.

Goal: detect meaningful boundary-usage changes, not just capability additions.

Deliver (narrow boundary scope):
- capability contract refinements (for example allowed topics/event types)
- optional per-op interaction constraints (such as cardinality)
- generated policy tests for observable interaction patterns

Non-goal:
- full behavior modeling or business-logic DSL

Success criteria:
- meaningful interaction-pattern changes produce deterministic test diffs or constraint violations

## Explicitly Out of Scope (for now)

- runtime sandboxing
- runtime permission enforcement and policy engines
- full formal behavioral verification
- complex workflow/orchestration modeling
- rich domain DSL for business semantics
- cross-language parity before JVM enforcement is mature

## 12-Month Honest Success Definition

BEAR is successful if all are true:
- agents move fast inside blocks
- new external interactions cannot be introduced silently
- boundary expansion is explicit and quickly reviewable
- build/test gates enforce declared structure
- developers do not feel burdened by BEAR mechanics

## Current Immediate Execution Focus

Near-term implementation is tracked in `doc/ROADMAP_V0.md` and `doc/STATE.md`.
