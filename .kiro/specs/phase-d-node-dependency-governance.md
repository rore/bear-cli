# Phase D: Node Target - Dependency Governance

## Overview

Add repo-level dependency governance signaling for the Node target in `pr-check`. This phase
detects dependency expansion (new or changed dependencies in `package.json` and `pnpm-lock.yaml`)
between base and head commits and classifies it as boundary-expanding.

After this phase, `bear pr-check --all` on a Node fixture repo returns exit `5` when
dependencies expand and exit `0` when they do not.

Source documents:
- `roadmap/ideas/future-target-adaptable-cli-node.md` Phase 5 (lines 168-181)
- `roadmap/ideas/future-multi-target-spec-design.md` Node dependency governance (lines 314-319)

## Anchoring Constraints

1. **IR v1 is the boundary source of truth.** No per-target IR additions.
2. **Exit code registry is frozen.** `0`, `2`, `4`, `5`, `6`, `64`, `74` only.
3. **CODE/PATH/REMEDIATION envelope is frozen.**
4. **JVM behavior must remain byte-identical.**
5. **No runtime policy engine additions.**
6. **Generated artifacts live under `build/generated/bear/`.**

## Prerequisites

- Phase B complete (NodeTarget registered and functional)
- Phase A2 complete (TargetRegistry resolves Node projects)

## Functional Requirements

### FR-D1: NodePrCheckContributor

**Requirement**: Implement `NodePrCheckContributor` that detects dependency expansion between
base and head commits for Node projects and classifies changes as boundary-expanding or ordinary.

**Boundary-Expanding Changes (exit `5`)**:
- `package.json` `dependencies` additions or version changes
- `package.json` `devDependencies` additions or version changes
- `package.json` `peerDependencies` additions or version changes
- `package.json` `optionalDependencies` additions or version changes
- Any `pnpm-lock.yaml` change between base and head

**Ordinary Changes (exit `0`)**:
- Dependency removal (version downgrade or deletion)
- No dependency changes
- Changes only to non-dependency fields in `package.json`

**Design Decisions**:
- Governance is repo-level only; not per-block allowlisting
- No IR changes required -- dependency governance is an overlay on the existing diff-based
  pr-check model
- `package.json` parsing extracts the four dependency sections and compares key-value maps
  between base and head versions
- `pnpm-lock.yaml` change detection is file-level (any content change is boundary-expanding)
- The contributor integrates with the existing pr-check pipeline and produces the standard
  `CODE/PATH/REMEDIATION` envelope on exit `5`

**Failure Envelope (when boundary-expanding)**:
```
CODE=BOUNDARY_EXPANDING
PATH=package.json (or pnpm-lock.yaml)
REMEDIATION=Review new or changed dependencies before merging.
```

**New Files**:
- `kernel/.../target/node/NodePrCheckContributor.java` (or integrated into NodeTarget)

**Acceptance Criteria**:
- AC-D1.1: New dependency added to `package.json` `dependencies` produces exit `5`
- AC-D1.2: Dependency version bumped in `package.json` produces exit `5`
- AC-D1.3: New devDependency added produces exit `5`
- AC-D1.4: `pnpm-lock.yaml` content changed produces exit `5`
- AC-D1.5: Dependency removed from `package.json` produces exit `0`
- AC-D1.6: No dependency changes produces exit `0`
- AC-D1.7: Changes only to non-dependency fields (e.g., `scripts`, `name`) produce exit `0`
- AC-D1.8: Multiple boundary-expanding changes in same PR produce a single exit `5` with
  findings listed for each expanding change

## Python Forward Compatibility

- **NodePrCheckContributor** establishes the pattern for `PythonPrCheckContributor`, which
  detects changes in `pyproject.toml` dependency sections (`[project] dependencies`,
  `[project.optional-dependencies]`, `[tool.uv.dev-dependencies]`) and lock file changes
  (`uv.lock` or `poetry.lock`)
- The boundary-expanding/ordinary classification model is identical across all targets
- Python additionally triggers a `site-packages` power-surface scan when the lock file changes,
  which is an extension of this same pr-check model

Reference: `roadmap/ideas/future-python-implementation-context.md` section: Dependency Governance.

## References

- `roadmap/ideas/future-target-adaptable-cli-node.md` -- Phase 5: Node Dependency Governance (lines 168-181)
- `roadmap/ideas/future-multi-target-spec-design.md` -- Node dependency governance pr-check (lines 314-319)
- `roadmap/ideas/future-multi-target-expansion-plan.md` -- Node tradeoffs table (lines 144-152)
