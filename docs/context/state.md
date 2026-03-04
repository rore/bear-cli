# BEAR Session State

This file is the short operational handoff for the current work window.
For milestone status and backlog ordering, use `docs/context/program-board.md`.
Long-form historical notes are archived in `docs/context/archive/archive-state-history.md`.

## Last Updated

2026-03-04

## Current Focus

Deterministic agent diagnostics v1 for `check` / `pr-check` (`--agent` JSON mode + `--collect=all` exhaustive collection mode).

## Next Concrete Task

1. Run remote CI workflows (`build-and-test`, `bear-gates`) for the diagnostics branch and confirm green.
2. Decide whether to expose deterministic cluster keys/file-group behavior in public output docs/examples with concrete JSON snippets.

## Session Notes

- Implemented `--agent` and `--collect=all` parsing/wiring across single and `--all` command flows for `check` and `pr-check`.
- Added deterministic diagnostics core:
  - `AgentDiagnostics` (problem IDs, ordering, clustering, truncation, JSON rendering, next-action payloads)
  - `AgentTemplateRegistry` (bounded deterministic template mapping + safe fallback)
  - `GovernanceRuleRegistry` (bounded public governance rule IDs)
- Added structured problem propagation through command services and all-mode aggregation paths; agent JSON now emits `problems`, `clusters`, and one deterministic `nextAction`.
- Preserved default human output paths (non-agent) while adding JSON-only stdout behavior for `--agent`.
- Updated usage/help and public docs for new flags and agent stream semantics.
- Synced packaged agent docs under `docs/bear-package/.bear/agent/**` and `docs/bear-package/README.md` to include optional `--collect=all` / `--agent` gate forms and agent JSON stdout note.
- Added explicit package-level agent-loop intent/contract (`check --all --collect=all --agent` -> consume single `nextAction` -> rerun -> `pr-check --all --collect=all --agent`) in `docs/bear-package/.bear/agent/REPORTING.md` and `docs/bear-package/README.md`.
- Added tests:
  - `AgentDiagnosticsTest`
  - `BearCliAgentModeTest`
  - `AllModeOptionParserAgentTest`
  - targeted `BearCliTest` updates for new argument surface and exhaustive collection behavior.
- Verification runs:
  - `./gradlew.bat :app:compileJava`
  - `./gradlew.bat :app:test --tests com.bear.app.AgentDiagnosticsTest --tests com.bear.app.BearCliAgentModeTest --tests com.bear.app.AllModeOptionParserAgentTest`
  - `./gradlew.bat :app:test --tests com.bear.app.ContextDocsConsistencyTest --tests com.bear.app.BearPackageDocsConsistencyTest`
  - `./gradlew.bat :app:test`
- Follow-up hardening completed:
  - Added explicit infra `reasonKey` emission at origin failure sites (`PROJECT_TEST_LOCK`, `PROJECT_TEST_BOOTSTRAP`, `READ_HEAD_FAILED`, `NOT_A_GIT_REPO`, `MERGE_BASE_FAILED`).
  - Added infra invariant guard in diagnostics builder (`INFRA` problems cannot carry `ruleId`).
  - Updated rerun command interpolation to preserve mode flags (`--all`, `--collect=all`) and `--agent` when already in agent mode.
  - Refined template content for git/base diagnostics and boundary-expansion guidance.
- Fixed agent JSON renderer correctness bug so arrays/objects are emitted as valid JSON values (removed malformed `":,[` pattern risk).
- Expanded docs with explicit deterministic agent-loop semantics and `nextAction` mapping/fallback contract:
  - `docs/public/output-format.md`
  - `docs/public/FOUNDATIONS.md`
  - `docs/public/FOUNDATIONS.md`
  - `docs/bear-package/.bear/agent/TROUBLESHOOTING.md`
- Additional verification runs (post-fix):
  - `./gradlew.bat :app:test --tests com.bear.app.AgentDiagnosticsTest --tests com.bear.app.BearCliAgentModeTest`
  - `./gradlew.bat :app:test`
  - `./gradlew.bat :app:test --tests com.bear.app.ContextDocsConsistencyTest --tests com.bear.app.BearPackageDocsConsistencyTest`

