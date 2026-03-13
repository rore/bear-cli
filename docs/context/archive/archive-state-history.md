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




---

## Compaction Snapshot (2026-02-25)

The full pre-compaction `docs/context/state.md` content was archived during context-bootstrap refactor.

### Archived Source: docs/context/state.md (pre-compaction)

# BEAR Session State

This file is the short operational handoff for the current work window.
For milestone status and backlog ordering, use `docs/context/program-board.md`.

## Last Updated

2026-02-25

## Current Focus

P2 feature delivery:
- active milestone is `P2`
- complete BEAR package drift-barrier hard cutover (`BOOTSTRAP` + routed references)
- post-hard-break follow-through with explicit dual-gate agent completion evidence
- preserve structural governance focus (no endpoint-per-block policy, no style policing)
- declared deps containment strict marker semantics are now implemented with selection gating
- `_shared` allowedDeps policy contract is now implemented and stabilized
- Slice 1 containment auto-wiring in `check` is implemented and validated
- generated structural tests are now implemented as evidence-first with strict opt-in
- keep deterministic diagnostics high-signal and directly actionable

## Next Concrete Task

next feature sequence (one-by-one):
1. run stabilization bake period for structural evidence signals and decide strict-mode default timing
2. keep containment-lane smoke fixtures handy for regressions (`exit 3` drift lane vs `exit 74` verification lane)
3. keep full `:kernel:test` + `:app:test` + root `test` green after each incremental update

## Session Notes

- Implemented BEAR package drift-barrier hard cutover:
  - replaced package entrypoint with `.bear/agent/BOOTSTRAP.md`
  - split package contracts into `.bear/agent/CONTRACTS.md`, `.bear/agent/TROUBLESHOOTING.md`, `.bear/agent/REPORTING.md`
  - replaced IR split docs with `.bear/agent/ref/IR_REFERENCE.md`
  - switched package reference folder to canonical `.bear/agent/ref/*`
  - removed legacy package files (legacy package file set)
  - updated active docs/scripts/tests and added exact package file-set checks in `BearPackageDocsConsistencyTest`
  - updated `scripts/sync-bear-demo.ps1` to replace `.bear/agent` directory atomically and verify recursive tree hashes
- Operationalized governance signal accounting in run completion contracts:
  - added mechanically-checkable completion block requirement:
    - `GOVERNANCE_SIGNAL_DISPOSITION`
    - `MULTI_BLOCK_PORT_IMPL_ALLOWED: none|<count>`
    - `JUSTIFICATION` + `TRADEOFF` required when count > 0
  - froze `<count>` definition as number of `MULTI_BLOCK_PORT_IMPL_ALLOWED` lines emitted by `pr-check --all` in that run.
  - applied to all agent-run evaluations (not demo-only).
- Canonical wiring recipe source-of-truth lock:
  - full normative adapter-shape recipe now lives in `docs/bear-package/.bear/agent/CONTRACTS.md` only.
  - `docs/bear-package/.bear/agent/BOOTSTRAP.md` and `docs/bear-package/README.md` now reference that section instead of duplicating recipe prose.

- Grading policy normalization:
  - run-grading rubric in `docs/context/demo-agent-simulation.md` is now canonical for all BEAR run evaluations (not simulation-only).
  - `docs/context/user-guide.md` now explicitly points non-simulated/user-provided run reviews to the same grading rubric.

- Extended isolated simulation contract with mandatory post-run BEAR analysis + grading:
  - `docs/context/demo-agent-simulation.md` now requires:
    - went well / did not go well / lessons / BEAR improvements sections
    - weighted 0..5 run grading across workflow, governance, gates, correctness, and hygiene
    - explicit letter grade and weighted score output
  - added ready-state consistency criteria for demo confidence:
    - repeated isolated runs (`>=5`) with grade distribution threshold and structural consistency expectations

- Simulation runbook branch lock added:
  - `docs/context/demo-agent-simulation.md` now requires per-run metadata:
    - `stepId`
    - `simulationBranch`
    - `baseRef`
  - current default profile documented for greenfield:
    - `stepId=greenfield`
    - `simulationBranch=main` (or fresh branch from `main`)
    - `baseRef=HEAD` for completion evidence

- Added canonical isolated demo simulation runbook:
  - new doc: `docs/context/demo-agent-simulation.md`
  - defines hard isolation requirements (fresh session, demo-only context), exact bootstrap/task prompts, required evidence capture, and pass/fail rubric.
  - clarifies prep automation boundary:
    - `scripts/run-demo-simulated.ps1` is prep/smoke only, not isolated-agent reasoning.
  - linked from:
    - `docs/context/start-here.md`
    - `docs/context/user-guide.md`

- Added repeatable simulated-demo protocol (clean-room approximation):
  - new script: `scripts/run-demo-simulated.ps1`
  - documented in:
    - `docs/context/user-guide.md`
    - `docs/context/start-here.md`
  - flow supports:
    - reset (`clean-demo-branch.ps1`)
    - sync (`sync-bear-demo.ps1`)
    - optional gate smoke (`compile/check/pr-check --all`)
  - this is intentionally documented as simulation only (not true isolated-memory session behavior).

- Implemented P2 next slice: generated structural tests + minimal parity lock.
  - `JvmTarget` now emits:
    - `<BlockName>StructuralDirectionTest`
    - `<BlockName>StructuralReachTest`
  - generated structural line format is frozen:
    - `BEAR_STRUCTURAL_SIGNAL|blockKey=<blockKey>|test=<Direction|Reach>|kind=<KIND>|detail=<detail>`
  - detail formatting is stable and grep-friendly:
    - single line
    - no absolute paths
    - method signature shape uses `<InterfaceSimple>#<methodName>(<paramSimpleCsv>)`
  - generated tests embed expected ordered metadata from generator canonical ordering.
  - generated tests sort mismatches internally; strict mode is opt-in with:
    - `-Dbear.structural.tests.strict=true`
    - single aggregated fail per structural test class.
  - no impl-path policing in structural tests (generated-surface-only checks).
  - parity lock added in CLI tests for unsupported containment preflight between single `check` and `check --all`.
  - docs/package sync updated:
    - `docs/public/commands-check.md`
    - `docs/public/output-format.md`
    - `docs/public/troubleshooting.md`
    - `docs/context/user-guide.md`
    - `docs/bear-package/README.md`
    - `docs/bear-package/.bear/agent/BOOTSTRAP.md`
    - `docs/bear-package/.bear/agent/CONTRACTS.md`

