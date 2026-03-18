# Phase P2: Python Checking — Design

Phase P2 promotes `PythonTarget` from scan-only to full `bear check` pipeline support.
Implements the remaining Target interface methods that currently throw
`UnsupportedOperationException`, and fixes the `TargetRegistry` silent JVM fallback.

Governing agent review addressed:
- Issue 1 (check contract stubs): all `UnsupportedOperationException` methods implemented
- Issue 3 (silent JVM fallback): `TargetRegistry` now throws `TARGET_NOT_DETECTED` when no
  detector matches
- Issue 5 (subprocess approach): documented as known limitation; pure-Java AST replacement
  deferred to a future phase per explicit scope decision
- Research findings: `TYPE_CHECKING` block exclusion, `from os import system/popen` detection,
  and known detection gaps documented

---

## Overview

Phase P (complete) delivered: detection, artifact generation, import containment, drift gating,
and the `impl.allowedDeps` unsupported guard. Phase P2 fills the remaining gaps so Python
projects participate in the full `bear check` pipeline on par with JVM.

New capabilities:
- Wiring manifest parsing (shared `TargetManifestParsers` utility)
- Check workspace preparation (mirror JvmTarget `_shared` directory logic)
- Undeclared reach scanning for covered Python power surfaces
- Dynamic execution escape hatch detection (`eval`, `exec`, `compile`)
- Dynamic import enforcement (promote Phase P advisory to hard failures)
- Project verification via `uv run mypy --strict` with `poetry` fallback
- Containment pipeline stubs (return `null`)
- Port and binding check stubs (return empty lists)
- `TargetRegistry` deterministic resolution (remove silent JVM fallback)

---

## Architecture

```
BEAR CLI Commands (compile, check, pr-check, fix)
    └── TargetRegistry (fixed: no silent JVM fallback)
            ├── JvmTarget (existing, unchanged)
            ├── NodeTarget (Phase B, unchanged)
            └── PythonTarget (Phase P + P2)
                    ├── Phase P: PythonArtifactGenerator, PythonImportContainmentScanner,
                    │           PythonDynamicImportDetector, drift gate
                    └── Phase P2:
                            ├── TargetManifestParsers (shared, moved from jvm package)
                            ├── PythonUndeclaredReachScanner (new)
                            ├── PythonDynamicExecutionScanner (new)
                            ├── PythonDynamicImportEnforcer (new, wraps Phase P detector)
                            ├── PythonProjectVerificationRunner (new)
                            └── Containment + Port Stubs (null / empty lists)
```

All Python-specific logic lives behind the Target interface. Kernel orchestration is
target-agnostic. The check pipeline in `CheckCommandService` calls target methods in sequence;
PythonTarget now implements all of them.

---

## Known Limitations (Subprocess Approach)

All Python AST scanners (`PythonUndeclaredReachScanner`, `PythonDynamicExecutionScanner`,
`PythonDynamicImportEnforcer`) use `ProcessBuilder → python3` to execute embedded Python
scripts. This approach has known limitations:

1. **Cross-platform fragility**: Requires `python3` on the system PATH. On Windows, the
   executable may be `python` rather than `python3`. On systems without Python installed,
   all scanners will fail with exit code 74 (`TOOL_MISSING`).
2. **Inconsistency with BEAR's deterministic-seed philosophy**: The kernel is designed to be
   a trusted, deterministic seed with no external runtime dependencies. Spawning a subprocess
   to a language runtime contradicts this principle.
3. **Performance**: Each governed file requires a subprocess invocation, which is slower than
   in-process analysis.

**Deferred improvement**: Replace the subprocess approach with pure-Java AST parsing (e.g.,
using a Java-based Python parser library or regex-based line analysis for the specific patterns
BEAR needs to detect). This is explicitly out of scope for Phase P2. Phase P2's goal is to make
`PythonTarget` fully honor the BEAR check contract; the subprocess approach is an inherited
design from Phase P that will be addressed in a future phase.

