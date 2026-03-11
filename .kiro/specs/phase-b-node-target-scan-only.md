# Phase B: Node Target - Scan Only

## Overview

Add `NodeTarget` as the first non-JVM target, implementing scan-only capabilities: target
detection, code generation, governed roots, import containment, drift checking, and the
`impl.allowedDeps` unsupported guard. This corresponds to Phase 3 of the Node CLI plan.

After this phase, a Node/TypeScript fixture project can compile governed blocks, pass `check`
for drift plus import containment, fail `check` deterministically when an import escapes the
block root, and preserve `pr-check` exit behavior.

Source documents:
- `roadmap/ideas/future-target-adaptable-cli-node.md` Phase 3 (lines 103-151)
- `roadmap/ideas/future-multi-target-spec-design.md` Node/TypeScript Target Spec (lines 235-338)

## Anchoring Constraints

1. **IR v1 is the boundary source of truth.** No `target:` field, no per-target IR additions.
2. **Exit code registry is frozen.** `0`, `2`, `4`, `5`, `6`, `64`, `74` only.
3. **CODE/PATH/REMEDIATION envelope is frozen.** Last three stderr lines always conform.
4. **JVM behavior must remain byte-identical.** Non-JVM work arrives behind the Target seam.
5. **No runtime policy engine additions.**
6. **Generated artifacts live under `build/generated/bear/`.** User-owned impl never overwritten.

## Prerequisites

- Phase A complete (TargetDetector, DetectedTarget, refactored TargetRegistry, CanonicalLocator, GovernanceProfile)

## Functional Requirements

### FR-B1: NodeTargetDetector

**Requirement**: Implement `NodeTargetDetector` that determines whether a project root is a
supported Node/TypeScript project.

**Detection Signals** (all must be present for SUPPORTED):
- `package.json` at project root with `"type": "module"` and `"packageManager": "pnpm@..."` field
- `pnpm-lock.yaml` at project root
- `tsconfig.json` at project root

**Detection Results**:
- All signals present -> `SUPPORTED` with `targetId=NODE`
- `pnpm-workspace.yaml` present -> `UNSUPPORTED` (workspace layout excluded from first slice), exit `64`
- Any required file absent -> `NONE` (not a Node project)

**Design Decisions**:
- Detection is file-presence based; package.json is parsed minimally for `type` and `packageManager`
- No version checking in first slice (version-aware detection is a future enhancement)
- Detector does not validate tsconfig.json content

**Acceptance Criteria**:
- AC-B1.1: Detector returns `SUPPORTED` for a directory with valid package.json (type:module, packageManager:pnpm@...), pnpm-lock.yaml, and tsconfig.json
- AC-B1.2: Detector returns `UNSUPPORTED` when pnpm-workspace.yaml is also present
- AC-B1.3: Detector returns `NONE` when package.json is missing
- AC-B1.4: Detector returns `NONE` when pnpm-lock.yaml is missing
- AC-B1.5: Detector returns `NONE` when tsconfig.json is missing
- AC-B1.6: Detector returns `NONE` for a JVM project directory (no false positive cross-detection)

### FR-B2: NODE in TargetId Enum and TargetRegistry Registration

**Requirement**: Add `NODE("node")` to the `TargetId` enum and register `NodeTarget` with
`NodeTargetDetector` in the default `TargetRegistry`.

**Current State**:
- `TargetId.java` has only `JVM("jvm")`
- `TargetRegistry.defaultRegistry()` creates only JVM target

**Design Decisions**:
- `NodeTarget` initially stubs all Target interface methods except detection-related ones
- Methods that are not yet implemented throw `UnsupportedOperationException` with a message
  indicating which phase will implement them
- `TargetRegistry.defaultRegistry()` registers both JVM and NODE targets with their detectors

**Files to Modify**:
- `kernel/.../target/TargetId.java` -- add `NODE("node")`
- `kernel/.../target/TargetRegistry.java` -- register NodeTarget + NodeTargetDetector

**New Files**:
- `kernel/.../target/node/NodeTarget.java` -- Target implementation (initially stubbed)
- `kernel/.../target/node/NodeTargetDetector.java` -- from FR-B1

**Acceptance Criteria**:
- AC-B2.1: `TargetId.NODE` exists with value `"node"`
- AC-B2.2: `TargetRegistry.defaultRegistry()` includes both JVM and NODE
- AC-B2.3: `TargetRegistry.resolve()` returns `NodeTarget` for a Node fixture project root
- AC-B2.4: `TargetRegistry.resolve()` continues to return `JvmTarget` for JVM projects
- AC-B2.5: All existing JVM tests pass without modification

