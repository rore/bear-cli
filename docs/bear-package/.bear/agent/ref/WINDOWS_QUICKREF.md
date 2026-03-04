# WINDOWS_QUICKREF.md

Purpose:
- Canonical PowerShell commands for deterministic BEAR agent loops.

## Repo-Root Gates (Machine Mode)

```powershell
.\bin\bear.ps1 check --all --project . --collect=all --agent
.\bin\bear.ps1 pr-check --all --project . --base origin/main --collect=all --agent
```

## Single-IR Baseline Sequence

```powershell
.\bin\bear.ps1 validate spec\<block>.bear.yaml
.\bin\bear.ps1 compile spec\<block>.bear.yaml --project .
```

## Marker Recovery

```powershell
.\bin\bear.ps1 unblock --project .
```

## Rules

1. After a gate failure in `--agent` mode, run only commands listed in `nextAction.commands`.
2. Keep shell and command shape deterministic; avoid ad-hoc flags.