- Implemented Slice 1 containment auto-wiring + post-test marker verification:
  - `ProjectTestRunner` now supports deterministic optional init-script injection and fixed arg order.
  - `check` and `check --all` now inject `-I build/generated/bear/gradle/bear-containment.gradle` only when containment scope is active for that root.
  - containment preflight remains scope-gated and runs before tests only when containment scope is active.
  - `check --all` now runs containment preflight once per containment-enabled `projectRoot` (before root test run), not per block.
  - marker/hash verification now runs only after project tests exit `0` (single and all-mode).
  - `check --all` keeps one root-level project test invocation per containment-enabled root.
  - docs and bear-package guidance updated to remove manual `build.gradle` containment patching requirement for `check`.
  - validation:
    - `.\gradlew.bat --no-daemon :app:test` (green)
    - `.\gradlew.bat --no-daemon test` (green)
    - targeted smoke: fresh marker pass, stale marker fail (`exit 74`), no-scope no-preflight pass, one-invocation-per-root in `check --all` (green).
    - added fail-fast regression proving root-once preflight behavior in `check --all` (no per-block preflight/skip artifact).

- Implemented P2 `_shared` allowedDeps policy (final lock v2):
  - added kernel-owned parser/normalizer for `spec/_shared.policy.yaml` with strict schema validation.
  - integrated `_shared` as conditional containment unit in kernel generation:
    - in scope when policy exists or `_shared` has `.java` sources
    - omitted when out of scope
    - deterministic `_shared` config/index emission
  - generated containment Gradle entrypoint now includes `_shared` compile/classpath wiring and emits deterministic shared-violation marker line.
  - `check` / `check --all` containment gating now includes `_shared` policy/source scope in addition to selected `impl.allowedDeps`.
  - shared containment compile failures map to containment lane (`exit 74`, `CONTAINMENT_NOT_VERIFIED`) with shared-policy remediation (policy update or `_shared` dep removal).
  - `pr-check` / `pr-check --all` now include shared-policy deltas:
    - add/change => `BOUNDARY_EXPANDING`
    - remove => `ORDINARY`
    - malformed policy => `exit 2`, `CODE=POLICY_INVALID`, `PATH=spec/_shared.policy.yaml`
    - `pr-check --all` renders shared policy changes once in `REPO DELTA:` before `SUMMARY`.
  - tests added/updated:
    - kernel parser tests (`SharedAllowedDepsPolicyParserTest`)
    - kernel generation tests for `_shared` inclusion/omission and Gradle wiring (`JvmTargetTest`)
    - app tests for containment gating (`_shared` policy/source/empty dir), shared violation mapping, and shared-policy pr-check deltas
    - renderer/project-test runner coverage for repo delta placement and shared marker parsing.
  - docs/package sync updated:
    - `docs/public/commands-check.md`
    - `docs/public/commands-pr-check.md`
    - `docs/public/output-format.md`
    - `docs/public/troubleshooting.md`
    - `docs/context/user-guide.md`
    - `docs/bear-package/README.md`
    - `docs/bear-package/.bear/agent/BOOTSTRAP.md`
    - `docs/bear-package/.bear/agent/CONTRACTS.md`
    - `docs/context/program-board.md`
    - `docs/context/state.md`
  - verification in-session:
    - `.\gradlew.bat --no-daemon :kernel:test` (green)
    - `.\gradlew.bat --no-daemon :app:test` (green)

- Implemented `Wiring drift diagnostics` feature (deterministic, no semantics change):
  - canonical wiring drift path contract now uses repo-relative on-disk paths:
    - `build/generated/bear/wiring/<blockKey>.wiring.json`
  - missing baseline now emits explicit wiring-path drift signal in addition to baseline-root signal.
  - wiring drift lines are deduped to one line per `(reason, path)` (no mixed `wiring/...` vs `build/generated/bear/wiring/...` duplicates).
  - `CheckResult.detail` now carries bounded wiring drift summary when wiring drift exists:
    - deterministic ordering by `path`, then reason rank:
      - `MISSING_BASELINE`, `REMOVED`, `CHANGED`, `ADDED`
    - capped at 20 entries with deterministic `(+N more)` suffix.
  - `check --all` renderer unchanged; block-level `DETAIL` now surfaces exact wiring drift files via existing flow.
  - tests added/updated:
    - `BearCliTest`:
      - missing baseline includes explicit wiring path
      - canonical wiring path/no-duplicate drift line
      - `check --all` detail includes exact canonical wiring path for wiring drift
      - capped summary behavior (`(+5 more)` with 25 synthetic entries)
    - `DriftAnalyzerTest`:
      - wiring detail ordering test aligned to frozen reason rank
  - docs updated:
    - `docs/public/commands-check.md`
    - `docs/public/output-format.md`
    - `docs/public/troubleshooting.md`
  - verification:
    - `.\gradlew.bat --no-daemon :app:test` (green)
    - `.\gradlew.bat --no-daemon test` (green)
    - focused smoke:
      - `.\gradlew.bat --no-daemon :app:test --tests "com.bear.app.BearCliTest.checkWiringDriftUsesCanonicalPathWithoutDuplicates" --tests "com.bear.app.BearCliTest.checkAllBlockDetailIncludesCanonicalWiringPathForWiringDrift"` (green)

