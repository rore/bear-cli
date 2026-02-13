# BEAR Target Roadmap

This roadmap expresses the broader target direction for BEAR.
For current v0 contract details, see `doc/ARCHITECTURE.md` and `doc/GOVERNANCE.md`.

## Why This Roadmap

BEAR is successful only if agent speed is paired with structural control:
- agents can move fast inside blocks
- new external power cannot be introduced silently
- boundary expansion is explicit and reviewable
- deterministic build/test gates enforce declared structure
- developers are not burdened with IR micromanagement

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

CI must fail if:
- code uses undeclared external surfaces
- implementation diverges from declared structural boundaries

Success criteria:
- an agent cannot introduce a new HTTP call, filesystem write, or DB client usage without corresponding declared boundary change plus passing enforcement checks

Notes:
- start on JVM first
- cross-language parity comes after JVM maturity

## Phase 3 - Boundary Expansion Classification

Goal: make power expansion visible and reviewable.

Deliver:
- deterministic IR diff classification:
  - ordinary change
  - boundary expansion
- deterministic CI/report output that labels boundary expansion clearly
- optional governance hooks (for example CODEOWNERS/approval on boundary-expanding diffs)

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

## Current Immediate Execution Focus (v0)

Near-term implementation still follows v0 execution documents:
- `doc/STATE.md` for current task
- `doc/ARCHITECTURE.md` for v0 guarantees/non-guarantees
- `doc/GOVERNANCE.md` for normative classification policy
