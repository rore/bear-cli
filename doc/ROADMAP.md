# BEAR v0 Roadmap

This roadmap is strictly for v0.
If something is not listed here, it does not get built.

Governance policy reference (normative): `doc/GOVERNANCE.md`.

v0 uses:
- Logic blocks only
- Effects expressed as structured ports
- No capability blocks
- No block-to-block composition
- No behavior DSL

v0 guarantees:
- Structural contract enforcement (inputs/outputs)
- Structural effect boundary enforcement via generated structured ports
- Deterministic invariant and idempotency test gating
- Drift detection on generated artifacts
- Deterministic signaling for boundary-expanding changes in check workflow

v0 non-guarantees:
- Business correctness beyond declared invariants
- Real database/concurrency/transaction semantics
- Runtime enforcement beyond test harness
- Full static hard-blocking of all arbitrary impl-side calls (post-v0 hardening unless delivered)

## Governance Milestone (Cross-Phase)

- [x] Define normative IR diff classes: `ordinary` vs `boundary-expanding`
- [x] Add concrete decision table for change classification
- [ ] Ensure `bear check` emits deterministic boundary-expansion signals for covered v0 cases
- [ ] Keep ordinary IR evolution low-friction under standard validate/compile/check loop

Milestone:
Boundary expansion cannot be silent in v0 workflow.

## Phase 0 - Project Setup (bear-cli)

- [x] Create Gradle multi-module project
- [x] Create `kernel` module (trusted seed)
- [x] Create `app` module (CLI wrapper)
- [x] Ensure CLI entrypoint runs: `bear --help`
- [x] Add JUnit 5 test setup
- [x] Add README
- [x] Add `doc/ARCHITECTURE.md`

Milestone:
CLI builds and runs, no BEAR logic yet.

## Phase 1 - BEAR IR Foundation (kernel)

Goal: deterministic parsing + validation + normalization.

- [x] Define v0 IR model (`logic` block, contract, effects, idempotency, invariants)
- [x] Implement strict YAML parsing + unknown-key rejection
- [x] Implement semantic validation (field refs, effect refs, uniqueness)
- [x] Implement deterministic normalization and canonical YAML emit
- [x] Implement `bear validate <file>`
- [x] Lock `doc/IR_SPEC.md` as canonical IR schema contract

Milestone:
`bear validate` succeeds/fails deterministically with canonical output.

## Phase 2 - JVM Target (deterministic codegen)

Goal: generate deterministic enforcement artifacts for demo projects.

- [x] Define `Target` abstraction
- [x] Implement `JvmTarget`
- [x] Generate BEAR-owned entrypoint, logic interface, models, ports, support types
- [x] Generate user-owned impl stub once and preserve it
- [x] Generate conditional idempotency/invariant test templates
- [x] Lock compile contract in `spec/commands/compile.md`
- [x] Add compile golden corpus + conformance tests
- [x] Enforce replay payload integrity (`hit=true` requires all `result.*`)

Milestone:
`bear compile` produces deterministic, byte-stable, spec-conformant output.

## Phase 3 - Drift Regeneration Gate (`bear check` v1)

Goal: deterministic regeneration drift enforcement.

- [x] Implement `bear check <ir-file> --project <path>`
- [x] Validate + normalize IR before comparison
- [x] Compile into temp project tree
- [x] Diff candidate vs `<project>/build/generated/bear`
- [x] Emit deterministic drift lines (`ADDED`/`REMOVED`/`CHANGED`)
- [x] Fail deterministically on missing/empty baseline
- [x] Ensure check is compare-only (no project mutation)

Milestone:
Generated artifact drift is a deterministic CI gate.

## Phase 4 - Project Test Gate (`bear check` v1.1)

Goal: extend `check` to run project tests after drift passes.

- [x] Short-circuit: drift failure stops before test execution
- [x] Use project wrapper only (`gradlew` / `gradlew.bat`), no system Gradle fallback
- [x] Add deterministic timeout handling and exit code
- [x] Add deterministic failed-test output tailing
- [x] Freeze command contract in `spec/commands/check.md`

Milestone:
Single command gate enforces regeneration conformance plus project test pass.

## Phase 5 - Governance Signaling in Check (v0 completion target)

Goal: make boundary expansion explicitly visible during check workflow.

- [ ] Implement deterministic signaling for boundary-expanding changes (per `doc/GOVERNANCE.md`)
- [ ] Keep ordinary changes non-blocking under normal flow
- [ ] Add conformance tests for classification and signal output stability
- [ ] Document expected CI usage pattern for quick human review

Milestone:
Boundary-expanding changes produce small, deterministic review signals.

## Phase 6 - Demo Proof (bear-account-demo)

Goal: prove value in a minimal app workflow.

- [ ] Implement naive Withdraw logic
- [ ] Confirm `bear check` fails deterministically
- [ ] Implement corrected Withdraw logic
- [ ] Confirm `bear check` passes deterministically
- [ ] Capture concise before/after runbook

Milestone:
"Naive withdraw fails. Correct withdraw passes." remains reproducible.

## Explicitly Not in v0

- Capability blocks in IR
- Block-to-block composition graph
- Behavior DSL
- Requires/ensures language
- State delta modeling
- Infrastructure simulation
- Spec -> IR lowering automation
- BEAR runtime enforcement layer
- Embedded LLM logic in BEAR core
- Cross-service modeling
- Multi-language targets
- Plugin architecture
- UI support
- Rich invariant catalog
- Full self-hosting of kernel

If it does not contribute to deterministic boundary governance and the demo proof loop, it is out of scope.