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
- `scenario/boundary-expansion-visible`: reserved next BEAR-specific scenario

## v0 Done Criteria

All must be true:
- deterministic validate pipeline
- deterministic compile pipeline
- two-tree ownership enforced
- check gate (drift + tests + boundary signal)
- demo proof reproducible end-to-end