- Implemented P2 containment addendum lock-ins for selection-aware check signaling:
  - single `check` now emits one stdout info line (before `check: OK`) when:
    - selected invocation does not enforce containment surfaces,
    - `build/generated/bear/config/containment-required.json` exists and parses,
    - required block set is non-empty.
  - `check --all` now computes containment-surface enforcement per `projectRoot` from selected blocks and passes that flag into per-block checks.
  - for skipped roots in `check --all`, exactly one contextual info line is attached as `DETAIL:` to the first passing block in that root.
  - renderer now prints `DETAIL:` for `PASS` blocks only when non-blank.
  - lane remediation precision tightened in containment verification:
    - generated containment artifact missing/malformed (`build/generated/bear/...`) -> drift lane (`exit 3`) with compile regeneration remediation.
    - handshake marker missing/stale (`build/bear/containment/applied.marker`) -> containment-not-verified lane (`exit 74`) with Gradle marker refresh remediation.
  - tests added/updated:
    - single-check info emission/no-emission coverage (non-empty/missing/empty containment-required cases)
    - `check --all` first-pass-block detail placement + deterministic rerun output
    - renderer pass-detail placement coverage
  - verification:
    - `.\gradlew.bat --no-daemon :app:test` (green)
    - `.\gradlew.bat --no-daemon test` (green)

- Implemented P2 strict containment marker semantics with selection gating (next slice):
  - containment verification (`containment-required`, aggregate marker, per-block markers) now runs only when `considerContainmentSurfaces=true`.
  - skip mode (`considerContainmentSurfaces=false`) is non-failing for containment files/markers and emits info only when required index exists + parses + has non-empty required block set.
  - strict marker contract now enforced:
    - aggregate `build/bear/containment/applied.marker` must match both required hash and canonical `blocks=` CSV.
    - per-block `build/bear/containment/<blockKey>.applied.marker` required for every canonical required block key; each must match `block=<blockKey>` and required hash.
  - deterministic per-block fail-fast order uses canonical lexicographic required block keys.
  - tests added:
    - `BearCliTest.checkAllowedDepsPerBlockMarkerUsesFirstSortedRequiredBlock`
    - `BearCliTest.checkAllEnforcesContainmentWhenSelectedBlocksIncludeAllowedDeps`
  - docs updated:
    - `docs/public/commands-check.md`
    - `docs/public/troubleshooting.md`
    - `docs/bear-package/README.md`
  - verification:
    - `.\gradlew.bat --no-daemon :app:test --tests "com.bear.app.BearCliTest.checkAllowedDepsPerBlockMarkerUsesFirstSortedRequiredBlock" --tests "com.bear.app.BearCliTest.checkAllEnforcesContainmentWhenSelectedBlocksIncludeAllowedDeps"` (green)
    - `.\gradlew.bat --no-daemon :app:test` (green)
    - `.\gradlew.bat --no-daemon test` (green)

- Hardened general BEAR agent done-gate contract (not demo-only):
  - updated package agent/workflow docs to require both gates before done:
    - `bear check --all --project <repoRoot>`
    - `bear pr-check --all --project <repoRoot> --base <ref>`
  - updated public/operator docs and context guide with the same completion contract and rationale.
  - updated package README workflow note for dual-gate completion evidence.
  - smoke/validation:
    - `.\gradlew.bat --no-daemon :app:test` (green)
    - `.\gradlew.bat --no-daemon test` (green)
    - CLI smoke via `:app:run`:
      - `check --all --project ..\bear-account-demo` executed and deterministically failed on existing demo bypass (`RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN`, exit 7)
      - `pr-check --all --project ..\bear-account-demo --base HEAD` executed and deterministically failed on boundary-expansion deltas (exit 5)

- Implemented `MULTI_BLOCK_PORT_IMPL_FORBIDDEN` structural guard in boundary-bypass pipeline:
  - scanner now detects classes implementing generated ports from multiple generated block packages.
  - explicit marker contract enforced:
    - marker text must be exact `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL`
    - valid only under `src/main/java/blocks/_shared/**`
    - valid only within 5 non-empty lines above class declaration.
  - marker misuse outside `_shared` now fails deterministically (`KIND=MARKER_MISUSED_OUTSIDE_SHARED`).
  - dedupe contract implemented: if `PORT_IMPL_OUTSIDE_GOVERNED_ROOT` exists for a file, multi-block findings for that file are suppressed.
  - remediation routing updated for both `check` and `pr-check`.
  - docs updated:
    - `docs/public/commands-check.md`
    - `docs/public/commands-pr-check.md`
    - `docs/public/output-format.md`
    - `docs/public/troubleshooting.md`
    - `docs/context/user-guide.md`
  - verification:
    - `.\gradlew.bat --no-daemon :app:test` (green)

- Updated planning context to reflect agreed next-feature order:
  - queued `Multi-block port implementer guard` as highest-signal structural follow-up.
  - queued demo done-gate hardening (`check --all` + `pr-check --all`) as mandatory evidence.
  - queued deterministic wiring drift diagnostics ahead of broader allowedDeps expansion.
  - explicitly recorded no endpoint-per-block enforcement decision in program board.
  - locked exact next-feature contracts in `docs/context/program-board.md` (`Next Feature Specs (Locked)`) so future sessions can implement without reinterpretation.

- Synced remaining `pr-check` public/operator docs to current behavior:
  - updated `docs/public/commands-pr-check.md` for dual governance role (IR delta + port-impl containment), `exit 7`, and deterministic wiring-only temp staging note.
  - updated `docs/public/exit-codes.md` registry/command matrix/rank to include structural bypass lane `exit=7`.
  - updated `docs/context/user-guide.md` `pr-check` behavior and quick-exit table for `CODE=BOUNDARY_BYPASS` + `RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT`.
  - updated `docs/public/output-format.md` with `pr-check` boundary-bypass line format + deterministic ordering note.
  - updated `docs/public/troubleshooting.md` with `PORT_IMPL_OUTSIDE_GOVERNED_ROOT` remediation section.
  - updated `docs/public/ENFORCEMENT.md` to include `pr-check` generated-port containment signaling.
  - verified docs/package consistency test remains green:
    - `.\gradlew.bat --no-daemon :app:test --tests com.bear.app.BearPackageDocsConsistencyTest`