- Public docs structure cleanup (no new folders):
  - guide path now explicit in `docs/public/INDEX.md` (OVERVIEW -> QUICKSTART -> PR_REVIEW)
  - `docs/public/CONTRACTS.md` now acts as the single reference gateway
  - merged model mechanics into `docs/public/FOUNDATIONS.md` and removed redundant `docs/public/MODEL.md`
  - tightened `docs/public/TERMS.md` to minimal reader vocabulary
  - updated command pages with short `Quick use` sections before full contract details
- Guardrail respected: no edits were made under `docs/bear-package/.bear/agent/*` in this change set.
- Verification:
  - `./gradlew.bat --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest --tests com.bear.app.BearPackageDocsConsistencyTest`
- Added Mermaid workflow diagrams to public docs for operator-facing visualization:
  - `README.md` (top workflow overview)
  - `docs/public/PR_REVIEW.md` (governed roots / generated artifacts / app boundary sketch)
  - `docs/public/output-format.md` (deterministic reporting sequence)
- Verification: `./gradlew.bat --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest --tests com.bear.app.BearPackageDocsConsistencyTest`
- Agent-package protocol refactor completed:
  - `BOOTSTRAP.md` reduced to command-centric protocol (pre-failure vs post-failure behavior + command whitelist).
  - `REPORTING.md` now states JSON-first automation rule and stderr-as-evidence behavior.
  - `TROUBLESHOOTING.md` now includes registry-synced template key tables (exact + failure defaults).
- Added package quickrefs:
  - `.bear/agent/ref/AGENT_JSON_QUICKREF.md`
  - `.bear/agent/ref/WINDOWS_QUICKREF.md`
- Added deterministic sync test in `BearPackageDocsConsistencyTest` to assert troubleshooting key tables match `AgentTemplateRegistry` maps.
- Verification:
  - `./gradlew.bat :app:test --tests com.bear.app.BearPackageDocsConsistencyTest --tests com.bear.app.ContextDocsConsistencyTest --tests com.bear.app.RepoArtifactPolicyTest`
  - `./gradlew.bat :app:test`
- Removed redundant public page `docs/public/MODEL.md` again to keep the guide/reference structure aligned and avoid dead pages.
- Fixed GitHub Mermaid parse error in `README.md` by simplifying node label text (`Update BEAR IR file`) to avoid unsupported token parsing in GFM renderer.
- Standardized Mermaid styling across public diagrams (`README.md`, `docs/public/PR_REVIEW.md`, `docs/public/output-format.md`) with one dark-mode-friendly palette for consistent semantic colors and contrast.
- Agent-package follow-up tightening applied from review:
  - BOOTSTRAP deduped (precondition/greenfield policy details routed to TROUBLESHOOTING; bootstrap kept router-first).
  - Routing key text standardized to `(category, failureCode, ruleId|reasonKey)` across package docs.
  - REPORTING now references `.bear/agent/ref/AGENT_JSON_QUICKREF.md` directly.
  - AGENT_JSON_QUICKREF now includes `nextAction.kind` and `nextAction.primaryClusterId` parse targets.
- Verification:
  - `./gradlew.bat :app:test --tests com.bear.app.BearPackageDocsConsistencyTest --tests com.bear.app.ContextDocsConsistencyTest`
  - `./gradlew.bat :app:test`
- README wording update: introduced a plain-language `block` definition in "What BEAR does" and added acronym expansion line (`BEAR = Block Enforceable Architectural Representation`) before non-goals.
- Verification: `./gradlew.bat --no-daemon :app:test --tests com.bear.app.ContextDocsConsistencyTest --tests com.bear.app.BearPackageDocsConsistencyTest`
- Docs readability pass: fixed Markdown fence spacing in public docs, aligned README demo quickstart with `compile -> check -> pr-check`, and cleaned list formatting in `FOUNDATIONS.md` / `ENFORCEMENT.md` for correct rendering.
