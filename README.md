# BEAR (bear-cli)

<p align="center">
  <img src="assets/logo/bear.png" alt="BEAR logo" width="360" />
</p>

BEAR is a deterministic governance CLI for agentic backend development: agents implement inside declared IR boundaries, and BEAR turns boundary risk into explicit PR/CI signals.

It is designed for AI-assisted development (or any fast iteration), where boundary expansion and generated-artifact drift can be easy to miss.

BEAR is a compiler and CI gate for architecture and declared semantics. It makes certain constraints non-bypassable by generating the only allowed integration surface and rejecting code that reaches around it. If a behavior is declared in BEAR IR and is supported by the target wrapper and ports, BEAR enforces it by construction; if it is not declared (or not supportable by the underlying ports/runtime), BEAR does not pretend to guarantee it.

## What BEAR Does (Plain Terms)

- You declare what code is allowed to do at the boundary.
- BEAR generates deterministic guardrails from that declaration.
- Implementation can evolve freely inside those guardrails.
- CI gets deterministic governance signals from `check` and `pr-check`.

## What You Get

- Boundary power expansion becomes explicit and machine-parseable in PRs.
- Generated guardrails cannot drift silently.
- Every non-zero failure is actionable: `CODE`, `PATH`, `REMEDIATION`.

## What BEAR is not (Preview non-goals)

- Not a business-rules engine.
- Not a runtime transaction framework.
- Not an agent orchestrator.
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

Success = exit `0` and no failing blocks (in `--all`, summary shows `EXIT_CODE: 0`).

## Preview Scope and Coverage

Preview focuses on deterministic contracts and CI-friendly governance.
Enforcement coverage is intentionally bounded to supported targets/surfaces in Preview.

## How It Works

- BEAR IR: small YAML boundary declaration.
- `compile`: deterministic BEAR-owned generated artifacts.
- `check`: deterministic local enforcement gate.
- `pr-check`: deterministic boundary-governance classification against base.

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

## Supported Targets

- JVM/Java target in Preview.
- Primary containment enforcement path is Java plus Gradle wrapper when `impl.allowedDeps` is declared.
