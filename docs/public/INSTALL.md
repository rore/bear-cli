# Install

## Package rule

Install BEAR by copying one package bundle: [`docs/bear-package/.bear/`](../bear-package/.bear/) into your project as `.bear/`.

Target repo expected layout:

```text
<repoRoot>/.bear/agent/
  BOOTSTRAP.md
  CONTRACTS.md
  TROUBLESHOOTING.md
  REPORTING.md
  ref/BEAR_PRIMER.md
  ref/IR_REFERENCE.md
  ref/BLOCK_INDEX_QUICKREF.md

<repoRoot>/.bear/tools/bear-cli/
  bin/bear(.bat)
  lib/*.jar
```

## Install steps

1. Copy the full BEAR package bundle.

Windows (PowerShell):

```powershell
New-Item -ItemType Directory -Force .\.bear | Out-Null
Copy-Item -Recurse -Force ..\bear-cli\docs\bear-package\.bear\* .\.bear\
```

macOS/Linux (bash/zsh):

```sh
mkdir -p ./.bear
cp -R ../bear-cli/docs/bear-package/.bear/. ./.bear/
```

2. Ensure project root `AGENTS.md` points to `.bear/agent/BOOTSTRAP.md`.

If `AGENTS.md` already exists, append the one-line pointer from [`../bear-package/AGENTS_SHIM.md`](../bear-package/AGENTS_SHIM.md).

3. Verify installation.

Windows (PowerShell):

```powershell
.\.bear\tools\bear-cli\bin\bear.bat --help
```

macOS/Linux (bash/zsh):

```sh
./.bear/tools/bear-cli/bin/bear --help
```

4. Run first deterministic gate.

Use `--all` only when your repo has `bear.blocks.yaml`:

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check --all --project .
```

If no block index exists yet, run single-block check:

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check spec\<block>.bear.yaml --project .
```

## Related

- [INDEX.md](INDEX.md)
- [QUICKSTART.md](QUICKSTART.md)
- [commands-check.md](commands-check.md)
- [commands-unblock.md](commands-unblock.md)
- [troubleshooting.md](troubleshooting.md)
