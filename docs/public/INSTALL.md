# Install

Install BEAR by copying one package bundle: [`docs/bear-package/.bear/`](../bear-package/.bear/) into your project as `.bear/`.

BEAR is intended to run "behind the scenes" in agentic workflows:
- the agent reads [`.bear/agent/BOOTSTRAP.md`](../bear-package/.bear/agent/BOOTSTRAP.md) and follows the BEAR loop
- the developer mostly reviews deterministic signals (`check` and `pr-check`), not IR schema minutiae

## Target repo expected layout

```text
<repoRoot>/.bear/agent/
  BOOTSTRAP.md
  CONTRACTS.md
  TROUBLESHOOTING.md
  REPORTING.md
  ref/BEAR_PRIMER.md
  ref/IR_REFERENCE.md
  ref/BLOCK_INDEX_QUICKREF.md

<repoRoot>/.bear/ci/
  bear-gates.sh
  bear-gates.ps1
  baseline-allow.json
  README.md

<repoRoot>/.bear/tools/bear-cli/
  bin/bear(.bat)
  lib/*.jar
```

## Install steps

1. Copy the full BEAR package bundle.

```sh
mkdir -p ./.bear
cp -R ../bear-cli/docs/bear-package/.bear/. ./.bear/
# Windows:
# New-Item -ItemType Directory -Force .\.bear | Out-Null
# Copy-Item -Recurse -Force ..\bear-cli\docs\bear-package\.bear\* .\.bear\
```

2. Ensure project root `AGENTS.md` points to [`.bear/agent/BOOTSTRAP.md`](../bear-package/.bear/agent/BOOTSTRAP.md).

If `AGENTS.md` already exists, append the one-line pointer from [`../bear-package/AGENTS_SHIM.md`](../bear-package/AGENTS_SHIM.md).

3. Verify installation.

```sh
./.bear/tools/bear-cli/bin/bear --help
# Windows: .\.bear\tools\bear-cli\bin\bear.bat --help
```

4. Verify the packaged CI wrapper and confirm it writes `build/bear/ci/bear-ci-report.json` plus `build/bear/ci/bear-ci-summary.md`.

```sh
./.bear/ci/bear-gates.sh --mode observe --base-sha HEAD
# Windows: .\.bear\ci\bear-gates.ps1 --mode observe --base-sha HEAD
```

On bash-based runners, `.bear/ci/bear-gates.sh` requires `pwsh`. If `pwsh` is unavailable, run `.bear/ci/bear-gates.ps1` directly or install PowerShell 7.

5. Run the first deterministic gate.

Use `--all` only when your repo has `bear.blocks.yaml`:

```sh
./.bear/tools/bear-cli/bin/bear check --all --project .
# Windows: .\.bear\tools\bear-cli\bin\bear.bat check --all --project .
```

If no block index exists yet, run single-block check:

```sh
./.bear/tools/bear-cli/bin/bear check bear-ir/<block>.bear.yaml --project .
# Windows: .\.bear\tools\bear-cli\bin\bear.bat check bear-ir\<block>.bear.yaml --project .
```
