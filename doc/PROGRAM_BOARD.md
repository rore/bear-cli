# BEAR Program Board

## Last Updated

2026-02-21

## Current Milestone

`P2` (active)

## Milestone Pipeline

`v0 complete -> M1 complete -> M1.1 complete -> Preview Release complete -> P2 active -> P3`

## Interpretation Guardrails

- This file tracks milestone feature status and queue order.
- This file is not the canonical milestone feature-definition document.
- For "what features are in Preview?", use `doc/ROADMAP.md` -> `Preview Release` -> `Preview contract (must ship)`.

## Preview Feature Status (Roadmap Contract)

This section is the operational answer for:
- "What are Preview features?"
- "Where are we standing on Preview features?"

Status summary:
- `10/10` Preview contract features are `DONE`

1. Deterministic `bear validate` (schema/semantic + normalization).  
   Status: `DONE`
2. Deterministic `bear compile` + impl-preservation ownership.  
   Status: `DONE`
3. Deterministic `bear check` drift gate (`ADDED|REMOVED|CHANGED|MISSING_BASELINE`).  
   Status: `DONE`
4. Deterministic `check` test-stage semantics (drift-first, stable failure/timeout).  
   Status: `DONE`
5. Deterministic `bear pr-check` boundary verdict (`0` / `5`).  
   Status: `DONE`
6. Standardized non-zero failure envelope (`CODE/PATH/REMEDIATION`).  
   Status: `DONE`
7. Preview undeclared-reach enforcement (covered JVM HTTP surfaces, exit `6`).  
   Status: `DONE`
8. Self-hosting baseline (clean-clone style normal wrapper flow, no bespoke ritual).  
   Status: `DONE`
9. Single preview exit-code registry (`0,2,3,4,5,6,64,70,74`).  
   Status: `DONE`
10. Failure-envelope compliance coverage across validation/drift/test/usage/IO/git/internal paths.  
    Status: `DONE`

Preview standing note:
- Preview is treated as feature-complete for product development.
- No additional release-evidence documentation is required to start next feature work.

## Ready Queue (Ordered, Execution Work Items)

1. `Boundary hardening v1.2 Final Lock++` (implemented in cli/kernel; stabilization + demo sync pending)
2. `Declared allowed deps containment` (stabilization/operational hardening)
3. Generated structural tests and cross-target parity follow-up

## Backlog Buckets (P1/P2/P3)

- `P1`
  - `doc/backlog/P1_PREVIEW_CLOSURE_GAPS.md` (parked; non-blocking for product development)
- `P2`
  - `doc/backlog/P2_BEAR_FIX_GENERATED_ONLY.md` (`Completed`)
  - `doc/backlog/P2_DECLARED_ALLOWED_DEPS_CONTAINMENT.md` (`In Progress`)
- `P3`
  - `doc/backlog/P3_MAVEN_ALLOWED_DEPS_CONTAINMENT.md` (`Queued`)

## Open Risks / Decisions

- Risk: historical references still pointing to `doc/ROADMAP_V0.md` can reintroduce drift if not cleaned.
- Direction lock: BEAR semantic scope follows enforceability + determinism (wrapper-owned where possible), not domain-specific rule coverage.

