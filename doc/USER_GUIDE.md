# BEAR CLI User Guide (Preview)

This guide is for day-to-day usage of `bear` in a project.

## What BEAR Is (quick)

BEAR is a deterministic CLI that helps you control boundary changes in backend code.

It uses an **IR** (Intermediate Representation): a small YAML spec file (for example `spec/withdraw.bear.yaml`) that declares:
- block contract (inputs/outputs)
- allowed external capabilities (effects/ports)
- key invariants and idempotency settings

From that IR, BEAR validates, generates deterministic code artifacts, and enforces consistency through `bear check` and `bear pr-check`.

## Operating model (developer + agent)

Default expectation in BEAR workflows:
- developer provides domain intent and reviews boundary-expanding changes
- agent performs BEAR mechanics (IR updates, generation, gate runs, and failure triage)

Developers are not expected to hand-edit IR as routine workflow.
IR remains the control surface that agents update when boundary/contract/effect changes are required.

## Why This Exists (AI development context)

In AI-assisted development, code can be produced quickly, but structural risk can spread just as quickly:
- new external calls can appear without explicit review
- generated artifacts can drift from declared design
- CI signals can become noisy or non-actionable

BEAR is designed to counter that pattern with deterministic, machine-checkable contracts:
- IR-first boundary declaration instead of implicit behavior spread
- explicit boundary-expansion signaling (`pr-check`) for review and governance
- deterministic gate outputs and failure envelopes for reliable CI/agent loops

Intent:
- keep inner-loop speed for AI/human implementation work
- make boundary power changes explicit, reviewable, and hard to miss
- provide stable, automatable signals rather than subjective prompts/process

For full normative command contracts, see:
- `spec/commands/validate.md`
- `spec/commands/compile.md`
- `spec/commands/check.md`
- `spec/commands/pr-check.md`
- `spec/commands/exit-codes.md`

For full invariant intent and enforcement status (`ENFORCED`/`PARTIAL`/`PLANNED`), see:
- `doc/INVARIANT_CHARTER.md`

## Core commands

### 1. Validate IR

```text
bear validate <ir-file>
```

Use when:
- reviewing or checking IR produced/updated by the agent
- checking schema/semantic correctness before generation

Success:
- exits `0`
- prints canonical normalized YAML to stdout

### 2. Compile generated artifacts

```text
bear compile <ir-file> --project <path>
```

Use when:
- creating or updating generated BEAR-owned code from IR

Behavior:
- regenerates BEAR-owned artifacts under `<project>/build/generated/bear`
- does not overwrite user-owned impl files under `<project>/src/main/java`

### 3. Local gate (drift + tests)

```text
bear check <ir-file> --project <path>
```

Use when:
- verifying generated artifacts are in sync
- running project test gate after drift passes

Behavior:
- fails with drift exit code when generated baseline is stale/missing
- fails with undeclared-reach exit code when covered direct HTTP client usage bypasses declared ports
- runs project tests only after no-drift result

### 4. PR governance gate (base diff classification)

```text
bear pr-check <ir-file> --project <path> --base <ref>
```

Use when:
- classifying IR deltas against base branch in CI/review flow

Behavior:
- exits `5` when boundary expansion is detected
- exits `0` for no-boundary-expansion outcomes

## Non-zero failure envelope

All non-zero exits in `validate`, `compile`, `check`, and `pr-check` include:

```text
CODE=<enum>
PATH=<locator>
REMEDIATION=<deterministic-step>
```

Contract:
- emitted exactly once
- always the last 3 stderr lines
- no stderr output after `REMEDIATION=...`

## `PATH` locator meaning

`PATH` is a locator, not only a filesystem path.

Allowed forms:
- repo-relative path (example: `spec/withdraw.bear.yaml`)
- stable pseudo-path token (example: `cli.args`, `cli.command`, `project.tests`, `internal`, `build/generated/bear`)

Disallowed:
- absolute filesystem paths

## Exit codes (quick reference)

- `0` pass
- `2` validation/schema/semantic failure
- `3` drift failure
- `4` project test failure (including timeout)
- `5` boundary expansion detected in `pr-check`
- `6` undeclared reach detected in `check` (`CODE=UNDECLARED_REACH`)
- `64` usage/argument failure
- `70` internal/unexpected failure
- `74` IO/git failure

## Invariant Contract (preview)

`bear check` enforces:
- deterministic generated-artifact drift gate
- covered undeclared-reach gate for direct HTTP bypass surfaces
- project tests (only after drift and undeclared-reach pass)

`bear pr-check` enforces:
- deterministic base-vs-head boundary-delta visibility
- explicit boundary-expansion verdict for CI/review

Planned after preview:
- broader undeclared-reach coverage classes
- dependency-direction and cross-domain leakage hardening
- additional structural invariants listed in `doc/INVARIANT_CHARTER.md`

All non-zero command exits include deterministic footer lines:
- `CODE=...`
- `PATH=...`
- `REMEDIATION=...`

## Typical loop

1. Developer states feature intent in domain terms.
2. Agent updates IR if boundary/contract/effect changes are needed.
3. Agent runs `bear validate <ir-file>`.
4. Agent runs `bear compile <ir-file> --project <path>`.
5. Agent implements user-owned logic/tests.
6. Agent runs `bear check <ir-file> --project <path>`.
7. For PR governance, run `bear pr-check <ir-file> --project <path> --base <ref>`.