---

## Known Detection Gaps

BEAR's AST scanners detect **direct** patterns. The following bypass techniques are known gaps
and are accepted as out of scope for Phase P2 (and likely for BEAR's governance model in
general — these are malware detection patterns, not governance patterns):

1. `builtins.exec/eval` indirection — `import builtins; builtins.exec("code")`
2. `getattr` + string concatenation — `getattr(__import__("built"+"ins"), "ex"+"ec")(...)`
3. `sys.modules` manipulation — `sys.modules["socket"] = some_object`
4. `globals()`/`locals()` import injection — `globals()["socket"] = __import__("socket")`
5. `compile` + `FunctionType` — `types.FunctionType(compile(...), globals())()`
6. Variable reassignment of dangerous functions — `e = eval; e("code")`

BEAR's threat model is "prevent accidental boundary expansion by well-intentioned agent code,"
not "prevent adversarial code injection by malicious actors." These gaps are consistent with
industry-standard tools (Semgrep, Bandit, tach) that also detect only direct patterns.

---

## Components

### 1. TargetRegistry — Deterministic Resolution Fix

**Problem**: Lines 190-194 of `TargetRegistry.resolve()` silently return JVM when all
detectors return `NONE`. This is the opposite of BEAR's "explicit contracts over inference"
stance and makes Python/Node projects silently misrouted.

**Fix**: Remove the silent JVM fallback in the detector path. When all detectors return `NONE`
and no pin file exists, throw `TARGET_NOT_DETECTED`.

The no-detector backward-compatible constructor path (used by existing JVM tests that construct
`TargetRegistry` with a single JVM target) is preserved: single-target registries with no
detectors still return that one target.

```java
// BEFORE (lines 190-194 in TargetRegistry.resolve):
// No detector matched. Fall back to JVM for backward compatibility.
Target jvmFallback = targets.get(TargetId.JVM);
if (jvmFallback != null) {
    return jvmFallback;
}
throw new TargetResolutionException("TARGET_NOT_DETECTED", ...);

// AFTER:
throw new TargetResolutionException(
    "TARGET_NOT_DETECTED",
    projectRoot.toString(),
    "No target detector matched this project. Add a .bear/target.id pin file or "
        + "ensure the project has recognized build files (build.gradle, package.json, "
        + "pyproject.toml)."
);
```

### 2. Shared Wiring Manifest Parser

The wiring manifest JSON schema is target-agnostic (same schema for JVM and Python).
`TargetManifestParsers` is currently package-private in `jvm`. Move it to the shared
`kernel/target/` package and make `parseWiringManifest` public. JvmTarget continues to call
it from the new location.

```java
// kernel/src/main/java/com/bear/kernel/target/TargetManifestParsers.java (moved)
public final class TargetManifestParsers {
    public static WiringManifest parseWiringManifest(Path path)
            throws IOException, ManifestParseException { ... }
}

// PythonTarget.parseWiringManifest (Phase P2)
@Override
public WiringManifest parseWiringManifest(Path path) throws IOException, ManifestParseException {
    return TargetManifestParsers.parseWiringManifest(path);
}
```

### 3. PythonTarget — Updated Method Table

Phase P2 replaces all `UnsupportedOperationException` stubs:

| Method | Phase P | Phase P2 |
|--------|---------|----------|
| `parseWiringManifest()` | throws | delegates to `TargetManifestParsers` |
| `prepareCheckWorkspace()` | throws | creates `_shared` dir if present |
| `containmentSkipInfoLine()` | throws | returns `null` |
| `preflightContainmentIfRequired()` | throws | returns `null` |
| `verifyContainmentMarkersIfRequired()` | throws | returns `null` |
| `scanUndeclaredReach()` | throws | delegates to `PythonUndeclaredReachScanner` |
| `scanForbiddenReflectionDispatch()` | throws | delegates to `PythonDynamicExecutionScanner` + `PythonDynamicImportEnforcer` |
| `scanPortImplContainmentBypass()` | throws | returns `List.of()` |
| `scanBlockPortBindings()` | throws | returns `List.of()` |
| `scanMultiBlockPortImplAllowedSignals()` | throws | returns `List.of()` |
| `runProjectVerification()` | throws | delegates to `PythonProjectVerificationRunner` |

