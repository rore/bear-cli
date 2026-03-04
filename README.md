# BEAR (bear-cli)

<p align="center">
  <img src="assets/logo/bear-header-1400x320-clean.png" alt="BEAR logo" width="100%" />
</p>

BEAR is a deterministic governance CLI and CI gate for agentic backend development.

You edit code plus BEAR IR, then BEAR reports stable, machine-parseable signals: either green, or a precise failure with a remediation hint.

Figure: the BEAR workflow (compile -> check -> pr-check) and the outputs CI should consume.
Legend: yellow = IR you edit, green = BEAR commands, orange = what automation parses.

```mermaid
%% id: bear-workflow-v1
flowchart LR
  A[Agent / Dev edits code]:::actor --> B[Edit BEAR IR\n(.bear.yaml)]:::ir
  B --> C[bear compile]:::cmd
  C --> D[Generate boundary glue\n(wiring, ports, wrappers)]:::gen
  D --> E[bear check]:::cmd
  E --> F{CI green?}:::gate
  F -- yes --> G[Merge]:::ok
  F -- no --> H[Fix code or IR]:::bad

  I[bear pr-check]:::cmd --> J{Boundary delta in PR?}:::gate
  J -- none --> F
  J -- expansion/bypass --> H

  E -. emits .-> S1[[CI contract output:\nexit code + CODE/PATH/REMEDIATION]]:::signal
  I -. emits .-> S2[[PR output:\npr-delta + verdict + footer]]:::signal

  classDef actor fill:#EEF2FF,stroke:#6366F1,color:#0B1220;
  classDef ir fill:#FFFBEB,stroke:#F59E0B,color:#0B1220;
  classDef cmd fill:#ECFDF5,stroke:#10B981,color:#0B1220;
  classDef gen fill:#F1F5F9,stroke:#64748B,color:#0B1220;
  classDef gate fill:#FFFFFF,stroke:#0F172A,color:#0F172A;
  classDef ok fill:#DCFCE7,stroke:#22C55E,color:#0B1220;
  classDef bad fill:#FEE2E2,stroke:#EF4444,color:#0B1220;
  classDef signal fill:#FFF7ED,stroke:#F97316,color:#0B1220;
```

## What BEAR does (plain terms)

- An agent updates code and (when needed) a small YAML IR contract (BEAR IR).
- A block is a governed backend unit; its operations and allowed effects are declared in BEAR IR.
- BEAR generates deterministic guardrails (wrappers/ports) from that declaration.
- Implementation can evolve freely inside those guardrails.
- CI gets deterministic governance signals from `check` and `pr-check`.

## What you get

- Boundary power expansion becomes explicit and machine-parseable in PRs.
- Generated guardrails cannot drift silently.
- Every non-zero failure is actionable: `CODE`, `PATH`, `REMEDIATION`.

BEAR = Block Enforceable Architectural Representation.

## What BEAR is not (preview non-goals)

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

4. Compile deterministic generated artifacts.

```powershell
.\.bear\tools\bear-cli\bin\bear.bat compile --all --project .
```

5. Run the deterministic enforcement gate.

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check --all --project .
```

6. Run the PR governance gate.

Local sanity (base is self):

```powershell
.\.bear\tools\bear-cli\bin\bear.bat pr-check --all --project . --base HEAD
```

In a real PR/CI flow, set `--base` to the merge-base target (for example `origin/main`).

## Links

- Start here: [docs/public/INDEX.md](docs/public/INDEX.md)
- Quickstart: [docs/public/QUICKSTART.md](docs/public/QUICKSTART.md)
- PR/CI review: [docs/public/PR_REVIEW.md](docs/public/PR_REVIEW.md)
- Guarantees and non-goals: [docs/public/ENFORCEMENT.md](docs/public/ENFORCEMENT.md)
- Automation/reference contracts: [docs/public/CONTRACTS.md](docs/public/CONTRACTS.md)

## Supported targets

- JVM/Java target in Preview.
- Primary containment enforcement path is Java plus Gradle wrapper when `impl.allowedDeps` is declared.





