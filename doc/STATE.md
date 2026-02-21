# BEAR Session State

This file is the short operational handoff for the current work window.
For milestone status and backlog ordering, use `doc/PROGRAM_BOARD.md`.

## Last Updated

2026-02-21

## Current Focus

P2 feature delivery:
- active milestone is `P2`
- boundary hardening v1.1 implementation is complete in CLI/kernel/docs/tests
- active validation focus is demo sync and post-hardening greenfield behavior
- compile/check reliability remains focused on deterministic lock/bootstrap triage behavior

## Next Concrete Task

Close boundary-hardening rollout in demo and operator flow:
1. sync updated CLI + BEAR package docs into demo
2. run greenfield demo gate and verify `BOUNDARY_BYPASS`/blocked-marker behavior end-to-end
3. capture any false-positive patterns and tune deterministic scanner rules only if needed
4. prepare commit/review split (kernel/app/spec/docs)

## Session Notes

- Created `doc/PROGRAM_BOARD.md` as the single live milestone/backlog board.
- Merged near-term and strategic roadmap content into `doc/ROADMAP.md`.
- Archived old `doc/ROADMAP_V0.md` snapshot at `doc/archive/ROADMAP_V0.md`.
- Moved historical state bulk to `doc/archive/STATE_HISTORY.md`.
- Added P1 preview-closure backlog item and normalized backlog metadata contract.
- Added interpretation guardrails to `doc/ROADMAP.md`, `doc/PROGRAM_BOARD.md`, and `doc/START_HERE.md` to separate feature scope from closure queue items.
- Added explicit Preview feature standing section in `doc/PROGRAM_BOARD.md` and aligned navigation docs to reference it.
- Rebased active planning to product-development-first flow and removed release-evidence gating from active queue.
- Implemented `bear fix` command (`single` + `--all`) with tests and synchronized agent-package/docs command surface.
- Migrated core withdraw fixture/golden IR to `v1`.
- Added `spec/golden/compile/withdraw-v1` generated fixture set (including containment outputs).
- Restored full green build after allowed-deps implementation updates (`./gradlew test` passing).
- Added CLI containment acceptance tests (unsupported target, missing marker, stale marker, fresh marker pass).
- Added `pr-check` allowed-deps delta classification tests (add/version-change boundary-expanding, removal ordinary).
- Synced core docs/spec text to v1 + allowed-deps containment behavior (`AGENTS.md`, `doc/IR_SPEC.md`, `spec/commands/check.md`, `spec/commands/pr-check.md`, `README.md`).
- Fixed generated Gradle containment wiring (`SourceSetOutput.dir` argument order) so demo Gradle integration executes successfully.
- Verified demo end-to-end flow: `compile -> gradle test (containment tasks+marker) -> bear check` returns `check: OK`.
- Added follow-up backlog item for optional non-Gradle parity: `doc/backlog/P3_MAVEN_ALLOWED_DEPS_CONTAINMENT.md`.
- Synced agent-package and user guide docs for allowed-deps containment workflow (`doc/bear-package/*`, `doc/USER_GUIDE.md`).
- Renamed terminology across implementation/contracts/docs from `pureDeps` to `allowedDeps` (including manifests and `pr-check` category `ALLOWED_DEPS`).
- Added preview demo operator guide `doc/demo/PREVIEW_DEMO.md` and wired README/START_HERE navigation to it.
- Updated stale demo references in `doc/ARCHITECTURE.md` and `doc/ROADMAP.md` to the new scenario naming/model.
- Hardened `JvmTarget` generated-file sync: fallback to in-place rewrite when replace fails on writable existing targets under lock-like conditions.
- Added kernel regression test `compileReplaceLockFallsBackToInPlaceRewrite` and retained deterministic lock-failure behavior for unrecoverable cases.
- Tightened BEAR package lock policy (`doc/bear-package/BEAR_AGENT.md`) and user/demo docs to forbid IR/ACL workaround mutations after lock failures.
- Tightened BEAR package decomposition policy to reduce single-vs-many block variability: explicit split reasons, mandatory spec-citation evidence for multi-block decomposition, anti-router rule, and workflow/reporting updates.
- Improved `bear check` project-test classification so Gradle wrapper bootstrap/unzip failures map to `IO_ERROR` (including `check --all` root-level detail enrichment with first failing line and short tail context).
- Updated BEAR package docs for v1 IR clarity (`doc/bear-package/IR_EXAMPLES.md`, `doc/bear-package/BEAR_PRIMER.md`) and added doc consistency test coverage (`BearPackageDocsConsistencyTest`).
- Added safe cleanup utility `scripts/safe-clean-bear-generated.ps1` with dry-run mode and optional greenfield reset scope.
- Added end-to-end demo sync utility `scripts/sync-bear-demo.ps1` to build CLI, sync vendored demo runtime (`.bear/tools/bear-cli`), and sync `.bear/agent` package files with hash verification.
- Extended `scripts/safe-clean-bear-generated.ps1` to remove full `build/` outputs so BEAR-generated classfiles under `build/classes/**/com/bear/generated` are fully cleaned in demo resets.
- Tracked future expansion idea in `doc/FUTURE.md`: operation-scoped definitions inside one block to support multi-operation domain aggregation without opcode-router patterns.
- Updated JVM compile generation so user-owned `*Impl.java` stubs are emitted under package-aligned paths (`src/main/java/blocks/<pkg-segment>/impl`, package `blocks.<pkg-segment>.impl`), switched containment metadata to `implDir` (with tolerant legacy parse), and refreshed tests/docs/golden accordingly.
- Implemented BEAR Boundary Hardening v1.1:
  - compile emits deterministic wiring manifest per block (`build/generated/bear/wiring/<blockKey>.wiring.json`)
  - `check`/`check --all` enforce `BOUNDARY_BYPASS` rules (`DIRECT_IMPL_USAGE`, `NULL_PORT_WIRING`, `EFFECTS_BYPASS`)
  - project-test lock/bootstrap now write check-only marker (`build/bear/check.blocked.marker`) and `bear unblock --project <path>` clears it
  - updated CLI/kernel tests and docs/spec (`spec/commands/check.md`, `doc/bear-package/*`, `doc/USER_GUIDE.md`)

