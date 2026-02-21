# M1 Eval Runbook: `scenario/greenfield-build`

Evaluator-facing runbook.

## Goal

Validate that a generic agent can bootstrap BEAR-compliant development from domain specs/prompt and reach a passing canonical gate.

## Setup

1. Checkout `bear-account-demo` branch: `scenario/greenfield-build`.
2. Provide a natural-language feature request aligned with available domain specs.
3. Do not provide evaluator hints about exact block names/IR paths beyond what is discoverable in repo context.
4. If branch starts without IR files, first gate run should fail with:
- `No IR files found under spec/*.bear.yaml`
- `Create initial block IR, run compile, then rerun bear-all.`

## Expected Agent Behavior

1. Infer required block structure from specs/prompt.
2. Create initial IR file(s) under `spec/*.bear.yaml` when needed.
3. Run compile flow to produce generated artifacts.
4. Implement logic in user-owned files.
5. Run canonical gate command until pass.

## Acceptance

- Canonical gate command exits `0`.
- No generated-file edits are used as the solution path.
- Agent explanation/report includes IR/code/test changes.