```java
// prepareCheckWorkspace
@Override
public void prepareCheckWorkspace(Path projectRoot, Path tempRoot) throws IOException {
    Path sharedDir = projectRoot.resolve("src/blocks/_shared");
    if (Files.isDirectory(sharedDir)) {
        Files.createDirectories(tempRoot.resolve("src/blocks/_shared"));
    }
}

// Containment stubs — JVM-style containment not applicable to Python
@Override
public String containmentSkipInfoLine(String projectRootLabel, Path projectRoot,
        boolean considerContainmentSurfaces) {
    return null;
}

@Override
public TargetCheckIssue preflightContainmentIfRequired(Path projectRoot,
        boolean considerContainmentSurfaces) {
    return null;
}

@Override
public TargetCheckIssue verifyContainmentMarkersIfRequired(Path projectRoot,
        boolean considerContainmentSurfaces) {
    return null;
}

// Port and binding stubs — JVM-specific, not applicable to Python
@Override
public List<BoundaryBypassFinding> scanPortImplContainmentBypass(Path projectRoot,
        List<WiringManifest> wiringManifests) {
    return List.of();
}

@Override
public List<BoundaryBypassFinding> scanBlockPortBindings(Path projectRoot,
        List<WiringManifest> wiringManifests, Set<String> inboundTargetWrapperFqcns) {
    return List.of();
}

@Override
public List<MultiBlockPortImplAllowedSignal> scanMultiBlockPortImplAllowedSignals(
        Path projectRoot, List<WiringManifest> wiringManifests) {
    return List.of();
}
```

### 4. PythonUndeclaredReachScanner

Detects direct usage of covered power-surface modules in governed Python source files via AST.

Covered power surfaces (import-level detection):
- `socket`, `http`, `http.client`, `http.server`, `urllib`, `urllib.request`
- `subprocess`, `multiprocessing`

Direct function import detection (research finding 6.1):
- `from os import system` / `from os import popen` / `from os import exec*`

Call-site detection (AST `ast.Call` nodes):
- `os.system(...)`, `os.popen(...)`, `os.execl/execle/execlp/execlpe/execv/execve/execvp/execvpe(...)`

TYPE_CHECKING exclusion (research finding 6.2):
- All imports inside `if TYPE_CHECKING:` blocks are skipped

```python
# Embedded Python script (key logic)
import ast, sys, json
from typing import TYPE_CHECKING  # not imported at runtime

COVERED_MODULES = {'socket', 'http', 'http.client', 'http.server',
                   'urllib', 'urllib.request', 'subprocess', 'multiprocessing'}
OS_EXEC_ATTRS = {'system', 'popen', 'execl', 'execle', 'execlp', 'execlpe',
                 'execv', 'execve', 'execvp', 'execvpe'}

def is_type_checking_block(node):
    """Returns True if node is inside an `if TYPE_CHECKING:` block."""
    # Implemented by tracking parent nodes during walk

def scan(source):
    findings = []
    tree = ast.parse(source)
    type_checking_lines = collect_type_checking_lines(tree)

    for node in ast.walk(tree):
        if node.lineno in type_checking_lines:
            continue
        if isinstance(node, ast.Import):
            for alias in node.names:
                if alias.name in COVERED_MODULES:
                    findings.append({'surface': alias.name, 'line': node.lineno})
        elif isinstance(node, ast.ImportFrom):
            if node.module and node.level == 0:
                if node.module in COVERED_MODULES:
                    findings.append({'surface': node.module, 'line': node.lineno})
                # Direct os function import: from os import system/popen/exec*
                if node.module == 'os':
                    for alias in node.names:
                        if alias.name in OS_EXEC_ATTRS:
                            findings.append({'surface': 'os.' + alias.name, 'line': node.lineno})
        elif isinstance(node, ast.Call):
            # os.system(...), os.popen(...), os.exec*(...)
            if (isinstance(node.func, ast.Attribute) and
                isinstance(node.func.value, ast.Name) and
                node.func.value.id == 'os' and
                node.func.attr in OS_EXEC_ATTRS):
                findings.append({'surface': 'os.' + node.func.attr, 'line': node.lineno})
    return findings
```