### FR-B3: Node Compile Output (TypeScript Artifact Generation)

**Requirement**: Implement `NodeTarget.compile()` and `NodeTarget.generateWiringOnly()` to
generate BEAR-owned TypeScript artifacts and wiring manifests for Node/TypeScript blocks.

**Generated Artifact Layout (BEAR-owned, regenerated, drift-checked)**:
```
build/generated/bear/
  wiring/<blockKey>.wiring.json
  types/<blockKey>/
    <BlockName>Ports.ts          # port type declarations from effects.allow
    <BlockName>Logic.ts          # logic interface from contract inputs/outputs
    <BlockName>Wrapper.ts        # wrapper shell with one wiring factory
```

**User-owned (created once, never overwritten)**:
```
src/blocks/<blockKey>/
  impl/<BlockName>Impl.ts        # implementation skeleton
```

**Design Decisions**:
- TypeScript artifacts derive from the same IR as JVM but produce `.ts` files instead of `.java`
- Port type declarations map IR `effects.allow` entries to TypeScript interfaces
- Logic interface maps IR contract inputs/outputs to TypeScript interfaces
- Wrapper shell provides a wiring factory connecting ports, logic, and impl
- Wiring manifest schema matches JVM manifest structure adapted for TypeScript paths
- User-owned impl skeleton is created only if it does not already exist (idempotent)
- `generateWiringOnly()` generates only the wiring manifest (no user-impl touches), used by `pr-check`

**Acceptance Criteria**:
- AC-B3.1: `NodeTarget.compile()` generates `<BlockName>Ports.ts`, `<BlockName>Logic.ts`, `<BlockName>Wrapper.ts` under `build/generated/bear/types/<blockKey>/`
- AC-B3.2: `NodeTarget.compile()` generates wiring manifest at `build/generated/bear/wiring/<blockKey>.wiring.json`
- AC-B3.3: `NodeTarget.compile()` creates user-owned impl at `src/blocks/<blockKey>/impl/<BlockName>Impl.ts` if absent
- AC-B3.4: Re-running compile does not overwrite existing user-owned impl file
- AC-B3.5: `NodeTarget.generateWiringOnly()` generates only the wiring manifest
- AC-B3.6: Generated TypeScript is syntactically valid (parseable by tsc)

### FR-B4: Node Governed Roots

**Requirement**: `NodeTarget` must correctly compute governed source roots for Node/TypeScript
projects.

**Governed Roots**:
```
src/blocks/<blockKey>/**/*.ts    # block-local user-authored governed root
src/blocks/_shared/**/*.ts       # optional shared user-authored governed root
```

**Not Governed**:
```
test/**
src/**/*.test.ts
scripts/**
config files
src/ outside src/blocks/
```

**Design Decisions**:
- Governed roots follow the same `src/blocks/<blockKey>/` pattern as JVM but with `.ts` extension
- `_shared` is optional; if the directory does not exist, it is silently excluded
- Test files (`*.test.ts`) within governed roots are excluded from governance scanning
- The root computation is used by import containment, drift checking, and undeclared reach scanning

**Acceptance Criteria**:
- AC-B4.1: Governed roots for a single block include `src/blocks/<blockKey>/`
- AC-B4.2: Governed roots include `src/blocks/_shared/` when it exists
- AC-B4.3: Governed roots exclude `src/blocks/_shared/` when the directory is absent
- AC-B4.4: Files outside `src/blocks/` are not included in governed roots
- AC-B4.5: Test files (`*.test.ts`) are not considered governed source

### FR-B5: NodeImportContainmentScanner

**Requirement**: Implement `NodeImportContainmentScanner` that enforces import boundaries
in governed TypeScript source files. This is the primary containment mechanism for Node targets.

**Scanning Rules**:
- Scan literal `import` and `export ... from` specifiers in governed `.ts` files
- Resolve relative specifiers lexically against the importing file's location
- **Fail** (exit `6`, `CODE=BOUNDARY_BYPASS`) when:
  - Resolved target escapes the block root
  - Resolved target reaches a sibling block root
  - Resolved target reaches nongoverned repo source
- **Fail** on non-relative user-authored imports from governed roots:
  - Bare package specifiers (e.g., `import x from 'lodash'`)
  - `#` package-imports aliases
  - URL-like specifiers
  - Package self-name imports
- **Allow**:
  - Imports within the same block root
  - Imports from `_shared` root
  - Imports of BEAR-generated companions under `build/generated/bear/`

