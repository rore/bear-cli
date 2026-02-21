# Multi-Block Eval Runbook (Shared Root)

Evaluator-facing guide for the new two-scenario demo flow.
Do not copy this runbook into `bear-account-demo`.

## Objective

Prove this claim in realistic agent workflow:
- developer gives normal product request
- agent creates/updates BEAR governance artifacts
- BEAR gates enforce deterministically
- one shared service root can host multiple BEAR blocks

## Repos / Preconditions

1. Sibling repos:
- `bear-cli`
- `bear-account-demo`

2. Demo branch for scenario start:
- `scenario/1-greenfield-multiblock-start`

3. Canonical commands (inside demo):
- `.\bin\bear-all.ps1` or `./bin/bear-all.sh`
- `.\bin\pr-gate.ps1 <base-ref>` or `./bin/pr-gate.sh <base-ref>`

4. Gate behavior contract in demo wrappers:
- if `bear.blocks.yaml` exists, use `check/pr-check --all`
- if no index and no IR files, return deterministic greenfield guidance (`64`)
- if no index and exactly one IR file, single-block fallback is allowed
- if no index and two or more IR files, fail `64` and require index creation (no fallback)

## Pre-Run Cleanup (Mandatory)

Before every rerun, clean prior run artifacts in `bear-account-demo` so agent behavior is measured from a consistent baseline.

PowerShell (from `bear-cli` repo root):

```powershell
$paths = @(
  '..\bear-account-demo\bin\main',
  '..\bear-account-demo\bin\test',
  '..\bear-account-demo\build',
  '..\bear-account-demo\spec',
  '..\bear-account-demo\.tmp-compile-work',
  '..\bear-account-demo\.data',
  '..\bear-account-demo\src\main\java\com\bear\generated',
  '..\bear-account-demo\src\test\java\com\bear\generated'
)
foreach ($p in $paths) {
  if (Test-Path $p) {
    Remove-Item -Recurse -Force $p
  }
}
git -C ..\bear-account-demo status --short
```

Expected post-clean status:
- only intentionally kept package/wrapper changes are present (or clean working tree).
- no generated BEAR artifacts under `spec/`, `build/`, `bin/main`, `bin/test`, or `src/**/com/bear/generated`.

## Scenario 1: Greenfield Multi-Block Build

Branch:
- `scenario/1-greenfield-multiblock-start`

Prompt:

`Build an account service with immediate DEPOSIT only. Add scheduled transfers with create-only API (no cancel/query), asynchronous worker execution, no retries, fixed single daily transfer limit per account, and audit/event emission for schedule create and execution success/failure. Enforce non-negative balances and idempotency for immediate and worker paths.`

Prompt note:
- This is the tracked Scenario 1 prompt version for current multi-block reruns.
- In-memory storage/adapters are acceptable for this eval; durability and external integrations are out of scope for Scenario 1.

Expected agent behavior:
1. Create `spec/*.bear.yaml` from scratch.
2. Create `bear.blocks.yaml`.
3. Compile affected blocks.
4. Implement user-owned logic/tests.
5. Reach green via canonical gate.

Acceptance:
1. At least two enabled blocks exist in `bear.blocks.yaml`.
2. Multiple blocks share `projectRoot: .`.
3. Blocks are materially distinct (not copy-split placeholders).
4. `bear-all` exits `0`.
5. `bear.blocks.yaml` remains present at end of run.
6. `.\bin\bear.ps1 check --all --project .` exits `0`.

Command:

```powershell
.\bin\bear-all.ps1
```

If greenfield starts with no IR/index, expected guidance is:
- `No BEAR block index or IR files found`
- `Create initial IR file(s), create bear.blocks.yaml, compile, then rerun bear-all.`

Explicit failure condition:
- multi-IR run without `bear.blocks.yaml` is invalid even if per-IR fallback could otherwise pass.
- greenfield implementation-first runs are invalid: if no IR existed at start, agent must create IR and run validate/compile before writing feature implementation source files.
- any run that introduces ad-hoc replacement architecture (custom contracts/ports) before BEAR generation is invalid even if final gate is green.

## Promote Baseline for Scenario 2

After Scenario 1 passes:

```powershell
git checkout -b scenario/1-greenfield-pass
git push -u origin scenario/1-greenfield-pass
```

Use this as deterministic base for extension PR governance.

## Scenario 2: Extension (Update Existing + Add New)

Start branch:
- current cycle baseline is `scenario/2-extension-from-greenfield-output`
- create a new working branch from that baseline (do not modify `scenario/1-greenfield-multiblock-start`)

Prompt:

`Extend the current scheduled-transfer service with two features: (1) add a read API to return schedule status and failure reason by scheduleId, and (2) add a separate asynchronous notification job that scans failed schedules and emits exactly one notification per scheduleId. Keep existing immediate API and existing execution worker behavior unchanged. Notification emission must be idempotent by notification request id and must not emit for executed schedules.`

Expected agent behavior:
1. Modify at least one existing block (account-service and/or scheduled-transfer-worker).
2. Add one new block for failed-schedule notification responsibility.
3. Update `bear.blocks.yaml`.
4. Compile and implement to green.

Acceptance:
1. `bear-all` exits `0`.
2. Existing block delta is present.
3. New block addition is present.
4. PR governance output is deterministic against scenario-1 baseline.

Commands:

```powershell
.\bin\bear-all.ps1
.\bin\pr-gate.ps1 origin/scenario/2-extension-from-greenfield-output
```

Timeout handling note:
- If a tool/harness returns `124` while running `bear-all`/`pr-gate`/Gradle, treat it as execution-timeout noise, not an automatic BEAR-methodology failure.
- Re-run the same command with a longer timeout budget and use the final deterministic exit/output as evidence.

## Evidence to Capture

Store evidence in `bear-cli/doc/m1-eval/` only:
1. Branch name.
2. Prompt used.
3. First failing gate output snippet.
4. Final passing gate output snippet.
5. PR gate snippet for Scenario 2.
6. `bear.blocks.yaml` snippet.
7. Explicit `check --all` output snippet.
8. Brief IR/code/test delta summary.
9. Ordering proof snippet: first successful `validate/compile` occurs before first feature implementation edit under `src/main/java` or `src/test/java`.

## Failure Triage

1. Exit `64`:
- missing/invalid inputs (greenfield start expected before IR/index exist)

2. Exit `2`:
- IR/schema/semantic issue

3. Exit `3`:
- drift (regen needed)

4. Exit `4`:
- project test failure

5. Exit `5` (`pr-gate`):
- boundary-expanding PR delta detected

6. Exit `6`:
- undeclared reach detected

7. Exit `74`:
- IO/state issue (including orphan/legacy marker guards in `--all` mode)
