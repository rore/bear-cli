# BEAR v0 Roadmap

This roadmap is strictly for v0.  
If something is not listed here, it does not get built.

v0 uses:
- Logic blocks only
- Effects expressed as structured ports
- No capability blocks
- No block-to-block composition

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

- [ ] Define `BlockModel`
  - name
  - kind (`logic` only in v0)
  - contract (inputs/outputs)
  - effects (structured ports)
  - idempotency
  - invariants (non_negative only)

- [ ] Define `EffectPortModel`
  - port name
  - list of ops

### Parsing

- [ ] Add YAML parsing (SnakeYAML)
- [ ] Implement strict schema validation
  - fail on unknown keys
  - fail on invalid enums
  - fail on invalid references

### Validation Rules

- [ ] Unique input names
- [ ] Unique output names
- [ ] Unique port names
- [ ] Unique ops per port
- [ ] idempotency.key must reference input
- [ ] invariant field must reference output

### Normalization (Deterministic Canonical Form)

- [ ] Sort inputs by name
- [ ] Sort outputs by name
- [ ] Sort ports by name
- [ ] Sort ops within each port
- [ ] Sort invariants deterministically
- [ ] Emit canonical key order

- [ ] Implement `bear validate <file>`

Milestone:  
`bear validate withdraw.bear.yaml` succeeds/fails deterministically and emits canonical form.

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
- [ ] Write Withdraw BEAR IR (logic block)
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
