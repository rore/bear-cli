# REPORTING.md

Purpose:
- Canonical run report schema.
- Mechanically checkable governance-signal disposition contract.

## DEVELOPER_SUMMARY

Run report MUST start with this exact template and order:
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
2. when `Run outcome: WAITING_FOR_BASELINE_REVIEW`, first two entries are exactly `bear.blocks.yaml`, `spec/*.bear.yaml` (literal token, not expanded files).

## Agent Loop Contract

Deterministic machine loop when automation consumes BEAR output:
1. Before any failure, you may run the standard gate sequence: `validate`, `compile|fix`, `check`, `pr-check`.
2. For machine gates, run `--agent`:
- `bear check --all --project <repoRoot> --collect=all --agent`
- `bear pr-check --all --project <repoRoot> --base <ref> --collect=all --agent`
3. Automation MUST parse only stdout JSON in `--agent` mode; never parse human prose as control input.
4. If `status=fail` and `nextAction.commands` exists, execute only those BEAR commands and rerun the same gate.
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

## Required Fields

Run report MUST include:
1. `Request summary: <one line>`
2. `Block decision: updated=<...> added=<...>`
3. `Decomposition evidence: <explicit rubric/trigger evidence>`
4. `Decomposition rubric: state_domain_<same|split>; effects_<read_only|write>; idempotency_<same|split|n/a>; lifecycle_<same|split>; authority_<same|split>`
5. `Decomposition mode: single|grouped|multi`
6. `Groups: n/a` OR `Groups: [<group_name>:{<block1>,<block2>}; <group_name>:{<block3>}]`
7. `Decomposition reason: default|trigger:<canonical_name>|spec_explicit`
8. `Blocks added: [...]`
9. `Grouped operations: n/a` OR `Grouped operations: [<block>:{<op1>,<op2>}; ...]`
10. `IR delta: <files + boundary notes>`
11. `Implementation delta: <files>`
12. `Tests delta: <files>`
13. `Surface evidence: n/a (spec does not require an API surface)` OR
14. `Surface evidence: <file1>,<file2>,...` OR
15. `Surface deferred: <reason_token>`
16. `Gate results:`
17. `- bear check --all --project <repoRoot> [--collect=all] [--agent] => <exit>`
18. `- bear pr-check --all --project <repoRoot> --base <ref> [--collect=all] [--agent] => <exit>`
19. `Gate run order: <ordered list of executed gates>`
20. `Run outcome: COMPLETE|BLOCKED|WAITING_FOR_BASELINE_REVIEW`
21. `Required next action: <...>` (required when `Run outcome` is `BLOCKED` or `WAITING_FOR_BASELINE_REVIEW`)
22. `Gate blocker: IO_LOCK | TEST_FAILURE | BOUNDARY_EXPANSION | OTHER`
23. `Stopped after blocker: yes|no`
24. `First failing command: <exact command line>|none (preflight)`
25. `First failure signature: <one copied verbatim line>`
26. `Tooling anomaly: yes|no`
27. `Tooling anomaly first command: <exact command>|n/a`
28. `Tooling anomaly exit code: <code>|n/a`
29. `Tooling anomaly signature: <first crash/timeout/internal line>|n/a`
30. `Repro case: ir=<file(s)>; command=<exact>; expected=<...>; actual=<...>; clean_state=<yes|no>`
31. `PR base used: <ref>`
32. `PR base rationale: <merge-base against target branch OR user-provided base SHA>`
33. `PR classification interpretation: <expected|unintended> - <brief rationale>`
34. `Baseline review scope: <required for WAITING_FOR_BASELINE_REVIEW; must include bear.blocks.yaml and spec/*.bear.yaml>`
35. `Constraint conflicts encountered: none|<list>`
36. `Escalation decision: none|<reason>`
37. `Containment sanity check: pass|fail|n/a - <evidence>`
38. `Infra edits: none|<list>`
39. `Unblock used: no|yes - <reason>`
40. `Gate policy acknowledged: yes|no`
41. `Final git status: <git status --short summary>`
42. `GOVERNANCE_SIGNAL_DISPOSITION`
43. `MULTI_BLOCK_PORT_IMPL_ALLOWED: none|<count>`
44. `JUSTIFICATION: <required when count > 0>`
45. `TRADEOFF: <required when count > 0>`

## Decomposition Field Rules

