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
9. On containment/classpath mismatch, run one `bear compile --all --project <repoRoot>` repair and rerun once; never move/copy impl or exception classes into `_shared`.
10. If `nextAction` is `null`, route to `.bear/agent/TROUBLESHOOTING.md` using `(category, failureCode, ruleId|reasonKey)`.
11. Completion requires both gates and minimal core reporting contract compliance:
- `bear check --all --project <repoRoot> [--collect=all] --agent`
- `bear pr-check --all --project <repoRoot> --base <ref> [--collect=all] --agent`

## Implementation Preconditions

Before implementation edits, load `.bear/agent/TROUBLESHOOTING.md` and `.bear/agent/REPORTING.md`.

Minimum required sections (to avoid context overload):
1. `.bear/agent/TROUBLESHOOTING.md` -> `Agent JSON-First Protocol`, `PROCESS_VIOLATION`, `GREENFIELD_PR_CHECK_POLICY`.
2. `.bear/agent/REPORTING.md` -> `Agent Loop Contract`, `Required Fields (Minimal Core)`, `Outcome Rules`.

Mandatory stop conditions:
1. `GREENFIELD_HARD_STOP`:
- if `spec/*.bear.yaml` is empty, do not edit implementation.
- first create IR, then run `bear validate <ir-file>` and `bear compile <ir-file> --project <repoRoot>` (or `compile --all` after index preflight).
2. `AGENT_PACKAGE_PARITY_PRECONDITION`:
- before implementation edits, `.bear/agent/TROUBLESHOOTING.md` and `.bear/agent/REPORTING.md` must both exist and be readable.
- if any required file is missing/unreadable, stop with `PROCESS_VIOLATION|AGENT_PACKAGE_PARITY_PRECONDITION|<missingPath>` and escalate.
3. `INDEX_REQUIRED_PREFLIGHT`:
- before any `--all` gate, `bear.blocks.yaml` must exist and be readable.
- if missing/unreadable, stop and resolve preflight before continuing.
4. `POST_FAILURE_DISCIPLINE`:
- after any gate failure in `--agent` mode, execute only `nextAction.commands`.
- do not run ad-hoc gate reruns unless rerun is explicitly listed in `nextAction.commands`.
- if `nextAction` is `null`, route deterministically via troubleshooting.
- any command variant drift is a process violation and must stop.
5. `COMPLETE_DISCIPLINE`:
- report `Run outcome: COMPLETE` only after canonical done gates are green.
- allowed run outcomes are fixed: `COMPLETE | BLOCKED | WAITING_FOR_BASELINE_REVIEW`.

## Command Surface

Before any failure:
1. You may run the standard gate sequence: `validate`, `compile|fix`, `check`, `pr-check`.
2. For automation/machine loops, run `check` and `pr-check` in `--agent` mode.

After failure in `--agent` mode:
1. If JSON `nextAction.commands` is present, execute only those commands.
2. If `nextAction` is `null`, use `.bear/agent/TROUBLESHOOTING.md` key routing and escalate with deterministic evidence when required.


Execution note:
1. Execute `nextAction.commands` exactly as written.
2. If a command starts with `bear` and `bear` is not on PATH, run the same command arguments via the repo-local launcher under `.bear/tools/bear-cli/bin/bear*` or `bin/bear*` for that repo.

Forbidden:
1. ad-hoc scripts or speculative tool flags as gate remediation
2. command variants not in the current `nextAction` after a gate failure

## Machine Gate Loop

1. Evaluate decomposition contract before authoring/changing `spec/*.bear.yaml`; then update IR/implementation from spec intent.
2. Run pre-gate sequence as needed:
- `bear validate <ir-file>`
- `bear compile <ir-file> --project <repoRoot>` or `bear compile --all --project <repoRoot>`
3. Run machine gate:
- `bear check --all --project <repoRoot> --collect=all --agent`
4. If `status=fail` and `nextAction` exists:
- execute only `nextAction.commands`
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
2. For process preconditions (missing agent package files, greenfield implementation before IR compile, missing index preflight, post-failure command drift), classify `PROCESS_VIOLATION|<label>|<evidence>` and follow `.bear/agent/TROUBLESHOOTING.md` labels.
3. For expected greenfield baseline `BOUNDARY_EXPANSION_DETECTED`, follow `.bear/agent/TROUBLESHOOTING.md` greenfield policy and report `WAITING_FOR_BASELINE_REVIEW` per `.bear/agent/REPORTING.md`.
4. If spec conflicts with explicit policy/contract rules, stop and escalate unless the spec explicitly authorizes rule changes.

## Done Gate Contract

Required evidence before completion:
1. `bear check --all --project <repoRoot> [--collect=all] --agent => 0`
2. `bear pr-check --all --project <repoRoot> --base <ref> [--collect=all] --agent => 0`
3. completion report follows `.bear/agent/REPORTING.md` exactly


