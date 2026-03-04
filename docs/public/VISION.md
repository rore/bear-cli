# Vision (Directional, Engineering Scope)

This page captures longer-horizon BEAR direction.
It is not a milestone plan and it does not imply dates or active implementation.

For the frozen Preview contract, use [CONTRACTS.md](CONTRACTS.md) and [VERSIONING.md](VERSIONING.md).

## Scope of this vision

This vision includes only directions that are both:
- not already completed in the current Preview contract, and
- aligned with BEAR's core model: deterministic boundary governance for agent-driven development.

This means the page avoids near-term queue items and avoids restating capabilities that are already shipped.

## Baseline assumptions

- Agent-first workflow remains the default: agents handle IR mechanics.
- Developers are not expected to hand-author IR in routine usage.
- BEAR remains deterministic and CI-oriented, not a runtime policy engine.

## Direction 1: Boundary usage visibility, not only capability visibility

Current governance is strong at detecting boundary expansion.
A future step is to make interaction-shape changes visible even when allowed capabilities are unchanged.
Examples include count/order/outcome-coupled expectations for declared boundary operations.
The target is deterministic, review-visible signals rather than full business behavior modeling.

## Direction 2: Side-effect taxonomy and stronger external-reach semantics

Future policy can evolve from a simple allowlist into explicit side-effect categories (for example network, database, messaging, filesystem, process, and escape hatches).
The intent is stable governance semantics across targets: pure/internal libraries remain broadly usable, while new external reach requires explicit declaration and review visibility.

## Direction 3: Post-operation-set governance precision

Preview already supports operation-scoped contracts/usages within one block (`block.operations`) with block-level boundary authority.
Future direction is finer governance precision on top of that model, for example richer operation-usage deltas and stronger cross-operation policy diagnostics without weakening determinism.

## Direction 4: Cross-block and system-level modeling

Preview focuses on block-local contracts.
Future evolution can add deterministic multi-block dependency modeling and cross-block invariant context, so governance signals scale from single-block changes to system-shape changes.

## Direction 5: Extensibility with deterministic boundaries

Future extension points for targets, invariants, and optional policy hooks should remain deterministic by contract.
The constraint is that extension mechanisms must not weaken stable CLI output and gate behavior.

## Direction 6: Multi-target expansion, including Node.js

Preview target support is JVM-first.
A future direction is additional first-class targets while preserving the same governance command model (`validate`, `compile`, `check`, `pr-check`).
One explicit candidate is Node.js/TypeScript, with target-specific containment coverage for common external-reach surfaces (HTTP, database, messaging, filesystem/process escapes).

## Reading guidance

- Directional, not committed.
- Engineering scope, not marketing roadmap.
- Theme ordering is conceptual, not delivery sequence.

## Related

- [INDEX.md](INDEX.md)
- [OVERVIEW.md](OVERVIEW.md)
- [FOUNDATIONS.md](FOUNDATIONS.md)
- [CONTRACTS.md](CONTRACTS.md)
- [VERSIONING.md](VERSIONING.md)
