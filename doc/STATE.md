# BEAR Project State

This file captures execution state.  
It must stay concise and operational.

Last Updated: 2026-02-16

---

## Current Focus

Post-v0 milestone execution toward Preview Release: execute M1.1 governance signal hardening.

---

## Current Phase

Phase: M1 workflow proof (completed), M1.1 governance signal hardening (active)

Checklist:
- [x] Add canonical BEAR workflow source texts in `bear-cli` (`doc/m1-canonical/BEAR_PRIMER.md`, `doc/m1-canonical/AGENTS.md`, `doc/m1-canonical/BEAR_AGENT.md`, `doc/m1-canonical/WORKFLOW.md`)
- [x] Add demo-local `BEAR_PRIMER.md`, `AGENTS.md`, `BEAR_AGENT.md`, and `WORKFLOW.md`
- [x] Add demo-local spec pack (`doc/spec/*`) + local IR (`spec/withdraw.bear.yaml`)
- [x] Add demo wrapper + canonical gate scripts (`bin/bear*`)
- [x] Add minimal `verifyNoUndeclaredReach` and wire to demo verification
- [x] Remove demo cheat artifacts (block index + scenario runbooks/snippets) from `bear-account-demo`
- [x] Create canonical realism branches in demo:
  - `scenario/greenfield-build`
  - `scenario/feature-extension`
- [x] Move evaluator scenario matrix/runbooks to `bear-cli/doc/m1-eval/*`
- [x] Update canonical gate contract to deterministic `spec/*.bear.yaml` discovery with actionable no-IR guidance
- [x] Execute isolated acceptance run: greenfield build flow
- [x] Execute isolated acceptance run: feature-extension flow

M1 acceptance status:
Completed.
M1 acceptance required:
1. Non-boundary feature flow succeeds with canonical gate.
2. Boundary-expanding feature flow is IR-first, emits deterministic boundary signal in stale-baseline check, then succeeds after regen + implementation.
3. Workflow is runnable from demo repo alone.

---

## Next Concrete Task

Start M1.1 implementation (entry gate to Preview Release):

1. Define `pr-check` command contract (or equivalent):
   - input model (base branch + project/IR targets)
   - deterministic output schema for boundary deltas
   - exit-code contract
2. Implement base-branch boundary diff classification:
   - ports/ops/effects/contract/invariants delta categories
   - stable, greppable output lines for CI
3. Normalize stale/boundary exit-code semantics:
   - remove ambiguous generic failures for classified boundary/drift paths
4. Add tests/spec coverage and CI usage notes for M1.1 behavior.

Notes:
- Gradle wrapper is available: use `.\gradlew.bat` (Windows) to build/run without a global Gradle install.
- Canonical IR specification is now `doc/IR_SPEC.md`.
- Canonical BEAR workflow source texts for M1 are stored in `doc/m1-canonical/`.

---

## Phase Pipeline (Do Not Skip)

v0 complete -> M1 workflow proof -> M1.1 governance hardening -> preview release -> future maturity decisions

If work does not advance:

> "Isolated demo repo lets a generic agent complete one non-boundary and one boundary-expanding feature via one canonical gate."

It is scope drift.

---

## Upcoming Design Decisions (Not Blocking Current Phase)

- Strictness model for effects enforcement (compile-time vs runtime test only)
- PR governance interface: boundary diff against base branch (`pr-check` shape, output contract, CI integration points)
- Exit-code normalization policy for stale/boundary classifications (stable CI semantics)

---

## Non-Blocking (Parked)

- Early self-hosting (NormalizeIr)
- CLI packaging strategy
- Multi-target ideas
- Enterprise features

---

## M1 Success Condition

All of the following must be true:

- Demo has direct, isolated workflow assets (`BEAR_PRIMER.md`, `AGENTS.md`, `BEAR_AGENT.md`, `WORKFLOW.md`, spec pack)
- Demo has one canonical gate command (`bin/bear-all.*`)
- Canonical gate handles greenfield deterministically when no IR files exist
- Demo does not contain evaluator answer-key scenario docs
- Canonical scenario branches are `scenario/greenfield-build` and `scenario/feature-extension`
- Demo includes minimal no-undeclared-reach verification in test/check path
- First-time isolated agent can explain core BEAR concepts from demo-local docs before coding

Status:
- Achieved.

---

## Session Notes

Append short bullet points only.  
No essays. No philosophy.

