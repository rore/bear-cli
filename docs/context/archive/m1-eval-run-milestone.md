# M1 Milestone Run Instructions (Operator Guide)

Use this guide to run the M1 proof with an isolated `bear-account-demo` workspace.
This is evaluator-facing guidance; do not copy it into demo repo docs.

## Objective

Prove that, from demo repo context only, an agent can:
- complete greenfield bootstrap flow
- complete feature-extension flow with IR-first boundary governance
- use one canonical gate command as the done signal
- run explicit PR governance checks (`pr-gate`) that classify boundary expansion against base branch

## Preconditions

1. Repos exist as siblings:
- `bear-cli`
- `bear-account-demo`

2. BEAR CLI is available to demo wrapper:
- preferred: `.bear/tools/bear-cli/bin/bear(.bat)` inside demo
- fallback: `bear` on PATH

3. Branch states:
- `scenario/greenfield-build`: greenfield-ready (no initial `spec/*.bear.yaml`, no initial impl)
- `scenario/feature-extension`: based on successful greenfield result (ported from `scenario/greenfield-build`)
- `scenario/pr-non-boundary`: impl/test-only change for PR governance pass flow
- `scenario/pr-boundary-expand`: boundary-expanding IR change for PR governance fail flow

## One-Time Setup

From `bear-cli`:

```powershell
.\gradlew.bat :app:installDist
```

In this repo, `installDist` outputs under:
- `%TEMP%\bear-cli-build\<runId>\app\install\bear`

Copy/link that directory to demo expected path:
- `bear-account-demo/.bear/tools/bear-cli`

Then ensure demo wrapper can execute BEAR CLI (`bin/bear.ps1` / `bin/bear.sh`).

## Run 1: Greenfield Build

1. Open a fresh VS Code window with only `bear-account-demo`.
2. Checkout:

```powershell
git checkout scenario/greenfield-build
```

3. Ask agent to build from domain spec/prompt under BEAR rules.
4. First gate run should show deterministic bootstrap guidance:
- `No IR files found under spec/*.bear.yaml`
- `Create initial block IR, run compile, then rerun bear-all.`

5. Agent should:
- create first IR in `spec/*.bear.yaml`
- run compile
- implement user-owned logic/tests
- rerun canonical gate to pass

Canonical gate command:

```powershell
.\bin\bear-all.ps1
```

Pass criteria:
- final gate exit `0`
- no generated-file edits
- agent report includes IR/code/test changes

## Promote Greenfield Result To Feature Baseline

After greenfield success, port the resulting commit(s) to `scenario/feature-extension`.

Example:

```powershell
git checkout scenario/feature-extension
git cherry-pick <greenfield_success_commit_sha>
```

Feature-extension run must start from this promoted baseline, not from an older placeholder implementation.

## Run 2: Feature Extension

1. Open a fresh VS Code window with only `bear-account-demo`.
2. Checkout:

```powershell
git checkout scenario/feature-extension
```

3. Use this recommended feature request (default for M1 Run 2):

> Extend Withdraw to support a per-transaction fee, while preserving no-overdraft and idempotency behavior. Update the project so this is fully implemented and validated under BEAR rules.

Optional boundary-expansion variant (if you specifically want boundary signal proof):

> Add a fraud pre-check before withdrawal using a new fraud-check capability, and implement the change under BEAR rules.

4. Agent should:
- inspect existing IR/impl
- choose update existing block vs create new block
- apply IR-first changes for boundary/contract/effect updates
- run stale-baseline gate and observe boundary/drift behavior when applicable
- regenerate/implement and rerun gate to pass

Canonical gate command:

```powershell
.\bin\bear-all.ps1
```

Pass criteria:
- stale-baseline flow shows deterministic boundary/drift (`exit 3`) when boundary expands
- final gate exit `0`
- agent report separates boundary deltas from implementation deltas

## Run 3: PR Governance Pass Path

1. Open a fresh VS Code window with only `bear-account-demo`.
2. Checkout:

```powershell
git checkout scenario/pr-non-boundary
```

3. Run:

```powershell
.\bin\pr-gate.ps1 origin/main
```

Pass criteria:
- command exits `0`
- no boundary-expanding verdict line is emitted

## Run 4: PR Governance Boundary Path

1. Open a fresh VS Code window with only `bear-account-demo`.
2. Checkout:

```powershell
git checkout scenario/pr-boundary-expand
```

3. Run:

```powershell
.\bin\pr-gate.ps1 origin/main
```

Pass criteria:
- command exits `5`
- stderr includes deterministic boundary classification lines (for example `pr-delta: BOUNDARY_EXPANDING: ...`)
- stderr includes `pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED`

Expected snippet for current fixture:

```text
pr-gate: checking spec/withdraw.bear.yaml against origin/main
pr-delta: BOUNDARY_EXPANDING: INVARIANTS: REMOVED: non_negative:balance
pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED
```

Note:
- Additional `pr-delta:` boundary lines are acceptable if IR changes evolve.
- Hard pass criteria remain: boundary verdict line present and exit code `5`.

## Evidence To Capture

Capture in `bear-cli/doc/m1-eval/` only:
- branch name
- feature prompt
- first failing gate snippet (if any)
- final passing gate snippet
- concise summary of IR/code/test deltas

Do not add evaluator expected-output hints into `bear-account-demo`.

Hosted CI evidence (M1.1 PR governance):

Non-boundary PR run snippet:

```text
pr-gate: checking spec/withdraw.bear.yaml against origin/main
pr-check: OK: NO_BOUNDARY_EXPANSION
```

Boundary-expanding PR run snippet:

```text
pr-gate: checking spec/withdraw.bear.yaml against origin/main
pr-delta: BOUNDARY_EXPANDING: INVARIANTS: REMOVED: non_negative:balance
pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED
Error: Process completed with exit code 5.
```

## Quick Failure Triage

1. Exit `64` from `bear-all`:
- no IR files found (greenfield expected)
- create first IR and compile

2. Exit `2`:
- IR/schema/semantic issue
- fix IR and rerun

3. Exit `3`:
- drift (and possibly boundary expansion lines)
- compile affected IR and rerun

4. Exit `4`:
- tests/verification failure
- fix impl/tests and rerun

5. Exit `5` from `pr-gate`:
- boundary-expanding PR deltas detected
- expected for intentional boundary changes; route to explicit review flow

## Completion Check

M1/M1.1 run is complete when all four scenario runs satisfy their pass criteria and evidence is captured in `bear-cli/doc/m1-eval/`.
