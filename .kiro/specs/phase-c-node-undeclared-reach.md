# Phase C: Node Target - Undeclared Reach

## Overview

Add covered undeclared-reach detection for the Node target. This phase extends the scan-only
Node target (Phase B) with detection of direct usage of covered Node built-in modules and
dynamic import facilities in governed TypeScript source files.

After this phase, `bear check` on a Node project detects and reports usage of network,
filesystem, child process, and dynamic import facilities in governed code.

Source documents:
- `roadmap/ideas/future-target-adaptable-cli-node.md` Phase 4 (lines 152-166)
- `roadmap/ideas/future-multi-target-spec-design.md` Node undeclared reach rules (lines 298-312)

## Anchoring Constraints

1. **IR v1 is the boundary source of truth.** No per-target IR additions.
2. **Exit code registry is frozen.** `0`, `2`, `3`, `4`, `5`, `6`, `7`, `64`, `70`, `74`.
3. **CODE/PATH/REMEDIATION envelope is frozen.**
4. **JVM behavior must remain byte-identical.**
5. **No runtime policy engine additions.**
6. **Generated artifacts live under `build/generated/bear/`.**

## Prerequisites

- Phase B complete (NodeTarget with scan-only capabilities, governed roots, import containment)

## Functional Requirements

### FR-C1: NodeUndeclaredReachScanner

**Requirement**: Implement `NodeUndeclaredReachScanner` that detects direct usage of covered
Node built-in modules in governed TypeScript source files.

**Covered Built-in Set (first slice)**:
| Module (node: prefix) | Module (bare) |
|---|---|
| `node:http` | `http` |
| `node:https` | `https` |
| `node:net` | `net` |
| `node:child_process` | `child_process` |
| `node:fs` | `fs` |
| `node:fs/promises` | `fs/promises` |

**Failure Envelope**:
- Exit `6`, `CODE=UNDECLARED_REACH`
- PATH = repo-relative file path
- REMEDIATION = specific to the module (e.g., "Declare http capability in effects.allow or remove direct import")

**Design Decisions**:
- Scanner checks `import` and `export ... from` specifiers for exact match against covered set
- Both `node:` prefixed and bare module names are detected (they are equivalent in Node ESM)
- Scanner runs on governed `.ts` files only
- Findings are deterministic, stable-ordered, and include repo-relative path locators
- Follows existing `UndeclaredReachFinding` record pattern: `(String path, String surface)`
- Scanner integrates into the existing check pipeline stage ordering

**New Files**:
- `kernel/.../target/node/NodeUndeclaredReachScanner.java`

**Acceptance Criteria**:
- AC-C1.1: `import http from 'node:http'` in governed file produces `UNDECLARED_REACH` finding
- AC-C1.2: `import http from 'http'` (bare form) produces the same finding
- AC-C1.3: Each covered module in the table is detected (both node: and bare forms)
- AC-C1.4: Import of a non-covered module (e.g., `node:path`) does not produce a finding
- AC-C1.5: Import of covered module in non-governed file (e.g., test file) is not flagged
- AC-C1.6: Findings are deterministically ordered by file path then by import specifier
- AC-C1.7: Exit code is `6` when any undeclared reach finding is present

### FR-C2: Dynamic Import Detection

**Requirement**: Detect dynamic import facilities (`import()`, `require()`, `createRequire()`)
in governed TypeScript source files as boundary bypass vectors.

**Detected Patterns**:
- `import(...)` dynamic import expression
- `require(...)` CommonJS require call
- `module.createRequire(...)` or `createRequire(...)` call

**Failure Envelope**:
- Exit `7`, `CODE=BOUNDARY_BYPASS`
- Status: `PARTIAL` (BEAR detects direct call-site patterns but cannot trace loader hooks or
  preloaded resolver hooks outside governed roots)

**Design Decisions**:
- Detection is syntactic/lexical: scan for `import(`, `require(`, `createRequire(` patterns
  in governed `.ts` files
- No AST parsing required for TypeScript (lexical match is sufficient for these patterns)
- `PARTIAL` status is explicitly documented in the finding to set correct expectations
- This check could be part of `NodeImportContainmentScanner` or a separate scanner

**Acceptance Criteria**:
- AC-C2.1: `const m = await import('./module')` produces `BOUNDARY_BYPASS` finding
- AC-C2.2: `const m = require('./module')` produces `BOUNDARY_BYPASS` finding
- AC-C2.3: `const r = createRequire(import.meta.url)` produces `BOUNDARY_BYPASS` finding
- AC-C2.4: Static `import x from './module'` does not produce a dynamic import finding
- AC-C2.5: Dynamic import in non-governed file is not flagged
- AC-C2.6: Finding detail includes `PARTIAL` status indicator

## Python Forward Compatibility

- **NodeUndeclaredReachScanner** establishes the pattern for `PythonUndeclaredReachScanner`, which
  covers `socket`, `http`, `urllib`, `subprocess`, `multiprocessing` and `os.system`/`os.popen`
  call-site patterns
- **Dynamic import detection** establishes the pattern for Python's `importlib.import_module()`,
  `__import__()`, `importlib.util.spec_from_file_location()`, and `sys.path` mutation detection
- Python adds an additional `PythonDynamicExecutionScanner` for `eval()`/`exec()`/`compile()`
  that has no Node equivalent
- Python uses AST-based analysis instead of lexical matching, but the finding model and exit
  code mapping are identical

Reference: `roadmap/ideas/future-python-implementation-context.md` sections: Covered Power
Surfaces, Analysis Strategy: AST-First.

## References

- `roadmap/ideas/future-target-adaptable-cli-node.md` -- Phase 4: Node Covered Undeclared Reach (lines 152-166)
- `roadmap/ideas/future-multi-target-spec-design.md` -- Node undeclared reach rules (lines 298-312)
- Current implementation: `kernel/src/main/java/com/bear/kernel/target/jvm/UndeclaredReachScanner.java` (JVM pattern reference)
- Current implementation: `kernel/src/main/java/com/bear/kernel/target/UndeclaredReachFinding.java` (finding record)
