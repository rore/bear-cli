# REPORTING.md

Purpose:
- Canonical run report schema.
- Mechanically checkable governance-signal disposition contract.

## Required Fields

Run report MUST include:
1. `Request summary: <one line>`
2. `Block decision: updated=<...> added=<...>`
3. `Decomposition evidence: <single-block rationale OR per-block spec citations>`
4. `IR delta: <files + boundary notes>`
5. `Implementation delta: <files>`
6. `Tests delta: <files>`
7. `Gate results:`
8. `- bear check --all --project <repoRoot> => <exit>`
9. `- bear pr-check --all --project <repoRoot> --base <ref> => <exit>`
10. `Gate run order: <ordered list of executed gates>`
11. `Run outcome: COMPLETE|BLOCKED`
12. `Required next action: <...>` (required when `Run outcome: BLOCKED`)
13. `Gate blocker: IO_LOCK | TEST_FAILURE | BOUNDARY_EXPANSION | OTHER`
14. `Stopped after blocker: yes|no`
15. `First failing command: <exact command line>`
16. `First failure signature: <one copied verbatim line>`
17. `PR base used: <ref>`
18. `PR base rationale: <merge-base against target branch OR user-provided base SHA>`
19. `PR classification interpretation: <expected|unintended> - <brief rationale>`
20. `Constraint conflicts encountered: none|<list>`
21. `Escalation decision: none|<reason>`
22. `Containment sanity check: pass|fail|n/a - <evidence>`
23. `Infra edits: none|<list>`
24. `Unblock used: no|yes - <reason>`
25. `Gate policy acknowledged: yes|no`
26. `Final git status: <git status --short summary>`
27. `GOVERNANCE_SIGNAL_DISPOSITION`
28. `MULTI_BLOCK_PORT_IMPL_ALLOWED: none|<count>`
29. `JUSTIFICATION: <required when count > 0>`
30. `TRADEOFF: <required when count > 0>`

## Outcome Rules

1. `pr-check` exit is non-zero -> `Run outcome` MUST be `BLOCKED`.
2. If `Run outcome` is `BLOCKED`, `Required next action` is mandatory.
3. Both gate exits are `0` -> `Run outcome` MUST be `COMPLETE`.
4. Do not present `BLOCKED` runs as completion.
5. Gate policy acknowledged must reflect: completion requires both repo-level gates green.
6. If `Gate blocker` is `IO_LOCK`, `Stopped after blocker` MUST be `yes`.
7. If `pr-check` prints `BOUNDARY_EXPANSION_DETECTED` but exit is not `5`, classify `Gate blocker` as `OTHER` and stop.
8. For this anomaly, `First failure signature` must include both marker text and observed exit code.

## Governance-Signal Disposition Rules

1. `MULTI_BLOCK_PORT_IMPL_ALLOWED: none` is valid when no governance signal lines exist.
2. If governance signal count is non-zero, both `JUSTIFICATION` and `TRADEOFF` are required.
3. Missing disposition block, mismatched count, or missing required fields means report is incomplete.
4. `PR base used` and `PR base rationale` are mandatory; defaulting to `HEAD` without explicit instruction is invalid.
5. `PR classification interpretation` is mandatory and must state whether the classification is expected or unintended for this change.
6. `Gate blocker`, `Stopped after blocker`, `First failing command`, `First failure signature`, `Constraint conflicts encountered`, `Escalation decision`, `Containment sanity check`, `Infra edits`, `Unblock used`, `Gate policy acknowledged`, `Gate run order`, and `Final git status` are mandatory.

## Count Rule (Frozen)

`<count>` equals:
- number of `MULTI_BLOCK_PORT_IMPL_ALLOWED` governance signal lines emitted by:
- `bear pr-check --all --project <repoRoot> --base <ref>`
- for the exact completion run being reported.
- Copy this count from the `pr-check` output of that exact completion run; do not infer.

## Minimal COMPLETE Example

```text
Request summary: Add transfer fee invariants to existing withdrawal flow
Block decision: updated=withdraw added=none
Decomposition evidence: single block retained; no new lifecycle/effect/authority/state split reason in spec
IR delta: spec/withdraw.bear.yaml (invariants updated)
Implementation delta: src/main/java/blocks/withdraw/impl/WithdrawImpl.java
Tests delta: src/test/java/blocks/withdraw/WithdrawImplTest.java
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
PR classification interpretation: expected - new boundary declarations were intentional and match IR delta
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

## Minimal BLOCKED Example

```text
Request summary: Add new block in greenfield repo
Block decision: updated=none added=wallet
Decomposition evidence: new externally visible operation required by spec
IR delta: spec/wallet.bear.yaml, bear.blocks.yaml
Implementation delta: src/main/java/blocks/wallet/impl/WalletImpl.java
Tests delta: src/test/java/blocks/wallet/WalletImplTest.java
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