1. Mode/groups coupling:
- `Decomposition mode: grouped` => `Groups: [<group_name>:{<block1>,<block2>}; <group_name>:{<block3>}]`
- `Decomposition mode: single|multi` => `Groups: n/a`
2. `Groups` format is stable:
- group names sorted lexicographically
- block names inside each group sorted lexicographically
- no freeform prose
3. `Grouped operations` format is stable when mode is grouped:
- block names sorted lexicographically
- operation names per block sorted lexicographically
- operation names are block-local; do not key contract fields across different operations
4. `idempotency_n/a` is valid only when no operation in the decomposition is idempotent.
5. `Decomposition reason: trigger:<canonical_name>` must use only canonical split-trigger tokens defined in `.bear/agent/CONTRACTS.md` (`Decomposition Signals (Normative)`).

PR delta interpretation addendum:
1. Operation add/remove must be interpreted as `BOUNDARY_EXPANDING` surface expansion.
2. Operation `uses`, operation idempotency, and operation invariants deltas are `BOUNDARY_EXPANDING`.
3. Operation contract deltas follow contract semantics and must remain operation-attributed (`op.<operation>:...` keys).

Surface contract notes:
1. `Surface deferred` allowed reason tokens only: `out_of_scope_by_spec|explicit_user_deferral|demo_minimalism`.
2. `Surface evidence` must reference concrete non-generated runtime entrypoint/routing files under `src/main/java`.
3. Generated stubs/wrappers under generated directories do not count as surface evidence.
4. This is an agent reporting contract requirement; CLI does not parse/enforce this field.

## Outcome Rules

1. Both gate exits are `0` -> `Run outcome` MUST be `COMPLETE`.
2. `Run outcome` MUST be `WAITING_FOR_BASELINE_REVIEW` only when `GREENFIELD_BASELINE_WAITING_SEMANTICS` criteria are all true.
3. Any other non-zero `pr-check` result -> `Run outcome` MUST be `BLOCKED`.
4. If `Run outcome` is `BLOCKED` or `WAITING_FOR_BASELINE_REVIEW`, `Required next action` is mandatory.
5. Do not present `BLOCKED` or `WAITING_FOR_BASELINE_REVIEW` runs as completion.
6. Gate policy acknowledged must reflect: completion requires both repo-level gates green.
7. If `Gate blocker` is `IO_LOCK`, `Stopped after blocker` MUST be `yes`.
8. If `pr-check` prints `BOUNDARY_EXPANSION_DETECTED` but exit is not `5`, classify `Gate blocker` as `OTHER` and stop.
9. For this anomaly, `First failure signature` must include both marker text and observed exit code.

## GREENFIELD_BASELINE_WAITING_SEMANTICS

Use `Run outcome: WAITING_FOR_BASELINE_REVIEW` only when all are true:
1. run is greenfield baseline (`spec/*.bear.yaml` newly introduced in this PR),
2. `pr-check` fails with `BOUNDARY_EXPANSION_DETECTED`,
3. expansion corresponds to newly introduced blocks/contracts/ports in this PR.

Deterministic pairing:
1. `Gate blocker: BOUNDARY_EXPANSION`
2. `Run outcome: WAITING_FOR_BASELINE_REVIEW`

Non-applicability:
1. do not use this outcome for unexpected expansion in non-greenfield repos.
2. do not use this outcome for unrelated failures.

## Blocker And Anomaly Reporting

1. For policy/tool/process anomalies, set `Gate blocker: OTHER`.
2. For `PR_CHECK_EXIT_ENVELOPE_ANOMALY`, stop immediately and capture exact marker + observed exit in `First failure signature`.
3. `Gate blocker`, `Stopped after blocker`, `First failing command`, and `First failure signature` are always required.
4. For process/preflight anomalies, use signature format: `PROCESS_VIOLATION|<label>|<evidence>`.
5. If no command failed because failure occurred at preflight observation time, set:
- `First failing command: none (preflight)`
- `First failure signature: PROCESS_VIOLATION|<label>|<missing/evidence>`

## Tooling Anomaly Reporting

When `Tooling anomaly: yes`:
1. stop immediately after first anomaly (no workaround edits)
2. populate anomaly fields using first failing command/signature only
3. `Repro case` must be minimal and deterministic:
- `ir=<single file or exact set>`
- `command=<single command>`
- `expected=<expected behavior>`
- `actual=<observed behavior>`
- `clean_state=yes|no` (whether unrelated extra IR/index files were present)

