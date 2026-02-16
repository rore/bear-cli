# M1 Eval Runbook: `scenario/feature-extension`

Evaluator-facing runbook.

## Goal

Validate that a generic agent can evolve an existing BEAR project and correctly choose:
- update existing block, or
- create a new block

while following IR-first boundary governance.

## Setup

1. Checkout `bear-account-demo` branch: `scenario/feature-extension`.
2. Ensure branch baseline has been promoted from successful `scenario/greenfield-build` commit(s).
   - example: `git cherry-pick <greenfield_success_commit_sha>`
3. Provide a feature request that requires meaningful design choice (update vs new block).
4. Include at least one requested capability that should trigger boundary expansion signaling.

## Expected Agent Behavior

1. Inspect existing IR + implementation to choose update/new block path.
2. Apply IR-first changes for boundary/contract/effect updates.
3. Run canonical gate on stale baseline and observe deterministic boundary/drift signal when IR expands boundary.
   - if multiple IR files exist, checks run in deterministic sorted filename order and stop on first failure
4. Regenerate/compile baseline and complete implementation/tests.
5. Re-run canonical gate to pass.

## Acceptance

- Stale-baseline run surfaces expected boundary-expansion context and exits `3`.
- Final canonical gate exits `0`.
- Agent report clearly distinguishes boundary changes from implementation changes.
