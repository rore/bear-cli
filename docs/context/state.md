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

## Next Concrete Task

1. Continue command-domain test split for `BearCliTest` by extracting `compile`/`fix` command suites.
2. Add guard slices for long app classes still near threshold (`CheckCommandService`, `PrCheckCommandService`, `ProjectTestRunner`).
3. Add/extend quality-guard tests for class-size ceilings and deterministic output ordering where missing.

## Session Notes

- Continued stability-first quality rollout for BEAR docs and process guardrails.
- Implemented v2.2.5.1 docs/test hardening:
  - added labeled preconditions in packaged BOOTSTRAP (`AGENT_PACKAGE_PARITY_PRECONDITION`, `GREENFIELD_HARD_STOP`, `INDEX_REQUIRED_PREFLIGHT`).
  - added deterministic process-violation signature format in TROUBLESHOOTING (`PROCESS_VIOLATION|<label>|<evidence>`).
  - aligned REPORTING for no-command preflight failures (`First failing command: none (preflight)`).
  - extended docs consistency anchors for the new headings while keeping minimal checks.
- Kept blocker taxonomy unchanged (`OTHER` for process/tool anomalies).
- Continued `JvmTarget` decomposition with deterministic file-ops extraction:
  - added `JvmFileSyncSupport` and moved lock-retry sync/write/delete internals out of `JvmTarget`.
  - rewired `JvmTarget` to delegate file operations through the new helper with no contract changes.
  - extracted lexical/type/literal helpers into `JvmLexicalSupport` and removed duplicated logic from `JvmTarget`.
  - reduced `JvmTarget` to 890 LOC (below 900 threshold).
  - revalidated full parity: `:kernel:compileJava :app:compileJava`, `:kernel:test --tests com.bear.kernel.JvmTargetTest`, `:app:test :kernel:test`, and BEAR `compile/check/pr-check --all`.
- Started test architecture split:
  - moved validate command scenarios to `BearCliValidateCommandTest` and removed them from `BearCliTest`.
  - reduced `BearCliTest` to 5076 LOC and kept behavior assertions/envelopes unchanged.
  - revalidated parity with `:app:test` (including `BearCliTest` + new class), full `:app:test :kernel:test`, and BEAR `compile/check/pr-check --all`.
- Implemented `Guardrails v2.2.6` docs/tests-only hardening:
  - added deterministic greenfield baseline waiting semantics (`WAITING_FOR_BASELINE_REVIEW`) and blocker/outcome pairing in REPORTING.
  - added decomposition default/split-trigger anchors in BOOTSTRAP and baseline triage anchor in TROUBLESHOOTING.
  - added docs-consistency anchor checks for new headings and kept exact package parity checks intact.
  - marked reach import-vs-FQCN symmetry as deferred/non-enforced and added optional verification-hygiene guidance.
- Fixed GitHub Actions wrapper execution failure on Linux runners:
  - set `gradlew` file mode to executable in git index (`100755`).
  - added explicit `chmod +x ./gradlew` steps in both CI jobs before Gradle invocations.
- Fixed Linux-only CI test regressions after wrapper-permission recovery:
  - made `AllModeOptionParserTest.parseAllCheckOptionsRejectsAbsoluteBlocksPath` OS-agnostic by using a runtime absolute path instead of `C:/...`.
  - fixed `ContextDocsConsistencyTest` archive exclusion to normalize path separators (`\\` vs `/`) before matching.
  - hardened `BearCliTest.writeProjectWrapper` with a Unix executable fallback (`File#setExecutable`) to reduce env-specific wrapper execution failures.
- Full historical details remain in archive docs; this file stays operational and bounded.
