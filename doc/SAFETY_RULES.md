# Safety Rules

These rules exist to prevent destructive mistakes.

## Hard Rules

1. Never run recursive delete commands against broad paths (for example `C:\Dev`, `..`, or any absolute path outside this repo).
2. Never use wildcard deletes unless the script first verifies the resolved target is inside the repository root.
3. Before any delete operation, print the exact target list and require explicit confirmation (or use a script that enforces this).
4. Prefer archive/rename over deletion for uncertain cleanup.
5. If path ownership/ACL looks abnormal, stop and ask before proceeding.

## Safe Temp Cleanup

Use:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\safe-clean-temp.ps1
```

Dry run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\safe-clean-temp.ps1 -WhatIf
```

This script only allows deleting known temp/staging paths under the current repository root.

## Safe BEAR Artifact Cleanup

Generated BEAR artifacts only:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\safe-clean-bear-generated.ps1
```

Dry run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\safe-clean-bear-generated.ps1 -WhatIf
```

Fresh greenfield reset (also removes `spec/`, `bear.blocks.yaml`, and `src/main/java/blocks`):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\safe-clean-bear-generated.ps1 -IncludeGreenfieldReset
```

Non-interactive mode (for automation/agents):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\safe-clean-bear-generated.ps1 -Yes
```
