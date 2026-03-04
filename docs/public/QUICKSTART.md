# Quickstart

This quickstart runs BEAR on the demo repo.
The intended workflow is agent-first: the agent updates code and IR as needed; humans review deterministic gate output.

Prerequisites:

- Demo repo is present at `../bear-account-demo`.
- Demo repo contains vendored BEAR CLI at `.bear/tools/bear-cli`.
- Canonical `--all` gate requires `bear.blocks.yaml`.

## Try BEAR on the demo repo

1. Enter the demo repository.

```powershell
Set-Location ..\bear-account-demo
```

Expected outcome: shell is at demo repo root.

2. Verify vendored BEAR CLI is available.

Windows (PowerShell):

```powershell
.\.bear\tools\bear-cli\bin\bear.bat --help
```

macOS/Linux (bash/zsh):

```sh
./.bear/tools/bear-cli/bin/bear --help
```

Expected outcome: CLI usage/help output is displayed.

3. Let your agent implement the project specs.

```text
Implement the specs.
```

Expected outcome: agent creates/updates governed code and IR under the repo.

4. Run the deterministic enforcement gate.

Windows (PowerShell):

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check --all --project .
```

macOS/Linux (bash/zsh):

```sh
./.bear/tools/bear-cli/bin/bear check --all --project .
```

Expected outcome: all selected blocks are `PASS`, summary `EXIT_CODE: 0`.

5. Run the PR governance gate.

For a quick local sanity run, compare against `HEAD`:

```powershell
.\.bear\tools\bear-cli\bin\bear.bat pr-check --all --project . --base HEAD
```

Expected outcome: `pr-check: OK: NO_BOUNDARY_EXPANSION` and exit `0`.

In a real PR/CI flow, set `--base` to the merge-base target (for example `origin/main`).

## If `bear.blocks.yaml` is missing

All `--all` commands require `bear.blocks.yaml`.

Minimal valid example:

```yaml
version: v1
blocks:
  - name: inventory-sync
    ir: spec/inventory-sync.bear.yaml
    projectRoot: .
```

Fallback if you do not want `--all` yet:

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check spec\<block>.bear.yaml --project .
.\.bear\tools\bear-cli\bin\bear.bat pr-check spec\<block>.bear.yaml --project . --base HEAD
```

For single-file IR with `kind=block` effects, index defaults to `./bear.blocks.yaml` (see command docs for override and failure details).

## Related

- [OVERVIEW.md](OVERVIEW.md)
- [PR_REVIEW.md](PR_REVIEW.md)
- [ENFORCEMENT.md](ENFORCEMENT.md)
- [commands-check.md](commands-check.md)
- [commands-pr-check.md](commands-pr-check.md)
- [troubleshooting.md](troubleshooting.md)

