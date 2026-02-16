# BEAR v0 Execution Roadmap

This is the concrete execution tracker for v0 delivery.
Use this for implementation sequencing and completion status.
For broader target direction, see `doc/ROADMAP.md`.

## v0 Scope Lock

In scope:
- JVM target only
- single logic block per IR
- deterministic validate/compile/check pipeline
- governance signaling for boundary expansion in check workflow
- demo proof loop

Out of scope:
- runtime sandboxing
- full static isolation hardening of all impl calls
- full behavioral specification DSL
- cross-language parity

## Phase 1 - Deterministic IR Core

- [x] Strict IR parsing + schema validation
- [x] Semantic validation rules
- [x] Deterministic normalization
- [x] Canonical YAML emission
- [x] `bear validate <file>` contract + tests

Milestone:
Validation is deterministic and boring.

## Phase 2 - Deterministic JVM Compile

- [x] `Target` abstraction + `JvmTarget`
- [x] BEAR-owned generated tree
- [x] user-owned impl create-once preservation
- [x] deterministic models/ports/entrypoint generation
- [x] conditional idempotency + invariant tests
- [x] compile spec (`spec/commands/compile.md`)
- [x] compile golden corpus + conformance tests

Milestone:
`bear compile` output is deterministic and byte-stable.

## Phase 3 - Drift Gate (`bear check` v1)

- [x] validate + temp compile + diff
- [x] deterministic drift lines (`ADDED`/`REMOVED`/`CHANGED`)
- [x] missing/empty baseline deterministic failure
- [x] compare-only (no project mutation)
- [x] check spec (`spec/commands/check.md`) for drift behavior

Milestone:
Drift cannot pass silently.

## Phase 4 - Project Test Gate (`bear check` v1.1)

- [x] run project tests after drift pass
- [x] wrapper-only execution
- [x] deterministic timeout + failure handling
- [x] deterministic normalized output tailing
- [x] drift short-circuit before tests

Milestone:
Single command enforces regen conformance + project tests.

## Phase 5 - Boundary-Expansion Signaling (v0 closeout)

- [x] implement deterministic boundary-expansion classification in check
- [x] emit stable boundary-signal output lines
- [x] conformance tests for signal determinism/order
- [x] update check spec with boundary-signal contract

Milestone:
Boundary expansion cannot be introduced without explicit CI-visible signal.

## Phase 6 - Demo Proof (bear-account-demo)

- [x] implement naive Withdraw variant
- [x] prove `bear check` fails deterministically
- [x] implement corrected Withdraw variant
- [x] prove `bear check` passes deterministically
- [x] publish concise reproducible runbook

Milestone:
"Naive withdraw fails. Correct withdraw passes."

Scenario branch model (demo repo):
- `main`: spec-first runnable baseline
- `scenario/naive-fail-withdraw`: deterministic test-failure proof (`check: TEST_FAILED...`, exit `4`)
- `scenario/corrected-pass-withdraw`: deterministic pass proof (`check: OK`, exit `0`)
- `scenario/boundary-expansion-visible`: historical placeholder, replaced in M1 realism reset by canonical branches (`scenario/greenfield-build`, `scenario/feature-extension`)

## v0 Done Criteria

All must be true:
- deterministic validate pipeline
- deterministic compile pipeline
- two-tree ownership enforced
- check gate (drift + tests + boundary signal)
- demo proof reproducible end-to-end

## Post-v0 Milestone Split

### M1 (active): isolated workflow proof

Deliver:
- demo-local `BEAR_PRIMER.md` + `AGENTS.md` (thin bootstrap) + `BEAR_AGENT.md` + `WORKFLOW.md` + minimal spec pack
- demo-local canonical gate scripts (`bin/bear-all.ps1`, `bin/bear-all.sh`)
- demo-local BEAR wrapper scripts (`bin/bear.ps1`, `bin/bear.sh`) with pinned local CLI path
- canonical scenario branches:
  - `scenario/greenfield-build`
  - `scenario/feature-extension`
- minimal undeclared-reach check wired into demo verification path
- evaluator runbooks moved to `bear-cli/doc/m1-eval/*` (kept out of demo repo to avoid cheat hints)
- canonical gate behavior for greenfield:
  - discovers `spec/*.bear.yaml` in deterministic order
  - fails with actionable message when no IR files exist

Done:
- isolated agent can complete one non-boundary feature and one boundary-expanding feature using demo repo only
- both flows terminate via the same canonical gate command
- first-time isolated agent can explain BEAR concepts from demo-local docs and run bootstrap loop correctly

### M1.1 (next hardening target): PR governance signal quality

Deliver:
- add PR/base-branch boundary diff mode (`bear pr-check` or equivalent) to classify capability/port/op/contract/invariant deltas against base
- support CI marking for boundary-expanding PRs (approval hook input)
- normalize exit-code semantics for stale/boundary paths (avoid ambiguous generic failure code for classified drift/boundary states)

Done:
- PR runs produce deterministic boundary classification against base branch
- CI can gate on explicit boundary-expansion status without depending on local stale-check ordering

Legacy/non-canonical proof branches:
- `scenario/naive-fail-withdraw`
- `scenario/corrected-pass-withdraw`

### M2 (future candidate): resource packaging/versioning

Not an immediate next step after M1. Revisit only when BEAR maturity justifies packaging/distribution design.

Potential future scope:
- versioned resource bundle format
- import/export automation
- checksum/lock/provenance automation
