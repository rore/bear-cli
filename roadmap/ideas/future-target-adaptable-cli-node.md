---
id: future-target-adaptable-cli-node
title: Target-adaptable CLI and initial Node/TypeScript target
status: queued
priority: medium
commitment: uncommitted
milestone: Future
---

## Normative Constraints

- Preserve deterministic CLI behavior and deterministic failure envelope; the last three stderr lines remain `CODE/PATH/REMEDIATION`.
- Preserve IR as the boundary source of truth (`version: v1`, strict, normalized, wrapper-owned semantics where enforceable).
- Preserve the governance model: `pr-check` signals boundary expansion deterministically; `check` enforces drift plus covered gates.
- Never edit generated artifacts under `build/generated/bear/**`.
- Multi-block continues to require `bear.blocks.yaml` and `--all` gates; completion continues to require both `check --all` and `pr-check --all` and the report schema.

## Non-Goals

- No IR schema changes in this work (no target field, no npm dependency schema).
- No exit code registry expansion; keep the existing numeric set.
- No attempt to make docs fully target-neutral in one sweep; add a small target overlay instead of rewriting the package.

## High-Level Outcome

- CLI core becomes target-agnostic: it orchestrates commands, determinism, and governance.
- Each target owns detection, generation, wiring-only generation for `pr-check`, governed-root computation, and target-specific scanners and verification runner.
- Add `NodeTarget` (TypeScript plus pnpm profile) behind that seam.

## Phase 0: Contract Freeze and Regression Harness

Deliverables:
- Add or extend behavior-parity tests that pin JVM outputs for:
  - `check` stage ordering and exit mapping
  - `check --all` severity aggregation
  - `pr-check` boundary verdict rendering and exit mapping
  - failure-envelope terminal-line rule
- Add one no-behavioral-diff test that compares the pre-refactor JVM path and the post-refactor JVM path once the seam exists and asserts identical stdout and stderr.

Acceptance:
- All existing JVM tests pass without updating golden outputs.

## Phase 1: Introduce a Target Seam

Design rule:
- core must not contain scattered `if (java)` / `if (node)` branches; one dispatch point only.

Deliverables:
1. New kernel-level interfaces:
- `TargetId`: `jvm`, `node`
- `TargetDetector`:
  - `detect(projectRoot) -> DetectedTarget { targetId, confidence, reason }`
  - deterministic ambiguity behavior
- `Target`:
  - `targetId()`
  - `compileSingle(ir, projectRoot, blockKey, indexContext)`
  - `compileAll(blocksIndex, repoRoot, selection)`
  - `generateWiringOnlySingle(ir, projectRoot, blockKey, indexContext)`
  - `generateWiringOnlyAll(...)`
  - `targetChecks()`
  - `runProjectVerification(projectRoot, mode)`
  - `governedRoots(projectRoot, blockKey)`
2. Add `TargetRegistry`.
3. Add one dispatch point in each command service:
- `CheckCommandService` resolves one `Target` per project root.
- `PrCheckCommandService` does the same.
- all-mode services choose per project root.

Ambiguity and unsupported behavior:
- If multiple detectors match with equal confidence, fail with exit `64` and:
  - `CODE=TARGET_AMBIGUOUS`
  - `PATH=project.root`
  - `REMEDIATION=Pin target by adding .bear/target.id (see docs) or remove conflicting build files.`
- Add optional pin file `.bear/target.id` containing exactly `jvm` or `node`.
- If present and invalid, fail with exit `2` using validation-style semantics.

Acceptance:
- JVM behavior remains byte-identical on all existing command tests.
- No generated file-layout changes for JVM in this phase.

## Phase 2: Move JVM-Specific Logic Behind `JvmTarget`

Goal:
- after this phase, core services do not know Java paths, Gradle, or Java syntax.

Deliverables:
- `JvmTarget` owns:
  - existing generation logic
  - wiring-only generation logic used by `pr-check`
  - governed roots and `governedSourceRoots` interpretation
  - all JVM scanners: undeclared-reach, boundary-bypass, placeholder guards, port-impl containment, multi-block port implementer guard, and related checks
  - Gradle `ProjectTestRunner` and containment init-script / marker handshake mechanics
- Core keeps only orchestration:
  - validate and normalize IR
  - drift check
  - target scanners
  - target verification runner
  - consistent envelope rendering

Acceptance:
- JVM outputs remain identical and tests pass.

## Phase 3: Add `NodeTarget` Scan-Only

Supported Node profile:
- required at project root:
  - `package.json`
  - `pnpm-lock.yaml`
  - `tsconfig.json`
