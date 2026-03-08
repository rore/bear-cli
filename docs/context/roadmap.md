# BEAR Roadmap

This file defines milestone feature contracts and done criteria.
Live roadmap planning now lives in minimap:
- `roadmap/board.md`: canonical live groups and item order
- `roadmap/scope.md`: canonical current-focus narrative
- `roadmap/features/*.md`: committed, active, and completed roadmap items
- `roadmap/ideas/*.md`: parked or uncommitted roadmap ideas, including broad future idea families

## Planning Split

1. `docs/context/roadmap.md`:
- milestone contracts, done criteria, and scope guardrails
2. `roadmap/board.md` + `roadmap/scope.md`:
- canonical live planning state and execution order
3. `roadmap/features/*.md` + `roadmap/ideas/*.md`:
- canonical item files and detailed roadmap specs
4. `docs/context/state.md`:
- current-window handoff only

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

## Live Planning Note

Do not duplicate live queue ordering, parked-item state, or completed-item summaries in this file.
When the roadmap changes:
1. update the owning minimap item file under `roadmap/features/` or `roadmap/ideas/`
2. update `roadmap/board.md` if grouping or order changed
3. update `roadmap/scope.md` if near-term focus changed

## Out of Scope (Current Roadmap)

- runtime sandboxing
- policy engines and runtime authorization frameworks
- full formal behavioral verification
- workflow orchestration platforms
- business-logic DSL expansion
