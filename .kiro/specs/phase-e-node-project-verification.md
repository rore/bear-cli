# Phase E: Node Target - Project Verification

## Overview

Add project verification (type checking) for the Node target. This phase implements
`NodeProjectTestRunner` which executes `pnpm exec tsc --noEmit` to verify that governed
TypeScript code compiles cleanly, with deterministic output tailing, timeout behavior, and
stable exit mapping that mirrors the JVM Gradle runner contract.

After this phase, `bear check` on a Node project includes a project verification step that
confirms TypeScript structural integrity.

Source documents:
- `roadmap/ideas/future-target-adaptable-cli-node.md` Phase 6 (lines 183-191)
- `roadmap/ideas/future-multi-target-spec-design.md` Node project verification (lines 329-338)

## Anchoring Constraints

1. **IR v1 is the boundary source of truth.** No per-target IR additions.
2. **Exit code registry is frozen.** `0`, `2`, `3`, `4`, `5`, `6`, `7`, `64`, `70`, `74`.
3. **CODE/PATH/REMEDIATION envelope is frozen.**
4. **JVM behavior must remain byte-identical.**
5. **No runtime policy engine additions.**
6. **Generated artifacts live under `build/generated/bear/`.**

## Prerequisites

- Phase B complete (NodeTarget registered and functional)

## Functional Requirements

### FR-E1: NodeProjectTestRunner

**Requirement**: Implement `NodeProjectTestRunner` that executes TypeScript type checking
as the project verification step for Node targets.

**Verification Command**:
```
pnpm exec tsc --noEmit -p tsconfig.json
```

**Exit Mapping**:
| tsc result | ProjectTestStatus | BEAR exit code |
|---|---|---|
| `tsc` exit `0` | `PASSED` | `0` |
| `tsc` exits non-zero with type errors | `FAILED` | `4` |
| `pnpm` not found | `TOOL_MISSING` | `74` |
| timeout exceeded | `TIMEOUT` | `4` |

**Design Decisions**:
- Mirrors the JVM `ProjectTestRunner` contract (see `kernel/.../target/jvm/ProjectTestRunner.java`)
- Deterministic output tailing: capture last N lines of tsc stderr for diagnostic output
- Timeout is target-owned (configurable, with a sensible default matching JVM runner behavior)
- `pnpm` availability is checked before execution; if missing, fail with `TOOL_MISSING` immediately
- The runner produces a `ProjectTestResult` record consistent with the existing type
- No Node project-test execution in earlier phases: Phase B prints `project.tests: SKIPPED (target=node)`.
  This phase replaces that skip with actual verification.

**Current `ProjectTestResult` Fields**:
```java
record ProjectTestResult(
    ProjectTestStatus status,
    String output,
    String attemptTrail,
    String firstLockLine,
    String firstBootstrapLine,
    String firstSharedDepsViolationLine,
    String cacheMode,
    boolean fallbackToUserCache,
    String phase,
    String lastObservedTask
)
```
**ProjectTestResult Target-Neutrality Prerequisite**:

Several `ProjectTestResult` fields are JVM-specific: `firstLockLine`, `firstBootstrapLine`,
`firstSharedDepsViolationLine`, `cacheMode`, `fallbackToUserCache`, `phase`, `lastObservedTask`.
Before the Node runner lands, one of the following strategies must be chosen:

- **Option A: Accept target-neutral nulls as v1 compromise.** Node runner populates JVM-specific
  fields as `null`/`false`. This is the fastest path and acceptable if `ProjectTestResult`
  consumers already handle nulls gracefully. Document this as an intentional v1 compromise,
  not an oversight. Add a `@DesignDebt("v1-target-neutral-nulls")` annotation or comment to
  `ProjectTestResult` to track the compromise.

- **Option B: Add a lightweight builder/wrapper.** Introduce `ProjectTestResultBuilder` that
  sets target-common fields (`status`, `output`, `attemptTrail`) and leaves target-specific
  fields with safe defaults. Each target provides a static factory:
  `ProjectTestResult.forNode(status, output)`, `ProjectTestResult.forJvm(status, output, ...jvmFields)`.

Decision must be made before Task E1 implementation begins. Either option is acceptable for v1;
the key requirement is that the choice is explicit and documented.

**Constraints**:
- If `pnpm` is unavailable, fail deterministically with existing usage or IO semantics rather
  than expanding the exit registry
- Output must be deterministic (no timestamps or machine-specific content in reported output)
- Timeout handling must be clean: kill the process and report `TIMEOUT` status

**New Files**:
- `kernel/.../target/node/NodeProjectTestRunner.java`

**Acceptance Criteria**:
- AC-E1.1: Successful `tsc` run maps to `ProjectTestStatus.PASSED` and exit `0`
- AC-E1.2: Failed `tsc` run (type errors) maps to `ProjectTestStatus.FAILED` and exit `4`
- AC-E1.3: Missing `pnpm` maps to `ProjectTestStatus.TOOL_MISSING` and exit `74`
- AC-E1.4: Timeout maps to `ProjectTestStatus.TIMEOUT` and exit `4`
- AC-E1.5: Diagnostic output includes relevant tsc error lines (last N lines of stderr)
- AC-E1.6: `ProjectTestResult` is populated using the chosen target-neutrality strategy
  (Option A or Option B, decided before implementation)
- AC-E1.7: Runner integrates into the check pipeline and replaces the Phase B "SKIPPED" output

## Python Forward Compatibility

- **NodeProjectTestRunner** establishes the pattern for `PythonProjectTestRunner`, which runs
  `uv run mypy src/blocks/ --strict` (falling back to `poetry run mypy src/blocks/ --strict`)
- The exit mapping model is identical: tool exit 0 = PASSED, type errors = FAILED, tool not
  found = TOOL_MISSING (exit 74), timeout = TIMEOUT (exit 4)
- Python adds a `mypy` not-installed check (distinct from `uv`/`poetry` not found) that also
  maps to `TOOL_MISSING`
- The `ProjectTestResult` field population pattern carries over; Python-specific fields would
  similarly be null/empty for JVM-inapplicable fields

Reference: `roadmap/ideas/future-python-implementation-context.md` section: Project Verification.

## References

- `roadmap/ideas/future-target-adaptable-cli-node.md` -- Phase 6: Node Project Verification Runner (lines 183-191)
- `roadmap/ideas/future-multi-target-spec-design.md` -- Node project verification (lines 329-338)
- Current implementation: `kernel/src/main/java/com/bear/kernel/target/jvm/ProjectTestRunner.java` (JVM pattern reference)
- Current implementation: `kernel/src/main/java/com/bear/kernel/target/ProjectTestResult.java`, `ProjectTestStatus.java`
