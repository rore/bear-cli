# BEAR Program Board

## Last Updated

2026-02-24

## Current Milestone

`P2` (active)

## Milestone Pipeline

`v0 complete -> M1 complete -> M1.1 complete -> Preview Release complete -> P2 active -> P3`

## Interpretation Guardrails

- This file tracks milestone feature status and queue order.
- This file is not the canonical milestone feature-definition document.
- For "what features are in Preview?", use `docs/context/roadmap.md` -> `Preview Release` -> `Preview contract (must ship)`.

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
9. Single preview exit-code registry (`0,2,3,4,5,6,7,64,70,74`).  
   Status: `DONE`
10. Failure-envelope compliance coverage across validation/drift/test/usage/IO/git/internal paths.  
    Status: `DONE`

Preview standing note:
- Preview is treated as feature-complete for product development.
- No additional release-evidence documentation is required to start next feature work.

## Ready Queue (Ordered, Execution Work Items)

1. `_shared` allowedDeps policy (path-scoped shared policy, no IR schema changes; next after core containment hardening)
2. Generated structural tests and cross-target parity follow-up
3. BEAR-owned generated-source wiring auto-enforcement (avoid ad-hoc `build.gradle` patching)

## Recently Completed (P2)

1. `Wiring drift diagnostics` (deterministic canonical wiring paths + bounded detail)
   - wiring drift now reports canonical repo-relative paths:
     - `build/generated/bear/wiring/<blockKey>.wiring.json`
   - drift output no longer emits duplicate wiring path variants.
   - `check --all` block `DETAIL` now carries explicit wiring drift reason/path for faster remediation.
   - wiring drift detail ordering is frozen (`MISSING_BASELINE > REMOVED > CHANGED > ADDED`) and capped to 20 entries with deterministic overflow suffix.
   - exit taxonomy/envelopes/CLI surface unchanged.

2. `General agent done-gate hardening` (`check --all` + `pr-check --all --base <ref>`)
   - package agent workflow now requires dual-gate completion evidence before reporting done.
   - public command/context docs aligned to require both local gates as completion evidence.
   - CI remains authoritative remote `pr-check`; local `pr-check` required for fast governance feedback.

3. `Multi-block port implementer guard` (`MULTI_BLOCK_PORT_IMPL_FORBIDDEN`)
   - added structural bypass rule for classes implementing generated `*Port` interfaces across multiple generated block packages.
   - marker exception contract finalized:
     - exact marker line `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL`
     - only valid under `src/main/java/blocks/_shared/**`
     - must appear within 5 non-empty lines above class declaration.
   - marker misuse outside `_shared` fails deterministically (`KIND=MARKER_MISUSED_OUTSIDE_SHARED`).
   - dedupe lock: when `PORT_IMPL_OUTSIDE_GOVERNED_ROOT` exists for a file, multi-block findings for that file are suppressed.
   - enforced via `check`/`check --all`/`pr-check` in bypass lane (`exit=7`, `CODE=BOUNDARY_BYPASS`).

4. `Declared allowed deps containment strict marker semantics` (selection-gated)
   - containment verification now runs only when selected blocks for a `projectRoot` include at least one `impl.allowedDeps` block.
   - skip mode is non-failing for containment artifacts/markers and emits deterministic info only when required index exists+parses+non-empty.
   - aggregate marker strictness:
     - `build/bear/containment/applied.marker` must match required hash and canonical `blocks=` CSV.
   - per-block marker strictness:
     - `build/bear/containment/<blockKey>.applied.marker` required for every canonical required block key (`block=` and `hash=` must match).
   - deterministic per-block fail-fast uses lexicographic canonical required block order.
   - lane/remediation split remains locked:
     - generated containment artifacts -> drift lane (`exit 3`, compile remediation)
     - handshake marker issues -> containment-not-verified lane (`exit 74`, marker refresh remediation)

## Next Feature Specs (Locked)

### 1) Multi-block port implementer guard (`P2` completed)

Goal:
- prevent structural collapse where one adapter class implements generated ports owned by multiple blocks.

Scope:
- JVM/Java.
- structural governance only (no style/location policing inside one block).

Detection contract:
- scan Java classes in `src/main/java/**`.
- for each class, collect implemented generated interfaces matching:
  - FQCN starts with `com.bear.generated.`
  - simple name ends with `Port`
- resolve owning block using wiring identity (`entrypointFqcn` package mapping), not package-prefix heuristics.
- owner resolution outcomes:
  - ambiguous owner => `MANIFEST_INVALID` (`exit=2`)
  - missing owner in current manifest scope => ignore (no fail)

Rule:
- if a class implements generated `*Port` interfaces from more than one owning block, fail unless exception marker is present.

Exception marker:
- exact line text: `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL`
- marker must be in the same file and appear within 5 non-empty lines above class declaration.
- marker is valid only under `src/main/java/blocks/_shared/**`; elsewhere it is a deterministic failure (`KIND=MARKER_MISUSED_OUTSIDE_SHARED`).

Failure envelope:
- `exit=7`
- `CODE=BOUNDARY_BYPASS`
- `RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN`
- `PATH=<repo-relative source file>`
- `DETAIL=KIND=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: <implClassFqcn> -> <sortedGeneratedPackageCsv>`
- remediation: move adapters so each class serves one owning block, or place intentional cross-block adapter under `_shared` with explicit marker.

Determinism:
- source traversal sorted by repo-relative path.
- findings sorted by `(path, rule, detail)`.

### 2) General agent done-gate hardening (`P2` completed)

Contract:
- agent completion requires both commands green:
  - `bear check --all --project <repoRoot>`
  - `bear pr-check --all --project <repoRoot> --base <ref>`
- completion reports must include both gate results.

Docs/package updates required:
- `docs/public/commands-check.md`
- `docs/public/commands-pr-check.md`
- `docs/context/user-guide.md`
- `docs/bear-package/.bear/agent/BEAR_AGENT.md`
- `docs/bear-package/.bear/agent/WORKFLOW.md`

### 3) Wiring drift diagnostics (`P2` completed)

Goal:
- eliminate guesswork on wiring drift failures.

Contract:
- when generated wiring drift is detected, output exact drifted wiring paths and reason class:
  - `ADDED`
  - `REMOVED`
  - `CHANGED`
  - `MISSING_BASELINE`
- keep deterministic sorted path order.
- keep one canonical remediation step (`bear fix` or compile/regenerate path) in envelope.
- do not change exit taxonomy for drift.

### 4) `_shared` allowedDeps policy (`P2`, queued after core allowedDeps stabilization)

Direction lock:
- no IR schema changes in this slice.
- path-scoped shared policy under `spec/_shared.policy.yaml` is acceptable follow-up design.
- treat as boundary/governance surface change in `pr-check`.

## Backlog Buckets (P1/P2/P3)

- `P1`
  - `docs/context/backlog/p1-preview-closure-gaps.md` (parked; non-blocking for product development)
- `P2`
  - `docs/context/backlog/p2-bear-fix-generated-only.md` (`Completed`)
  - `docs/context/backlog/p2-declared-allowed-deps-containment.md` (`In Progress`)
- `P3`
  - `docs/context/backlog/p3-maven-allowed-deps-containment.md` (`Queued`)

## Open Risks / Decisions

- Risk: historical references still pointing to `docs/context/roadmap-v0.md` can reintroduce drift if not cleaned.
- Direction lock: BEAR semantic scope follows enforceability + determinism (wrapper-owned where possible), not domain-specific rule coverage.
- Decision lock: do not enforce endpoint-per-block decomposition; preserve structural governance focus over style/location policing.



