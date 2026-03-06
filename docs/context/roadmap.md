# BEAR Roadmap

This file defines milestone feature contracts and done criteria.
Live execution status and queue ordering are tracked in `docs/context/program-board.md`.
Concrete feature specs live under `docs/context/backlog/`.
Deferred initiatives and idea buckets live in `docs/context/future.md`.

## Planning Doc Roles

1. `docs/context/roadmap.md`:
- milestone contracts and done criteria (definition)
2. `docs/context/program-board.md`:
- active milestone status and ordered execution queue (operations)
3. `docs/context/backlog/*.md`:
- one feature spec per queued or deferred feature-sized item
4. `docs/context/future.md`:
- parked initiatives and idea buckets that are not committed roadmap work
5. `docs/context/state.md`:
- current-window handoff (session)

## Milestone Pipeline

`v0 complete -> M1 workflow proof -> M1.1 governance hardening -> Preview Release -> P2 -> P3`

## Milestone Definitions

### M1: Workflow Proof

Goal:
- prove isolated BEAR-aware agent workflow in the demo repo

Done criteria:
1. Isolated agent completes one non-boundary feature and one boundary-expanding feature.
2. Both flows terminate through one canonical gate command.
3. Demo workflow is runnable from demo repo context only.

### M1.1: Governance Signal Hardening

Goal:
- make boundary governance CI-native and ordering-independent

Done criteria:
1. PR runs classify boundary deltas deterministically.
2. CI can gate on explicit boundary expansion without stale-baseline ordering dependencies.

### Preview Release

Goal:
- ship a credible, tryable preview on a realistic multi-block banking slice

Preview contract (must ship):
1. Deterministic `validate`, `compile`, `check`, `pr-check` command behavior.
2. Deterministic non-zero failure envelope (`CODE`, `PATH`, `REMEDIATION`).
3. Deterministic undeclared-reach and governance signaling in preview scope.
4. Stable preview exit-code registry (`0,2,3,4,5,6,7,64,70,74`).
5. Self-hosting baseline without bespoke ritual.

Preview definition of done:
1. Agents can implement or refactor inside declared boundaries.
2. Boundary expansion is deterministic and review-visible in PR or CI.
3. Covered boundary bypass attempts fail with deterministic remediation.
4. Workflow remains low-friction and deterministic.

## Active Post-Preview Feature Roadmap

Priority 2:
1. CI boundary governance + PR diff ergonomics + telemetry unification. Spec: `docs/context/backlog/p2-ci-owned-bear-gates.md`
2. Minimal taste-invariants rule pack. Spec: `docs/context/backlog/p2-minimal-taste-invariants-rule-pack.md`

Priority 3:
1. Maven allowed-deps containment parity (optional future expansion). Spec: `docs/context/backlog/p3-maven-allowed-deps-containment.md`
2. Capability templates. Spec: `docs/context/backlog/p3-capability-templates.md`
3. Broader boundary-escape coverage. Spec: `docs/context/backlog/p3-broader-boundary-escape-coverage.md`
4. Multi-block and multi-module composition hardening. Spec: `docs/context/backlog/p3-multi-block-multi-module-composition-hardening.md`
5. Optional deterministic policy hooks. Spec: `docs/context/backlog/p3-optional-deterministic-policy-hooks.md`

Shipped in active post-Preview work already:
1. `bear fix` for generated artifacts only.
2. Generated structural tests (default evidence-only; opt-in strict mode remains available via JVM property).
3. Declared allowed-deps containment for Gradle or JVM projects.
4. Boundary regression suite.

Parked and non-committed items:
1. Future initiatives and idea families belong in `docs/context/future.md`, not in the active roadmap queue.
2. Documentation-only historical cleanup belongs in `docs/context/backlog/p1-preview-closure-gaps.md` and remains non-blocking.

## Out of Scope (Current Roadmap)

- runtime sandboxing
- policy engines and runtime authorization frameworks
- full formal behavioral verification
- workflow orchestration platforms
- business-logic DSL expansion
