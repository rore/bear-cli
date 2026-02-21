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

This removes BEAR-related generated/runtime outputs, including `build/` artifacts.

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

Optional full cache wipe (only when explicitly requested):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\safe-clean-bear-generated.ps1 -IncludeGradleCache
```

## Demo Cleanup Contract (Mandatory)

When asked to "clean the demo", use this exact contract:

1. Remove generated/demo-run artifacts:
   - `build/`
   - `bin/main`
   - `bin/test`
   - `bear.blocks.yaml`
   - `spec/`
   - `src/main/java/blocks/`
2. Keep `.bear-gradle-user-home/` by default.
3. Only remove `.bear-gradle-user-home/` if explicitly requested.
4. After cleanup, always report:
   - `git status --short`
   - explicit exists/missing checks for the cleanup target paths.

## Safe Demo Sync

Build latest CLI + sync vendored demo CLI and `.bear/agent` package files:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync-bear-demo.ps1
```

Dry run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync-bear-demo.ps1 -WhatIf
```

Skip build (use already built installDist output):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync-bear-demo.ps1 -SkipBuild
```
