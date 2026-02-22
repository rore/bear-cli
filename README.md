# BEAR (bear-cli)

<p align="center">
  <img src="assets/logo/bear.png" alt="BEAR logo" width="360" />
</p>

BEAR is a deterministic governance CLI for agentic backend development: agents implement inside declared IR boundaries, and BEAR turns boundary risk into explicit PR/CI signals.

Role of this page: fast orientation. Deeper rationale and contracts are linked below.

## What BEAR enforces and alerts on

- `check` enforces deterministic drift, boundary-policy, and test-stage gates.
- `pr-check` alerts on boundary-expanding IR deltas against a base ref.
- Non-zero failures always end with deterministic `CODE`, `PATH`, and `REMEDIATION`.

## What BEAR is not (Preview non-goals)

- Not a business-rules engine.
- Not a runtime transaction framework.
- Not a verifier of domain correctness beyond declared contract checks.
- Not a replacement for application test strategy.

## Quickstart

Prerequisites:

- demo repo is present at `../bear-account-demo`
- vendored CLI exists at `.bear/tools/bear-cli`
- canonical `--all` success path requires `bear.blocks.yaml`

1. Open the demo repo.

```powershell
Set-Location ..\bear-account-demo
```

2. Verify vendored CLI (not PATH).

Windows (PowerShell):

```powershell
.\.bear\tools\bear-cli\bin\bear.bat --help
```

macOS/Linux (bash/zsh):

```sh
./.bear/tools/bear-cli/bin/bear --help
```

3. Let your agent implement specs.

```text
Implement the specs.
```

4. Run the canonical gate when index is present.

Windows (PowerShell):

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check --all --project .
```

macOS/Linux (bash/zsh):

```sh
./.bear/tools/bear-cli/bin/bear check --all --project .
```

Fallback if `bear.blocks.yaml` is missing:

1. pick one IR file under `spec/*.bear.yaml`
2. run single-block check

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check spec\<block>.bear.yaml --project .
```

Success signal: canonical `bear check --all --project .` exits `0` and reports no failing blocks.

## Mental model

- `IR`: declared block contract and allowed boundaries.
- `compile`: deterministic generated artifacts from IR.
- `check`: deterministic local gate (drift, boundary checks, tests).
- `pr-check`: deterministic PR governance classification against base.

Pipeline: `IR -> compile -> check`, then `pr-check --base <ref>` for PR governance.

## Links

- Start here: [docs/public/INDEX.md](docs/public/INDEX.md)
- First run: [docs/public/QUICKSTART.md](docs/public/QUICKSTART.md)
- Install in another repo: [docs/public/INSTALL.md](docs/public/INSTALL.md)
- What BEAR protects: [docs/public/ENFORCEMENT.md](docs/public/ENFORCEMENT.md)
- Operating model: [docs/public/MODEL.md](docs/public/MODEL.md)
- Rationale and architecture: [docs/public/FOUNDATIONS.md](docs/public/FOUNDATIONS.md)
- Frozen contracts: [docs/public/CONTRACTS.md](docs/public/CONTRACTS.md)
- Command contracts: [docs/public/commands-check.md](docs/public/commands-check.md), [docs/public/commands-pr-check.md](docs/public/commands-pr-check.md), [docs/public/commands-unblock.md](docs/public/commands-unblock.md)
- Machine-facing behavior: [docs/public/exit-codes.md](docs/public/exit-codes.md), [docs/public/output-format.md](docs/public/output-format.md)
- Failure triage: [docs/public/troubleshooting.md](docs/public/troubleshooting.md)
- Optional project context: [docs/context/state.md](docs/context/state.md)

## Preview scope and supported targets

Preview scope:

- Deterministic `validate`, `compile`, `fix`, `check`, `unblock`, and `pr-check` contracts.
- Deterministic failure footer and stable exit code registry.
- Deterministic boundary-expansion signaling in `pr-check`.

Supported targets:

- JVM/Java target in Preview.
- Primary containment enforcement path is Java plus Gradle wrapper when `impl.allowedDeps` is declared.
