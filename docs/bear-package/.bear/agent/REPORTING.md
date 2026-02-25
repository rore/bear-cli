# REPORTING.md

Purpose:
- Canonical completion report schema.
- Mechanically checkable governance-signal disposition contract.

## Required Fields

Completion report MUST include:
1. `Request summary: <one line>`
2. `Block decision: updated=<...> added=<...>`
3. `Decomposition evidence: <single-block rationale OR per-block spec citations>`
4. `IR delta: <files + boundary notes>`
5. `Implementation delta: <files>`
6. `Tests delta: <files>`
7. `Gate results:`
8. `- bear check --all --project <repoRoot> => <exit>`
9. `- bear pr-check --all --project <repoRoot> --base <ref> => <exit>`
10. `GOVERNANCE_SIGNAL_DISPOSITION`
11. `MULTI_BLOCK_PORT_IMPL_ALLOWED: none|<count>`
12. `JUSTIFICATION: <required when count > 0>`
13. `TRADEOFF: <required when count > 0>`

## Governance-Signal Disposition Rules

1. `MULTI_BLOCK_PORT_IMPL_ALLOWED: none` is valid when no governance signal lines exist.
2. If governance signal count is non-zero, both `JUSTIFICATION` and `TRADEOFF` are required.
3. Missing disposition block, mismatched count, or missing required fields means report is incomplete.

## Count Rule (Frozen)

`<count>` equals:
- number of `MULTI_BLOCK_PORT_IMPL_ALLOWED` governance signal lines emitted by:
- `bear pr-check --all --project <repoRoot> --base <ref>`
- for the exact completion run being reported.

## Minimal Valid Example

```text
Request summary: Add transfer fee invariants to existing withdrawal flow
Block decision: updated=withdraw added=none
Decomposition evidence: single block retained; no new lifecycle/effect/authority/state split reason in spec
IR delta: spec/withdraw.bear.yaml (invariants updated)
Implementation delta: src/main/java/blocks/withdraw/impl/WithdrawImpl.java
Tests delta: src/test/java/blocks/withdraw/WithdrawImplTest.java
Gate results:
- bear check --all --project . => 0
- bear pr-check --all --project . --base HEAD => 0
GOVERNANCE_SIGNAL_DISPOSITION
MULTI_BLOCK_PORT_IMPL_ALLOWED: none
```