## Recommended Verification Notes (Optional)

Optional field:
1. `Recommended verification notes: <summary>`

Guidance:
1. For ordered/filtered/structured outputs, prefer parsed property assertions over substring-only checks.
2. This section is advisory and must not be treated as a required gate/report field.

## Governance-Signal Disposition Rules

1. `MULTI_BLOCK_PORT_IMPL_ALLOWED: none` is valid when no governance signal lines exist.
2. If governance signal count is non-zero, both `JUSTIFICATION` and `TRADEOFF` are required.
3. Missing disposition block, mismatched count, or missing required fields means report is incomplete.
4. `PR base used` and `PR base rationale` are mandatory; defaulting to `HEAD` without explicit instruction is invalid.
5. `PR classification interpretation` is mandatory and must state whether the classification is expected or unintended.
6. `Gate blocker`, `Stopped after blocker`, `First failing command`, `First failure signature`, `Constraint conflicts encountered`, `Escalation decision`, `Containment sanity check`, `Infra edits`, `Unblock used`, `Gate policy acknowledged`, `Gate run order`, and `Final git status` are mandatory.

## Count Rule (Frozen)

`<count>` equals:
1. number of `MULTI_BLOCK_PORT_IMPL_ALLOWED` governance signal lines emitted by `bear pr-check --all --project <repoRoot> --base <ref> [--collect=all] [--agent]` for the exact completion run.
2. Copy this count from the command output; do not infer.

## Minimal COMPLETE Example

```text
Developer Summary
What changed: Updated withdraw invariant and tests.
How to run: .\bin\bear.ps1 check --all --project . ; .\bin\bear.ps1 pr-check --all --project . --base origin/main
Status: tests=PASS; check=0; pr-check=0 base=origin/main; outcome=COMPLETE
Blocking: none
Next action: none
Review scope: spec/withdraw.bear.yaml, src/main/java/blocks/withdraw/impl/WithdrawImpl.java, src/test/java/blocks/withdraw/WithdrawImplTest.java

Request summary: Add transfer fee invariants to existing withdrawal flow
Block decision: updated=withdraw added=none
Decomposition evidence: grouped model retained; compatibility dimensions stayed `_same`
Decomposition rubric: state_domain_same; effects_write; idempotency_n/a; lifecycle_same; authority_same
Decomposition mode: grouped
Groups: [wallet_write_flow:{withdraw}]
Decomposition reason: default
Blocks added: []
IR delta: spec/withdraw.bear.yaml (invariants updated)
Implementation delta: src/main/java/blocks/withdraw/impl/WithdrawImpl.java
Tests delta: src/test/java/blocks/withdraw/WithdrawImplTest.java
Surface evidence: n/a (spec does not require an API surface)
Gate results:
- bear check --all --project . => 0
- bear pr-check --all --project . --base origin/main => 0
Gate run order: bear check --all --project . -> bear pr-check --all --project . --base origin/main
Run outcome: COMPLETE
Gate blocker: OTHER
Stopped after blocker: no
First failing command: none
First failure signature: none
PR base used: origin/main
PR base rationale: target branch merge-base reference for this completion run
PR classification interpretation: expected - boundary declarations match intentional IR delta
Baseline review scope: n/a
Constraint conflicts encountered: none
Escalation decision: none
Containment sanity check: pass - containment diagnostics were not needed for this run
Infra edits: none
Unblock used: no - not needed
Gate policy acknowledged: yes
Final git status: clean (no tracked or untracked changes)
GOVERNANCE_SIGNAL_DISPOSITION
MULTI_BLOCK_PORT_IMPL_ALLOWED: none
```

## Minimal WAITING_FOR_BASELINE_REVIEW Example

