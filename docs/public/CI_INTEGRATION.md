# CI Integration

BEAR ships a downstream CI wrapper under `.bear/ci/` so consuming repos can run deterministic governance gates without copying policy logic into workflow YAML.
This is the downstream PR/CI layer. The inner-loop BEAR path still lives in the agent workflow through `validate`, `compile`, and `check` before code reaches review.

Canonical packaged assets:
- `.bear/ci/bear-gates.ps1`
- `.bear/ci/bear-gates.sh`
- `.bear/ci/baseline-allow.json`
- `.bear/ci/README.md`

Canonical wrapper outputs:
- report artifact: `build/bear/ci/bear-ci-report.json`
- markdown summary: `build/bear/ci/bear-ci-summary.md`
- when `GITHUB_STEP_SUMMARY` is set, the wrapper appends the exact markdown summary content to that path

## What The Wrapper Runs

The wrapper owns CI policy and base selection. BEAR still owns the governance facts.

Execution order:
1. `bear check --all --project . --blocks <path> --collect=all --agent`
2. `bear pr-check --all --project . --base <sha> --blocks <path> --collect=all --agent` when base resolution and `check` exit rules allow it

Skip rules:
- stop after `check` on exits `2`, `64`, `70`, `74`
- stop after `check` on unexpected exit `5`
- if base cannot be resolved, record `prCheck.status=not-run` with reason `BASE_UNRESOLVED`
- continue to `pr-check` after `check` exits `0`, `3`, `4`, `6`, `7`

In v1, `check` exit `6` is reported under `CI_GOVERNANCE_DRIFT` because the CI class vocabulary does not add a dedicated undeclared-reach token.

## Wrapper Modes

`enforce`:
- wrapper fails on any non-zero `check` or `pr-check`
- exception: `pr-check exit 5` may pass as `allowed-expansion` when `.bear/ci/baseline-allow.json` exactly matches resolved base SHA and the observed boundary-expanding `deltaId` set

`observe`:
- wrapper still records both gate results
- wrapper returns `review-required` for boundary expansion (`pr-check exit 5`)
- wrapper returns `fail` for real blocking problems, including drift, test failures, bypass, validation/usage, IO/git, base-resolution failure, and internal wrapper errors

Wrapper process exit contract:
- `0` for `pass`
- `0` for `review-required`
- `0` for `allowed-expansion`
- `1` for `fail`

## Base Resolution

Priority:
1. explicit `--base-sha <sha>`
2. GitHub pull request event payload `pull_request.base.sha`
3. GitHub push event payload `before`
4. GitHub push fallback `HEAD~1` only when `before` is missing or all zeroes

If no base SHA can be resolved, `pr-check` is not run and the wrapper fails closed.

## Demo Pattern In GitHub

The companion demo repo shows the intended GitHub usage pattern:

