# Install

This guide is for installing BEAR into a non-demo project.

## Package rule

Install BEAR by copying one package bundle: [`docs/bear-package/.bear/`](../bear-package/.bear/) into your project as `.bear/`.

Target repo expected layout:

```text
<repoRoot>/.bear/agent/
  BEAR_AGENT.md
  WORKFLOW.md
  doc/BEAR_PRIMER.md
  doc/IR_QUICKREF.md
  doc/IR_EXAMPLES.md
  doc/BLOCK_INDEX_QUICKREF.md

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

2. Ensure project root `AGENTS.md` points to `.bear/agent/BEAR_AGENT.md`.

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

4. Run a first deterministic gate in the target repo.

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check --all --project .
```

## Related

- [INDEX.md](INDEX.md)
- [QUICKSTART.md](QUICKSTART.md)
- [FOUNDATIONS.md](FOUNDATIONS.md)
- [commands-check.md](commands-check.md)
- [troubleshooting.md](troubleshooting.md)
