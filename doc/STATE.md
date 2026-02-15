# BEAR Project State

This file captures execution state.  
It must stay concise and operational.

Last Updated: 2026-02-15

---

## Current Focus

Phase 6 completed: demo proof loop and scenario-branch model are established in `bear-account-demo`.

---

## Current Phase

Phase: v0 complete; next iteration planning

Checklist:
- [x] Phase 1 deterministic IR validation + normalization
- [x] Phase 2 deterministic JVM compile + golden conformance
- [x] Phase 3 drift regeneration gate
- [x] Phase 4 project test execution gate
- [x] Phase 5 deterministic boundary-expansion signaling in `bear check`
- [x] Phase 6 demo proof: naive fails, corrected passes

Exit condition:
All v0 phase gates and demo proof milestones are complete.

---

## Next Concrete Task

Implement the reserved BEAR-specific demo scenario in `bear-account-demo`:

1. Create `scenario/boundary-expansion-visible` branch
2. Introduce deterministic capability/op expansion change
3. Keep project tests passing
4. Show `bear check` boundary signal + drift failure (`exit 3`)
5. Capture exact expected output snippet in scenario docs

Notes:
- Gradle wrapper is available: use `.\gradlew.bat` (Windows) to build/run without a global Gradle install.
- Canonical IR specification is now `doc/IR_SPEC.md`.
- Canonical demo IR fixture path is `spec/fixtures/withdraw.bear.yaml`.

---

## Phase Pipeline (Do Not Skip)

Phase 1 -- IR foundation  
Phase 2 -- JVM target  
Phase 3 -- Two-file enforcement  
Phase 4 -- `bear check`  
Phase 5 -- Demo proof  

If work does not advance the pipeline toward:

> "Naive withdraw fails. Correct withdraw passes."

It is scope drift.

---

## Upcoming Design Decisions (Not Blocking Current Phase)

- Strictness model for effects enforcement (compile-time vs runtime test only)

---

## Non-Blocking (Parked)

- Early self-hosting (NormalizeIr)
- CLI packaging strategy
- Multi-target ideas
- Enterprise features

---

## v0 Success Condition

All of the following must be true:

- Deterministic IR validation
- Deterministic JVM code generation
- Two-file enforcement
- `bear check` gate
- Demo proves regression prevention

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