- demo repo: [bear-account-demo](https://github.com/rore/bear-account-demo)
- demo guide: [DEMO.md](DEMO.md)

That pattern is:

1. checkout with full history
2. set up Java
3. run `bear compile --all --project .`
4. run `.bear/ci/bear-gates.sh --mode observe`
5. upload `bear-ci-report.json` and `bear-ci-summary.md`
6. publish a sticky PR comment summarizing the BEAR decision

In that setup:
- `PASS` means the PR is structurally clean
- `REVIEW REQUIRED` means BEAR detected intentional governance expansion, but the CI lane stays non-blocking
- `FAIL` means the PR has a real blocking BEAR problem

The PR target branch defines the comparison base in CI. In the demo, that is what makes these two cases differ cleanly:

- greenfield baseline review against `main` -> `REVIEW REQUIRED`
- ordinary feature extension against `baseline/greenfield-output` -> `PASS`

## Allow File And Allow Entry Candidate

Path:
- `.bear/ci/baseline-allow.json`

Minimal shape:

```json
{
  "schemaVersion": "bear.ci.allow.v1",
  "entries": [
    {
      "baseSha": "abc123",
      "deltaIds": [
        "BOUNDARY_EXPANDING|ALLOWED_DEPS|ADDED|.:_shared:com.example:demo@1.0.0"
      ]
    }
  ]
}
```

Rules:
- only boundary expansion (`pr-check exit 5`) consults the allow file
- match is exact on both `baseSha` and the full boundary-expanding `deltaId` set
- in `pr-check --all`, that set includes repo-level and block-level boundary-expanding deltas
- missing, stale, extra, or mismatched entries fail in `enforce`
- if `extensions.prGovernance` is missing or unparsable on a boundary-expansion path, allow evaluation is unavailable and the wrapper fails closed

When `mode=enforce`, `pr-check` exits `5`, and PR telemetry is usable, the wrapper also emits the exact allow entry needed for approval. This removes the need to reconstruct `deltaIds` manually.

Console output on that path:

```text
ALLOW_ENTRY_CANDIDATE:
{"baseSha":"<sha>","deltaIds":["..."]}
```

If telemetry is unusable on the same path:

```text
ALLOW_ENTRY_CANDIDATE: UNAVAILABLE
```

## Report Contract

Canonical report artifact:
- `build/bear/ci/bear-ci-report.json`
- `schemaVersion=bear.ci.governance.v1`

Top-level fields:
- `schemaVersion`, `mode`, `resolvedBaseSha`, `commands[]`, `bearRaw`, `check`, `prCheck`, `allowEvaluation`, `decision`

`check` shape:
- `status="ran"`
- `exitCode`, `code`, `path`, `remediation`, `classes[]`

`prCheck` ran shape:
- `status="ran"`
- `exitCode`, `code`, `path`, `remediation`, `classes[]`, `allowEntryCandidate`, `deltas[]`, `governanceSignals[]`

`prCheck` not-run shape:
- `status="not-run"`
- `reason`
- `exitCode=null`, `code=null`, `path=null`, `remediation=null`
- `classes=[]`, `allowEntryCandidate=null`, `deltas=[]`, `governanceSignals=[]`

`prCheck.allowEntryCandidate` is either `null` or:

```json
{
  "baseSha": "<resolvedBaseSha>",
  "deltaIds": ["<sorted-boundary-delta-id>"]
}
```

In `pr-check --all`, `allowEntryCandidate.deltaIds[]` is derived from the full boundary-expanding delta set across repo-level and block-level results, deduped and deterministically ordered.

Allowed `reason` values:
- `CHECK_PRECONDITION_FAILURE`
- `BASE_UNRESOLVED`
- `UNEXPECTED_CHECK_EXIT`

`decision` values:
- `pass`
- `review-required`
- `fail`
- `allowed-expansion`

The report also stores `bearRaw.checkAgentJson`, `bearRaw.prCheckAgentJson`, and deterministic stdout/stderr SHA-256 hashes so derived fields are auditable.

## Console Summary

Wrapper stdout stays compact and begins with a human-facing decision header. The structured line remains immediately after it for deterministic parsing:

```text
BEAR Decision: PASS
MODE=observe DECISION=pass BASE=<sha>
CHECK exit=0 code=- classes=CI_NO_STRUCTURAL_CHANGE
PR-CHECK exit=0 code=- classes=CI_NO_STRUCTURAL_CHANGE
```

When `pr-check` is skipped:

```text
PR-CHECK NOT_RUN: BASE_UNRESOLVED
```

In `observe`, boundary expansion is surfaced as `DECISION=review-required` instead of a clean pass. The allow-entry candidate block remains enforce-only.

## GitHub Markdown Summary

The wrapper always writes a deterministic markdown summary to:
- `build/bear/ci/bear-ci-summary.md`

If `GITHUB_STEP_SUMMARY` is set, the wrapper appends the exact file contents to that GitHub summary path.

The markdown summary is derived only from the same wrapper facts already used for console output, report generation, allow evaluation, and final decision. Near the top it includes the same human-facing decision header, for example `BEAR Decision: REVIEW REQUIRED`, while retaining the bullet metadata below it.

Summary sections:
- heading with `mode`, `decision`, `base SHA`, and report path
- `Review Required` line when the wrapper decision is `review-required`
- `Check`
- `PR Check` or `NOT_RUN`
- `Boundary Deltas` when any boundary-expanding deltas exist
- `Allow Entry Candidate` when the exact candidate can be generated
- the fixed unavailable note when boundary expansion occurred but telemetry was unusable

In `pr-check --all`, the boundary summary uses the full boundary-expanding delta set across repo-level and block-level results.

## GitHub Actions Examples

Canonical sample workflow:
- [examples/github-actions-bear-ci.yml](examples/github-actions-bear-ci.yml)

Ubuntu runner (`observe`, recommended for review UX):

```yaml
- name: Generate BEAR artifacts
  run: ./.bear/tools/bear-cli/bin/bear compile --all --project .

- name: BEAR CI governance
  run: ./.bear/ci/bear-gates.sh --mode observe
```

Ubuntu runner (`enforce`):

```yaml
- name: Generate BEAR artifacts
  run: ./.bear/tools/bear-cli/bin/bear compile --all --project .

- name: BEAR CI governance (enforce)
  run: ./.bear/ci/bear-gates.sh --mode enforce
```

Windows runner (`enforce`):

```yaml
- name: BEAR CI governance
  shell: powershell
  run: .\.bear\ci\bear-gates.ps1 --mode enforce
```

The sample workflow shows the intended GitHub pattern:
- checkout with full history so BEAR base resolution has the expected git context
- set up Java before invoking the vendored BEAR wrapper
- compile generated artifacts before running governance so `check` evaluates repo state rather than stale generated output
- run `.bear/ci/bear-gates.sh --mode observe` when you want governance-review PRs to stay non-blocking but visible
- upload `build/bear/ci/bear-ci-report.json` and `build/bear/ci/bear-ci-summary.md` as artifacts
- optionally publish a sticky PR comment using the report + summary files, as the demo repo does

Runtime note:
- `bear-gates.sh` is a thin bash launcher that requires `pwsh`
- if `pwsh` is unavailable, the script fails deterministically and tells the operator to install PowerShell 7 or run `bear-gates.ps1` directly
- other CI systems should pass `--base-sha <sha>` explicitly

