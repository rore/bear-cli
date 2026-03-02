# BEAR Session State

This file is the short operational handoff for the current work window.
For milestone status and backlog ordering, use `docs/context/program-board.md`.
Long-form historical notes are archived in `docs/context/archive/archive-state-history.md`.

## Last Updated

2026-03-02

## Current Focus

Stability-first quality rollout (aggressive track):
- restore green `main` for policy and docs gates
- add in-repo CI gate contract for test + dual BEAR checks
- introduce low-risk refactor seams (`IrPipeline`, shared marker/constants, envelope emitter)
- standardize non-trivial task execution via repo-local `workflow-orchestration` skill + AGENTS registry entry
- finalize v2.2.6.3 guardrails hardening (decomposition rubric determinism + reporting precision + noop-update widening)

## Next Concrete Task

1. Continue command-domain test split for `BearCliTest` by extracting `compile`/`fix` command suites.
2. Add guard slices for long app classes still near threshold (`CheckCommandService`, `PrCheckCommandService`, `ProjectTestRunner`).
3. Add/extend quality-guard tests for class-size ceilings and deterministic output ordering where missing.

## Session Notes

- Continued `JvmTarget` decomposition with deterministic file-ops extraction:
  - added `JvmFileSyncSupport` and moved lock-retry sync/write/delete internals out of `JvmTarget`.
  - rewired `JvmTarget` to delegate file operations through the new helper with no contract changes.
  - extracted lexical/type/literal helpers into `JvmLexicalSupport` and removed duplicated logic from `JvmTarget`.
  - reduced `JvmTarget` to 890 LOC (below 900 threshold).
  - revalidated full parity: `:kernel:compileJava :app:compileJava`, `:kernel:test --tests com.bear.kernel.JvmTargetTest`, `:app:test :kernel:test`, and BEAR `compile/check/pr-check --all`.
- Started test architecture split:
  - moved validate command scenarios to `BearCliValidateCommandTest` and removed them from `BearCliTest`.
  - reduced `BearCliTest` to 5076 LOC while preserving behavior assertions/envelopes and parity (`:app:test`, full `:app:test :kernel:test`, BEAR `compile/check/pr-check --all`).
- Implemented `Guardrails v2.2.6` docs/tests-only hardening:
  - added deterministic greenfield baseline waiting semantics (`WAITING_FOR_BASELINE_REVIEW`) and blocker/outcome pairing in REPORTING.
  - added decomposition default/split-trigger anchors in BOOTSTRAP and baseline triage anchor in TROUBLESHOOTING.
  - added docs-consistency anchor checks for new headings and kept exact package parity checks intact.
  - marked reach import-vs-FQCN symmetry as deferred/non-enforced and added optional verification-hygiene guidance.
- Implemented `Guardrails v2.2.6.3` docs/scanner hardening:
  - replaced decomposition policy with deterministic grouped rubric tokens + derivation rules in BOOTSTRAP.
  - removed residual per-operation decomposition mandate language from CONTRACTS and aligned package wording.
  - tightened REPORTING with strict `DEVELOPER_SUMMARY`, deterministic status line format, grouped decomposition fields, and required `Surface evidence` forms.
  - added TROUBLESHOOTING non-solutions section (`REACH_REMEDIATION_NON_SOLUTIONS`) forbidding import-to-FQCN bypass remediation.
  - widened `STATE_STORE_NOOP_UPDATE` detection to include null-guard silent no-op branch with deterministic pattern IDs.
  - added anti-overreach scanner tests and normalized bootstrap line-budget docs consistency test.
  - aligned demo simulation runbook with required Developer Summary + Surface evidence reporting fields.
- Fixed GitHub Actions wrapper execution failure on Linux runners:
  - set `gradlew` file mode to executable in git index (`100755`).
  - added explicit `chmod +x ./gradlew` steps in both CI jobs, set CI concurrency grouping to `${{ github.sha }}`, and pinned `GRADLE_USER_HOME=/home/runner/.gradle` + ensured cache dirs to eliminate cache save-path warnings.
- Fixed Linux-only CI test regressions after wrapper-permission recovery:
  - made `AllModeOptionParserTest.parseAllCheckOptionsRejectsAbsoluteBlocksPath` OS-agnostic by using a runtime absolute path instead of `C:/...`.
  - fixed `ContextDocsConsistencyTest` archive exclusion to normalize path separators (`\\` vs `/`) before matching.
  - hardened `BearCliTest.writeProjectWrapper` with a Unix executable fallback (`File#setExecutable`) to reduce env-specific wrapper execution failures.
- Hardened project test execution on Unix in runtime path:
  - `ProjectTestRunner` now invokes wrapper scripts via `sh <project>/gradlew` instead of direct exec on Unix.
  - removed strict Unix executable-bit precheck in `resolveWrapper` (still requires wrapper file presence).
  - this avoids CI/container `noexec` mount failures while preserving wrapper-missing detection semantics.
- CI execution audit pass:
  - repeated `checkProjectTestTimeoutReturnsExit4` 8x with no failures; reran CI-equivalent flow locally (`:app:test :kernel:test`) with all green.
- Eliminated timeout test flakiness at source:
  - added deterministic command-layer test hook in `CheckCommandService` (`bear.check.test.forceTimeoutOutcome`) and wired `BearCliTest.checkProjectTestTimeoutReturnsExit4` to use it with deterministic property restore in `finally`.
  - kept timeout assertion classification-based (`TEST_TIMEOUT`), added focused `ProjectTestRunnerTest.runProjectTestsCanForceTimeoutViaProperty`, and removed forced-timeout process-kill race by returning synthetic timeout before process start in `ProjectTestRunner.runProjectTestsOnce`; validated with 15 repeated runs plus full `:app:test :kernel:test` green.
- Full historical details remain in archive docs; this file stays operational and bounded.
