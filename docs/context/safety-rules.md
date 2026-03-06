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

When asked to "clean the demo" or "clean demo branch", use this exact contract:

1. Run:
   - `powershell -ExecutionPolicy Bypass -File .\scripts\clean-demo-branch.ps1`
2. Default mode is branch reset only:
   - remove build/runtime/generated artifacts
   - restore tracked changes
   - remove untracked and ignored files (except `.bear-gradle-user-home/` unless explicitly requested)
   - preserve committed BEAR-authored paths already present on the branch
3. Use `-IncludeGreenfieldReset` only when rerunning a spec-only greenfield branch and you intentionally want to clear BEAR-authored implementation/IR paths before restoring branch HEAD.
4. Additional artifact scope removed only in greenfield reset mode:
   - `build/`
   - legacy numbered build directories (for example `build2`, `build3`, `build4`)
   - `bin/main`
   - `bin/test`
   - `bear.blocks.yaml`
   - `spec/`
   - `src/main/java/blocks/`
5. Keep `.bear-gradle-user-home/` by default.
6. Only remove `.bear-gradle-user-home/` if explicitly requested.
7. Routine demo cleanup is operational; do not update `docs/context/state.md` for cleanup-only work.
8. After cleanup, always report:
   - `git status --short`
   - explicit exists/missing checks for the cleanup target paths.

Dry run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\clean-demo-branch.ps1 -WhatIf
```

Greenfield reset dry run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\clean-demo-branch.ps1 -IncludeGreenfieldReset -WhatIf
```

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

Deterministic source rule:
1. Default sync builds latest CLI from current source and syncs from installDist artifacts produced by that same invocation (local or Gradle temp build root).
2. `-SkipBuild` must use an explicit trusted install path (`-CliInstallPath`) or an already-present local `app/build/install/bear`.
3. Sync must not use packaged CLI snapshots as fallback when enforcing "latest" behavior.