- Implemented `pr-check` wiring-only staging + governed port-impl containment enforcement:
  - kernel target contract now includes `generateWiringOnly(...)`.
  - `JvmTarget` now emits wiring-only manifests without generating Java sources/tests/runtime trees.
  - `pr-check` now uses fixed OS-temp internal layout:
    - `<tempRoot>/work/base/base.bear.yaml`
    - `<tempRoot>/generated/base/wiring/<blockKey>.wiring.json`
    - `<tempRoot>/generated/head/wiring/<blockKey>.wiring.json`
  - `pr-check` now enforces deterministic `BOUNDARY_BYPASS` failures (`RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT`) when classes implementing generated `*Port` interfaces are outside governed roots.
  - internal IO fallback line for `pr-check` is now deterministic (`pr-check: IO_ERROR: INTERNAL_IO`) to avoid temp-path leakage.
  - added scanner + CLI coverage:
    - `PortImplContainmentScannerTest`
    - new `BearCliTest` cases for failure, `_shared` pass, fixed temp layout, and wiring-only artifact shape.
  - added kernel coverage:
    - wiring-only output byte-identical to compile wiring
    - wiring-only emits no Java source trees
  - verified green:
    - `.\gradlew.bat --no-daemon :kernel:test`
    - `.\gradlew.bat --no-daemon :app:test`
    - `.\gradlew.bat --no-daemon test`

- Fixed relative `--project` handling for project test execution:
  - normalized `ProjectTestRunner.runProjectTests(...)` to use absolute normalized project roots before wrapper resolution/process launch.
  - added CLI regression `BearCliTest.checkPassesWhenProjectPathIsRelative` to verify `bear check ... --project <relative-path>` succeeds.
  - verified green:
    - `.\gradlew.bat --no-daemon :app:test --tests "com.bear.app.ProjectTestRunnerTest" --tests "com.bear.app.BearCliTest.checkPassesWhenProjectPathIsRelative"`
    - `.\gradlew.bat --no-daemon :app:test`
    - `.\gradlew.bat --no-daemon test`

- Implemented BEAR v1.3.x hard-break containment upgrade:
  - wiring manifests now require `governedSourceRoots` (v2-only wiring contract for containment).
  - kernel emits deterministic `governedSourceRoots` (`blockRootSourceDir`, mandatory reserved `src/main/java/blocks/_shared` at index `1`).
  - check/check-all enforce containment against `governedSourceRoots` and scan containment only inside governed impl `execute(...)` bodies.
  - removed containment policy toggle path; `check-rules` policy support is no longer part of check flows.
  - docs/package guidance updated for always-on execute-scoped containment and `_shared` convention.
  - regression green:
    - `.\gradlew.bat --no-daemon :kernel:test`
    - `.\gradlew.bat --no-daemon :app:test`
    - `.\gradlew.bat --no-daemon test`

- Implemented BEAR v1.3 deterministic containment + consistency + `compile --all`:
  - fixed semantic-port enforcement mismatch by removing v2 fallback from `logicRequiredPorts` to `requiredEffectPorts`.
  - made v2 semantic manifest fields strict (`logicRequiredPorts`, `wrapperOwnedSemanticPorts`, `wrapperOwnedSemanticChecks`, `blockRootSourceDir`) with deterministic `MANIFEST_INVALID` (`exit 2`) classification in single/all check.
  - added governed impl containment scanner (`RULE=IMPL_CONTAINMENT_BYPASS`) with frozen call-shape/resolution/lookup contracts and deterministic finding ordering.
  - containment policy toggle was removed in the hard-break follow-up; containment is always on.
  - added `bear compile --all --project <repoRoot> [--blocks ...] [--only ...] [--fail-fast] [--strict-orphans]` with deterministic per-block output + summary.
  - kernel wiring manifest now emits deterministic `blockRootSourceDir`.
  - synced public docs + package docs/policy templates + demo sync script for the new contracts.
  - verified green:
    - `.\gradlew.bat --no-daemon :kernel:test`
    - `.\gradlew.bat --no-daemon :app:test`
    - `.\gradlew.bat --no-daemon test`

- Implemented governed-impl binding seam closure + sanctioned wrapper factory path:
  - generator now emits `Wrapper.of(<ports...>)` for logic wrappers (constructor `(ports..., Logic)` preserved).
  - boundary scanner now detects governed logic->governed impl bindings in:
    - `src/main/resources/META-INF/services/**`
    - `src/main/java/module-info.java` (`provides ... with ...`)
  - deterministic finding details:
    - `KIND=IMPL_SERVICE_BINDING: <logicFqcn> -> <providerFqcn>`
    - `KIND=IMPL_MODULE_BINDING: <logicFqcn> -> <providerFqcn>`
  - check/check-all now map missing governed wiring fields (`logicInterfaceFqcn` / `implFqcn`) to `MANIFEST_INVALID` (`exit 2`) instead of drift/internal buckets.
  - updated kernel/app tests and docs/package guidance; verified green:
    - `.\gradlew.bat --no-daemon :kernel:test --tests com.bear.kernel.JvmTargetTest`
    - `.\gradlew.bat --no-daemon :app:test --tests com.bear.app.BoundaryBypassScannerTest --tests com.bear.app.BearCliTest`
    - `.\gradlew.bat --no-daemon test`

- Adjusted package guidance to allow practical history use in real projects:
  - replaced strict no-history rule with: history/branches/stashes may be used as auxiliary context, but BEAR decisions must be grounded in current working tree + current IR/index contracts.
  - updated `docs/bear-package/.bear/agent/BOOTSTRAP.md` and `docs/bear-package/.bear/agent/CONTRACTS.md` accordingly.
  - updated invalid-pattern wording to forbid history-based outputs that conflict with current state/contracts (instead of forbidding history lookup itself).
  - verified with `.\gradlew.bat --no-daemon :app:test --tests com.bear.app.BearPackageDocsConsistencyTest`.

