# REPORTING.md

Purpose:
- Canonical minimal run report contract for agent consistency.
- Mechanically checkable fields consumed by core lint.

## DEVELOPER_SUMMARY

Run report MUST start with this concise template and order:
1. `What changed:`
2. `How to run:`
3. `Status:`
4. `Blocking:`
5. `Next action:`
6. `Review scope:`

`Status:` MUST use this one-line format:
1. `tests=<PASS|FAIL>; check=<code>; pr-check=<code> base=<ref>; outcome=<COMPLETE|BLOCKED|WAITING_FOR_BASELINE_REVIEW>`

`Review scope:` MUST follow:
1. max 8 comma-separated entries.
2. when `Run outcome: WAITING_FOR_BASELINE_REVIEW`, first two entries are exactly `bear.blocks.yaml`, `bear-ir/*.bear.yaml`.

## Agent Loop Contract

Deterministic machine loop when automation consumes BEAR output:
1. Before any failure, you may run the standard gate sequence: `validate`, `compile|fix`, `check`, `pr-check`.
2. For machine gates, run `--agent`:
- `bear check --all --project <repoRoot> --collect=all --agent`
- `bear pr-check --all --project <repoRoot> --base <ref> --collect=all --agent`
3. Automation MUST parse only stdout JSON in `--agent` mode; never parse human prose as control input.
4. If `status=fail` and `nextAction.commands` exists, execute only those BEAR commands in listed order (no ad-hoc retries).
5. If `status=fail` and `nextAction` is `null`, route to `.bear/agent/TROUBLESHOOTING.md` using `(category, failureCode, ruleId|reasonKey)` and escalate with deterministic evidence.
6. stderr may contain tool output; treat stderr as evidence only, not as control input.
7. Field-level quickref: `.bear/agent/ref/AGENT_JSON_QUICKREF.md`.

Supported command whitelist for the agent loop:
1. `bear validate`
2. `bear compile`
3. `bear fix`
4. `bear check`
5. `bear pr-check`
6. `bear unblock`

## Required Fields (Minimal Core)

Core lint reads only these required fields:
1. `Status: tests=<PASS|FAIL>; check=<code>; pr-check=<code> base=<ref>; outcome=<token>`
2. `Run outcome: COMPLETE|BLOCKED|WAITING_FOR_BASELINE_REVIEW`
3. `Gate results:`
4. `- bear check --all --project <repoRoot> [--blocks <repo-relative>] [--collect=all] --agent => <exit>`
5. `- bear pr-check --all --project <repoRoot> --base <ref> [--blocks <repo-relative>] [--collect=all] --agent => <exit>`
6. `IR delta: <files + boundary notes>`
7. `Decomposition contract consulted: yes (before IR authoring)` or `n/a (no IR authoring/change in this run)`

Conditional required fields:
1. when `Run outcome` is `BLOCKED|WAITING_FOR_BASELINE_REVIEW`:
- `Required next action: <...>`
- `Gate blocker: <...>`
2. when `Run outcome` is `WAITING_FOR_BASELINE_REVIEW`:
- `Gate blocker: BOUNDARY_EXPANSION`
- `Baseline review scope: ...` including `bear.blocks.yaml` and `bear-ir/*.bear.yaml` (pinned v1 contract)

Additional fields are allowed but ignored by core lint.

## Optional Evidence Fields (Not Lint-Required)

These may be included when useful, but are non-authoritative for pass/fail:
1. `Request summary`
2. `Block decision`
3. `Decomposition evidence`
4. `Decomposition rubric`
5. `Decomposition mode`
6. `Groups`
7. `Decomposition reason`
8. `Blocks added`
9. `Implementation delta`
10. `Tests delta`
11. `Surface evidence` or `Surface deferred`
12. `Gate run order`
13. `Stopped after blocker`
14. `First failing command`
15. `First failure signature`
16. `Tooling anomaly` and anomaly details
17. `Repro case`
18. `PR base used` and `PR base rationale`
19. `PR classification interpretation`
20. `Constraint conflicts encountered`
21. `Escalation decision`
22. `Containment sanity check`
23. `Infra edits`
24. `Unblock used`
25. `Gate policy acknowledged`
26. `Final git status`
27. `GOVERNANCE_SIGNAL_DISPOSITION` block

