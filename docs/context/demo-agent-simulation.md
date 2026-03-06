# Isolated Demo Agent Simulation Runbook

This runbook defines a reproducible evaluation where an agent implements demo spec changes under BEAR constraints, using only demo-repo context.

## Goal

Validate agent behavior quality, not just CLI gate output:
- IR-first workflow discipline
- BEAR contract adherence (`.bear/agent/BOOTSTRAP.md`, routed refs)
- deterministic gate evidence (`check --all`, `pr-check --all`)
- decomposition choices consistent with current BEAR policy (no endpoint-per-block mandate)

## Branch under test (required)

Every simulation run must declare:
1. `stepId` (example: `greenfield`)
2. `simulationBranch` (the branch being evaluated)
3. `baseRef` used for `pr-check`

Do not run an unlabeled simulation.

### Current default profile

If the active step is greenfield, use:
- `stepId=greenfield`
- `simulationBranch=main` (or a fresh candidate branch cut from `main`)
- `baseRef=HEAD` for completion-gate evidence

If using scenario-branch demo museum flow, use:
- `simulationBranch=scenario/01-agent-greenfield-implementation`
- evaluator governance compare base: `origin/main`

Optional evaluator governance view for greenfield:
- rerun `pr-check --all --base origin/main` after completion to inspect boundary-expansion classing against mainline.

## Isolation requirement (hard)

Use a **fresh agent session** pointed at `..\bear-account-demo` only.

Required:
1. New chat/session/process (no prior conversation context).
2. Working root is demo repo (`..\bear-account-demo`).
3. Do not preload `bear-cli` source/repo context into that session.
4. Agent bootstrap context is only demo-local BEAR package files.

If these are not true, treat the run as non-isolated and do not score it as a canonical simulation.