- Clarified packaged agent policy semantics in one place:
  - added `Policy Contract (Check)` section to `docs/bear-package/.bear/agent/BOOTSTRAP.md` covering strict hygiene mode, optional policy files, exact-path allowlist format, and `POLICY_INVALID` / `HYGIENE_UNEXPECTED_PATHS` behavior.
  - linked to `.bear/policy/*.txt` header comments for concrete syntax examples.
  - verified with `.\gradlew.bat --no-daemon :app:test --tests com.bear.app.BearPackageDocsConsistencyTest`.

- Synced BEAR package bundle with CLI hardening updates:
  - added package policy templates under `docs/bear-package/.bear/policy/` (`reflection-allowlist.txt`, `hygiene-allowlist.txt`)
  - updated packaged agent docs (`BOOTSTRAP.md`, `CONTRACTS.md`, `ref/IR_REFERENCE.md`) for `check [--strict-hygiene]` and policy/hardening semantics
  - updated `docs/bear-package/README.md` package layout/distributed-file set and canonical runtime path note
  - updated `scripts/sync-bear-demo.ps1` so demo sync copies `.bear/policy/*` templates from package source
  - verified with `.\gradlew.bat --no-daemon :app:test --tests com.bear.app.BearPackageDocsConsistencyTest`

- Implemented BEAR CLI hardening v1 (CLI-only) with passing tests:
  - classloading reflection seam closure in `src/main/**` (`Class.forName`, `loadClass`) with deterministic `RULE=DIRECT_IMPL_USAGE` detail token `KIND=REFLECTION_CLASSLOADING: ...`.
  - deterministic exact-path allowlist parser (`PolicyAllowlistParser`) and new policy code `POLICY_INVALID` (exit `2`).
  - machine-readable reach contract scanner (`app/src/main/resources/reach-surfaces.v1.txt`) with sanitizer-based FQCN/import-bound detection.
  - strict hygiene mode (`--strict-hygiene`) in single/all check with code `HYGIENE_UNEXPECTED_PATHS` (exit `6`) and allowlist support.
  - placeholder impl stub gate (`RULE=IMPL_PLACEHOLDER`) in boundary bypass scanning.
  - all-mode invariant parity: `INVARIANT_VIOLATION` now classified consistently in `check --all` with exit `4`.
  - bounded lock/bootstrap remediation text aligned to BEAR-selected gradle-home + `bear unblock` flow.
  - runtime path migration finalized as breaking change: compile emits runtime classes only to canonical `build/generated/bear/src/main/java/com/bear/generated/runtime/**`; legacy runtime path is no longer emitted and now surfaces as drift if present.
  - added policy files: `.bear/policy/reflection-allowlist.txt`, `.bear/policy/hygiene-allowlist.txt`.
  - updated tests/docs and verified green: `.\gradlew.bat --no-daemon test`.

- Added explicit public promise-boundary wording:
  - `README.md` now includes the precise statement that BEAR enforces declared+supportable semantics by construction and does not over-claim undeclared/non-supportable behavior.
  - `docs/public/ENFORCEMENT.md` now includes a concise `Promise boundary` section with the same contract intent.
  - `docs/public/FOUNDATIONS.md` philosophy now states supportability-by-target as a first-class constraint.
- Applied architect review refinements on public docs:
  - rewrote README top section in plain terms (`what problem`, `what BEAR does`, `what you get`, quickstart first).
  - added explicit bounded coverage language in `README.md` and `docs/public/FOUNDATIONS.md`.
  - softened positioning language to `designed for agent-driven workflows` in `docs/public/FOUNDATIONS.md`.
  - clarified `INVARIANT_VIOLATION -> exit 4` mapping in `docs/public/troubleshooting.md`.
  - made `--all` severity ranking explicitly frozen in `docs/public/exit-codes.md`.
- Completed public docs onboarding upgrade (concise-first, deep-link-second):
  - rewrote README.md for fast orientation (what it is, what it enforces/alerts, reproducible quickstart, focused links).
  - restructured docs/public/INDEX.md into Start in 5 minutes, Understand deeper, and Integrate in CI.
  - hardened docs/public/QUICKSTART.md and docs/public/INSTALL.md with explicit check --all prerequisite (bear.blocks.yaml) and deterministic single-block fallback.
  - added missing public command contract docs/public/commands-unblock.md and linked it from contracts/index/readme/troubleshooting.
  - aligned shared public contract pages for unblock coverage (CONTRACTS.md, exit-codes.md, output-format.md).
  - reduced repetition via role statements in README, MODEL, FOUNDATIONS, and ENFORCEMENT.
- Stabilized internal context wording to current Preview framing:
  - updated docs/context/start-here.md (v1 IR and current command surface).
  - rewrote docs/context/architecture.md and docs/context/governance.md from stale v0 framing to current Preview semantics
- Implemented BEAR Windows Gradle reliability hardening v1:
  - `ProjectTestRunner` now enforces deterministic cache strategy with Windows early fallback (`isolated -> user-cache -> user-cache-retry`) and fixed `200ms` backoff.
  - tightened lock classification for `Failed to delete file` to scoped Gradle temp paths under selected `GRADLE_USER_HOME`.
  - bounded stale-only self-heal (`10m`) for known artifacts (`.zip.lck`, `.zip.part`, `.tmp/*.tmp`, groovy-dsl instrumented `*.tmp`) with deterministic sorted deletion.
  - `check` / `check --all` lock/bootstrap details now include `attempts=...`, `CACHE_MODE=...`, `FALLBACK=...`.
  - `bear unblock` now retries marker delete (3 attempts, `200ms`), stays idempotent when marker missing, and emits deterministic `CODE=UNBLOCK_LOCKED` on persistent lock (exit `74`) with `ATTRS=...`.
  - added/updated tests in `ProjectTestRunnerTest` and `BearCliTest`; verified with `.\gradlew.bat --no-daemon :app:test`.
- Updated marker semantics to remove check dead-end:
  - `check` and `check --all` no longer hard-fail on pre-existing `build/bear/check.blocked.marker`.
  - marker is now advisory; fresh gate run proceeds and clears marker on pass.
  - added/updated tests for advisory marker behavior in single and all-mode check flows.
  - synced updated CLI + package docs into `bear-account-demo` after cleaning demo workspace.
