# Quickstart

Role of this page: shortest reliable path to first BEAR success.

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

3. Implement project specs with your agent.

```text
Implement the specs.
```

Expected outcome: agent creates or updates governed implementation and IR artifacts.

4. Run the canonical repo gate when index exists.

Windows (PowerShell):

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check --all --project .
```

macOS/Linux (bash/zsh):

```sh
./.bear/tools/bear-cli/bin/bear check --all --project .
```

Expected outcome: all selected blocks are `PASS`, summary `EXIT_CODE: 0`.

Fallback when `bear.blocks.yaml` is missing:

1. pick one IR file under `spec/*.bear.yaml`
2. run single-block check

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check spec\<block>.bear.yaml --project .
```

Success signal:

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check --all --project .
```

## Related

- [INDEX.md](INDEX.md)
- [INSTALL.md](INSTALL.md)
- [MODEL.md](MODEL.md)
- [commands-check.md](commands-check.md)
- [troubleshooting.md](troubleshooting.md)
