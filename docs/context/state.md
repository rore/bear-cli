# BEAR Session State

This file is the short operational handoff for the current work window.
For milestone status and backlog ordering, use `docs/context/program-board.md`.
Long-form historical notes are archived in `docs/context/archive/archive-state-history.md`.

## Last Updated

2026-03-01

## Current Focus

P2 stabilization and BEAR guardrails hardening:
- lock lane-scope scanner behavior to path allowlists so `_shared/state` cannot be accidentally scanned by purity/import bans
- keep `pr-check` exit-envelope anomaly handling aligned between runtime behavior and packaged docs
- reduce docs consistency overhead to minimal section-anchor checks

## Next Concrete Task

1. Bake v2.2.5-lite in greenfield + extension runs and confirm no false positives on `_shared/state`.
2. Monitor `pr-check` anomaly path (`PR_CHECK_EXIT_ENVELOPE_ANOMALY`) in single and `--all` flows during dogfood.
3. Keep docs anchor set minimal/stable and avoid reintroducing broad token-level assertions.

## Session Notes

- Hardened demo cleanup process:
  - `scripts/clean-demo-branch.ps1` now uses ignored-file cleanup (`git clean -fdx`, preserving `.bear-gradle-user-home` by default) so stale ignored outputs do not survive.
  - post-clean path checks now include `.gradle` and legacy artifact dirs `build2/`, `build3/`, `build4/`.
  - `docs/context/safety-rules.md` demo cleanup contract updated to require removing untracked+ignored files and to list `build2/3/4` explicitly.
- Implemented Guardrails v2.2.5-lite (revised):
  - `BoundaryBypassScanner` now gates rule evaluation through explicit `ruleAppliesToPath(ruleId, relPath)` allowlists.
  - Lane-scope hardening ensures `_shared/state` is excluded from `SHARED_PURITY_VIOLATION` and `SCOPED_IMPORT_POLICY_BYPASS` evaluation.
  - Added scanner tests for both rule-scope white-box checks and non-evaluation behavior on `_shared/state`.
  - Added deterministic `POLICY_SCOPE_MISMATCH` guidance to packaged docs (`BOOTSTRAP`, `TROUBLESHOOTING`).
  - Added explicit bootstrap heading `GREENFIELD_ARTIFACT_SOURCE_RULE` and docs-anchor enforcement to block greenfield artifact-mining drift.
  - Added reporting anchor section for blocker/anomaly handling and kept `pr-check` anomaly semantics aligned.
  - Reduced `BearPackageDocsConsistencyTest` to minimal checks: package file set, legacy-file removal, required section anchors.
- Implemented guardrails v2.2.3 docs/test hardening:
  - IO lock lane now requires `gradlew(.bat) --stop`, two unchanged retries, then `BLOCKED(IO_LOCK)`.
  - lock triage now forbids command variants and env knob changes (`GRADLE_USER_HOME`, `buildDir`, wrapper env tweaks) unless explicitly instructed.
  - reporting schema now includes blocker/evidence fields (`Gate blocker`, `Stopped after blocker`, `First failing command`, `First failure signature`).
  - scoped import policy wording now explicitly states lane/path scope and app-layer non-global applicability unless separately constrained.
  - docs consistency tests now enforce the new IO lock anchors and reporting/scoped-conflict tokens.
- Guardrails baseline v2.2.1 remains active (lane purity/state enforcement + immutable allowlist contract).
- Implemented guardrails v2.2.4 lock-candidate package:
  - runtime `pr-check` exit-envelope anomaly enforcement for single + `--all`:
    - marker `BOUNDARY_EXPANSION_DETECTED` with non-`5` exit is converted to deterministic internal failure (`exit 70`, `INTERNAL_ERROR`, `PR_CHECK_EXIT_ENVELOPE_ANOMALY`).
  - scanner additions:
    - `STATE_STORE_OP_MISUSE` (adapter update/create co-occurrence signals, including rename-dodge via `balanceCents`/`updateBalance` signals).
    - `STATE_STORE_NOOP_UPDATE` (silent missing-state return patterns in `_shared/state` update methods).
  - check/check-all remediation mapping added for both new scanner rules.
  - IR validator guard added:
    - empty `effects.allow` only permitted for echo-safe pure blocks (no idempotency, no invariants, output tuple subset of input tuples via canonical `name:type` set matching).
  - docs hardened:
    - no-artifact-mining explicitly documented as agent contract rule (not runtime-enforced guarantee).
    - troubleshooting adds `PR_CHECK_EXIT_ENVELOPE_ANOMALY` routing and blocker handling.
    - reporting adds anomaly classification rule (`OTHER`) + marker/exit signature evidence requirement.
    - IR reference now documents create/update/read walletStore pattern and explicit found-state reads.
  - new repo policy test:
    - `RepoArtifactPolicyTest` enforces no tracked `build[0-9]+/` paths and no stale build path tokens in tracked source/docs text files.
- Context routing/model remains stable (`CONTEXT_BOOTSTRAP`, coverage map, and archived historical snapshots already in place).
