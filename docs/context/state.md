# BEAR Session State

This file is the short operational handoff for the current work window.
For milestone status and backlog ordering, use `docs/context/program-board.md`.
Long-form historical notes are archived in `docs/context/archive/archive-state-history.md`.

## Last Updated

2026-03-06

## Current Focus

Local `main` now includes the completed downstream CI integration kit plus the repo-layout and containment-classification hardening. The next BEAR execution slice is `P3` capability templates.

## Next Concrete Task

1. Start `docs/context/backlog/p3-capability-templates.md` as the next execution slice.
2. Keep the shipped CI contracts stable in adopter or demo usage: `extensions.prGovernance`, `bear.ci.governance.v1`, `.bear/ci/baseline-allow.json`, and `build/bear/ci/bear-ci-summary.md`.
3. Continue with broader boundary-escape coverage after capability templates.

## Session Notes

- Fixed post-push CI breakage: made the CI wrapper tests use the correct PowerShell executable on non-Windows, finished the remaining app-test fixture migration from spec/ to ear-ir/, and pinned .sh files to LF via .gitattributes so the bash launcher executes deterministically in cross-platform tests.
- Verification: ./gradlew.bat --no-daemon :app:test :kernel:test.
- Merged the completed CI boundary-governance feature into local `main`: packaged `.bear/ci` wrappers, deterministic base resolution, enforce or observe decisioning, exact-match allow-file handling, reproducible CI report output, allow-entry candidate generation, markdown summary generation, and public CI integration docs plus a GitHub Actions sample.
- Merged the repo-owned layout split and containment hardening into local `main`: canonical IR now lives under `bear-ir/`, repo-authored policy under `bear-policy/`, and containment fallthroughs in agent-mode `check` now classify deterministically as `CONTAINMENT_NOT_VERIFIED` with `CONTAINMENT_METADATA_MISMATCH` when appropriate.
- Verification: `./gradlew.bat --no-daemon :app:test --tests com.bear.app.BearCiIntegrationScriptsTest --tests com.bear.app.BearPackageDocsConsistencyTest --tests com.bear.app.ContextDocsConsistencyTest`.
- Verification: `./gradlew.bat --no-daemon :app:test --tests com.bear.app.RepoIrLayoutPolicyTest --tests com.bear.app.RunReportLintTest --tests com.bear.app.BearCliAgentModeTest --tests com.bear.app.BearCliTest`.
- Verification: `./gradlew.bat --no-daemon :kernel:test --tests com.bear.kernel.SharedAllowedDepsPolicyParserTest --tests com.bear.kernel.BearIrValidatorTest --tests com.bear.kernel.JvmTargetTest`.