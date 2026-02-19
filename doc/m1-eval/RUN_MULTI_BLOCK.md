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

## Scenario 1: Greenfield Multi-Block Build

Branch:
- `scenario/1-greenfield-multiblock-start`

Prompt:

`Build an account service with immediate DEPOSIT, WITHDRAW, and TRANSFER APIs. Also add scheduled transfers with these requirements: (1) scheduling is durable (survives restart) and has its own create/cancel/query API, (2) execution is asynchronous via a background worker, (3) failed executions are retried with backoff, (4) enforce daily transfer limits by account tier at execution time, (5) keep immediate transfer APIs synchronous and unchanged in behavior, (6) every schedule/create/cancel and every execution attempt/success/failure must write append-only audit records and emit events, (7) enforce non-negative balances and idempotent request handling for both scheduling and execution paths.`

Prompt note:
- This is the tracked Scenario 1 prompt version for current multi-block reruns.

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

## Promote Baseline for Scenario 2

After Scenario 1 passes:

```powershell
git checkout -b scenario/1-greenfield-pass
git push -u origin scenario/1-greenfield-pass
```

Use this as deterministic base for extension PR governance.

## Scenario 2: Extension (Update Existing + Add New)

Start branch:
- create from `scenario/1-greenfield-pass`

Prompt:

`Add scheduled transfers. Scheduling must be durable (survive restart) and executed asynchronously. Enforce daily limits by tier at execution time and record audit for both scheduling and execution.`

Expected agent behavior:
1. Modify at least one existing block.
2. Add at least one new block for scheduling/execution responsibility.
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
.\bin\pr-gate.ps1 origin/scenario/1-greenfield-pass
```

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