```text
Developer Summary
What changed: Introduced initial greenfield wallet baseline blocks and implementations.
How to run: .\bin\bear.ps1 check --all --project . ; .\bin\bear.ps1 pr-check --all --project . --base origin/main
Status: tests=PASS; check=0; pr-check=5 base=origin/main; outcome=WAITING_FOR_BASELINE_REVIEW
Blocking: boundary governance review pending
Next action: boundary governance review and baseline merge
Review scope: bear.blocks.yaml, spec/*.bear.yaml

Request summary: Initial greenfield wallet baseline
Block decision: updated=none added=create-wallet,deposit-to-wallet,withdraw-from-wallet,get-wallet-balance,get-wallet-statement
Decomposition evidence: split required by `state_domain_split` and `effects_split`
Decomposition rubric: state_domain_split; effects_write; idempotency_split; lifecycle_same; authority_same
Decomposition mode: multi
Groups: n/a
Decomposition reason: trigger:effects_split
Blocks added: [create-wallet, deposit-to-wallet, withdraw-from-wallet, get-wallet-balance, get-wallet-statement]
IR delta: spec/*.bear.yaml, bear.blocks.yaml
Implementation delta: src/main/java/blocks/**, src/main/java/com/bear/account/demo/WalletService.java
Tests delta: src/test/java/com/bear/account/demo/AppTest.java
Surface evidence: src/main/java/com/bear/account/demo/App.java,src/main/java/com/bear/account/demo/WalletService.java
Gate results:
- bear check --all --project . => 0
- bear pr-check --all --project . --base origin/main => 5
Gate run order: bear check --all --project . -> bear pr-check --all --project . --base origin/main
Run outcome: WAITING_FOR_BASELINE_REVIEW
Required next action: boundary governance review and baseline merge
Gate blocker: BOUNDARY_EXPANSION
Stopped after blocker: yes
First failing command: bear pr-check --all --project . --base origin/main
First failure signature: DETAIL: pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED
PR base used: origin/main
PR base rationale: target branch merge-base reference for this run
PR classification interpretation: expected - baseline introduces new blocks/contracts/ports
Baseline review scope: bear.blocks.yaml, spec/*.bear.yaml
Constraint conflicts encountered: none
Escalation decision: required - baseline boundary review pending
Containment sanity check: n/a - no containment/classpath failure signature in check output
Infra edits: none
Unblock used: no - not needed
Gate policy acknowledged: yes
Final git status: M src/main/java/blocks/... (summary)
GOVERNANCE_SIGNAL_DISPOSITION
MULTI_BLOCK_PORT_IMPL_ALLOWED: none
```

## Minimal BLOCKED Example

```text
Developer Summary
What changed: Added a new block with non-greenfield boundary expansion.
How to run: .\bin\bear.ps1 check --all --project . ; .\bin\bear.ps1 pr-check --all --project . --base origin/main
Status: tests=PASS; check=0; pr-check=5 base=origin/main; outcome=BLOCKED
Blocking: boundary governance review required
Next action: obtain governance approval for expansion
Review scope: bear.blocks.yaml, spec/wallet.bear.yaml

Request summary: Non-greenfield boundary expansion without approved scope
Block decision: updated=none added=wallet
Decomposition evidence: spec explicitly introduced isolated authority boundary
Decomposition rubric: state_domain_split; effects_write; idempotency_n/a; lifecycle_same; authority_split
Decomposition mode: multi
Groups: n/a
Decomposition reason: spec_explicit
Blocks added: [wallet]
IR delta: spec/wallet.bear.yaml, bear.blocks.yaml
Implementation delta: src/main/java/blocks/wallet/impl/WalletImpl.java
Tests delta: src/test/java/blocks/wallet/WalletImplTest.java
Surface deferred: explicit_user_deferral
Gate results:
- bear check --all --project . => 0
- bear pr-check --all --project . --base origin/main => 5
Gate run order: bear check --all --project . -> bear pr-check --all --project . --base origin/main
Run outcome: BLOCKED
Required next action: governance review/approval for expected boundary expansion
Gate blocker: BOUNDARY_EXPANSION
Stopped after blocker: yes
First failing command: bear pr-check --all --project . --base origin/main
First failure signature: CATEGORY: BOUNDARY_EXPANSION_DETECTED
PR base used: origin/main
PR base rationale: target branch merge-base reference for this completion run
PR classification interpretation: expected - intentional boundary expansion for new block
Baseline review scope: n/a
Constraint conflicts encountered: none
Escalation decision: required - boundary expansion governance review pending
Containment sanity check: n/a - no containment/classpath failure signature in check output
Infra edits: none
Unblock used: no - unblock is stale-marker-only and not valid for intentional expansion
Gate policy acknowledged: yes
Final git status: M src/main/java/blocks/wallet/impl/WalletImpl.java
GOVERNANCE_SIGNAL_DISPOSITION
MULTI_BLOCK_PORT_IMPL_ALLOWED: none
```
