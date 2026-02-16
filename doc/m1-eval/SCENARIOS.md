# M1 Evaluation Scenarios

Evaluator-facing docs only.
Do not copy these runbooks into `bear-account-demo`.

Primary operator guide:
- `doc/m1-eval/RUN_MILESTONE.md`

## Canonical M1 Branches

| Branch | Start Condition | Agent Task | Expected High-Level Outcome |
| --- | --- | --- | --- |
| `scenario/greenfield-build` | minimal app + domain specs; no completed BEAR block implementation | create initial block IR(s), compile/generate, implement, pass gate | canonical gate exits `0` |
| `scenario/feature-extension` | baseline promoted from successful greenfield result | decide update-existing-block vs create-new-block for new feature; perform IR-first boundary work when needed | stale-baseline check emits boundary expansion + drift (`3`) when applicable, then final gate exits `0` |

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

## Legacy Branches (Non-Canonical)

- `scenario/naive-fail-withdraw`
- `scenario/corrected-pass-withdraw`

These remain historical proof branches and are not the active M1 realism model.
