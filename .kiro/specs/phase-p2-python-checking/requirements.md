# Requirements Document

## Introduction

Phase P2 promotes PythonTarget from scan-only to a full checking target. Phase P (complete)
delivered detection, artifact generation, import containment, and drift gating. Phase P2
implements the remaining Target interface methods that currently throw
`UnsupportedOperationException`: wiring manifest parsing, check workspace preparation,
undeclared reach scanning, dynamic execution escape hatch detection, dynamic import enforcement,
project verification via mypy, and the containment pipeline stubs. After Phase P2, Python
projects participate in the full `bear check` pipeline on par with JVM.

Phase P2 also fixes the `TargetRegistry` silent JVM fallback (Issue 3 from the governing agent
review): when no detector matches, the registry must throw `TARGET_NOT_DETECTED` rather than
silently returning JVM. This is a prerequisite for Python being a safe live target.

Inner profile only: `python/service` (strict third-party import blocking). All enforcement is
AST-based via `ProcessBuilder → python3`. The subprocess approach is a known limitation
documented in the design; replacing it with pure-Java AST parsing is deferred to a future phase.
All existing JVM, Node, and Python Phase P tests must pass without modification.

## Glossary

- **Check_Pipeline**: The `bear check` orchestration sequence: drift gate → containment
  preflight → undeclared reach scan → reflection/dynamic dispatch scan → boundary bypass scan
  → project verification → containment marker verification.
- **PythonTarget**: The `Target` implementation for Python projects, registered in
  `TargetRegistry`.
- **Wiring_Manifest**: A JSON file (`wiring/<blockKey>.wiring.json`) describing block
  structure, ports, wrappers, and governed source roots for a compiled block.
- **Undeclared_Reach_Scanner**: A scanner that detects direct usage of covered power-surface
  modules (`socket`, `http`, `subprocess`, etc.) in governed Python source files.
- **Dynamic_Execution_Scanner**: A scanner that detects direct calls to `eval()`, `exec()`,
  and `compile()` in governed Python source files via AST.
- **Dynamic_Import_Enforcer**: Enforcement of dynamic import facility detection that Phase P
  already detects but does not fail on (`importlib.import_module`, `__import__`, `sys.path`
  mutation).
- **Project_Verification_Runner**: A runner that executes `uv run mypy src/blocks/ --strict`
  (or `poetry run mypy`) and maps exit codes to `ProjectTestResult`.
- **Governed_Roots**: Source directories under BEAR governance: `src/blocks/<blockKey>/` and
  `src/blocks/_shared/`.
- **Covered_Power_Surfaces**: Standard library modules whose direct import from governed roots
  constitutes undeclared reach: `socket`, `http`, `http.client`, `http.server`, `urllib`,
  `urllib.request`, `subprocess`, `multiprocessing`.
- **Os_Call_Site_Patterns**: Call-site patterns `os.system(`, `os.popen(`, `os.exec*(` in
  governed source that constitute undeclared reach despite `import os` being benign.
- **AST_Analysis**: Python `ast` module-based static analysis; the primary enforcement
  mechanism for all Python scanners.
- **Containment_Pipeline**: The subset of check pipeline methods related to JVM-style
  containment markers: `containmentSkipInfoLine`, `preflightContainmentIfRequired`,
  `verifyContainmentMarkersIfRequired`.
- **TYPE_CHECKING_Block**: An `if TYPE_CHECKING:` guarded import block that only executes
  during type checking, not at runtime. These imports must be excluded from all scanners.

## Requirements

### Requirement 1: Wiring Manifest Parsing

**User Story:** As a developer running `bear check` on a Python project, I want BEAR to parse
Python wiring manifests, so that the check pipeline can validate block structure and pass
manifest data to downstream scanners.

#### Acceptance Criteria

1. WHEN a valid Python `wiring.json` file is provided, THE PythonTarget SHALL parse the file
   and return a `WiringManifest` record with all fields populated from the JSON content.
2. WHEN a `wiring.json` file contains malformed JSON, THE PythonTarget SHALL throw a
   `ManifestParseException` with a descriptive reason code.
3. WHEN a `wiring.json` file is missing required fields (`schemaVersion`, `blockKey`,
   `implSourcePath`, `blockRootSourceDir`, `governedSourceRoots`), THE PythonTarget SHALL
   throw a `ManifestParseException` identifying the missing field.
4. THE PythonTarget SHALL populate `governedSourceRoots` from the wiring manifest JSON so
   that downstream scanners can determine governed file scope.

