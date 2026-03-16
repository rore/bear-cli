# Quickstart

This quickstart shows BEAR in the local agent loop first, using the demo repo and the minimal local command path.

Prerequisites:

- Demo repo is present at `../bear-account-demo`.
- Demo repo contains vendored BEAR CLI at `.bear/tools/bear-cli`.
- Canonical `--all` flows require `bear.blocks.yaml`.

## Run on the demo repo

1. Enter the demo repo.

```powershell
Set-Location ..\bear-account-demo
```

2. Verify the vendored CLI.

```powershell
.\.bear\tools\bear-cli\bin\bear.bat --help
```

3. Let your agent update the IR if boundary authority changes, then implement the code inside the generated constraints.

```text
Implement the specs. Update BEAR IR first if the boundary must change.
```

4. Compile deterministic generated artifacts.

```powershell
.\.bear\tools\bear-cli\bin\bear.bat compile --all --project .
```

Expected outcome: all selected blocks compile and summary `EXIT_CODE: 0`.

5. Run enforcement.

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check --all --project .
```

Expected outcome: all selected blocks pass and summary `EXIT_CODE: 0`.

6. Run PR governance.

```powershell
.\.bear\tools\bear-cli\bin\bear.bat pr-check --all --project . --base HEAD
```

Expected outcome: `pr-check: OK: NO_BOUNDARY_EXPANSION` and exit `0`.
For real PR/CI, set `--base` to the target branch or merge-base target.

7. Run the packaged CI wrapper.

```powershell
.\.bear\ci\bear-gates.ps1 --mode observe --base-sha HEAD
```

Expected outcome: summary lines on stdout, `build/bear/ci/bear-ci-report.json`, and `build/bear/ci/bear-ci-summary.md` for downstream CI audit and GitHub step-summary routing.

## What This Does Not Show

The quickstart proves the local command path only.

The full review story lives in the companion demo repo, where BEAR is shown in actual pull requests with:

- `PASS` for ordinary governed evolution
- `REVIEW REQUIRED` for intentional boundary expansion
- sticky PR comment plus uploaded CI artifacts in GitHub Actions

See [DEMO.md](DEMO.md) for that walkthrough.

For the packaged downstream CI pattern, allow-file approval flow, and copyable GitHub Actions usage, continue with [CI_INTEGRATION.md](CI_INTEGRATION.md).

## If `bear.blocks.yaml` is missing

All `--all` commands require `bear.blocks.yaml`.

Minimal valid example:

```yaml
version: v0
blocks:
  - name: inventory-sync
    ir: bear-ir/inventory-sync.bear.yaml
    projectRoot: .
```

Fallback single-file path:

```powershell
.\.bear\tools\bear-cli\bin\bear.bat compile bear-ir\<block>.bear.yaml --project .
.\.bear\tools\bear-cli\bin\bear.bat check bear-ir\<block>.bear.yaml --project .
.\.bear\tools\bear-cli\bin\bear.bat pr-check bear-ir\<block>.bear.yaml --project . --base HEAD
```

If something fails, go to [troubleshooting.md](troubleshooting.md).

## Related

- [OVERVIEW.md](OVERVIEW.md)
- [DEMO.md](DEMO.md)
- [PR_REVIEW.md](PR_REVIEW.md)
- [ENFORCEMENT.md](ENFORCEMENT.md)
- [CONTRACTS.md](CONTRACTS.md)
- [CI_INTEGRATION.md](CI_INTEGRATION.md)

