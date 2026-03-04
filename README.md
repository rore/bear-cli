# BEAR (bear-cli)

<p align="center">
  <img src="assets/logo/bear-header-1400x320-clean.png" alt="BEAR logo" width="100%" />
</p>

BEAR is a deterministic governance CLI for agentic backend development.

```mermaid
%%{init: {"theme":"base","themeVariables":{
  "fontFamily":"ui-sans-serif, system-ui",
  "lineColor":"#64748B",
  "textColor":"#E2E8F0",
  "background":"#0F172A",
  "primaryColor":"#111827",
  "primaryBorderColor":"#475569",
  "primaryTextColor":"#E2E8F0",
  "secondaryColor":"#0B1220",
  "secondaryBorderColor":"#334155",
  "tertiaryColor":"#0B1220",
  "tertiaryBorderColor":"#334155",
  "noteBkgColor":"#1E293B",
  "noteBorderColor":"#475569",
  "noteTextColor":"#E2E8F0"
}}}%%
flowchart LR
  A[Agent / Dev edits code]:::actor --> B[Update BEAR IR file]:::ir
  B --> C[bear compile]:::cmd
  C --> D[Generated wiring + wrappers]:::gen
  D --> E[bear check]:::cmd
  E --> F{CI green?}:::gate
  F -- yes --> G[Merge]:::ok
  F -- no --> H[Fix: code or IR]:::bad

  I[bear pr-check]:::cmd --> J{Boundary delta?}:::gate
  J -- none --> F
  J -- expansion / bypass --> H

  E -. emits .-> S1[[Signals:\nexit code + CODE/PATH/REMEDIATION]]:::signal
  I -. emits .-> S2[[PR signals:\npr-delta + verdict + footer]]:::signal

  classDef actor fill:#312E81,stroke:#818CF8,color:#E2E8F0;
  classDef ir fill:#422006,stroke:#F59E0B,color:#E2E8F0;
  classDef cmd fill:#064E3B,stroke:#10B981,color:#E2E8F0;
  classDef gen fill:#0F172A,stroke:#64748B,color:#E2E8F0;
  classDef gate fill:#111827,stroke:#CBD5E1,color:#E2E8F0;
  classDef ok fill:#052E16,stroke:#22C55E,color:#E2E8F0;
  classDef bad fill:#3F0A0A,stroke:#F87171,color:#E2E8F0;
  classDef signal fill:#2B1405,stroke:#FB923C,color:#E2E8F0;
```

- as agents write more backend code, we may need strict, deterministic enforcement to prevent silent boundary expansion and generated-artifact drift
- governance should not depend on agent reasoning quality; it should show up as explicit PR/CI signals

## What BEAR does (plain terms)

- An agent updates code and (when needed) a small YAML IR contract.
- BEAR generates deterministic guardrails (wrappers/ports) from that declaration.
- Implementation can evolve freely inside those guardrails.
- CI gets deterministic governance signals from `check` and `pr-check`.

## What you get

- Boundary power expansion becomes explicit and machine-parseable in PRs.
- Generated guardrails cannot drift silently.
- Every non-zero failure is actionable: `CODE`, `PATH`, `REMEDIATION`.

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

4. Run the deterministic enforcement gate.

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check --all --project .
```

5. Run the PR governance gate.

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





