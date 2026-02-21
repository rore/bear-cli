# BEAR Roadmap

This file is the single canonical roadmap.
It defines milestone contracts, done criteria, and post-preview priorities.

Live execution status is tracked in `docs/context/program-board.md`.
Session handoff status is tracked in `docs/context/state.md`.

## How To Read Planning Docs

Use these files by intent, not interchangeably:
- `docs/context/roadmap.md`: milestone feature contracts and done criteria (what BEAR must do).
- `docs/context/program-board.md`: milestone feature status and execution queue (what to build next).
- `docs/context/state.md`: short session handoff (what to do next in this work window).

Important:
- The active queue in `docs/context/program-board.md` is product-development first.
- Do not treat queue order as the canonical feature list for a milestone.
- For "what are preview features?", use `Preview Release -> Preview contract (must ship)` in this file.
- For "where do we stand on Preview features right now?", use `docs/context/program-board.md` -> `Preview Feature Status (Roadmap Contract)`.

## Milestone Pipeline

`v0 complete -> M1 workflow proof -> M1.1 governance hardening -> Preview Release -> P2 -> P3`

Milestone status ownership:
- Active/completed state lives only in `docs/context/program-board.md`.
- This file stays stable and milestone-definition focused.

## Milestone Definitions

### M1: Workflow Proof

Goal:
- prove isolated BEAR-aware agent workflow in the demo repo

Deliver:
- demo-local BEAR workflow assets (`BEAR_PRIMER.md`, `AGENTS.md`, `BEAR_AGENT.md`, `WORKFLOW.md`)
- canonical gate scripts and wrapper scripts
- realistic scenario branches (`scenario/01-agent-greenfield-implementation`, `scenario/02-feature-extension-scheduled-transfers`)
- no demo answer-key hints

Done criteria:
1. Isolated agent completes one non-boundary feature and one boundary-expanding feature.
2. Both flows terminate through one canonical gate command.
3. Demo workflow is runnable from demo repo context only.

### M1.1: Governance Signal Hardening

Goal:
- make boundary governance CI-native and ordering-independent

Deliver:
- `bear pr-check` base-branch diff mode
- deterministic boundary-delta output (`PORTS|OPS|IDEMPOTENCY|CONTRACT|INVARIANTS`)
- CI-ready boundary-expansion status signal
- stable exit-code semantics for governance paths

Done criteria:
1. PR runs classify boundary deltas deterministically.
2. CI can gate on explicit boundary expansion without stale-baseline ordering dependencies.

### Preview Release

Goal:
- ship a credible, tryable preview on a realistic multi-block banking slice

Entry criteria:
- M1 accepted
- M1.1 accepted

Preview contract (must ship):
1. `bear validate` deterministic schema/semantic validation + canonical normalization.
2. `bear compile` deterministic generation + impl-preservation ownership contract.
3. `bear check` deterministic drift gate (`ADDED|REMOVED|CHANGED|MISSING_BASELINE`).
4. `bear check` project tests only after drift pass, with stable failure/timeout semantics.
5. `bear pr-check` deterministic boundary expansion verdict (`0` / `5`).
6. Non-zero exits include deterministic failure envelope:
   - `CODE=...`
   - `PATH=...`
   - `REMEDIATION=...`
7. Preview undeclared-reach enforcement:
   - direct HTTP bypass detection (JVM covered surfaces)
   - deterministic exit `6` and remediation
8. Self-hosting baseline:
   - BEAR commands run in clean clone with normal wrapper flow and no bespoke ritual
9. Single exit-code registry for preview:
   - `0,2,3,4,5,6,64,70,74`
10. Failure-envelope compliance coverage for validation/drift/test/usage/IO/git/internal paths.

Preview demo scope:
- small banking slice with 2-3 blocks
- canonical gate command as definition of done
- undeclared-reach guard in verification path
- packaged agent workflow docs in repo (`AGENTS.md`, `BEAR_AGENT.md`, `WORKFLOW.md`)
- repo-level block index mapping block to IR/project/test roots

Preview definition of done:
1. Agents can implement/refactor freely inside block boundaries.
2. Boundary expansion is deterministic and review-visible in PR/CI.
3. Covered undeclared-reach bypass attempts fail deterministically with remediation.
4. Workflow remains low-friction with one canonical gate and normal self-hosting.
5. CLI contracts remain deterministic, greppable, and exit-code stable.

## Post-Preview Priorities

Priority ordering is strict and maps to `docs/context/invariant-charter.md`.

### Priority 2 (next after preview)

1. `bear fix` for generated artifacts only.
2. Generated structural tests.
3. Minimal taste-invariants rule pack.
4. Boundary regression suite (`bear/regressions/` fixtures).
5. Better PR diff ergonomics.

### Priority 3 (strategic extensions)

1. Capability templates.
2. Broader boundary-escape coverage (DB/filesystem/messaging).
3. Multi-block and multi-module composition hardening.
4. Optional policy hooks (deterministic project-provided checks).

## Strategic Phases (Condensed)

### Phase 1: Deterministic Core
- strict IR validation
- canonical normalization and emission
- deterministic generation and drift checks
- stable exit semantics

### Phase 2: Structural Enforcement
- side-effect gating on real escape-hatch surfaces
- deterministic failures for undeclared external reach

### Phase 3: Boundary Classification
- explicit ordinary vs boundary-expanding diffs
- deterministic PR/CI governance signaling

### Phase 4: Agent-Native Workflow
- BEAR-aware default agent loop
- minimal developer IR micromanagement

### Phase 5: Controlled Behavioral Visibility (optional)
- detect meaningful interaction-shape changes
- avoid broad behavior DSL expansion

## Out of Scope (Current Roadmap)

- runtime sandboxing
- policy engines and runtime authorization frameworks
- full formal behavioral verification
- workflow orchestration platforms
- business-logic DSL expansion