- Added Gradle wrapper scripts + wrapper jar.
- Aligned docs to v0 clarified scope: deterministic constraint compiler, structured ports, explicit guarantees/non-guarantees.
- Locked canonical demo IR details (`version`, invariant `kind`, idempotency `store.port/getOp/putOp`).
- Implemented `bear validate <file>` end-to-end (strict schema + semantic validation, deterministic normalization, canonical YAML emission) with spec fixtures + golden output.
- Stabilized Gradle behavior for Windows locks: wrapper defaults `GRADLE_USER_HOME` to temp and Gradle build outputs are redirected to temp (`bear-cli-build/<runId>`).
- Implemented `bear compile <ir-file> --project <path>` with validate+normalize pre-pass and deterministic generation to `<project>/build/generated/bear`.
- Implemented two-tree ownership model: generated artifacts are fully regenerated; user-owned `<BlockName>Impl.java` is created once under `src/main/java` and then preserved.
- Added compile command spec at `spec/commands/compile.md`.
- Added compile coverage in app/kernel tests for argument handling, deterministic regeneration, and impl preservation.
- Parked feature request for later: configurable compile base package (`--base-package`) so adopter apps can own package namespace.
- Integrated minimal demo wiring with `../bear-account-demo`: manual compile works, generated sourceSets are wired, demo tests run green, and user impl preservation was verified.
- Fixed generator bug in runtime invariant emission for idempotency replay path (generated code now references correct result variable).
- Added compile golden corpus at `spec/golden/compile/withdraw` and kernel tests now assert exact generated file list/content against golden.
- Tightened generated replay decoding: `hit=true` now requires all `result.*` fields; missing field fails fast with deterministic error text.
- Updated compile/IR docs to explicitly define current v0 replay-hit behavior and `hit` protocol semantics.
- Implemented `bear check <ir-file> --project <path>` v1 drift gate: validate + temp compile + deterministic diff against `<project>/build/generated/bear`.
- Added deterministic drift reporting (`ADDED`/`REMOVED`/`CHANGED`) and explicit missing-baseline failure (`MISSING_BASELINE`) with exit code `3`.
- Added `spec/commands/check.md` to freeze v1 check command contract and non-mutation semantics.
- Tightened `bear check` baseline semantics: empty generated baseline tree now counts as `MISSING_BASELINE` (same deterministic drift failure path).
- Clarified `check` path semantics in spec: reported drift paths are relative to `build/generated/bear` root.
- Extended `bear check` to execute project Gradle wrapper tests after drift pass, with dedicated test-failure exit code (`4`) and deterministic timeout handling.
- Added deterministic test-failure reporting with normalized 40-line output tail and explicit drift short-circuit behavior (tests do not run on drift).
- Added normative governance policy in `doc/GOVERNANCE.md` and aligned `doc/ARCHITECTURE.md`, `doc/ROADMAP.md`, `doc/START_HERE.md`, `doc/PROMPT_BOOTSTRAP.md`, and `README.md` to boundary-governance-first framing.
- Expanded `doc/ARCHITECTURE.md` with explicit philosophy and agentic process contract sections (role split, default BEAR loop, and boundary-signal litmus).
- Added `doc/NORTH_STAR.md` to capture broader motivation and long-horizon success criteria, with cross-links from README/START_HERE/ARCHITECTURE/ROADMAP/PROMPT_BOOTSTRAP.
- Expanded broader-vision docs to include post-v0 boundary-usage semantics direction: updated `doc/NORTH_STAR.md`, added post-v0 hardening stages in `doc/ROADMAP.md`, and added concrete boundary-usage constraint candidates in `doc/FUTURE.md`.
- Reframed `doc/ROADMAP.md` into a target-phase roadmap (Deterministic Core -> Structural Enforcement -> Classification -> Agent-Native -> Controlled Behavioral Visibility) with explicit 12-month success definition and v0 execution cross-links.
- Added `doc/ROADMAP_V0.md` as the concrete execution tracker for current v0 delivery, while keeping `doc/ROADMAP.md` as broader target strategy.
- Added future roadmap direction for side-effect taxonomy and clarified principle "side-effect gating, not library gating" in target roadmap/future docs.
- Repositioned side-effect gating principle: concise philosophy statement in `doc/ARCHITECTURE.md`, phase-scoped placement in `doc/ROADMAP.md` (Phase 2), detailed taxonomy retained in `doc/FUTURE.md`.
- Started Phase 5 implementation: compile now emits deterministic `bear.surface.json`; check now classifies boundary expansion from manifests with deterministic warning/failure semantics and boundary signal lines.
- Added BEAR logo assets under `assets/logo/` (lockup + mark SVG) and wired lockup logo into `README.md`.
- Switched `README.md` logo source to the user-provided `assets/logo/bear.png` for exact visual match.
- Completed Phase 6 demo proof in `bear-account-demo` with branch-per-scenario model: `main` spec-first baseline, `scenario/naive-fail-withdraw` (deterministic exit `4`), and `scenario/corrected-pass-withdraw` (deterministic exit `0`), plus scenario matrix/runbook docs.
- Split post-v0 execution into M1 (minimal workflow proof) and future packaging/versioning (not immediate), with roadmap/state updates.
- Added canonical BEAR workflow source texts under `doc/m1-canonical/` and aligned demo-copied workflow resources to source-of-truth references.
- Implemented demo-local M1 assets in `bear-account-demo`: `AGENTS.md`, `BEAR_AGENT.md`, `WORKFLOW.md`, spec pack, local IR, canonical scripts (`bin/bear*`), and `verifyNoUndeclaredReach` wired into tests.
- Synced canonical naming model in `bear-cli/doc/m1-canonical/`: thin bootstrap in `AGENTS.md` and BEAR contract in `BEAR_AGENT.md`.
- M1 realism reset started: scenario evaluation guidance moves to `bear-cli/doc/m1-eval/*`; demo should keep only realistic project artifacts.
- Completed realism reset core wiring: removed demo scenario answer-key docs, created canonical branches (`scenario/greenfield-build`, `scenario/feature-extension`), and moved evaluator runbooks into `bear-cli/doc/m1-eval/`.
- Added M1 comprehension hardening requirements: local BEAR primer, stronger read-order guidance, and greenfield-safe canonical gate behavior.
- Added evaluator operator runbook `doc/m1-eval/RUN_MILESTONE.md` with end-to-end instructions for running both M1 scenario flows in isolated demo sessions.
- Added roadmap item for PR/base-branch boundary diff classification and CI boundary-expansion marking (plus exit-code normalization hardening) as post-M1 governance improvement.