- Clarified invariant-doc split:
  - keep `docs/context/invariant-charter.md` as internal normative catalog.
  - expose a distilled public Preview invariant view in `docs/public/ENFORCEMENT.md` (explicit `ENFORCED` vs `PARTIAL` statuses and coverage caveat).
  - linked `docs/public/ENFORCEMENT.md` from `docs/public/CONTRACTS.md`.
- Added explicit public enforcement/alerting explanation:
  - new `docs/public/ENFORCEMENT.md` summarizing what `check` enforces and what `pr-check` alerts on.
  - linked the new page from `README.md`, `docs/public/INDEX.md`, `docs/public/FOUNDATIONS.md`, and `docs/public/MODEL.md`.
- Clarified BEAR package structure and removed legacy duplication:
  - deleted root-level duplicate package docs from `docs/bear-package/` (`BOOTSTRAP.md`, `CONTRACTS.md`, `BEAR_PRIMER.md`, `IR_*`, `BLOCK_INDEX_QUICKREF.md`).
  - `.bear/agent/*` is now the only canonical location for distributed agent docs.
  - updated `BearPackageDocsConsistencyTest` to validate files under `docs/bear-package/.bear/agent/ref/*`.
  - updated `docs/context/start-here.md` package navigation to `docs/bear-package/.bear/agent/`.
- Refined README opening definition of BEAR to emphasize agent-first execution and PR/CI governance visibility in one concrete sentence.
- Normalized package installation to single-bundle copy semantics:
  - `docs/public/INSTALL.md` now installs by copying `docs/bear-package/.bear/*` into target `.bear/` (no separate doc/runtime copy flow).
  - `scripts/sync-bear-demo.ps1` now sources agent files from `docs/bear-package/.bear/agent/*` and prefers packaged CLI from `docs/bear-package/.bear/tools/bear-cli`.
  - `docs/bear-package/README.md` now documents `.bear/` as the canonical copy bundle source.
  - removed legacy duplicate `docs/bear-package/tools/` path; `.bear/` is now the only package bundle source.
- Aligned public messaging to agent-first framing:
  - updated `README.md` philosophy/mental model sections to make agent execution primary and developer value centered on PR/CI visibility.
  - updated `docs/public/MODEL.md` to split agent execution model from developer visibility model.
- Updated public onboarding docs to package-first runtime usage:
  - README and `docs/public/QUICKSTART.md` now use vendored CLI invocation (`.bear/tools/bear-cli/bin/bear(.bat)`) for demo flows instead of assuming global `bear` on PATH.
  - Added `docs/public/INSTALL.md` for non-demo projects (copy package bundle into `.bear/`, point root `AGENTS.md` to `.bear/agent/BOOTSTRAP.md`).
  - Improved `docs/public/INDEX.md` integration path to include install guidance.
- Packaged CLI runtime is now checked in under `docs/bear-package/.bear/tools/bear-cli/` (`bin/` + `lib/` from installDist output).
- Updated `scripts/sync-bear-demo.ps1`:
  - prefers packaged runtime from `docs/bear-package/.bear/tools/bear-cli`
  - falls back to installDist lookup when packaged runtime is absent or explicit path is requested
  - source package doc paths now use `docs/bear-package/.bear/agent/*`
  - updated post-sync messaging to package-path naming
- Updated `docs/bear-package/README.md` to document vendored CLI runtime as part of package layout/distribution.

- Restructured repository docs into a public/internal split:
  - new public contract set under `docs/public/*` (index, quickstart, model, contracts, command pages, exit/output/troubleshooting/versioning)
  - active internal docs moved to `docs/context/*`
  - historical docs from old `archive`, `demo`, and `m1-eval` moved under `docs/context/archive/*` with prefixes
- Replaced top-level `README.md` with public-entry flow and links to `docs/public/*`.
- Updated `AGENTS.md` navigation paths to `docs/context/*`.
- Removed legacy command-spec docs in favor of `docs/public/commands-*.md` and shared public contract pages.
- Preserved `docs/bear-package/*` unchanged and validated via `BearPackageDocsConsistencyTest`.

- Synced philosophy docs to v1.2 direction:
  - explicit enforcement-by-construction principle
  - explicit â€œwhy idempotency / why limited invariants / what is out of scopeâ€
  - canonical selection rule centralized in `docs/context/ir-spec.md` and referenced from user/workflow/spec docs
- Implemented BEAR v1.2 Final Lock++ core:
  - IR: `idempotency.keyFromInputs`, expanded invariant schema (`kind/scope/params`)
  - validator: strict exactly-one-of idempotency key mode + invariant kind/type/params checks
  - generator: wrapper-owned idempotency flow and wrapper-owned invariant checks on fresh+replay
  - generator: project-global runtime exception at `build/generated/bear/runtime/...` with write-if-diff
  - generator: logic signatures exclude wrapper-owned semantic ports
  - wiring manifest: `logicRequiredPorts`, `wrapperOwnedSemanticPorts`, `wrapperOwnedSemanticChecks`
  - scanner: reflection literal detection + semantic-port suppression/usage bans by manifest identifiers
  - check: marker-first invariant classification + deterministic `MANIFEST_INVALID` overlap gate
- Added/updated tests for:
  - keyFromInputs/idempotency validator rules
  - invariant kind validation and marker/rule serialization contracts
  - runtime exception single-path generation and write-if-diff
  - semantic-port identifier binding and reflection bypass detection
  - manifest overlap and unsupported-target semantic enforcement paths