### Requirement 2: Check Workspace Preparation

**User Story:** As a developer running `bear check`, I want BEAR to prepare a temporary
workspace for Python projects, so that candidate artifacts can be generated and compared
against baseline without polluting the project directory.

#### Acceptance Criteria

1. WHEN `prepareCheckWorkspace` is called for a Python project, THE PythonTarget SHALL create
   the temporary workspace directory structure required for candidate artifact generation.
2. WHEN a `src/blocks/_shared/` directory exists in the project root, THE PythonTarget SHALL
   create a corresponding `src/blocks/_shared/` directory in the temporary workspace root.
3. WHEN a `src/blocks/_shared/` directory does not exist in the project root, THE PythonTarget
   SHALL skip shared directory creation without error.

### Requirement 3: Undeclared Reach Scanning

**User Story:** As a developer, I want BEAR to detect direct usage of covered power-surface
modules in governed Python source files, so that undeclared network, process, and system
capabilities are surfaced before merge.

#### Acceptance Criteria

1. WHEN a governed Python source file contains a direct import of a covered power-surface
   module (`socket`, `http`, `http.client`, `http.server`, `urllib`, `urllib.request`,
   `subprocess`, `multiprocessing`), THE Undeclared_Reach_Scanner SHALL return an
   `UndeclaredReachFinding` with the file path and surface name.
2. WHEN a governed Python source file contains `import os` without any `os.system`,
   `os.popen`, or `os.exec*` call-site patterns, THE Undeclared_Reach_Scanner SHALL produce
   no finding for that file.
3. WHEN a governed Python source file contains `os.system(`, `os.popen(`, or `os.exec*(`
   call-site patterns, THE Undeclared_Reach_Scanner SHALL return an `UndeclaredReachFinding`
   with the `os.*` surface.
4. WHEN a governed Python source file contains `from os import system`, `from os import popen`,
   or `from os import exec*` (direct function import), THE Undeclared_Reach_Scanner SHALL
   return an `UndeclaredReachFinding` for the `os.*` surface.
5. THE Undeclared_Reach_Scanner SHALL use AST-based analysis as the primary detection
   mechanism for import statements.
6. THE Undeclared_Reach_Scanner SHALL use AST-based call-site analysis for `os.system`,
   `os.popen`, and `os.exec*` pattern detection.
7. WHEN multiple governed files contain covered power-surface imports, THE
   Undeclared_Reach_Scanner SHALL collect all findings and return them sorted by file path
   then surface name.
8. THE Undeclared_Reach_Scanner SHALL scan only files within governed roots
   (`src/blocks/<blockKey>/` and `src/blocks/_shared/`), excluding test files
   (`test_*.py`, `*_test.py`).
9. THE Undeclared_Reach_Scanner SHALL exclude imports inside `if TYPE_CHECKING:` blocks from
   all findings (these are type-hint-only imports that do not execute at runtime).
10. WHEN undeclared reach findings exist, THE Check_Pipeline SHALL fail with exit code 6 and
    `CODE=UNDECLARED_REACH`.

### Requirement 4: Dynamic Execution Escape Hatch Detection

**User Story:** As a developer, I want BEAR to detect direct calls to `eval()`, `exec()`, and
`compile()` in governed Python source files, so that escape hatches that bypass static
governance are surfaced.

#### Acceptance Criteria

1. WHEN a governed Python source file contains a direct call to `eval(...)`, THE
   Dynamic_Execution_Scanner SHALL return an `UndeclaredReachFinding` identifying the file
   and the `eval` surface.
2. WHEN a governed Python source file contains a direct call to `exec(...)`, THE
   Dynamic_Execution_Scanner SHALL return an `UndeclaredReachFinding` identifying the file
   and the `exec` surface.
3. WHEN a governed Python source file contains a direct call to `compile(...)`, THE
   Dynamic_Execution_Scanner SHALL return an `UndeclaredReachFinding` identifying the file
   and the `compile` surface.
4. THE Dynamic_Execution_Scanner SHALL use AST-based call-site analysis to detect `eval`,
   `exec`, and `compile` calls.
5. THE Dynamic_Execution_Scanner SHALL scan only files within governed roots, excluding test
   files.
6. THE Dynamic_Execution_Scanner SHALL exclude calls inside `if TYPE_CHECKING:` blocks.
7. WHEN dynamic execution escape hatch findings exist, THE Check_Pipeline SHALL fail with
   exit code 7 and `CODE=BOUNDARY_BYPASS`.

