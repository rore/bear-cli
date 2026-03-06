# BEAR Session State

This file is the short operational handoff for the current work window.
For milestone status and backlog ordering, use `docs/context/program-board.md`.
Long-form historical notes are archived in `docs/context/archive/archive-state-history.md`.

## Last Updated

2026-03-06

## Current Focus

Implemented the BEAR repo layout split and containment-failure classification hardening: canonical BEAR IR now uses `bear-ir/`, repo-authored BEAR policy uses `bear-policy/`, `.bear/` is reserved for packaged/runtime content, and agent-mode `check` surfaces containment/classpath fallthroughs as containment with bounded recovery.

## Next Concrete Task

1. Run broader verification only if needed beyond the focused feature slice already green.
2. Review any remaining doc/examples that still mention old `spec/` or `.bear/policy/` contracts outside explicit override scenarios.
3. Prepare the branch for review/commit once workspace changes are reconciled.

## Session Notes

- Updated runtime, packaged docs, public docs, tests, and repo fixtures to use the BEAR-owned layout split: `bear-ir/`, `bear-policy/`, root `bear.blocks.yaml`, and package/runtime-only `.bear/`.
- Moved packaged policy templates to `docs/bear-package/bear-policy/` and repo-owned internal fixtures/goldens out of `spec/` into `testdata/`.
- Added a shared containment/classpath signal classifier and applied it to both single-check and all-mode `COMPILE_FAILURE` and generic `FAILED` project-test paths.
- Agent-mode containment fallthroughs now emit `failureCode=CONTAINMENT_NOT_VERIFIED` with `reasonKey=CONTAINMENT_METADATA_MISMATCH` and the existing bounded containment retry action.
- Verification: `./gradlew.bat --no-daemon :app:test --tests com.bear.app.RepoIrLayoutPolicyTest --tests com.bear.app.BearPackageDocsConsistencyTest --tests com.bear.app.RunReportLintTest --tests com.bear.app.BearCliAgentModeTest --tests com.bear.app.BearCliTest`.
- Verification: `./gradlew.bat --no-daemon :kernel:test --tests com.bear.kernel.SharedAllowedDepsPolicyParserTest --tests com.bear.kernel.BearIrValidatorTest --tests com.bear.kernel.JvmTargetTest`.