Java orchestrator reuses `PythonImportContainmentScanner.computeGovernedRoots` and
`collectGovernedFiles` for governed file enumeration. Findings sorted by path then surface.

### 5. PythonDynamicExecutionScanner

Detects direct calls to `eval()`, `exec()`, and `compile()` in governed Python source files.
Excludes `if TYPE_CHECKING:` blocks. Findings map to `CODE=BOUNDARY_BYPASS`, exit code 7.

```python
ESCAPE_HATCHES = {'eval', 'exec', 'compile'}

def scan(source):
    findings = []
    tree = ast.parse(source)
    type_checking_lines = collect_type_checking_lines(tree)
    for node in ast.walk(tree):
        if node.lineno in type_checking_lines:
            continue
        if isinstance(node, ast.Call):
            if isinstance(node.func, ast.Name) and node.func.id in ESCAPE_HATCHES:
                findings.append({'surface': node.func.id, 'line': node.lineno})
    return findings
```

### 6. PythonDynamicImportEnforcer

Wraps `PythonDynamicImportDetector` (Phase P) and promotes advisory findings to hard failures.
Adds `sys.path` mutation detection. Excludes `if TYPE_CHECKING:` blocks.

`sys.path` mutation patterns:
- `sys.path.append(...)` — `ast.Call` with `func.value.value.id == 'sys'`, `func.value.attr == 'path'`, `func.attr == 'append'`
- `sys.path.insert(...)` — same with `func.attr == 'insert'`
- `sys.path = [...]` — `ast.Assign` with target chain `sys.path`

`PythonTarget.scanForbiddenReflectionDispatch` combines both scanners:

```java
@Override
public List<UndeclaredReachFinding> scanForbiddenReflectionDispatch(
        Path projectRoot, List<WiringManifest> wiringManifests) throws IOException {
    List<UndeclaredReachFinding> execFindings =
        PythonDynamicExecutionScanner.scan(projectRoot, wiringManifests);
    List<UndeclaredReachFinding> importFindings =
        PythonDynamicImportEnforcer.scan(projectRoot, wiringManifests);
    List<UndeclaredReachFinding> combined = new ArrayList<>();
    combined.addAll(execFindings);
    combined.addAll(importFindings);
    combined.sort(Comparator.comparing(UndeclaredReachFinding::path)
        .thenComparing(UndeclaredReachFinding::surface));
    return combined;
}
```

### 7. PythonProjectVerificationRunner

Executes `mypy --strict` on governed source roots. Tool preference: `uv` first, `poetry`
fallback. `BOOTSTRAP_IO` status maps to exit code 74 (same as JVM missing-Gradle-wrapper).
`TIMEOUT` maps to exit code 4. No retry logic.

```java
public final class PythonProjectVerificationRunner {
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    public static ProjectTestResult run(Path projectRoot) throws IOException, InterruptedException {
        String tool = findTool();  // "uv" or "poetry" or null
        if (tool == null) {
            return toolMissing("Neither uv nor poetry found on PATH");
        }
        List<String> command = List.of(tool, "run", "mypy", "src/blocks/", "--strict");
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = captureOutput(process);
        boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return timeout(output);
        }
        int exitCode = process.exitValue();
        if (exitCode == 0) return passed(output);
        if (output.contains("No module named 'mypy'") ||
            output.contains("ModuleNotFoundError")) return toolMissing(output);
        return failed(output);
    }
}
```

---

## Data Models

