# BEAR North Star

## BEAR: Making AI-Written Software Safer and More Predictable

Software is entering a new phase.
AI agents can write, modify, and refactor code quickly, including tests.
That speed creates a control problem:

- What actually changed?
- What is the system now allowed to do?
- Did external reach expand silently?

BEAR exists to make those changes explicit and enforceable.

## Core Thesis

BEAR is a structural governance layer around AI-generated software.
It does not try to stop agents from writing code.
It defines and checks boundaries around what that code is allowed to do.

Inside a block:
- implementation freedom

At the boundary:
- external interactions must be declared
- changes in external power must be visible
- deterministic gates must enforce the contract

Boundary visibility is necessary but not sufficient.
BEAR must also evolve to make meaningful boundary-usage pattern changes visible (not only capability additions).

## Hard Litmus Test

Can an agent introduce a new external side effect without a small, explicit boundary signal and without failing CI?

- If no: BEAR is working.
- If yes: BEAR is mostly documentation.

## Success Criteria

### 1. Boundary Visibility
- New external interactions produce explicit IR/boundary signals.
- Boundary expansion is reviewable in seconds.
- Silent capability growth is blocked or surfaced deterministically.

### 1b. Boundary Usage Visibility
- Material interaction-shape changes on declared capabilities become visible.
- Example: changing from one emitted event to two emitted events is detectable at the boundary layer.
- The goal is not full business-behavior modeling; the goal is observable boundary semantics.

### 2. Independent Verification
- `bear validate` + `bear check` + project tests are deterministic gates.
- Drift detection and normalization are stable.
- Correctness cannot be trivially redefined without visible structural change.

### 3. Reduced Fragility
- Feature work produces predictable structural diffs.
- Review focuses on "what powers changed?" as well as implementation detail.
- Incident analysis can reconstruct boundary history.

### 4. Developer Experience Neutrality
- Developers stay in domain language.
- Agents handle BEAR mechanics by default.
- IR micromanagement is minimized.

### 5. Cross-Language Structural Integrity
- Enforcement is build/compile/test based, not runtime magic.
- Equivalent constraints can be mapped across targets.
- CLI/governance model stays target-agnostic.

## Scope Boundary for Future Evolution

BEAR should govern:
- structural boundaries
- capability contracts
- observable interaction constraints at block interfaces

BEAR should not govern:
- full business logic correctness
- internal algorithm choices
- complete workflow choreography DSL

This keeps BEAR as structural governance, not a full formal behavior engine.

## Future Enforcement Direction (Post-v0)

1. Capability contracts as first-class objects:
   - allowed event types, topics/destinations, operation modes, schema/version constraints
2. Optional declarative boundary-usage constraints:
   - count/order/outcome-coupled interaction constraints
3. Policy assertion generation:
   - generated test scaffolding that checks boundary interactions deterministically

The intent is to prevent silent interaction-semantics drift while preserving implementation freedom inside the block.

## Progression Model

This doc is the long-horizon target model, not a claim that all properties exist in v0 today.

- `docs/context/architecture.md`: what BEAR guarantees now (v0 contract).
- `docs/context/governance.md`: normative classification and review policy.
- `docs/context/roadmap.md`: milestone definitions and phased path to stronger enforcement.
- `docs/context/program-board.md`: live milestone execution status and evidence (operational view).