### Requirement 5: Dynamic Import Enforcement

**User Story:** As a developer, I want BEAR to enforce (fail on) dynamic import facility usage
that Phase P only detected as advisory, so that `importlib.import_module`, `__import__`, and
`sys.path` mutation in governed roots produce check failures.

#### Acceptance Criteria

1. WHEN a governed Python source file contains a call to `importlib.import_module(...)`, THE
   Dynamic_Import_Enforcer SHALL return an `UndeclaredReachFinding` identifying the file and
   the `importlib.import_module` surface.
2. WHEN a governed Python source file contains a call to `__import__(...)`, THE
   Dynamic_Import_Enforcer SHALL return an `UndeclaredReachFinding` identifying the file and
   the `__import__` surface.
3. WHEN a governed Python source file contains `sys.path` mutation (`sys.path.append`,
   `sys.path.insert`, or direct `sys.path = ...` assignment), THE Dynamic_Import_Enforcer
   SHALL return an `UndeclaredReachFinding` identifying the file and the `sys.path` surface.
4. THE Dynamic_Import_Enforcer SHALL use AST-based analysis for all detection.
5. THE Dynamic_Import_Enforcer SHALL exclude patterns inside `if TYPE_CHECKING:` blocks.
6. WHEN dynamic import enforcement findings exist, THE Check_Pipeline SHALL fail with exit
   code 7 and `CODE=BOUNDARY_BYPASS`.

### Requirement 6: Project Verification

**User Story:** As a developer, I want BEAR to run `mypy --strict` on governed Python source
roots as the project verification step, so that type-checking failures are surfaced as part of
`bear check`.

#### Acceptance Criteria

1. WHEN `uv` is available on the system PATH, THE Project_Verification_Runner SHALL execute
   `uv run mypy src/blocks/ --strict` as the verification command.
2. WHEN `uv` is not available but `poetry` is available, THE Project_Verification_Runner SHALL
   fall back to `poetry run mypy src/blocks/ --strict`.
3. WHEN the verification command exits with code 0, THE Project_Verification_Runner SHALL
   return `ProjectTestStatus.PASSED`.
4. WHEN the verification command exits with type errors (non-zero exit from mypy), THE
   Project_Verification_Runner SHALL return `ProjectTestStatus.FAILED`.
5. WHEN neither `uv` nor `poetry` is found on the system PATH, THE Project_Verification_Runner
   SHALL return a result that maps to exit code 74 (`TOOL_MISSING`).
6. WHEN `mypy` is not installed in the project environment, THE Project_Verification_Runner
   SHALL return a result that maps to exit code 74 (`TOOL_MISSING`).
7. WHEN the verification command exceeds the target-owned timeout, THE
   Project_Verification_Runner SHALL return `ProjectTestStatus.TIMEOUT`.
8. THE Project_Verification_Runner SHALL capture the full stdout/stderr output from the
   verification command in the `ProjectTestResult.output` field.
9. WHEN project verification fails, THE Check_Pipeline SHALL fail with exit code 4.

### Requirement 7: Containment Pipeline Stubs

**User Story:** As a developer running `bear check` on a Python project, I want the containment
pipeline methods to return safe no-op results instead of throwing exceptions, so that the full
check pipeline completes without errors for Python targets.

#### Acceptance Criteria

1. THE PythonTarget `containmentSkipInfoLine` method SHALL return `null`, indicating no
   containment skip information is applicable for Python targets.
2. THE PythonTarget `preflightContainmentIfRequired` method SHALL return `null`, indicating no
   containment preflight issues for Python targets.
3. THE PythonTarget `verifyContainmentMarkersIfRequired` method SHALL return `null`, indicating
   no containment marker verification issues for Python targets.
4. THE PythonTarget `considerContainmentSurfaces` method SHALL return `false` for Python
   targets (JVM-style containment surfaces are not applicable).

### Requirement 8: Port and Binding Check Stubs

**User Story:** As a developer running `bear check` on a Python project, I want port
implementation containment and block port binding checks to return empty results, so that the
check pipeline completes without errors while these JVM-specific checks are not applicable.

#### Acceptance Criteria

1. THE PythonTarget `scanPortImplContainmentBypass` method SHALL return an empty list.
2. THE PythonTarget `scanBlockPortBindings` method SHALL return an empty list.
3. THE PythonTarget `scanMultiBlockPortImplAllowedSignals` method SHALL return an empty list.

