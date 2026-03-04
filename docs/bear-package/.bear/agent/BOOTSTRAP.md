# BOOTSTRAP.md

Purpose:
- Minimal BEAR startup protocol for agents.
- Always-load file for routing and non-negotiable operating rules.

Bootstrap guardrails:
- `BOOTSTRAP.md` must stay under 200 lines.
- Keep this file command-centric and operational.
- Put detailed troubleshooting in `.bear/agent/TROUBLESHOOTING.md`.
- Put normative policy/decomposition contracts in `.bear/agent/CONTRACTS.md`.

## If You Remember Nothing Else

1. Determine mode from disk first:
- greenfield: `0` files in `spec/*.bear.yaml`
- single block: `1` IR file
- multi-block: `>=2` IR files, `bear.blocks.yaml` required
- if `bear.blocks.yaml` exists, treat as multi-block regardless of IR file count
2. Canonical IR directory is `spec/` unless repo policy says otherwise.
3. IR-first always; in greenfield run `bear validate` and `bear compile` before implementation edits.
4. Never edit generated artifacts under `build/generated/bear/**`.
5. Do not self-edit infra harness files unless explicitly instructed:
- `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `.bear/**`, `bin/bear*`
6. For machine loops, run gates with `--agent` (usually with `--collect=all`).
7. In `--agent` mode, stdout JSON is the control interface; stderr is diagnostics/evidence.
8. After a gate failure in `--agent` mode, follow `nextAction.commands` only.
9. If `nextAction` is `null`, route to `.bear/agent/TROUBLESHOOTING.md` using `(failureCode, ruleId|reasonKey)`.
10. Completion requires both gates and reporting contract compliance:
- `bear check --all --project <repoRoot> [--collect=all] [--agent]`
- `bear pr-check --all --project <repoRoot> --base <ref> [--collect=all] [--agent]`

## Command Surface

Before any failure:
1. You may run the standard gate sequence: `validate`, `compile|fix`, `check`, `pr-check`.

After failure in `--agent` mode:
1. If JSON `nextAction.commands` is present, execute only those commands.
2. If `nextAction` is `null`, use `.bear/agent/TROUBLESHOOTING.md` key routing and escalate with deterministic evidence when required.

Supported BEAR commands for agent loop:
1. `bear validate`
2. `bear compile`
3. `bear fix`
4. `bear check`
5. `bear pr-check`
6. `bear unblock`

Forbidden:
1. ad-hoc scripts or speculative tool flags as gate remediation
2. command variants not in the current `nextAction` after a gate failure

## Machine Gate Loop

1. Update IR/implementation from spec intent.
2. Run pre-gate sequence as needed:
- `bear validate <ir-file>`
- `bear compile <ir-file> --project <repoRoot>` or `bear compile --all --project <repoRoot>`
3. Run machine gate:
- `bear check --all --project <repoRoot> --collect=all --agent`
4. If `status=fail` and `nextAction` exists:
- execute only `nextAction.commands`
- rerun the same gate command
5. Run governance gate:
- `bear pr-check --all --project <repoRoot> --base <ref> --collect=all --agent`
6. If `nextAction` is `null` on failure:
- route to `.bear/agent/TROUBLESHOOTING.md`
- escalate with deterministic failure evidence (`CODE`, `PATH`, `REMEDIATION`)

Windows command quickref:
- `.bear/agent/ref/WINDOWS_QUICKREF.md`

JSON fields quickref:
- `.bear/agent/ref/AGENT_JSON_QUICKREF.md`

## Routing Map

Always read:
1. `.bear/agent/BOOTSTRAP.md`

Read on demand:
1. IR authoring rules -> `.bear/agent/ref/IR_REFERENCE.md`
2. Multi-block index syntax -> `.bear/agent/ref/BLOCK_INDEX_QUICKREF.md`
3. Normative contracts/decomposition rules -> `.bear/agent/CONTRACTS.md`
4. Troubleshooting and keyed remediations -> `.bear/agent/TROUBLESHOOTING.md`
5. Completion reporting schema and loop evidence -> `.bear/agent/REPORTING.md`
6. First-time conceptual primer -> `.bear/agent/ref/BEAR_PRIMER.md`

## Hard-Stop Routing

1. On `INTERNAL_ERROR` (`70`), repeated timeout (`124`), or `IO_LOCK`, follow `.bear/agent/TROUBLESHOOTING.md` and stop when anomaly criteria require stop.
2. For expected greenfield baseline `BOUNDARY_EXPANSION_DETECTED`, do not force green; report `WAITING_FOR_BASELINE_REVIEW` per `.bear/agent/REPORTING.md`.
3. If spec conflicts with explicit policy/contract rules, stop and escalate unless the spec explicitly authorizes rule changes.

## AGENT_PACKAGE_PARITY_PRECONDITION

Before implementation starts, these files MUST exist:
1. `.bear/agent/CONTRACTS.md`
2. `.bear/agent/TROUBLESHOOTING.md`
3. `.bear/agent/REPORTING.md`
4. `.bear/agent/ref/IR_REFERENCE.md`

If any required file is missing:
1. classify as process/tool anomaly
2. stop immediately and escalate

## GREENFIELD_HARD_STOP

If `spec/*.bear.yaml` is empty:
1. next action MUST be creating IR files under canonical `spec/`
2. run `bear validate` and `bear compile` before implementation edits

## INDEX_REQUIRED_PREFLIGHT

If index-required mode is inferred from workflow/docs:
1. `bear.blocks.yaml` MUST be created after IR files exist and before `--all` gates
2. if preflight is unmet, stop and fix index/IR preconditions first

## GREENFIELD_PR_CHECK_POLICY

1. In greenfield baseline PRs, `bear pr-check` may expectedly fail with `BOUNDARY_EXPANSION_DETECTED`.
2. Do not shrink IR/contracts to force green.
3. Report baseline waiting semantics from `.bear/agent/REPORTING.md` and stop for boundary review.

## Done Gate Contract

Required evidence before completion:
1. `bear check --all --project <repoRoot> [--collect=all] [--agent] => 0`
2. `bear pr-check --all --project <repoRoot> --base <ref> [--collect=all] [--agent] => 0`
3. completion report follows `.bear/agent/REPORTING.md` exactly