- Refreshed `spec/golden/compile/withdraw-v1` to runtime-exception layout and updated canonical fixture expectations.
- Created `docs/context/program-board.md` as the single live milestone/backlog board.
- Merged near-term and strategic roadmap content into `docs/context/roadmap.md`.
- Archived old `docs/context/roadmap-v0.md` snapshot at `docs/context/archive/archive-roadmap-v0.md`.
- Moved historical state bulk to `docs/context/archive/archive-state-history.md`.
- Added P1 preview-closure backlog item and normalized backlog metadata contract.
- Added interpretation guardrails to `docs/context/roadmap.md`, `docs/context/program-board.md`, and `docs/context/start-here.md` to separate feature scope from closure queue items.
- Added explicit Preview feature standing section in `docs/context/program-board.md` and aligned navigation docs to reference it.
- Rebased active planning to product-development-first flow and removed release-evidence gating from active queue.
- Implemented `bear fix` command (`single` + `--all`) with tests and synchronized agent-package/docs command surface.
- Migrated core withdraw fixture/golden IR to `v1`.
- Added `spec/golden/compile/withdraw-v1` generated fixture set (including containment outputs).
- Restored full green build after allowed-deps implementation updates (`./gradlew test` passing).
- Added CLI containment acceptance tests (unsupported target, missing marker, stale marker, fresh marker pass).
- Added `pr-check` allowed-deps delta classification tests (add/version-change boundary-expanding, removal ordinary).
- Synced core docs/spec text to v1 + allowed-deps containment behavior (`AGENTS.md`, `docs/context/ir-spec.md`, `docs/public/commands-check.md`, `docs/public/commands-pr-check.md`, `README.md`).
- Fixed generated Gradle containment wiring (`SourceSetOutput.dir` argument order) so demo Gradle integration executes successfully.
- Verified demo end-to-end flow: `compile -> gradle test (containment tasks+marker) -> bear check` returns `check: OK`.
- Added follow-up backlog item for optional non-Gradle parity: `docs/context/backlog/p3-maven-allowed-deps-containment.md`.
- Synced agent-package and user guide docs for allowed-deps containment workflow (`docs/bear-package/*`, `docs/context/user-guide.md`).
- Renamed terminology across implementation/contracts/docs from `pureDeps` to `allowedDeps` (including manifests and `pr-check` category `ALLOWED_DEPS`).
- Added preview demo operator guide `docs/context/archive/demo-preview-demo.md` and wired README/START_HERE navigation to it.
- Updated stale demo references in `docs/context/architecture.md` and `docs/context/roadmap.md` to the new scenario naming/model.
- Hardened `JvmTarget` generated-file sync: fallback to in-place rewrite when replace fails on writable existing targets under lock-like conditions.
- Added kernel regression test `compileReplaceLockFallsBackToInPlaceRewrite` and retained deterministic lock-failure behavior for unrecoverable cases.
- Tightened BEAR package lock policy (`docs/bear-package/.bear/agent/BOOTSTRAP.md`) and user/demo docs to forbid IR/ACL workaround mutations after lock failures.
- Tightened BEAR package decomposition policy to reduce single-vs-many block variability: explicit split reasons, mandatory spec-citation evidence for multi-block decomposition, anti-router rule, and workflow/reporting updates.
- Improved `bear check` project-test classification so Gradle wrapper bootstrap/unzip failures map to `IO_ERROR` (including `check --all` root-level detail enrichment with first failing line and short tail context).
- Updated BEAR package docs for v1 IR clarity (`docs/bear-package/.bear/agent/ref/IR_REFERENCE.md`, `docs/bear-package/.bear/agent/ref/BEAR_PRIMER.md`) and added doc consistency test coverage (`BearPackageDocsConsistencyTest`).
- Added safe cleanup utility `scripts/safe-clean-bear-generated.ps1` with dry-run mode and optional greenfield reset scope.
- Added end-to-end demo sync utility `scripts/sync-bear-demo.ps1` to build CLI, sync vendored demo runtime (`.bear/tools/bear-cli`), and sync `.bear/agent` package files with hash verification.
- Extended `scripts/safe-clean-bear-generated.ps1` to remove full `build/` outputs so BEAR-generated classfiles under `build/classes/**/com/bear/generated` are fully cleaned in demo resets.
- Tracked future expansion idea in `docs/context/future.md`: operation-scoped definitions inside one block to support multi-operation domain aggregation without opcode-router patterns.
- Updated JVM compile generation so user-owned `*Impl.java` stubs are emitted under package-aligned paths (`src/main/java/blocks/<pkg-segment>/impl`, package `blocks.<pkg-segment>.impl`), switched containment metadata to `implDir` (with tolerant legacy parse), and refreshed tests/docs/golden accordingly.
- Implemented BEAR Boundary Hardening v1.1:
  - compile emits deterministic wiring manifest per block (`build/generated/bear/wiring/<blockKey>.wiring.json`)
  - `check`/`check --all` enforce `BOUNDARY_BYPASS` rules (`DIRECT_IMPL_USAGE`, `NULL_PORT_WIRING`, `EFFECTS_BYPASS`)
  - project-test lock/bootstrap now write check-only marker (`build/bear/check.blocked.marker`) and `bear unblock --project <path>` clears it
  - updated CLI/kernel tests and docs/spec (`docs/public/commands-check.md`, `docs/bear-package/*`, `docs/context/user-guide.md`)
- Started incremental `BearCli` modularization:
  - extracted module classes: `CliText`, `AllModeOptionParser`, `AllModeAggregation`, `AllModeRenderer`, `DriftAnalyzer`, `ManifestParsers`, `PrDeltaClassifier`, `UndeclaredReachScanner`, `BoundaryBypassScanner`, `ProjectTestRunner`
  - expanded shared package models in `AllModeModels.java` and delegated large method clusters from `BearCli`
  - added targeted unit tests: `AllModeOptionParserTest`, `AllModeAggregationTest`, `AllModeRendererTest`, `DriftAnalyzerTest`, `ManifestParsersTest`, `PrDeltaClassifierTest`, `BoundaryBypassScannerTest`, `ProjectTestRunnerTest` plus `CliTestAsserts`
  - verified regression gates: `:app:test` and root `test` pass after extraction
- Continued modularization with first command-service extraction:
  - extracted `executeCheck` into `app/src/main/java/com/bear/app/CheckCommandService.java`
  - `BearCli.executeCheck(...)` now delegates to `CheckCommandService.executeCheck(...)`
  - regression gates re-verified: `:app:test` and root `test` pass after this extraction