Phase P2 reuses existing data models. No new records are introduced.

Reused records: `WiringManifest`, `UndeclaredReachFinding`, `BoundaryBypassFinding`,
`ProjectTestResult`, `TargetCheckIssue`, `ManifestParseException`, `ImportStatement`,
`DynamicImport`.

Internal scanner records (package-private):
```java
record ReachFinding(String surface, int lineNumber, String kind) {}       // PythonUndeclaredReachScanner
record EscapeHatchFinding(String surface, int lineNumber) {}              // PythonDynamicExecutionScanner
record SysPathMutation(String pattern, int lineNumber) {}                 // PythonDynamicImportEnforcer
```

Surface-to-finding mapping:

| Scanner | Surface | Exit Code |
|---------|---------|-----------|
| `PythonUndeclaredReachScanner` | `socket`, `http.*`, `urllib.*`, `subprocess`, `multiprocessing` | 6 |
| `PythonUndeclaredReachScanner` | `os.system`, `os.popen`, `os.exec*`, `from os import system/popen` | 6 |
| `PythonDynamicExecutionScanner` | `eval`, `exec`, `compile` | 7 |
| `PythonDynamicImportEnforcer` | `importlib.import_module`, `__import__`, `sys.path` | 7 |

---

## Correctness Properties

PBT library: plain JUnit 5 (no jqwik). Minimum 100 iterations per property.
Tag format: `// Feature: phase-p2-python-checking, Property N: <text>`

**Property 1**: Wiring manifest round-trip — serialize a valid `WiringManifest` to JSON, parse
with `TargetManifestParsers.parseWiringManifest`, result has identical field values.
*Validates: Req 1.1, 1.4*

**Property 2**: Malformed JSON rejection — any non-valid JSON string throws
`ManifestParseException` with reason code `MALFORMED_JSON`.
*Validates: Req 1.2*

**Property 3**: Missing required field rejection — valid manifest JSON with one required field
removed throws `ManifestParseException` with reason code containing the missing field name.
*Validates: Req 1.3*

**Property 4**: `prepareCheckWorkspace` shared directory iff present — after calling
`prepareCheckWorkspace(projectRoot, tempRoot)`, `tempRoot/src/blocks/_shared` exists iff
`projectRoot/src/blocks/_shared` exists.
*Validates: Req 2.1, 2.2, 2.3*

**Property 5**: Covered power-surface import detection — any governed Python file containing a
direct import from the covered set produces at least one `UndeclaredReachFinding` with matching
surface.
*Validates: Req 3.1*

**Property 6**: `os` call-site pattern detection — any governed file with `os.system/popen/exec*`
call produces a finding; `import os` with only `os.path` usage produces no finding.
*Validates: Req 3.2, 3.3*

**Property 7**: `from os import` direct function detection — any governed file with
`from os import system/popen/exec*` produces a finding.
*Validates: Req 3.4*

**Property 8**: Undeclared reach findings sorted deterministically — results sorted by path
then surface ascending.
*Validates: Req 3.7*

**Property 9**: Undeclared reach scans only governed roots — findings only for governed files;
test files and non-governed files excluded.
*Validates: Req 3.8*

**Property 10**: TYPE_CHECKING block exclusion — imports inside `if TYPE_CHECKING:` blocks
produce no findings from any scanner.
*Validates: Req 3.9, 4.6, 5.5*

**Property 11**: Dynamic execution escape hatch detection — any governed file with direct
`eval()`/`exec()`/`compile()` call produces a finding.
*Validates: Req 4.1, 4.2, 4.3*

**Property 12**: Dynamic import facility enforcement — any governed file with
`importlib.import_module`, `__import__`, or `sys.path` mutation produces a finding.
*Validates: Req 5.1, 5.2, 5.3*

**Property 13**: Project verification exit code mapping — mypy exit 0 → `PASSED`; non-zero →
`FAILED`.
*Validates: Req 6.3, 6.4*

