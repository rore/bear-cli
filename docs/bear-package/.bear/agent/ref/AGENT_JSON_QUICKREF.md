# AGENT_JSON_QUICKREF.md

Purpose:
- Tiny field-level quick reference for `--agent` machine mode.

## Required Parse Targets

1. `schemaVersion` (expect `bear.nextAction.v1`)
2. `status` (`ok|fail`)
3. `exitCode`
4. `collectMode` (`first|all`)
5. `nextAction` (object|null)
6. `nextAction.kind` (`INFRA|GOVERNANCE`) when `nextAction` is present
7. `nextAction.primaryClusterId` when `nextAction` is present

On failure (`status=fail`):
1. If `nextAction.commands` exists, execute only these commands.
2. If `nextAction` is `null`, route via troubleshooting using problem keys.

## Problem Key Fields

Use these fields for deterministic routing:
1. `problems[*].category`
2. `problems[*].failureCode`
3. `problems[*].ruleId` (governance)
4. `problems[*].reasonKey` (infra)
5. `problems[*].messageKey`

## Notes

1. Parse stdout JSON only in `--agent` mode.
2. Treat stderr as evidence only.
3. Do not depend on object key order.
4. Array order is deterministic and contractual.