- if missing, Node target is not detected.

Governed roots:
- `src/blocks/<blockKey>/**`
- optional shared: `src/blocks/_shared/**`

Node compile output:
- Generate BEAR-owned TypeScript artifacts under `build/generated/bear/`, including:
  - per-block port type declarations derived from `effects.allow`
  - per-block logic interface derived from contract inputs and outputs
  - wrapper shell with one wiring factory
- Create user-owned impl skeleton once:
  - `src/blocks/<blockKey>/impl/<BlockName>Impl.ts`
- Generate Node wiring manifest under:
  - `build/generated/bear/wiring/<blockKey>.wiring.json`

Node check enforcement:
- drift gate uses the same generated-output comparison principle
- import containment forbids relative imports escaping the block root or importing another block root by filesystem path
- imports within the block and within `_shared` stay allowed
- no Node project-test execution yet:
  - `check` prints `project.tests: SKIPPED (target=node)` and continues
  - if everything else passes, exit `0`

Node `pr-check` behavior:
- boundary expansion classification stays target-agnostic via IR diff
- `NodeTarget` provides wiring-only generation for deterministic `pr-check`

`impl.allowedDeps` interaction:
- if any selected block has `impl.allowedDeps` or `_shared` policy scope is active, `check` fails with exit `64`:
  - `CODE=UNSUPPORTED_TARGET`
  - `PATH=<ir-file>`
  - `REMEDIATION=Remove impl.allowedDeps for node target, or use JVM target.`
- `pr-check` still works.

Acceptance:
- a Node fixture repo can:
  - compile a Node block
  - pass `check` for drift plus import containment
  - fail `check` deterministically when an import escapes the block root
  - preserve `pr-check` exit behavior (`0` vs `5`)

## Phase 4: Node Covered Undeclared Reach

Goal:
- regain a conservative covered undeclared-reach story for Node.

Covered scope:
- in governed roots, flag direct usage of imports from:
  - `node:http`, `http`
  - `node:https`, `https`
  - `node:net`, `net`
  - `node:child_process`, `child_process`
- map failures to existing exit code `6` with `CODE=UNDECLARED_REACH`

Acceptance:
- deterministic findings, stable ordering, repo-relative path locators, and existing stage-ordering rules preserved.

## Phase 5: Node Dependency Governance

Deliverables:
- In `pr-check`, add repo-level governance signaling for Node targets:
  - detect `package.json` dependency changes and `pnpm-lock.yaml` changes between base and head
  - classify dependency expansion as `BOUNDARY_EXPANDING` (exit `5`)

Notes:
- repo-level governance only; not per-block allowlisting
- no IR changes required

Acceptance:
- `pr-check --all` on a Node fixture repo returns exit `5` when dependencies expand and `0` when not.

## Phase 6: Node Project Verification Runner

Deliverables:
- `NodeTarget` implements `runProjectVerification` as `pnpm`-based verification under a strict supported profile.
- deterministic output tailing, timeout behavior, and stable exit mapping mirror the Gradle runner contract.
- if pnpm is unavailable, fail deterministically with existing usage or IO semantics rather than expanding the exit registry.

Acceptance:
- test failures map to exit `4`; tool or environment failures map to `74`; the deterministic envelope remains intact.

## Phase 7: Agent Package and Docs Updates

Deliverables:
- Add routed reference doc:
  - `.bear/agent/ref/TARGET_PROFILES.md`
- Update `.bear/agent/BOOTSTRAP.md` routing map with one line to the target profiles doc.
- Update troubleshooting with Node-specific branches:
  - `TARGET_AMBIGUOUS`
  - `UNSUPPORTED_TARGET`
  - Node undeclared-reach findings
- Keep docs changes minimal; do not attempt a full target-neutral rewrite.

Acceptance:
- package consistency tests are updated accordingly.

## Phase 8: Stabilization and No-Tech-Debt Proof

Deliverables:
- Add a target-seam enforcement test proving core services do not reference JVM-only path constants or Java-only scanners directly.
- Add an internal capability-matrix doc listing, per target, which invariants are `ENFORCED`, `PARTIAL`, or `NOT_SUPPORTED`.

## Definition of Done for the Initiative

- JVM behavior is unchanged and fully green.
- Target selection is deterministic and pin-able when needed.
- Node can:
  - compile deterministically
  - pass drift and containment checks deterministically
  - participate in `pr-check` boundary governance deterministically
- CLI core has no JVM or Node conditionals outside the target dispatch boundary.
