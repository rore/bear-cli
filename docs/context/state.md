# BEAR Session State

This file is the short operational handoff for the current work window.
For milestone status and backlog ordering, use `docs/context/program-board.md`.
Long-form historical notes are archived in `docs/context/archive/archive-state-history.md`.

## Last Updated

2026-03-04

## Current Focus

Block Ports v1 docs/contract sync hardening:
- single-command `--index` plumbing for `compile`/`fix`/`check`/`pr-check`
- deterministic block-edge graph validation + cycle canonicalization + single-file tuple membership
- block-port enforcement path (generated-client binding checks, app-lane inbound wrapper deny, user-root impl ban)

## Next Concrete Task

1. Run a demo-repo smoke (`account` + `transaction-log`) with single-file block-port commands omitting `--index` to confirm expected inference behavior in agent transcripts.
2. Decide whether to keep the `BLOCK_PORT_INDEX_REQUIRED` token name or split it into inferred-missing vs explicit-invalid variants.

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

- Reoriented public docs to the PoC + agent workflow (progressive disclosure):
  - added docs/public/OVERVIEW.md and docs/public/PR_REVIEW.md
  - updated README + public index/quickstart/install/model/foundations to emphasize: agent updates IR; humans review deterministic signals
  - softened CONTRACTS/VERSIONING wording to reflect early-stage nature while keeping automation-stable output contracts
- Verification:
  - ran :app:test doc consistency tests (ContextDocsConsistencyTest, BearPackageDocsConsistencyTest)


- Added public glossary: docs/public/TERMS.md and linked from OVERVIEW/INDEX/MODEL/PR_REVIEW.
- Fixed markdown formatting bug (removed literal \\n sequences) in public docs.
- Verification:
  - ran :app:test doc consistency tests (ContextDocsConsistencyTest, BearPackageDocsConsistencyTest)

- Completed Block Ports v1 freeze follow-through:
  - added deterministic block-port graph cycle regression (`BLOCK_PORT_CYCLE_DETECTED` least-rotation assertion)
  - added app-lane/non-app-lane enforcement coverage in `BlockPortBindingEnforcerTest`
  - added runtime-generated `@BearSharedOwner` annotation (`com.bear.generated.runtime.BearSharedOwner`) and generator assertion in `JvmTargetTest`
- Updated public command/docs contract to block-port model:
  - `commands-check`, `commands-pr-check`, `output-format`, `troubleshooting` now document `BLOCK_PORT_*` rules, app-lane path pinning (`src/main/java/com/**`), generated scan scope (`build/generated/bear/src/main/java/**`), and single-command `--index` requirements
  - `user-guide` command forms now include optional `--index` for single-command modes and block-port index tuple behavior
- Verification:
  - targeted suites: `JvmTargetTest`, `BlockPortBindingEnforcerTest`, `BlockPortGraphResolverTest`, docs consistency tests
  - full suites: `:kernel:test` and `:app:test` pass
  - repo-level BEAR gates via `:app:run` return deterministic missing-index envelope (`INDEX_REQUIRED_MISSING`, exit 2) because repo root intentionally has no `bear.blocks.yaml`

- Public + agent docs sync pass completed for block-port/index behavior:
  - updated `commands-compile`/`commands-fix`/`commands-validate`/`commands-check`/`commands-pr-check` and `QUICKSTART` for single-file `--index` and deterministic missing-index envelope consistency
  - refreshed `TERMS` and `ENFORCEMENT` language for `external ops` vs `block targetOps`
  - updated agent package refs (`IR_REFERENCE`, `BLOCK_INDEX_QUICKREF`, `BEAR_PRIMER`, `BOOTSTRAP`, `TROUBLESHOOTING`, `CONTRACTS`) to include block-port semantics and index requirements
- Verification:
  - `:app:test --tests com.bear.app.ContextDocsConsistencyTest --tests com.bear.app.BearPackageDocsConsistencyTest` pass




- Simplified single-file block-port index behavior:
  - compile/fix/check/pr-check now infer <project>/bear.blocks.yaml when --index is omitted and IR uses kind=block effects.
  - explicit --index remains supported as override.
  - deterministic failure remains CODE=IR_VALIDATION, PATH=bear.blocks.yaml when inferred index is missing.
- Added focused regression coverage: SingleFileIndexInferenceTest (compile/fix/check/pr-check inference + missing inferred index).
- Updated public + agent docs to reflect inferred default index path for single-file block-port flows.

- Docs coherence pass completed (public + agent refs):
  - removed detailed single-file `--index` override examples from `QUICKSTART`; kept one-line pointer only.
  - added explicit clarification that `block.kind` is unchanged (`logic`) and new semantics are at `port.kind` (`external` vs `block`) with `ops` vs `targetOps`.
  - aligned this clarification across `TERMS`, `MODEL`, `commands-validate`, `BEAR_PRIMER`, and `IR_REFERENCE`, and switched examples from wallet/account patterns to neutral fulfillment/inventory domains.
- Verification:
  - `:app:test --tests com.bear.app.ContextDocsConsistencyTest --tests com.bear.app.BearPackageDocsConsistencyTest` pass