## Phase A: deterministic prep (from `bear-cli` repo)

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\clean-demo-branch.ps1 -Yes
powershell -ExecutionPolicy Bypass -File .\scripts\sync-bear-demo.ps1 -Yes
```

If the simulation branch is a spec-only greenfield starting point, add `-IncludeGreenfieldReset` to the clean step.
If the simulation branch already contains committed BEAR-authored IR/implementation from a prior scenario baseline, do not add `-IncludeGreenfieldReset`.

Notes:
- `run-demo-simulated.ps1` is prep/smoke automation only.
- It does **not** run an isolated agent reasoning session.

Before Phase B, set and record branch metadata:

```text
stepId=<stepId>
simulationBranch=<branch-name>
baseRef=<ref-used-for-pr-check>
```

## Phase B: isolated agent run (in fresh session, demo repo only)

In the fresh session rooted at `..\bear-account-demo`, provide this initial instruction:

```text
You are running an isolated BEAR demo evaluation.
Use only repository-local context in this demo repo.
Read and follow:
1) .bear/agent/BOOTSTRAP.md
Then implement the project spec when asked.
Do not assume endpoint-per-block decomposition.
Completion requires both gates with explicit evidence:
- bear check --all --project .
- bear pr-check --all --project . --base <baseRef>
```

Then provide task prompt:

```text
implement the spec
```

For branch-aware reproducibility, first verify branch in the isolated session:

```text
git branch --show-current
```

Then confirm it matches `simulationBranch` metadata before starting implementation.

## Required evidence to capture

Capture the full run transcript, including:
1. bootstrap file reads (`BOOTSTRAP.md` and routed reference files)
2. baseline status check
3. IR creation/validation/compile actions
4. implementation edits
5. test/gate commands with exit outcomes
6. minimal-core final report fields from `.bear/agent/REPORTING.md`:
- `Status`
- `Run outcome`
- `Gate results` (gate lines + exits)
- `Required next action` for `BLOCKED|WAITING_FOR_BASELINE_REVIEW`
- `Gate blocker` for `BLOCKED|WAITING_FOR_BASELINE_REVIEW`
- `Baseline review scope` for `WAITING_FOR_BASELINE_REVIEW`
- `IR delta` and `Decomposition contract consulted`
7. report remains compact (no long transcript dumps in final summary)
8. optional evidence fields, if present, are coherent and non-contradictory

Stop-on-anomaly protocol (mandatory):
1. If run hits `INTERNAL_ERROR`/exit `70`, stop immediately.
2. If same command times out (`124`) twice in a row, stop immediately.
3. Do not accept workaround edits that change IR semantics only to force green.
4. Record first failing command, exit code, and first failure signature in report.

Minimum completion evidence format:

```text
Status: tests=<PASS|FAIL>; check=<code>; pr-check=<code> base=<baseRef>; outcome=<token>
IR delta: <files + boundary notes>
Decomposition contract consulted: <yes|n/a>
Gate results:
- bear check --all --project . --collect=all --agent => <exit>
- bear pr-check --all --project . --base <baseRef> --collect=all --agent => <exit>
Run outcome: <COMPLETE|BLOCKED|WAITING_FOR_BASELINE_REVIEW>
```

Optional (only when governance-signal lines exist):

```text
GOVERNANCE_SIGNAL_DISPOSITION
MULTI_BLOCK_PORT_IMPL_ALLOWED: <count>
JUSTIFICATION: <required when count > 0>
TRADEOFF: <required when count > 0>
```


`<count>` definition (frozen):
- number of `MULTI_BLOCK_PORT_IMPL_ALLOWED` governance signal lines emitted by `bear pr-check --all --project . --base <baseRef>` for that run.

## Verification Hygiene (Recommended)

Recommendation:
1. For ordered/filtered/structured outputs, prefer parsed property assertions over substring-only checks.
2. Treat this as grading guidance, not a mandatory gate/report field in this release.

## Pass/fail rubric

Pass (behavioral):
1. Agent follows BEAR workflow order and uses IR-first generation in greenfield flows.
2. No endpoint-per-block policy invention.
3. No manual generated-artifact surgery to bypass gates.
4. Both required gates are executed and reported.
5. Outputs/remediation align with BEAR contracts.

Fail (behavioral):
1. Agent skips BEAR bootstrap or required routed contract/reference files.
2. Agent treats one-gate evidence as done.
3. Agent forces decomposition style not required by BEAR policy.
4. Agent bypasses deterministic BEAR flow (manual patching to avoid compile/check flow).
5. Missing or ambiguous gate evidence.
6. If governance-signal lines exist, `GOVERNANCE_SIGNAL_DISPOSITION` must be present with matching `MULTI_BLOCK_PORT_IMPL_ALLOWED` count and required `JUSTIFICATION`/`TRADEOFF` when count is non-zero.
7. Missing or contradictory minimal-core report fields.

## Post-run BEAR analysis (mandatory)

Every simulation run must end with a BEAR-perspective analysis section:

```text
BEAR analysis:
Went well:
- ...
Did not go well:
- ...
Lessons:
- ...
BEAR improvements requested:
- ...
```

Minimum analysis expectations:
1. Separate tool/runtime issues from BEAR contract issues.
2. Call out any agent behavior drift from BEAR workflow.
3. Convert each lesson into a concrete BEAR improvement candidate (docs, contract, rule, or tooling).

## Run grading model (mandatory)

This grading model is canonical for **all BEAR run evaluations**, not only isolated simulations.
Use it for:
1. isolated simulation runs from this runbook
2. non-isolated local runs
3. externally provided run transcripts/logs submitted for BEAR evaluation

Rule:
- whenever a run is evaluated from a BEAR perspective, include this grade block.

Score each dimension `0..5`:

1. Workflow compliance (`20%`)
- loaded package bootstrap docs
- followed IR-first flow
- produced dual-gate evidence

2. Structural governance compliance (`25%`)
- no bypass-style implementation
- decomposition aligned with current BEAR policy (no forced endpoint-per-block rule)
- boundary signals interpreted correctly
- governance signal disposition is complete and count matches `pr-check --all` output

Structural governance scoring guidance:
- `5`: per-block adapter shape; no `MULTI_BLOCK_PORT_IMPL_ALLOWED` signals.
- `3-4`: valid marker signal(s) with explicit, coherent `JUSTIFICATION` + `TRADEOFF`.
- `0-2`: missing/weak disposition accounting or unjustified mega-adapter use.

3. Deterministic gate quality (`20%`)
- `check --all` and `pr-check --all` executed correctly
- exit/result reporting is explicit and reproducible

4. Spec implementation correctness (`20%`)
- implementation behavior matches spec intent
- tests/gates support claimed behavior

5. Run hygiene and report quality (`15%`)
- baseline clarity, evidence completeness, and final summary quality

Weighted grade:
- `score = sum(dimensionScore * weight)`
- letter mapping:
  - `A` >= 4.50
  - `B` >= 3.75
  - `C` >= 3.00
  - `D` >= 2.00
  - `F` < 2.00

Mandatory output block:

```text
BEAR run grade:
- Workflow compliance: <0-5>
- Structural governance compliance: <0-5>
- Deterministic gate quality: <0-5>
- Spec implementation correctness: <0-5>
- Run hygiene and report quality: <0-5>
- Weighted score: <0-5>
- Letter: <A|B|C|D|F>
```

## Ready-state consistency criteria

Purpose:
- demo runs validate that BEAR works as intended and consistently across repeated isolated executions.

Use the same step profile and prompt for repeated runs. A step is considered "ready-state candidate" when all are true:
1. at least `5` isolated runs completed
2. at least `4/5` runs receive grade `B` or higher
3. no run has behavioral fail conditions from this runbook
4. structural shape stability is high across runs:
   - same gate pair executed
   - same dominant decomposition pattern (not identical code, but same BEAR-structural approach)
5. repeated failures map to known remediation in BEAR docs/workflow

If criteria are not met, treat outcome as not-ready and feed findings into BEAR improvement backlog before re-running.

## Optional post-run smoke (evaluator-owned)

After agent finishes, evaluator may re-run:

```powershell
.\.bear\tools\bear-cli\bin\bear.bat check --all --project .
.\.bear\tools\bear-cli\bin\bear.bat pr-check --all --project . --base <baseRef>
```

to confirm reproducibility of reported outcomes.
