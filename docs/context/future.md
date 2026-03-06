# BEAR Future Ideas (Parking Lot)

Anything listed here is explicitly not part of the active roadmap queue.
These ideas are intentionally deferred to protect scope.
Spec-backed future initiatives should live in `docs/context/backlog/` and be linked from here.

---

## Deferred Initiatives With Spec Files

### Target-Adaptable CLI + Initial Node/TypeScript Target

Future initiative only; not part of the active P2/P3 queue.

Full spec: `docs/context/backlog/future-target-adaptable-cli-node.md`

Planned direction:
- refactor CLI core behind one target dispatch seam while preserving byte-stable JVM behavior
- keep IR unchanged; target selection is detector or pin based (`.bear/target.id`), not IR-driven
- move JVM behavior behind `JvmTarget` without behavior change first
- add a strict Node or TypeScript plus pnpm profile as an initial scan-only target
- Node first slice should support deterministic generate, drift, `pr-check` governance, and governed-root import containment before any Node test-runner work
- later follow-ups may add covered undeclared-reach checks, repo-level dependency governance, and pnpm verification runner support

Guardrails:
- no IR schema changes
- no exit-code expansion
- no broad docs neutralization sweep
- CLI core remains target-agnostic outside a single dispatch boundary

### Compile Package Customization

Future initiative only; not part of the active P2/P3 queue.

Full spec: `docs/context/backlog/future-compile-package-customization.md`

---

## Spec -> BEAR IR Lowering

- LLM-assisted transformation from feature specs to BEAR IR
- Validation that spec-derived IR matches domain constraints
- Diffing spec vs IR
- Partial regeneration when spec changes

---

## LLM Integration (Optional Layer)

- "Implement block" helper command
- Iterative agent loop inside CLI
- Agent plugin adapters (Codex, Copilot, Claude, etc.)
- Auto-fix suggestions when `bear check` fails

BEAR core must remain deterministic and agent-agnostic.

---

## Cross-Block / System Modeling

- Multi-block graph
- Dependency modeling
- Cross-block invariants
- Event-driven flows
- Microservice boundary modeling
- System-level IR

Not part of v0.

---

## Extended Invariants

- Referential integrity checks
- Concurrency invariants
- Balance consistency across aggregates
- Domain-specific invariant catalog
- Custom invariant plugins

v0 supports only:
- non_negative(field=...)

---

## Capability / Effects Model Expansion

- Effect types (read/write/network/event)
- Side-effect classification
- Security policies
- Policy-based effect enforcement
- Forbidden dependency scanning
- Integration with static analysis tools
- Capability contract metadata:
  - allowed event types
  - allowed destinations or topics
  - operation mode constraints (read/write/etc.)
  - schema or version constraints

v0 supports only:
- simple allowlist of effects

---

## Side-Effect Taxonomy (v1 Candidate)

Principle:
- side-effect gating, not library gating
- pure or internal libraries stay allowed
- external reach and escape hatches are governed

Candidate cross-language categories:
- network
- database
- messaging
- filesystem
- process
- time or random (policy-dependent)
- reflection or escape-hatch

JVM candidate enforcement artifacts:
- forbidden package or class symbol lists per category
- module boundary rules (block modules vs integration modules)
- CI checks that fail on undeclared side-effect surface usage

Success target:
- agents can use pure libraries freely
- new external side effects require declared boundary or IR changes
- policy remains small and stable

---

## Boundary Usage Constraints (Post-v0)

This track addresses the gap between:
- capability allowance ("can call this")
- capability usage semantics ("how this is used")

Candidate constraint types (optional, narrow, boundary-focused):
- max or expected call counts per capability op
- outcome-coupled constraints (must or must-not call on success or failure)
- exactly-once style interaction expectations where meaningful
- interaction ordering constraints for specific boundary events

Enforcement direction:
- deterministic validation or normalization in IR layer
- generated policy assertion tests in target scaffolding
- CI-visible signals for interaction-pattern changes

Non-goal:
- full business-behavior specification DSL

---

## Operation-Set Governance Precision (Future Expansion Candidate)

Current baseline:
- IR v1 includes first-class `block.operations` with per-operation contract or usages and block-level boundary authority.
- operation usage is constrained by block effects, idempotency capability, and allowed invariant set.

Candidate direction:
- improve precision of operation-level governance signals (for example richer usage-shape diagnostics)
- strengthen cross-operation boundary diagnostics without introducing router-style contracts

Design guardrails:
- remain deterministic and machine-checkable
- preserve block-level boundary authority as the canonical governance envelope
- avoid untyped opcode or action router patterns

Status:
- tracked as future expansion only; not committed to active milestone scope

---

## Multi-Target Support

- Kotlin target
- TypeScript target
- Python target
- Go target
- Framework integrations (Spring, Micronaut, etc.)

v0 supports:
- JVM (Java) only

---

## Plugin Architecture

- External invariant providers
- External target providers
- Extension SPI
- Third-party effect packs

---

## UI / Visualization

- Block graph visualization
- IR inspector
- Generated artifact viewer
- IDE plugin

---

## Self-Hosting BEAR

- Kernel isolation proof
- Formal bootstrapping model

Not before v1+.

---

## Packaging & Distribution

- Standalone binary distribution
- Homebrew install
- Docker distribution
- Maven plugin
- Gradle plugin

v0 can run as plain CLI only.

---

## Enterprise Features

- Policy enforcement modes
- Signed IR files
- Drift audit logs
- CI integration templates

---

## Research Ideas

- Formal verification integration
- Constraint solving backends
- Deterministic replay models
- Stronger semantic guarantees

---

If something feels exciting but does not directly contribute to:

"Naive withdraw fails. Correct withdraw passes."

It belongs here.