### Requirement 9: TargetRegistry Deterministic Resolution

**User Story:** As a developer, I want `TargetRegistry` to throw a deterministic
`TARGET_NOT_DETECTED` error when no detector matches, rather than silently falling back to JVM,
so that Python and Node projects are not silently misrouted to the wrong target.

#### Acceptance Criteria

1. WHEN all detectors return `DetectionStatus.NONE` for a project root and no pin file exists,
   THE TargetRegistry SHALL throw a `TargetResolutionException` with code `TARGET_NOT_DETECTED`.
2. THE TargetRegistry SHALL NOT silently return the JVM target when no detector matches.
3. WHEN the registry is constructed with no detectors (backward-compatible constructor) and
   exactly one target is registered, THE TargetRegistry SHALL return that single target
   (existing single-target behavior preserved).
4. WHEN the registry is constructed with no detectors and multiple targets are registered,
   THE TargetRegistry SHALL throw `TARGET_NOT_DETECTED` (no silent JVM fallback in
   multi-target no-detector mode).
5. All existing JVM tests that rely on single-target registry construction SHALL continue to
   pass without modification.

### Requirement 10: Full Check Pipeline Integration

**User Story:** As a developer, I want `bear check` on a Python project to execute the complete
check pipeline (drift → containment → undeclared reach → dynamic dispatch → boundary bypass →
project verification), so that Python projects receive the same governance rigor as JVM projects.

#### Acceptance Criteria

1. WHEN `bear check` is run on a clean Python project with no violations, THE Check_Pipeline
   SHALL complete with exit code 0 and output `check: OK`.
2. WHEN `bear check` is run with `runReachAndTests=true`, THE Check_Pipeline SHALL execute
   undeclared reach scanning, dynamic dispatch scanning, boundary bypass scanning, and project
   verification in sequence.
3. WHEN any check pipeline step produces findings, THE Check_Pipeline SHALL fail with the
   appropriate exit code and `CODE` value for that step.
4. THE Check_Pipeline SHALL use the parsed `WiringManifest` from the baseline wiring file to
   pass to downstream scanners.
5. WHEN `bear check` is run on a Python project, THE Check_Pipeline SHALL call
   `prepareCheckWorkspace` before generating candidate artifacts.

### Requirement 11: Regression Safety

**User Story:** As a developer, I want Phase P2 changes to preserve all existing JVM, Node,
and Python Phase P behavior, so that no regressions are introduced.

#### Acceptance Criteria

1. THE Phase P2 changes SHALL preserve all existing JVM test behavior without modification to
   JVM test files.
2. THE Phase P2 changes SHALL preserve all existing Node test behavior without modification to
   Node test files.
3. THE Phase P2 changes SHALL preserve all existing Python Phase P test behavior without
   modification to Phase P test files.
4. THE Phase P2 changes SHALL preserve byte-identical JVM and Node target behavior for
   identical inputs.
5. THE Phase P2 changes SHALL NOT modify any files under
   `kernel/src/main/java/com/bear/kernel/target/jvm/` or
   `kernel/src/main/java/com/bear/kernel/target/node/`.

### Requirement 12: Exit Code Compliance

**User Story:** As a developer, I want all Phase P2 findings to map to the frozen exit code
registry, so that Python check results are consistent with the BEAR exit code contract.

#### Acceptance Criteria

1. WHEN undeclared reach findings are detected, THE Check_Pipeline SHALL exit with code 6.
2. WHEN dynamic execution escape hatches (`eval`, `exec`, `compile`) are detected, THE
   Check_Pipeline SHALL exit with code 7 and `CODE=BOUNDARY_BYPASS`.
3. WHEN dynamic import enforcement findings are detected, THE Check_Pipeline SHALL exit with
   code 7 and `CODE=BOUNDARY_BYPASS`.
4. WHEN project verification fails with type errors, THE Check_Pipeline SHALL exit with code 4.
5. WHEN project verification tooling is missing (`uv`/`poetry`/`mypy` not found), THE
   Check_Pipeline SHALL exit with code 74.
6. WHEN project verification times out, THE Check_Pipeline SHALL exit with code 4.
7. WHEN wiring manifest parsing fails, THE Check_Pipeline SHALL exit with code 5 (drift) or
   code 2 (validation) depending on the failure context.
8. THE Check_Pipeline SHALL use the `CODE/PATH/REMEDIATION` three-line stderr envelope for
   all failure outputs.
