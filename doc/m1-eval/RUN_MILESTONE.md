# M1 Milestone Run Instructions (Operator Guide)

Use this guide to run the M1 proof with an isolated `bear-account-demo` workspace.
This is evaluator-facing guidance; do not copy it into demo repo docs.

## Objective

Prove that, from demo repo context only, an agent can:
- complete greenfield bootstrap flow
- complete feature-extension flow with IR-first boundary governance
- use one canonical gate command as the done signal

## Preconditions

1. Repos exist as siblings:
- `bear-cli`
- `bear-account-demo`

2. BEAR CLI is available to demo wrapper:
- preferred: `.bear/tools/bear-cli/bin/bear(.bat)` inside demo
- fallback: `bear` on PATH

3. Branch states:
- `scenario/greenfield-build`: greenfield-ready (no initial `spec/*.bear.yaml`, no initial impl)
- `scenario/feature-extension`: based on current `main` baseline

## One-Time Setup

From `bear-cli`:

```powershell
.\gradlew.bat :app:installDist
```

Ensure demo wrapper can execute BEAR CLI (`bin/bear.ps1` / `bin/bear.sh`).

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

## Run 2: Feature Extension

1. Open a fresh VS Code window with only `bear-account-demo`.
2. Checkout:

```powershell
git checkout scenario/feature-extension
```

3. Provide a feature request that forces update-vs-create decision and likely boundary expansion.
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

## Evidence To Capture

Capture in `bear-cli/doc/m1-eval/` only:
- branch name
- feature prompt
- first failing gate snippet (if any)
- final passing gate snippet
- concise summary of IR/code/test deltas

Do not add evaluator expected-output hints into `bear-account-demo`.

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

## Completion Check

M1 run is complete when both scenario runs satisfy their pass criteria and evidence is captured in `bear-cli/doc/m1-eval/`.
