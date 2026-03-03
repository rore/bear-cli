# BEAR Session State

This file is the short operational handoff for the current work window.
For milestone status and backlog ordering, use `docs/context/program-board.md`.
Long-form historical notes are archived in `docs/context/archive/archive-state-history.md`.

## Last Updated

2026-03-03

## Current Focus

P2 stabilization v3 hardening:
- unified `--all` missing-index envelope across compile/check/fix/pr-check
- deterministic `check --all` progress/heartbeat stream
- single-source invariant fingerprint + validator/codegen consistency for multi-op IR

## Next Concrete Task

1. If repo-level `--all` gates are required in this workspace, add `bear.blocks.yaml` and rerun:
- `bear check --all --project .`
- `bear pr-check --all --project . --base <ref>`
2. Optional follow-up: add explicit integration coverage for a non-`_shared` containment Gradle lane task in CLI tests.

## Session Notes

- Completed all-mode index preflight unification:
  - added shared preflight helper for `compile/check/fix/pr-check --all`
  - missing-index envelope is now deterministic and identical (`INDEX_REQUIRED_MISSING`, exit `2`, `project=.`)
- Added deterministic `check --all` progress stream:
  - `START`, `BLOCK_START`, `ROOT_TEST_START`, monotonic `HEARTBEAT`, `ROOT_TEST_DONE`, `DONE`
  - covered by new `AllModeContractTest`
- Added kernel/app invariant fingerprint single source:
  - new `InvariantFingerprint` in kernel
  - reused by validator and app `PrDeltaClassifier`
  - normalizer sorts invariants by canonical fingerprint
- Relaxed block invariant applicability:
  - block-level allowed invariants are no longer forced to apply to every operation output
  - operation invariants remain field/type validated per operation and subset-checked by fingerprint
- Fixed generator stability for mixed idempotency:
  - idempotency key codegen now guarded to `mode=use` paths only
  - added `JvmTargetTest` regression for mixed `none/use`
- Fixed structural contract drift in generated structural tests:
  - structural direction tests now assert shared `<Block>Logic` + per-op wrapper/request/result
  - no per-op `*Logic` expectation in generated structural assertions
- Added mechanical policy guard:
  - `RepoArtifactPolicyTest` now forbids tracked `src/main/java/com/bear/generated/**`
  - `AllModeContractTest` verifies `fix --all` does not mutate `spec/*.bear.yaml`
- Updated agent/public/context docs:
  - agent docs now include hard stop on tooling anomalies, timeout retry budget, and anomaly reporting fields
  - public command docs include exact missing-index signature and `check --all` heartbeat examples
  - simulation runbook now includes explicit stop-on-anomaly protocol
- Verification:
  - `:kernel:test` and `:app:test` pass
  - `:app:run --args "check --all --project ."` and `:app:run --args "pr-check --all --project . --base HEAD"` emit deterministic missing-index envelope and terminate with exit `2` (expected without repo-root index)
