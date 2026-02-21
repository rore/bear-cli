# STATE History Archive

This file preserves historical long-form `docs/context/state.md` content moved during documentation compaction on 2026-02-19.

---

# BEAR Project State

This file captures execution state.  
It must stay concise and operational.

Last Updated: 2026-02-19

---

## Current Focus

Preview Release hardening and packaging readiness: frozen command contracts, deterministic failure envelopes, and operator-facing reliability guidance.

---

## Current Phase

Phase: M1 workflow proof (completed), M1.1 governance signal hardening (completed), Preview Release preparation (active)

Checklist:
- [x] Add canonical BEAR workflow source texts in `bear-cli` (`docs/bear-package/BEAR_PRIMER.md`, `docs/bear-package/AGENTS.md`, `docs/bear-package/BEAR_AGENT.md`, `docs/bear-package/WORKFLOW.md`)
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

Finalize preview-release checkpoint:

1. Freeze command contracts and exit-code policy as release baseline (`check`, `pr-check`, `exit-codes`).
2. Publish user-facing lock-troubleshooting guidance and confirm `IO_ERROR` classification path.
3. Draft and review preview checkpoint release notes.
4. Open first post-preview P2 backlog item (`bear fix` for generated artifacts only).

Notes:
- Gradle wrapper is available: use `.\gradlew.bat` (Windows) to build/run without a global Gradle install.
- Canonical IR specification is now `docs/context/ir-spec.md`.
- BEAR package source texts for distributed workflow docs are stored in `docs/bear-package/`.

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
- Added compile command spec at `docs/public/commands-compile.md`.
- Added compile coverage in app/kernel tests for argument handling, deterministic regeneration, and impl preservation.
- Parked feature request for later: configurable compile base package (`--base-package`) so adopter apps can own package namespace.
- Integrated minimal demo wiring with `../bear-account-demo`: manual compile works, generated sourceSets are wired, demo tests run green, and user impl preservation was verified.
- Fixed generator bug in runtime invariant emission for idempotency replay path (generated code now references correct result variable).
- Added compile golden corpus at `spec/golden/compile/withdraw` and kernel tests now assert exact generated file list/content against golden.
- Tightened generated replay decoding: `hit=true` now requires all `result.*` fields; missing field fails fast with deterministic error text.
- Updated compile/IR docs to explicitly define current v0 replay-hit behavior and `hit` protocol semantics.
- Implemented `bear check <ir-file> --project <path>` v1 drift gate: validate + temp compile + deterministic diff against `<project>/build/generated/bear`.
- Added deterministic drift reporting (`ADDED`/`REMOVED`/`CHANGED`) and explicit missing-baseline failure (`MISSING_BASELINE`) with exit code `3`.
- Added `docs/public/commands-check.md` to freeze v1 check command contract and non-mutation semantics.
- Tightened `bear check` baseline semantics: empty generated baseline tree now counts as `MISSING_BASELINE` (same deterministic drift failure path).
- Clarified `check` path semantics in spec: reported drift paths are relative to `build/generated/bear` root.
- Extended `bear check` to execute project Gradle wrapper tests after drift pass, with dedicated test-failure exit code (`4`) and deterministic timeout handling.
- Added deterministic test-failure reporting with normalized 40-line output tail and explicit drift short-circuit behavior (tests do not run on drift).
- Added normative governance policy in `docs/context/governance.md` and aligned `docs/context/architecture.md`, `docs/context/roadmap.md`, `docs/context/start-here.md`, `docs/context/prompt-bootstrap.md`, and `README.md` to boundary-governance-first framing.
- Expanded `docs/context/architecture.md` with explicit philosophy and agentic process contract sections (role split, default BEAR loop, and boundary-signal litmus).
- Added `docs/context/north-star.md` to capture broader motivation and long-horizon success criteria, with cross-links from README/START_HERE/ARCHITECTURE/ROADMAP/PROMPT_BOOTSTRAP.
- Expanded broader-vision docs to include post-v0 boundary-usage semantics direction: updated `docs/context/north-star.md`, added post-v0 hardening stages in `docs/context/roadmap.md`, and added concrete boundary-usage constraint candidates in `docs/context/future.md`.
- Reframed `docs/context/roadmap.md` into a target-phase roadmap (Deterministic Core -> Structural Enforcement -> Classification -> Agent-Native -> Controlled Behavioral Visibility) with explicit 12-month success definition and v0 execution cross-links.
- Added `docs/context/roadmap-v0.md` as the concrete execution tracker for current v0 delivery, while keeping `docs/context/roadmap.md` as broader target strategy.
- Added future roadmap direction for side-effect taxonomy and clarified principle "side-effect gating, not library gating" in target roadmap/future docs.
- Repositioned side-effect gating principle: concise philosophy statement in `docs/context/architecture.md`, phase-scoped placement in `docs/context/roadmap.md` (Phase 2), detailed taxonomy retained in `docs/context/future.md`.
- Started Phase 5 implementation: compile now emits deterministic `bear.surface.json`; check now classifies boundary expansion from manifests with deterministic warning/failure semantics and boundary signal lines.
- Added BEAR logo assets under `assets/logo/` (lockup + mark SVG) and wired lockup logo into `README.md`.
- Switched `README.md` logo source to the user-provided `assets/logo/bear.png` for exact visual match.
- Completed Phase 6 demo proof in `bear-account-demo` with branch-per-scenario model: `main` spec-first baseline, `scenario/naive-fail-withdraw` (deterministic exit `4`), and `scenario/corrected-pass-withdraw` (deterministic exit `0`), plus scenario matrix/runbook docs.
- Split post-v0 execution into M1 (minimal workflow proof) and future packaging/versioning (not immediate), with roadmap/state updates.
- Added canonical BEAR workflow source texts under `docs/bear-package/` and aligned demo-copied workflow resources to source-of-truth references.
- Implemented demo-local M1 assets in `bear-account-demo`: `AGENTS.md`, `BEAR_AGENT.md`, `WORKFLOW.md`, spec pack, local IR, canonical scripts (`bin/bear*`), and `verifyNoUndeclaredReach` wired into tests.
- Synced canonical naming model in `bear-cli/docs/bear-package/`: thin bootstrap in `AGENTS.md` and BEAR contract in `BEAR_AGENT.md`.
- M1 realism reset started: scenario evaluation guidance moves to `bear-cli/doc/m1-eval/*`; demo should keep only realistic project artifacts.
- Completed realism reset core wiring: removed demo scenario answer-key docs, created canonical branches (`scenario/greenfield-build`, `scenario/feature-extension`), and moved evaluator runbooks into `bear-cli/doc/m1-eval/`.
- Added M1 comprehension hardening requirements: local BEAR primer, stronger read-order guidance, and greenfield-safe canonical gate behavior.
- Added evaluator operator runbook `docs/context/archive/m1-eval-run-milestone.md` with end-to-end instructions for running both M1 scenario flows in isolated demo sessions.
- Added roadmap item for PR/base-branch boundary diff classification and CI boundary-expansion marking (plus exit-code normalization hardening) as post-M1 governance improvement.
- Implemented `bear pr-check <ir-file> --project <path> --base <ref>` with merge-base comparison, deterministic `pr-delta` output (`PORTS|OPS|IDEMPOTENCY|CONTRACT|INVARIANTS`), explicit boundary verdict (`exit 5`), repo-relative path enforcement, and base-missing-as-empty classification.
- Added `pr-check` spec at `docs/public/commands-pr-check.md`, linked `check` doc to `pr-check` responsibility split, and added CLI tests covering args/path rules, base-missing behavior, deterministic ordering, idempotency add semantics, and exit-code contract.
- Tightened `pr-check` frozen contract details: explicit missing-head-IR behavior (`READ_HEAD_FAILED`, exit `74`), stable git/IO reason prefixes, and concrete output examples for boundary vs ordinary-only deltas.
- Merged harness-engineering additions into existing roadmap structure (not override): preserved prior phase/milestone plan while adding explicit preview contract requirements (actionable failures, deterministic undeclared-reach enforcement, and prioritized P2/P3 backlog) in `docs/context/roadmap.md` and `docs/context/roadmap-v0.md`.
- Tightened preview risk controls from roadmap review feedback: added scoped self-hosting definition/fallback, explicit undeclared-reach exit code + JVM detection surface/exclusions, unified exit-code registry requirement, and failure-envelope compliance test requirement in roadmap docs.
- Integrated demo M1.1 PR governance assets in `bear-account-demo`: added `bin/pr-gate.ps1`, `bin/pr-gate.sh`, `.github/workflows/pr-gate.yml`, and updated `README.md`/`WORKFLOW.md` for explicit `origin/main` usage.
- Created demo branches `scenario/pr-non-boundary` (impl/test-only delta) and `scenario/pr-boundary-expand` (invariant relaxation boundary delta) and validated with `bear pr-check` against `origin/main` (`0` vs `5` behavior).
- Extended evaluator runbooks in `docs/context/archive/m1-eval-run-milestone.md` and `docs/context/archive/m1-eval-scenarios.md` to include PR governance pass/fail scenario runs and expected outputs.
- Added explicit expected boundary `pr-gate` output snippet to `docs/context/archive/m1-eval-run-milestone.md` (with note that additional boundary delta lines are acceptable while exit `5` + fail verdict remain required).
- Updated `bear-account-demo` PR gate setup to avoid cross-repo checkout failures: vendored BEAR CLI bundle under `tools/bear-cli`, wrappers now prefer vendored bundle, and CI workflow runs `pr-gate` directly from repo assets.
- Confirmed hosted CI proof on GitHub PRs for M1.1: non-boundary scenario run emitted `pr-check: OK: NO_BOUNDARY_EXPANSION`; boundary scenario run emitted deterministic boundary delta + `pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED` with exit `5`.
- Added hosted CI evidence snippets to `docs/context/archive/m1-eval-run-milestone.md`.
- Implemented preview failure-envelope hardening in CLI: all non-zero exits in `validate`/`compile`/`check`/`pr-check` now emit deterministic footer (`CODE`/`PATH`/`REMEDIATION`) exactly once and as terminal stderr lines.
- Added centralized exit/failure-code registry at `docs/public/exit-codes.md` and aligned command specs + CLI contract tests, including `pr-check` exit `5` boundary verdict envelope and app-level internal fault-injection coverage.
- Added user-facing CLI documentation at `docs/context/user-guide.md` and linked it from `README.md` and `docs/context/start-here.md` so command usage + failure envelope semantics are discoverable outside command-spec docs.
- Expanded `docs/context/user-guide.md` intro to explicitly state BEARâ€™s intent in AI-assisted development: preserve implementation speed while making boundary expansion deterministic, reviewable, and CI-actionable.
- Clarified `docs/context/user-guide.md` operating model: developers provide domain intent/review while agents are expected to handle IR updates and BEAR command mechanics within declared boundaries.
- Added normative invariant catalog at `docs/context/invariant-charter.md` and linked it from `docs/context/start-here.md`, `docs/context/architecture.md`, `docs/context/governance.md`, `docs/context/roadmap.md`, `docs/context/roadmap-v0.md`, and `docs/context/user-guide.md`.
- Activated preview undeclared-reach enforcement in `bear check`: deterministic JVM covered direct-HTTP detection, dedicated exit `6`, `CODE=UNDECLARED_REACH`, and fixed remediation guidance.
- Updated command contracts in `docs/public/commands-check.md` and `docs/public/exit-codes.md` to freeze undeclared-reach stage ordering, detection scope/exclusions, and failure-envelope semantics.
- Added CLI tests for undeclared-reach detection scope, exclusions, deterministic ordering, drift precedence, envelope correctness, and check-stage short-circuit before project tests.
- Added repo-level multi-block contracts and parser model: new `bear.blocks.yaml` spec (`spec/repo/block-index.md`) and app-side index parser/validation.
- Implemented `bear check --all` and `bear pr-check --all` orchestration with canonical block ordering, `--only`, `--fail-fast`, optional `--strict-orphans`, deterministic summaries, and explicit severity-rank exit aggregation.
- Refactored single-block `check`/`pr-check` execution into structured result paths used by both single-block and `--all` modes (no stderr/footer parsing).
- Added deterministic global multi-block footer code `REPO_MULTI_BLOCK_FAILED` and updated exit-code/spec docs accordingly.
- Added CLI tests for `--all` pass/fail behavior, fail-fast skip semantics, strict orphan scan behavior, unknown `--only` handling, disabled-block skips, and mixed `pr-check --all` classifications.
- Upgraded compile ownership to block-scoped regeneration in shared roots (no full `build/generated/bear` wipe per compile).
- Switched surface marker contract to per-block files at `build/generated/bear/surfaces/<blockKey>.surface.json` with `surfaceVersion: 2`.
- Refactored `check --all` to run structural checks per block, then undeclared-reach/tests once per `projectRoot`, and emit root-level summary counters.
- Added managed-root default orphan guard and strict repo-wide orphan/legacy marker scans for `check --all` / `pr-check --all`.
- Removed block-index unique-enabled-`projectRoot` constraint and aligned tests/specs for shared-root multi-block behavior.
- Added evaluator runbook `docs/context/archive/m1-eval-run-multi-block.md` and linked it from `docs/context/archive/m1-eval-scenarios.md` for two-scenario shared-root multi-block demo execution.
- Tightened demo realism packaging: removed evaluator scenario docs from `bear-account-demo` and aligned distributed BEAR content (`BEAR_AGENT.md`/`WORKFLOW.md` + `docs/bear-package/*`) to no-`doc/spec/*` assumptions.
- Upgraded BEAR package definition to portable generic v0.1: added `docs/bear-package/IR_QUICKREF.md` and `docs/bear-package/IR_EXAMPLES.md`, enforced anti-reverse-engineering guidance in `BEAR_AGENT.md`, expanded deterministic workflow/failure triage docs, and synced the updated package files into `bear-account-demo`.
- De-domainized BEAR package IR samples/quickref terms (removed account-like names such as balance/ledger/audit-stream) to prevent steering eval agents toward the account-demo solution shape.
- Updated `docs/context/archive/m1-eval-run-multi-block.md` Scenario 1 prompt to the stronger multi-block-forcing version (scheduled durable async transfers + retries + tier limits + audit/event requirements) and marked it as the tracked rerun prompt.
- Hardened multi-block index governance at eval/package level: added `docs/bear-package/BLOCK_INDEX_QUICKREF.md`, updated package/runbook guidance to require index + `--all` for multi-block runs, switched runbook acceptance to canonical `projectRoot: .`, and updated demo `bear-all`/`pr-gate` wrappers to fail `64` when multiple IR files exist without `bear.blocks.yaml`.
- Updated CLI block-index parsing to accept `projectRoot: .` (canonical repo root) while keeping `ir: .` invalid, with parser tests added in `BlockIndexParserTest` to lock both behaviors.
- Added mandatory pre-run cleanup procedure to `docs/context/archive/m1-eval-run-multi-block.md` with deterministic path list and command snippet to reset demo artifacts before each rerun.
- Updated `docs/context/archive/m1-eval-run-multi-block.md` Scenario 1 prompt to a reduced-scope multi-block version (DEPOSIT+TRANSFER immediate APIs, create/cancel scheduled transfers, fixed single retry, single daily limit) to keep multi-block pressure with lower implementation burden.
- Further reduced Scenario 1 prompt scope in `docs/context/archive/m1-eval-run-multi-block.md` (immediate DEPOSIT only, scheduled create-only, async worker, no retries, fixed per-account daily limit) to preserve multi-block pressure with minimal implementation load.
- Hardened agent/runbook anti-bypass rules after an implementation-first greenfield run: package docs now require IR->validate->compile before implementation edits, forbid replacement contracts bypassing BEAR generation, and mark greenfield implementation-first behavior as eval failure.
- Synced hardened anti-bypass rules into demo-distributed `BEAR_AGENT.md` and `WORKFLOW.md` so isolated eval agents receive the same IR-first constraints as package source docs.
- Added minimal-sufficient-design policy to package+demo BEAR instructions, including required architecture rationale when new production components are introduced.
- Updated `docs/context/archive/m1-eval-run-multi-block.md` Scenario 1 prompt to a lower-complexity multi-block target (scheduled debit worker, no durability/external integration requirement) while preserving the BEAR checks under evaluation.
- Corrected `docs/context/archive/m1-eval-run-multi-block.md` to match executed flow: Scenario 1 prompt restored to the run-used version and Scenario 2 now explicitly starts from `scenario/2-extension-from-greenfield-output` with a new-block notification extension prompt.
- Added runbook timeout handling note for Scenario 2: exit `124` from tooling/harness during long gate/test commands is treated as infrastructure timeout noise and requires rerun with longer timeout before evaluating BEAR outcomes.
- Refined Scenario 2 eval prompt to a minimal feature phrasing that still pressures decomposition into existing-block extension plus a separate async failure-notification responsibility.
- Hardened JVM codegen for non-decimal blocks by always importing `java.math.BigDecimal` in generated entrypoints and preventing doubled `Port` suffixes in generated interface names.
- Made compile generation lock-resilient on Windows using staging-tree sync plus bounded retry/backoff and deterministic `WINDOWS_FILE_LOCK` IO diagnostics.
- Updated `bear check` project-test execution to default `GRADLE_USER_HOME` isolation and classify Gradle wrapper lock signatures as `IO_ERROR` (not `TEST_FAILURE`).
- Strengthened BEAR package rules to stop on tooling/IO defects and forbid workaround non-`*Impl.java` classes under `com.bear.generated.*`.
- Verified full CLI test suite passes after stabilization changes (`.\gradlew.bat --no-daemon test`).
- Added user-facing lock troubleshooting and classification guidance to `docs/context/user-guide.md`.
- Marked M1.1 governance signal hardening as complete; moved active phase to Preview Release preparation.
- Added preview checkpoint release note (`docs/context/release-preview-checkpoint-2026-02-19.md`).
- Opened first P2 backlog item for `bear fix` generated-only scope (`docs/context/backlog/p2-bear-fix-generated-only.md`).



