# BEAR CI Integration Assets

This directory is the packaged downstream CI surface for BEAR governance.

Files:
- `bear-gates.ps1`: canonical wrapper implementation
- `bear-gates.sh`: thin bash launcher for the PowerShell implementation
- `baseline-allow.json`: exact-match allow file for approved boundary expansion

Canonical outputs:
- console summary lines for `MODE`, `CHECK`, and `PR-CHECK`, including `DECISION=review-required` in observe-mode governance review paths
- optional `ALLOW_ENTRY_CANDIDATE` block on enforce-mode boundary expansion
- report artifact at `build/bear/ci/bear-ci-report.json`
- markdown summary at `build/bear/ci/bear-ci-summary.md`
- when `GITHUB_STEP_SUMMARY` is set, the wrapper appends the exact markdown summary content there

Canonical usage:

PowerShell:

```powershell
.\.bear\ci\bear-gates.ps1 --mode observe
```

bash:

```sh
./.bear/ci/bear-gates.sh --mode observe
```

Options:
- `--mode enforce|observe`
- `--base-sha <sha>`
- `--blocks <relative-path>`

Rules:
- wrappers run `check --all` first, then `pr-check --all` when allowed by the pinned decision matrix
- in `observe`, clean runs report `pass`, boundary expansion reports `review-required`, and blocking repo problems report `fail`
- `baseline-allow.json` is consulted only for `pr-check` boundary expansion in `enforce`
- the allow-entry candidate and markdown boundary section use the full boundary-expanding delta set from `pr-check --all` repo-level plus block-level results
- report and decision output must be reproducible from BEAR raw outputs plus wrapper mode and allow-file state

Runtime note:
- on bash-based GitHub runners, `bear-gates.sh` requires `pwsh`
- if `pwsh` is unavailable, `bear-gates.sh` fails deterministically and tells the operator to install PowerShell 7 or run `bear-gates.ps1` directly
- if local bash cannot launch PowerShell reliably, run `bear-gates.ps1` directly