**Design Decisions**:
- Resolution is lexical only (no node_modules traversal, no TypeScript path alias resolution)
- Bare specifiers are unconditionally blocked from governed roots (simplifies containment)
- Scanner implements the existing `TargetCheck` pattern or equivalent
- Finding locator includes the importing file path (repo-relative) and the import specifier

**New Files**:
- `kernel/.../target/node/NodeImportContainmentScanner.java`

**Acceptance Criteria**:
- AC-B5.1: Clean import within same block passes
- AC-B5.2: Import from `_shared` passes
- AC-B5.3: Relative import escaping block root fails with exit `6`, `CODE=BOUNDARY_BYPASS`
- AC-B5.4: Import reaching sibling block fails with exit `6`, `CODE=BOUNDARY_BYPASS`
- AC-B5.5: Bare package import from governed root fails with exit `6`, `CODE=BOUNDARY_BYPASS`
- AC-B5.6: `#` alias import fails with `CODE=BOUNDARY_BYPASS`
- AC-B5.7: Import of BEAR-generated companion passes
- AC-B5.8: Findings include repo-relative path and import specifier in the locator

### FR-B6: Node Drift Gate

**Requirement**: Implement drift checking for Node-generated artifacts using the same
generated-output comparison principle as JVM.

**Design Decisions**:
- Drift gate compares workspace artifacts against freshly generated output
- Uses the same `DRIFT_DETECTED` / `DRIFT_MISSING_BASELINE` TargetCheckIssueKind values
- Generated file set: `build/generated/bear/types/<blockKey>/*.ts` and `build/generated/bear/wiring/<blockKey>.wiring.json`
- Drift detection is deterministic and produces stable file-level findings

**Acceptance Criteria**:
- AC-B6.1: Clean state (freshly compiled) produces no drift findings
- AC-B6.2: Modified generated `.ts` file triggers `DRIFT_DETECTED`
- AC-B6.3: Missing generated file triggers `DRIFT_MISSING_BASELINE`
- AC-B6.4: User-owned impl files are not included in drift checking

### FR-B7: impl.allowedDeps Unsupported Check

**Requirement**: When any selected block IR contains `impl.allowedDeps` and the resolved
target is Node, `check` must fail with exit `64` and a clear remediation message.

**Failure Envelope**:
```
CODE=UNSUPPORTED_TARGET
PATH=<ir-file>
REMEDIATION=Remove impl.allowedDeps for node target, or switch to JVM target.
```

**Design Decisions**:
- This check runs early in the check pipeline, before containment scanning
- `pr-check` is not affected (still works regardless of allowedDeps)
- The check is target-specific: only NodeTarget triggers this guard

**Acceptance Criteria**:
- AC-B7.1: Block without `impl.allowedDeps` passes the check
- AC-B7.2: Block with `impl.allowedDeps` under Node target fails with exit `64`
- AC-B7.3: Error output includes `CODE=UNSUPPORTED_TARGET`, the IR file path, and the remediation message
- AC-B7.4: `pr-check` still operates normally regardless of `impl.allowedDeps`
- AC-B7.5: Same block with `impl.allowedDeps` under JVM target continues to work (JVM supports it)

## Python Forward Compatibility

- **NodeTargetDetector** establishes the pattern that `PythonTargetDetector` follows (checking
  `pyproject.toml`, `uv.lock`/`poetry.lock`, `mypy.ini`, `src/blocks/`)
- **NodeTarget compile** establishes the TypeScript artifact generation pattern; Python will
  generate `_ports.py`, `_logic.py`, `_wrapper.py` following the same structure
- **Governed roots** use the same `src/blocks/<blockKey>/` pattern; Python adds `__init__.py` requirement
- **NodeImportContainmentScanner** establishes the scanner pattern that `PythonImportContainmentScanner`
  follows, adapted for Python `import`/`from X import Y` statements and AST-based analysis
- **impl.allowedDeps** guard is identical for Python (`NOT_SUPPORTED` with same remediation pattern)

Reference: `roadmap/ideas/future-python-implementation-context.md` sections: Python Target
Detection, Generated Artifact Layout, Governed Roots and Import Policy.

## References

- `roadmap/ideas/future-target-adaptable-cli-node.md` -- Phase 3: Add NodeTarget Scan-Only (lines 103-151)
- `roadmap/ideas/future-multi-target-spec-design.md` -- Node/TypeScript Target Spec (lines 235-338)
- `roadmap/ideas/future-multi-target-expansion-plan.md` -- Target: Node/TypeScript (lines 112-162)
- Current implementation: `kernel/src/main/java/com/bear/kernel/target/jvm/JvmTarget.java` (pattern reference)
