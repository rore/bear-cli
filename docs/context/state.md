# BEAR Session State

This file is the short operational handoff for the current work window.
For milestone status and backlog ordering, use `docs/context/program-board.md`.

## Last Updated

2026-02-24

## Current Focus

P2 feature delivery:
- active milestone is `P2`
- post-hard-break follow-through with explicit dual-gate agent completion evidence
- preserve structural governance focus (no endpoint-per-block policy, no style policing)
- declared deps containment strict marker semantics are now implemented with selection gating
- prioritize `_shared` policy follow-up next
- keep deterministic diagnostics high-signal and directly actionable

## Next Concrete Task

next feature sequence (one-by-one):
1. implement `_shared` allowedDeps policy contract after core deps stabilization
2. keep containment-lane smoke fixtures handy for future regressions (`exit 3` drift lane vs `exit 74` verification lane)
3. keep full `:kernel:test` + `:app:test` + root `test` green after each incremental update

## Session Notes

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
  - updated `docs/bear-package/.bear/agent/BEAR_AGENT.md` and `docs/bear-package/.bear/agent/WORKFLOW.md` accordingly.
  - updated invalid-pattern wording to forbid history-based outputs that conflict with current state/contracts (instead of forbidding history lookup itself).
  - verified with `.\gradlew.bat --no-daemon :app:test --tests com.bear.app.BearPackageDocsConsistencyTest`.

- Clarified packaged agent policy semantics in one place:
  - added `Policy Contract (Check)` section to `docs/bear-package/.bear/agent/BEAR_AGENT.md` covering strict hygiene mode, optional policy files, exact-path allowlist format, and `POLICY_INVALID` / `HYGIENE_UNEXPECTED_PATHS` behavior.
  - linked to `.bear/policy/*.txt` header comments for concrete syntax examples.
  - verified with `.\gradlew.bat --no-daemon :app:test --tests com.bear.app.BearPackageDocsConsistencyTest`.

- Synced BEAR package bundle with CLI hardening updates:
  - added package policy templates under `docs/bear-package/.bear/policy/` (`reflection-allowlist.txt`, `hygiene-allowlist.txt`)
  - updated packaged agent docs (`BEAR_AGENT.md`, `WORKFLOW.md`, `IR_QUICKREF.md`) for `check [--strict-hygiene]` and policy/hardening semantics
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
  - deleted root-level duplicate package docs from `docs/bear-package/` (`BEAR_AGENT.md`, `WORKFLOW.md`, `BEAR_PRIMER.md`, `IR_*`, `BLOCK_INDEX_QUICKREF.md`).
  - `.bear/agent/*` is now the only canonical location for distributed agent docs.
  - updated `BearPackageDocsConsistencyTest` to validate files under `docs/bear-package/.bear/agent/doc/*`.
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
  - Added `docs/public/INSTALL.md` for non-demo projects (copy package bundle into `.bear/`, point root `AGENTS.md` to `.bear/agent/BEAR_AGENT.md`).
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
  - explicit “why idempotency / why limited invariants / what is out of scope”
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
- Tightened BEAR package lock policy (`docs/bear-package/BEAR_AGENT.md`) and user/demo docs to forbid IR/ACL workaround mutations after lock failures.
- Tightened BEAR package decomposition policy to reduce single-vs-many block variability: explicit split reasons, mandatory spec-citation evidence for multi-block decomposition, anti-router rule, and workflow/reporting updates.
- Improved `bear check` project-test classification so Gradle wrapper bootstrap/unzip failures map to `IO_ERROR` (including `check --all` root-level detail enrichment with first failing line and short tail context).
- Updated BEAR package docs for v1 IR clarity (`docs/bear-package/IR_EXAMPLES.md`, `docs/bear-package/BEAR_PRIMER.md`) and added doc consistency test coverage (`BearPackageDocsConsistencyTest`).
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
  - updated spec/docs: `docs/public/commands-compile.md`, `docs/public/commands-check.md`, `docs/context/user-guide.md`, `docs/bear-package/WORKFLOW.md`