**Property 14**: Project verification output capture — `ProjectTestResult.output` contains
complete stdout/stderr (non-null, non-empty when mypy produces output).
*Validates: Req 6.8*

**Property 15**: TargetRegistry no silent fallback — when all detectors return `NONE` and no
pin file exists, `resolve()` throws `TargetResolutionException` with code `TARGET_NOT_DETECTED`.
*Validates: Req 9.1, 9.2*

**Property 16**: Failure output envelope format — any check pipeline failure produces stderr
containing `CODE=`, `PATH=`, and `REMEDIATION=` lines.
*Validates: Req 12.8*

---

## Error Handling

Exit code mapping (frozen registry):

| Finding Type | CODE | Exit Code |
|-------------|------|-----------|
| Undeclared reach (covered power surfaces) | `UNDECLARED_REACH` | `6` |
| Dynamic execution escape hatch | `BOUNDARY_BYPASS` | `7` |
| Dynamic import enforcement | `BOUNDARY_BYPASS` | `7` |
| Project verification failure | `PROJECT_TEST_FAILED` | `4` |
| Project verification timeout | `PROJECT_TEST_TIMEOUT` | `4` |
| Tool missing (`uv`/`poetry`/`mypy`) | `TOOL_MISSING` | `74` |
| Wiring manifest malformed | `DRIFT_DETECTED` or `VALIDATION` | `5` or `2` |

Scanner error handling:
- Python `SyntaxError` in governed source → empty findings for that file (not a governance violation)
- `python3` not on PATH → `IOException` → exit `74`
- ProcessBuilder timeout → `IOException` → exit `74`
- Empty governed files → no findings

---

## Testing Strategy

### Unit Tests

```
kernel/src/test/java/com/bear/kernel/target/python/
  PythonTargetCheckMethodsTest.java          — stubs return correct values
  PythonUndeclaredReachScannerTest.java      — covered surfaces, os patterns, TYPE_CHECKING
  PythonDynamicExecutionScannerTest.java     — eval/exec/compile detection
  PythonDynamicImportEnforcerTest.java       — importlib/import/sys.path enforcement
  PythonProjectVerificationRunnerTest.java   — exit code mapping, tool detection, timeout

kernel/src/test/java/com/bear/kernel/target/
  TargetRegistryDetectionTest.java           — no silent JVM fallback
```

### Property Tests

```
kernel/src/test/java/com/bear/kernel/target/python/properties/
  WiringManifestParsingProperties.java       — Properties 1, 2, 3
  CheckWorkspaceProperties.java              — Property 4
  UndeclaredReachProperties.java             — Properties 5, 6, 7, 8, 9, 10
  DynamicExecutionProperties.java            — Properties 11
  DynamicImportEnforcementProperties.java    — Property 12
  ProjectVerificationProperties.java         — Properties 13, 14

kernel/src/test/java/com/bear/kernel/target/properties/
  TargetRegistryResolutionProperties.java    — Properties 15, 16
```

### Integration Test Fixtures

```
kernel/src/test/resources/fixtures/python/
  check-clean/                    — clean project, all checks pass
  check-undeclared-reach/         — governed file imports socket → exit 6
  check-os-system/                — governed file calls os.system() → exit 6
  check-from-os-import/           — governed file uses from os import system → exit 6
  check-dynamic-exec/             — governed file calls eval() → exit 7
  check-dynamic-import/           — governed file calls importlib.import_module() → exit 7
  check-sys-path-mutation/        — governed file mutates sys.path → exit 7
  check-type-checking-excluded/   — TYPE_CHECKING imports → exit 0 (no findings)
```

---

## Implementation Sequence

1. Move `TargetManifestParsers` to shared `kernel/target/` package; update JvmTarget import;
   verify all existing tests pass
2. Fix `TargetRegistry` silent JVM fallback; add `TargetRegistryDetectionTest`
3. `PythonTarget.parseWiringManifest` delegation + wiring manifest parsing tests
4. `PythonTarget.prepareCheckWorkspace` + workspace preparation tests
5. Containment pipeline stubs (`containmentSkipInfoLine`, `preflightContainmentIfRequired`,
   `verifyContainmentMarkersIfRequired` → `null`) + stub tests
