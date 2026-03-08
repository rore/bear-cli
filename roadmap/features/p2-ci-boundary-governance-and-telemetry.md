---
id: p2-ci-boundary-governance-and-telemetry
title: CI boundary governance and telemetry unification
status: done
priority: high
commitment: committed
milestone: P2
---

## Goal

Ship a deterministic downstream CI integration kit around BEAR's governance facts so consuming repos can run one canonical CI flow without inventing their own wrapper policy, allow-file matching, or reviewer-facing summary layer.

## Shipped Scope

1. Deterministic `pr-check --agent` governance telemetry under `extensions.prGovernance` for single and `--all`.
2. Packaged downstream CI assets under `.bear/ci/`:
- `bear-gates.ps1`
- thin `bear-gates.sh` launcher
- `baseline-allow.json`
- `README.md`
3. Deterministic wrapper behavior for:
- `check --all` followed by `pr-check --all` by a pinned skip matrix
- `enforce` and `observe` decision modes
- exact-match allow-file evaluation by resolved base SHA plus boundary `deltaId` set
- fail-closed behavior when required telemetry or footer data is unavailable
4. Reproducible wrapper outputs:
- `build/bear/ci/bear-ci-report.json` (`schemaVersion=bear.ci.governance.v1`)
- `build/bear/ci/bear-ci-summary.md`
- optional append of the exact markdown summary to `GITHUB_STEP_SUMMARY`
5. Reviewer-facing additive outputs:
- exact `ALLOW_ENTRY_CANDIDATE` for enforce-mode boundary expansion when telemetry is usable
- markdown boundary summary derived from the same wrapper facts used for report generation and decisioning
6. Public and package docs for downstream CI usage, including a copyable GitHub Actions example in `docs/public/examples/github-actions-bear-ci.yml`.

## Non-Goals

1. No new public `bear` command.
2. No change to existing BEAR CLI exit codes, failure footers, or non-agent text contracts.
3. No GitHub API comments, sticky PR comments, or richer check-run UI in v1.
4. No self-hosting migration of `bear-cli`'s own committed workflow as part of this feature.
5. No second parsing or decision engine in bash; `bear-gates.sh` remains a thin `pwsh` launcher.

## Decision Locks

1. BEAR exits, footers, deltas, and governance signals remain authoritative facts.
2. Wrapper `classes[]`, `decision`, allow-entry candidate, and markdown summary are derived only from BEAR facts plus wrapper mode and allow-file state.
3. Allow-file approval remains exact-match only:
- resolved base SHA must match
- the full boundary-expanding `deltaId` set must match
4. In `pr-check --all`, boundary approval and reviewer summaries use the full combined boundary-expanding delta set across repo-level and block-level results.
5. Wrapper process exit is simplified and pinned:
- `0` for `pass`
- `0` for `allowed-expansion`
- `1` for `fail`

## Acceptance Criteria

1. Existing non-agent CLI contracts remain unchanged.
2. `extensions.prGovernance` is deterministic and documented for pass and fail paths.
3. The packaged wrapper deterministically resolves base SHA, executes the pinned gate order, and emits reproducible report plus summary artifacts.
4. `enforce` blocks per contract, allowing only exact allow-file-approved boundary expansion.
5. `observe` swallows only the explicit governance or result lanes and still records underlying BEAR facts.
6. Reviewer-facing wrapper outputs are additive, deterministic, and derived from the same canonical boundary facts.
7. Tests cover wrapper decisioning, not-run serialization, allow-file matching, markdown summary output, and cross-shell launcher behavior.

## Delivered Artifacts

1. `docs/bear-package/.bear/ci/bear-gates.ps1`
2. `docs/bear-package/.bear/ci/bear-gates.sh`
3. `docs/bear-package/.bear/ci/baseline-allow.json`
4. `docs/bear-package/.bear/ci/README.md`
5. `docs/public/CI_INTEGRATION.md`
6. `docs/public/examples/github-actions-bear-ci.yml`
7. `app/src/test/java/com/bear/app/BearCiIntegrationScriptsTest.java`
