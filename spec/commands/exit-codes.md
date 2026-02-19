# BEAR CLI Exit Codes and Failure Envelope (Preview Contract)

This file is the single source of truth for:
- numeric exit code meanings
- non-zero failure-envelope format
- frozen `CODE` enum values
- `PATH` locator rules

## Numeric Exit Registry

- `0`: pass
- `2`: validation/schema/semantic failure
- `3`: drift failure
- `4`: project test failure (including timeout)
- `5`: boundary expansion detected (`pr-check`)
- `6`: undeclared reach detected (`check`)
- `64`: usage/argument failure
- `70`: internal/unexpected failure
- `74`: IO/git failure

## Non-Zero Failure Envelope

Every non-zero command exit in `validate`, `compile`, `check`, and `pr-check` must include this footer on stderr:

`CODE=<enum>`
`PATH=<locator>`
`REMEDIATION=<deterministic-step>`

Hard rules:
- envelope is emitted exactly once
- envelope is the last three stderr lines
- no stderr output may occur after `REMEDIATION=...`
- legacy command diagnostics may appear before the envelope

## `PATH` Locator Contract

`PATH` is a locator, not strictly a filesystem path.

Allowed forms:
- repo-relative filesystem path (example: `spec/withdraw.bear.yaml`)
- stable pseudo-path token (example: `cli.args`, `cli.command`, `project.tests`, `internal`, `build/generated/bear`)

Disallowed:
- absolute filesystem paths

## Frozen `CODE` Enum

- `USAGE_INVALID_ARGS`
- `USAGE_UNKNOWN_COMMAND`
- `IR_VALIDATION`
- `IO_ERROR`
- `IO_GIT`
- `DRIFT_MISSING_BASELINE`
- `DRIFT_DETECTED`
- `TEST_FAILURE`
- `TEST_TIMEOUT`
- `BOUNDARY_EXPANSION`
- `UNDECLARED_REACH`
- `INTERNAL_ERROR`

## IO Classification Rule

Use `IO_GIT` when failure comes from:
- invoking git
- reading/interpreting git command results

Use `IO_ERROR` when failure comes from:
- filesystem/path/permission operations
- project-root file reads/writes not owned by git invocation/result handling

## Undeclared Reach Classification Rule

Use `UNDECLARED_REACH` when `bear check` detects covered preview direct external-reach usage that bypasses declared ports.

Expected locator/remediation pattern:
- `PATH` should identify the first violating repo-relative source file (deterministic ordering).
- `REMEDIATION` should instruct:
  - declare required port/op in IR
  - regenerate via `bear compile`
  - route call through generated port interface