## Outcome Rules

1. Allowed `Run outcome` values are exactly: `COMPLETE | BLOCKED | WAITING_FOR_BASELINE_REVIEW`.
2. `Status:` and `Run outcome:` are both mandatory and MUST agree on outcome token.
3. `Run outcome: COMPLETE` requires canonical repo-level done gates in `Gate results` with `=> 0`.
4. When `Run outcome: COMPLETE`, `Gate results` must include canonical repo-level done gates.
5. `Run outcome: COMPLETE` requires `check=0` and `pr-check=0` in `Status`.
6. `Run outcome: BLOCKED|WAITING_FOR_BASELINE_REVIEW` requires `Required next action` and `Gate blocker`.
7. Do not present non-complete outcomes as completion in scoped summary/status lines.

## Decomposition Checkpoint Rule

1. `Decomposition contract consulted: yes (before IR authoring)` is required when `IR delta` indicates `bear-ir/*.bear.yaml` authoring/modification.
2. `Decomposition contract consulted: n/a (no IR authoring/change in this run)` is required when no IR authoring/modification occurred.

## GREENFIELD_BASELINE_WAITING_SEMANTICS

Use `Run outcome: WAITING_FOR_BASELINE_REVIEW` only when all are true:
1. run is greenfield baseline (`bear-ir/*.bear.yaml` newly introduced in this PR),
2. `pr-check` fails with boundary expansion (`exit=5`),
3. expansion corresponds to newly introduced blocks/contracts/ports in this PR.

Deterministic pairing:
1. `Gate blocker: BOUNDARY_EXPANSION`
2. `Run outcome: WAITING_FOR_BASELINE_REVIEW`

## Noise-Control Guidance

Keep report output compact and stable:
1. prefer minimal core fields + one-line values.
2. avoid long transcript dumps in the final report.
3. include optional evidence fields only when they add remediation value.

## Minimal COMPLETE Example

```text
Status: tests=PASS; check=0; pr-check=0 base=origin/main; outcome=COMPLETE
IR delta: modified bear-ir/withdraw.bear.yaml
Decomposition contract consulted: yes (before IR authoring)
Gate results:
- bear check --all --project . --collect=all --agent => 0
- bear pr-check --all --project . --base origin/main --collect=all --agent => 0
Run outcome: COMPLETE
```

## Minimal WAITING_FOR_BASELINE_REVIEW Example

```text
Status: tests=PASS; check=0; pr-check=5 base=origin/main; outcome=WAITING_FOR_BASELINE_REVIEW
IR delta: added bear-ir/*.bear.yaml and bear.blocks.yaml
Decomposition contract consulted: yes (before IR authoring)
Gate results:
- bear check --all --project . --collect=all --agent => 0
- bear pr-check --all --project . --base origin/main --collect=all --agent => 5
Run outcome: WAITING_FOR_BASELINE_REVIEW
Required next action: boundary governance review and baseline merge
Gate blocker: BOUNDARY_EXPANSION
Baseline review scope: bear.blocks.yaml, bear-ir/*.bear.yaml
```

## Minimal BLOCKED Example

```text
Status: tests=PASS; check=0; pr-check=5 base=origin/main; outcome=BLOCKED
IR delta: modified bear-ir/account.bear.yaml
Decomposition contract consulted: yes (before IR authoring)
Gate results:
- bear check --all --project . --collect=all --agent => 0
- bear pr-check --all --project . --base origin/main --collect=all --agent => 5
Run outcome: BLOCKED
Required next action: resolve governance findings and rerun pr-check
Gate blocker: OTHER
```


