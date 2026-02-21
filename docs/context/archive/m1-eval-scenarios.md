# M1 Evaluation Scenarios

Evaluator-facing docs only.
Do not copy these runbooks into `bear-account-demo`.

Primary operator guide:
- `docs/context/archive/m1-eval-run-milestone.md`

Shared-root multi-block operator guide:
- `docs/context/archive/m1-eval-run-multi-block.md`

## Current Active Eval Track

For current multi-block enforcement validation, use:
- `scenario/1-greenfield-multiblock-start` -> run Scenario 1 from `RUN_MULTI_BLOCK.md`
- `scenario/1-greenfield-pass` as base for Scenario 2 PR governance checks

## Canonical M1 Branches

| Branch | Start Condition | Agent Task | Expected High-Level Outcome |
| --- | --- | --- | --- |
| `scenario/greenfield-build` | minimal app + domain specs; no completed BEAR block implementation | create initial block IR(s), compile/generate, implement, pass gate | canonical gate exits `0` |
| `scenario/feature-extension` | baseline promoted from successful greenfield result | decide update-existing-block vs create-new-block for new feature; perform IR-first boundary work when needed | stale-baseline check emits boundary expansion + drift (`3`) when applicable, then final gate exits `0` |

## Canonical M1.1 PR Governance Branches

| Branch | Base Ref | Change Type | Expected High-Level Outcome |
| --- | --- | --- | --- |
| `scenario/pr-non-boundary` | `origin/main` | implementation/test-only (no IR boundary expansion) | `pr-gate` exits `0` with no `BOUNDARY_EXPANDING` verdict |
| `scenario/pr-boundary-expand` | `origin/main` | IR boundary expansion (invariant relaxation) | `pr-gate` emits deterministic `pr-delta` boundary line(s) and exits `5` |

## Canonical Gate Behavior Expectations

- `bear-all` discovers `spec/*.bear.yaml` in deterministic filename order.
- If no IR files exist:
  - non-zero failure (`64`)
  - actionable message:
    - `No IR files found under spec/*.bear.yaml`
    - `Create initial block IR, run compile, then rerun bear-all.`
- If multiple IR files exist:
  - check runs in sorted order
  - gate stops on first failure and propagates exit code

## Canonical PR Gate Behavior Expectations

- `pr-gate` requires explicit remote-tracking base ref (for example `origin/main`).
- `pr-gate` discovers `spec/*.bear.yaml` in deterministic filename order.
- If no IR files exist:
  - non-zero failure (`64`)
  - actionable message:
    - `pr-gate: No IR files found under spec/*.bear.yaml`
    - `pr-gate: Create initial block IR, run compile, then rerun pr-gate.`
- Gate stops on first failure and propagates exact `pr-check` exit code.

## Legacy Branches (Non-Canonical)

- `scenario/naive-fail-withdraw`
- `scenario/corrected-pass-withdraw`

These remain historical proof branches and are not the active M1 realism model.

