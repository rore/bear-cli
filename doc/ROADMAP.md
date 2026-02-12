# BEAR v0 Roadmap

This roadmap is strictly for v0.  
If something is not listed here, it does not get built.

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

v0 non-guarantees:
- Business correctness beyond declared invariants
- Real database/concurrency/transaction semantics
- Runtime enforcement beyond test harness
- General behavioral verification

---

## Phase 0 — Project Setup (bear-cli)

- [x] Create Gradle multi-module project
- [x] Create `kernel` module (trusted seed)
- [x] Create `app` module (CLI wrapper)
- [x] Ensure CLI entrypoint runs: `bear --help`
- [x] Add JUnit 5 test setup
- [x] Add README
- [x] Add ARCHITECTURE.md (locked)

Milestone: CLI builds and runs, no BEAR logic yet.

---

## Phase 1 — BEAR IR Foundation (kernel)

Goal: Deterministic parsing + validation + normalization.

### Core Model

- [x] Define `BlockModel`
  - name
  - kind (`logic` only in v0)
  - contract (inputs/outputs)
  - effects (structured ports)
  - idempotency
  - invariants (non_negative only)

- [x] Define `EffectPortModel`
  - port name
  - list of ops
- [x] Lock `doc/IR_SPEC.md` as the canonical v0 IR model

### Parsing

- [x] Add YAML parsing (SnakeYAML)
- [x] Implement strict schema validation
  - require root `version: v0`
  - fail on unknown keys
  - fail on invalid enums
  - fail on invalid references

### Validation Rules

- [x] Unique input names
- [x] Unique output names
- [x] Unique port names
- [x] Unique ops per port
- [x] idempotency.key must reference input
- [x] idempotency.store.port/getOp/putOp must reference declared effects
- [x] invariant field must reference output

### Normalization (Deterministic Canonical Form)

- [x] Sort inputs by name
- [x] Sort outputs by name
- [x] Sort ports by name
- [x] Sort ops within each port
- [x] Sort invariants deterministically
- [x] Emit canonical key order

- [x] Implement `bear validate <file>`

Milestone:  
`bear validate spec/fixtures/withdraw.bear.yaml` succeeds/fails deterministically and emits canonical form.

---

## Phase 2 — JVM Target (Deterministic Codegen)

Goal: Generate enforcement artifacts for demo projects.

- [ ] Define `Target` interface
- [ ] Implement `JvmTarget`

Generation must produce:

### 1. Logic Skeleton

- [ ] Non-editable skeleton class
- [ ] Constructor receives generated port interfaces
- [ ] Abstract or delegated `execute(...)` method
- [ ] Deterministic structure

### 2. Port Interfaces (Effects Boundary)

- [ ] Generate one Java interface per declared port
- [ ] Generate methods for each declared op
- [ ] No extra methods
- [ ] Deterministic method signatures

### 3. Implementation Stub

- [ ] Generate impl file if missing
- [ ] Never overwrite existing impl

### 4. JUnit Test Templates

- [ ] Idempotency test (if declared)
  - same key invoked twice returns same output
  - effect write op is applied at most once in deterministic harness
- [ ] non_negative invariant test
- [ ] Deterministic wiring with in-memory adapters

- [ ] Output to `build/generated/bear`
- [ ] Ensure deterministic generation (byte-stable output)

Milestone:  
`bear compile` creates compilable artifacts.

---

## Phase 3 — Two-File Enforcement

Goal: Prevent drift.

- [ ] Skeleton and impl separated
- [ ] Skeleton always regenerated
- [ ] Impl preserved
- [ ] Drift detection:
  - Fail if generated artifacts differ unexpectedly

Milestone:  
Manual edits to skeleton are rejected or overwritten deterministically.

---

## Phase 4 — bear check

Goal: Single deterministic enforcement gate.

- [ ] Implement `bear check --project <path>`
  - validate IR
  - compile artifacts
  - invoke Gradle tests

Fail on:

- invalid IR
- generation drift
- invariant violation
- idempotency violation

Milestone:  
One command enforces BEAR guarantees.

---

## Phase 5 — Demo (bear-account-demo)

Goal: Prove value.

- [ ] Create simple bank account domain
- [ ] Use canonical Withdraw IR fixture at `spec/fixtures/withdraw.bear.yaml` (single logic block)
- [ ] Declare ledger + idempotency ports
- [ ] Provide deterministic in-memory adapters
- [ ] Implement naive Withdraw
- [ ] Confirm `bear check` fails
- [ ] Fix implementation
- [ ] Confirm `bear check` passes

Milestone:  
Clear before/after demonstration.

---

## Explicitly Not in v0

- Capability blocks in IR
- Block-to-block composition
- Behavior DSL
- requires/ensures language
- State delta modeling
- Infrastructure simulation
- Spec → IR lowering
- LLM inside BEAR core
- Cross-service modeling
- Multi-language targets
- Plugin architecture
- UI support
- Rich invariant catalog
- Full self-hosting of kernel
- Rewriting CLI wiring using BEAR

If it does not contribute to:

> "Naive withdraw fails. Correct withdraw passes."

It is out of scope.