- Continued modularization with next command-service extractions:
  - extracted `runCheckAll` into `app/src/main/java/com/bear/app/CheckAllCommandService.java`
  - extracted `executePrCheck` into `app/src/main/java/com/bear/app/PrCheckCommandService.java`
  - `BearCli` now delegates both methods to the new services
  - regression gates re-verified: `:app:compileJava`, `:app:test`, and root `test` pass after this extraction
- Continued modularization with additional all-mode command extractions:
  - extracted `runFixAll` into `app/src/main/java/com/bear/app/FixAllCommandService.java`
  - extracted `runPrCheckAll` into `app/src/main/java/com/bear/app/PrCheckAllCommandService.java`
  - `BearCli` now delegates both all-mode commands to the new services
  - promoted minimal helpers (`executeFix`, `toFixBlockResult`, `validateIndexIrNameMatch`, `toPrBlockResult`) to package-private for service reuse
  - regression gates re-verified: `:app:compileJava`, `:app:test`, and root `test` pass after this extraction
- Added explicit demo-cleanup contract to `docs/context/safety-rules.md`:
  - remove generated run artifacts (`build/`, `bin/main`, `bin/test`, `bear.blocks.yaml`, `spec/`, `src/main/java/blocks`)
  - retain `.bear-gradle-user-home/` by default
  - remove cache only on explicit request (`-IncludeGradleCache`)
  - always report both git status and path exists/missing checklist after cleanup
- Implemented Gradle reliability hardening for `check`/`check --all`:
  - project test runner now uses deterministic attempt policy (`isolated + retry + user-cache fallback`, or external env pinned mode)
  - added bounded self-heal for stale wrapper artifacts under `wrapper/dists`
  - lock/bootstrap diagnostics now include deterministic attempt trails
  - marker write failures preserve root-cause classification and append `markerWrite=failed:...`
  - updated command/spec docs and expanded CLI/runner tests
- Implemented BEAR v1.2 Final Lock+ identity/matching precision:
  - compile target contract now receives explicit `blockKey` from CLI resolution (`Target.compile(..., blockKey)`)
  - added shared canonical block identity resolver/canonicalizer flow (`BlockIdentityResolver` + kernel canonicalizer)
  - single-command `compile`/`check`/`fix`/`pr-check` now perform optional index tuple matching with deterministic outcomes (`0/1/>1`)
  - index-resolved canonical mismatch now fails deterministically at `block.name` with index locator detail
  - all-mode services now pass explicit index locator context and parse index with strict duplicate-tuple guard
  - expanded tests for canonicalization, tuple matching, ambiguity, mismatch, and strict parser duplicate tuple guard
  - updated spec/docs: `docs/public/commands-compile.md`, `docs/public/commands-check.md`, `docs/context/user-guide.md`, `docs/bear-package/.bear/agent/CONTRACTS.md`












---

## Archived from state.md (2026-03-13) — Multi-Target Expansion Session Notes

- Phase P (Python Target — Scan Only) spec complete: `requirements.md`, `design.md`, `tasks.md` written in BEAR CLI terse/declarative style. 33 correctness properties defined. 11 implementation tasks covering detection, artifact generation, governed roots, AST-based import containment scanner, drift gate, and `impl.allowedDeps` guard. Inner profile only (`python/service`): strict third-party import blocking. AST-first analysis strategy using Python `ast` module. Reuses `BoundaryDecision` model from Node. `TargetId.PYTHON` to be added.
- Phase P tasks 1-10 complete: All Python target components implemented and tested: `PythonTargetDetector`, `PythonTarget` skeleton, artifact generators (`*_ports.py`, `*_logic.py`, `*_wrapper.py`, `wiring.json`), governed roots computation, AST-based import extraction/detection/resolution, import containment scanner, drift gate, `impl.allowedDeps` guard. All unit tests, property tests, and integration tests passing.
- Phase P task 11 (Fixture projects + integration tests) complete: Created 9 Python fixture projects under `kernel/src/test/resources/fixtures/python/`. Created `PythonFixtureIntegrationTest.java` with 18 integration tests. Full kernel test suite passing with zero JVM/Node regressions.
- Phase B implementation complete: 11 source files, 96 tests passing, branch pushed. Key fixes: `BoundaryDecision.allowed()` rename, `_shared` boundary logic, `StandardOpenOption.SYNC` for WSL2 write caching, `gradle.properties` toolchain path.
- Phase B (Node Target — Scan Only) Kiro spec complete: 36 correctness properties defined. 12 implementation tasks. Fixed `exit 6` → `exit 7` (`BOUNDARY_BYPASS`) typos. Confirmed `TargetId.NODE` already exists from Phase A.
- Completed `P3` target-adaptable CLI preparation as a JVM-only slice: app command orchestration now routes through a kernel-owned `Target` seam via `TargetRegistry`.
- Target-seam package cleanup: generic ownership stays in `com.bear.kernel.target`, JVM-only renderers/scanners and `JvmTarget` live under `com.bear.kernel.target.jvm`.
- Adopted minimap as the canonical live planning workflow under `roadmap/`.
- Added parked Python containment profile and React/TypeScript frontend containment profile.
- Expanded Python containment profile with gap solutions: static `site-packages` scan, commit-time boundary gate model.
- Added `future-multi-target-expansion-plan.md` and `future-multi-target-spec-design.md` to `roadmap/board.md` Ideas section.
- Refined multi-target plan/spec docs with architectural guardrails: two-seam model (`Target` + `AnalyzerProvider`), canonical locator schema, separated target identity from governance profile identity.
- Elevated `TargetDetector` + `.bear/target.id` to an explicit prerequisite epic.
- Added two concentric Python profiles: inner `python/service` (strict) and outer `python/service-relaxed` (pragmatic).
- PR review refinements: Python AST-first analysis, `eval`/`exec`/`compile` in covered power surfaces, broadened React framework surfaces, version-aware detection, `bear init` idea, Go as future target candidate.
- Created `future-python-implementation-context.md` as fast-onboarding summary for Python implementation specs.
