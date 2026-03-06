# Quickstart

This quickstart runs BEAR on the demo repo with the minimal command path.

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

3. Let your agent implement the spec.

```text
Implement the specs.
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
For real PR/CI, set `--base` to the target merge base (for example `origin/main`).

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
.\.bear\tools\bear-cli\bin\bear.bat compile spec\<block>.bear.yaml --project .
.\.bear\tools\bear-cli\bin\bear.bat check spec\<block>.bear.yaml --project .
.\.bear\tools\bear-cli\bin\bear.bat pr-check spec\<block>.bear.yaml --project . --base HEAD
```

If something fails, go to [troubleshooting.md](troubleshooting.md).

## Related

- [OVERVIEW.md](OVERVIEW.md)
- [PR_REVIEW.md](PR_REVIEW.md)
- [ENFORCEMENT.md](ENFORCEMENT.md)
- [CONTRACTS.md](CONTRACTS.md)