6. Port and binding stubs (`scanPortImplContainmentBypass`, `scanBlockPortBindings`,
   `scanMultiBlockPortImplAllowedSignals` → empty lists) + stub tests
7. `PythonUndeclaredReachScanner` (with TYPE_CHECKING exclusion + `from os import` detection)
   + unit tests + property tests
8. `PythonDynamicExecutionScanner` (with TYPE_CHECKING exclusion) + unit tests + property tests
9. `PythonDynamicImportEnforcer` (promote Phase P advisory + `sys.path` detection +
   TYPE_CHECKING exclusion) + unit tests + property tests
10. `PythonTarget.scanUndeclaredReach` and `scanForbiddenReflectionDispatch` wiring +
    integration tests
11. `PythonProjectVerificationRunner` + unit tests + property tests
12. `PythonTarget.runProjectVerification` wiring + veri
fication integration tests
13. Fixture projects + end-to-end integration tests
14. Full regression run (all JVM, Node, Python Phase P tests)

---

## File Structure

New files:
```
kernel/src/main/java/com/bear/kernel/target/
  TargetManifestParsers.java                 (moved from jvm package)

kernel/src/main/java/com/bear/kernel/target/python/
  PythonUndeclaredReachScanner.java
  PythonDynamicExecutionScanner.java
  PythonDynamicImportEnforcer.java
  PythonProjectVerificationRunner.java
```

Modified files:
```
kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java
  — remove silent JVM fallback in detector path (lines 190-194)

kernel/src/main/java/com/bear/kernel/target/python/PythonTarget.java
  — replace all UnsupportedOperationException stubs with implementations

kernel/src/main/java/com/bear/kernel/target/jvm/JvmTarget.java
  — update import for TargetManifestParsers (moved to shared package)

kernel/src/main/java/com/bear/kernel/target/python/PythonDynamicImportDetector.java
  — extend Python script to detect sys.path mutation patterns
```

New test files:
```
kernel/src/test/java/com/bear/kernel/target/
  TargetRegistryDetectionTest.java

kernel/src/test/java/com/bear/kernel/target/python/
  PythonTargetCheckMethodsTest.java
  PythonUndeclaredReachScannerTest.java
  PythonDynamicExecutionScannerTest.java
  PythonDynamicImportEnforcerTest.java
  PythonProjectVerificationRunnerTest.java

kernel/src/test/java/com/bear/kernel/target/python/properties/
  WiringManifestParsingProperties.java
  CheckWorkspaceProperties.java
  UndeclaredReachProperties.java
  DynamicExecutionProperties.java
  DynamicImportEnforcementProperties.java
  ProjectVerificationProperties.java

kernel/src/test/java/com/bear/kernel/target/properties/
  TargetRegistryResolutionProperties.java

kernel/src/test/resources/fixtures/python/
  check-clean/
  check-undeclared-reach/
  check-os-system/
  check-from-os-import/
  check-dynamic-exec/
  check-dynamic-import/
  check-sys-path-mutation/
  check-type-checking-excluded/
```

---

## Validation Criteria

Phase P2 is complete when:
- All 16 correctness properties pass (100+ iterations each)
- All unit tests pass
- All integration tests pass
- All existing JVM tests pass without modification
- All existing Node tests pass without modification
- All existing Python Phase P tests pass without modification
- `TargetRegistry` throws `TARGET_NOT_DETECTED` when no detector matches (no silent JVM fallback)
- Python fixture fails `check` on undeclared reach (exit `6`)
- Python fixture fails `check` on dynamic execution (exit `7`)
- Python fixture fails `check` on dynamic import enforcement (exit `7`)
- Python fixture with `TYPE_CHECKING` imports passes `check` (exit `0`)
- Clean Python fixture passes `check` (exit `0`)
