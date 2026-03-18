# Phase P2 Deep Research: Python Governance Gaps & Design Validation

## Purpose

This document provides comprehensive answers to six research questions about Python governance
gaps, validating and refining the Phase P2 design before implementation. It supersedes the
initial `research-findings.md` with deeper analysis based on source code review, web research,
and comparison with existing tools.

---

## Research Questions

1. **Import containment** — Complete ways governed Python code can load modules outside declared roots
2. **Escape hatches** — Beyond eval/exec/compile, what other code-loading primitives exist (runpy, importlib.util patterns); use Bandit's taxonomy
3. **Stdlib detection correctness** — Evaluate `sys.stdlib_module_names` vs hard-coded list; distutils removal as case study
4. **Architecture enforcement analogues** — Compare Import Linter and Tach to BEAR's block/key model
5. **Dependency governance vs block governance** — Validate "no third-party imports" sufficiency for strict profile
6. **Deterministic UX** — Propose finding normalization, sorting, lane mapping, exit code registry preservation

---

## Q1: Import Containment — Complete Module Loading Vectors

### 1.1 What BEAR Phase P2 Currently Detects

Based on source code analysis of `PythonDynamicImportDetector.java`:

| Pattern | Detection Method | Status |
|---------|-----------------|--------|
| `import module` | `ast.Import` | ✅ Detected (Phase P) |
| `from module import name` | `ast.ImportFrom` | ✅ Detected (Phase P) |
| `importlib.import_module(...)` | `ast.Call` with attribute check | ✅ Detected (Phase P) |
| `__import__(...)` | `ast.Call` with name check | ✅ Detected (Phase P) |
| `importlib.util.spec_from_file_location(...)` | `ast.Call` with nested attribute check | ✅ Detected (Phase P) |

### 1.2 What Phase P2 Design Adds

| Pattern | Detection Method | Status |
|---------|-----------------|--------|
| `sys.path.append(...)` | `ast.Call` with attribute chain | 🆕 P2 Design |
| `sys.path.insert(...)` | `ast.Call` with attribute chain | 🆕 P2 Design |
| `sys.path = [...]` | `ast.Assign` with target check | 🆕 P2 Design |

### 1.3 Complete Catalog of Module Loading Vectors NOT Detected

Based on Python documentation, security research, and Bandit's taxonomy:

| Vector | Example | Detection Difficulty | Recommendation |
|--------|---------|---------------------|----------------|
| `sys.modules` manipulation | `sys.modules["socket"] = obj` | Medium (AST subscript) | Document as known gap; consider future detection |
| `globals()`/`locals()` injection | `globals()["socket"] = __import__("socket")` | Hard (requires data flow) | Document as known gap; out of scope |
| `builtins.__import__` | `builtins.__import__("socket")` | Medium (attribute chain) | Document as known gap; high false-positive risk |
| `importlib.util.module_from_spec` | `module_from_spec(spec)` + `exec_module` | Medium (multi-step) | Document as known gap; exotic pattern |
| `runpy.run_module` | `runpy.run_module("module")` | Easy (direct call) | **Add to P2 detection** |
| `runpy.run_path` | `runpy.run_path("/path/to/file.py")` | Easy (direct call) | **Add to P2 detection** |
| Custom import hooks | `sys.meta_path.append(finder)` | Hard (requires semantic analysis) | Document as NOT_SUPPORTED |
| `pkgutil.get_loader` | `pkgutil.get_loader("module")` | Medium (direct call) | Document as known gap; rare pattern |
| `zipimport.zipimporter` | `zipimporter(path).load_module(name)` | Medium (multi-step) | Document as NOT_SUPPORTED |

### 1.4 Recommendation: Add `runpy` Detection

The `runpy` module is a real security concern (CVE-2026-22606 demonstrates runpy-based code
execution vulnerabilities). It provides two functions that execute arbitrary Python code:

```python
import runpy
runpy.run_module("module_name")  # Executes module by name
runpy.run_path("/path/to/file.py")  # Executes file by path
```

**Action**: Add `runpy.run_module` and `runpy.run_path` to `PythonDynamicImportEnforcer` detection.
These are direct call patterns, easy to detect via AST.

### 1.5 Known Gaps Summary

The following patterns are intentionally NOT detected (documented as known gaps):

1. **`sys.modules` manipulation** — Requires subscript assignment detection; consider for future
2. **`globals()`/`locals()` injection** — Requires data flow analysis; out of scope
3. **`builtins.__import__`** — High false-positive risk with legitimate `builtins` usage
4. **Custom import hooks** — Requires semantic analysis of `sys.meta_path`; NOT_SUPPORTED
5. **`pkgutil`/`zipimport`** — Rare patterns; document as known gaps

---

## Q2: Escape Hatches — Code-Loading Primitives Beyond eval/exec/compile

### 2.1 Bandit's Dangerous Function Taxonomy

Bandit (Python security scanner) categorizes dangerous calls into blacklists. Relevant to BEAR:

| Bandit ID | Name | Calls | BEAR Coverage |
|-----------|------|-------|---------------|
| B307 | eval | `eval` | ✅ P2 Design |
| B301 | pickle | `pickle.loads`, `pickle.load`, `pickle.Unpickler`, `dill.*`, `shelve.*`, `jsonpickle.*`, `pandas.read_pickle` | ❌ Not covered |
| B302 | marshal | `marshal.load`, `marshal.loads` | ❌ Not covered |
| B310 | urllib_urlopen | `urllib.urlopen`, `urllib.request.urlopen`, etc. | ❌ Not covered (network, not code execution) |
| B312 | telnetlib | `telnetlib.*` | ❌ Not covered (network) |
| B321 | ftplib | `ftplib.*` | ❌ Not covered (network) |

### 2.2 Code Execution Primitives Analysis

| Primitive | Mechanism | BEAR Relevance | Recommendation |
|-----------|-----------|----------------|----------------|
| `eval(expr)` | Evaluates expression | ✅ Covered | P2 Design |
| `exec(code)` | Executes statements | ✅ Covered | P2 Design |
| `compile(source, ...)` | Compiles to code object | ✅ Covered | P2 Design |
| `pickle.loads(data)` | Deserializes arbitrary objects | ⚠️ Security risk | Document as known gap; deserialization is different threat model |
| `marshal.loads(data)` | Deserializes code objects | ⚠️ Security risk | Document as known gap |
| `runpy.run_module(name)` | Executes module by name | ❌ Not covered | **Add to P2** |
| `runpy.run_path(path)` | Executes file by path | ❌ Not covered | **Add to P2** |
| `types.FunctionType(code, globals)` | Creates function from code object | ❌ Not covered | Document as known gap; requires `compile` first |
| `ctypes.CDLL(path)` | Loads native library | ❌ Not covered | Document as NOT_COVERABLE (native code) |

### 2.3 Recommendation: Scope Boundary

BEAR's threat model is "prevent accidental boundary expansion by well-intentioned agent code."
This is different from:
- **Malware detection** (obfuscated eval/exec via getattr/builtins)
- **Deserialization attacks** (pickle/marshal with untrusted data)
- **Native code analysis** (ctypes, cffi, native extensions)

**Action**: 
1. Add `runpy.run_module` and `runpy.run_path` to P2 detection (direct code execution)
2. Document pickle/marshal as known gaps (different threat model)
3. Document native code as NOT_COVERABLE

---

## Q3: Stdlib Detection Correctness

### 3.1 Current Implementation Analysis

`PythonImportBoundaryResolver.java` contains a hard-coded `STDLIB_MODULES` set:

```java
private static final Set<String> STDLIB_MODULES = Set.of(
    "abc", "aifc", "argparse", ...,
    "distutils",  // ⚠️ REMOVED in Python 3.12
    "imp",        // ⚠️ DEPRECATED since Python 3.4, removed in 3.12
    ...
);
```

**Critical Finding**: The hard-coded list includes `distutils` (removed in Python 3.12) and
`imp` (deprecated since 3.4, removed in 3.12). This creates false positives on Python 3.12+
where these modules don't exist.

### 3.2 `sys.stdlib_module_names` Evaluation

Python 3.10+ provides `sys.stdlib_module_names`:

| Attribute | Description | Availability |
|-----------|-------------|--------------|
| `sys.stdlib_module_names` | Frozenset of stdlib module names | Python 3.10+ |
| `sys.builtin_module_names` | Tuple of built-in modules compiled into interpreter | All versions |

**Characteristics of `sys.stdlib_module_names`**:
- Frozenset (immutable, hashable)
- Platform-independent (includes all modules across platforms)
- Automatically updated with Python releases
- Does NOT include deprecated/removed modules

### 3.3 Third-Party Alternative: `stdlibs` Package

The `stdlibs` PyPI package provides historical stdlib listings for Python 2.6 through 3.x:

```python
from stdlibs import module_names  # Superset across all Python 3.x versions
```

This is useful for static analysis tools that need to support multiple Python versions.

### 3.4 Recommendation: Hybrid Approach

**Option A (Recommended)**: Use `sys.stdlib_module_names` at runtime via Python script

```python
import sys
if hasattr(sys, 'stdlib_module_names'):
    STDLIB = sys.stdlib_module_names
else:
    # Fallback for Python < 3.10
    STDLIB = {...}  # Hard-coded list
```

**Option B**: Use `stdlibs` package as a dependency

**Option C**: Keep hard-coded list but remove deprecated modules

**Action for P2**: 
1. Remove `distutils` and `imp` from `STDLIB_MODULES` (they're removed in Python 3.12)
2. Add `tomllib` (added in Python 3.11)
3. Add `zoneinfo` (added in Python 3.9)
4. Document that stdlib detection uses a hard-coded list that may drift from runtime Python version
5. Consider future enhancement to use `sys.stdlib_module_names` via Python script

### 3.5 Modules to Update

| Module | Action | Reason |
|--------|--------|--------|
| `distutils` | Remove | Removed in Python 3.12 |
| `imp` | Remove | Removed in Python 3.12 |
| `aifc` | Remove | Removed in Python 3.13 |
| `audioop` | Remove | Removed in Python 3.13 |
| `cgi` | Remove | Removed in Python 3.13 |
| `cgitb` | Remove | Removed in Python 3.13 |
| `chunk` | Remove | Removed in Python 3.13 |
| `crypt` | Remove | Removed in Python 3.13 |
| `imghdr` | Remove | Removed in Python 3.13 |
| `mailcap` | Remove | Removed in Python 3.13 |
| `msilib` | Remove | Removed in Python 3.13 |
| `nis` | Remove | Removed in Python 3.13 |
| `nntplib` | Remove | Removed in Python 3.13 |
| `ossaudiodev` | Remove | Removed in Python 3.13 |
| `pipes` | Remove | Removed in Python 3.13 |
| `sndhdr` | Remove | Removed in Python 3.13 |
| `spwd` | Remove | Removed in Python 3.13 |
| `sunau` | Remove | Removed in Python 3.13 |
| `telnetlib` | Remove | Removed in Python 3.13 |
| `uu` | Remove | Removed in Python 3.13 |
| `xdrlib` | Remove | Removed in Python 3.13 |
| `tomllib` | Add | Added in Python 3.11 |
| `zoneinfo` | Add | Added in Python 3.9 |

---

## Q4: Architecture Enforcement Analogues

### 4.1 Import Linter Comparison

Import Linter provides three contract types:

| Contract Type | Description | BEAR Equivalent |
|---------------|-------------|-----------------|
| **Forbidden** | Block specific imports from specific modules | `THIRD_PARTY_IMPORT` failure |
| **Independence** | Modules cannot import each other | `BOUNDARY_BYPASS` for sibling blocks |
| **Layers** | Enforce layered architecture (A→B→C, no reverse) | Not directly supported |

**Key Differences**:
- Import Linter checks **indirect imports** (transitive dependencies); BEAR checks direct only
- Import Linter uses **contract files** (`.importlinter`); BEAR uses wiring manifests
- Import Linter is **test-time**; BEAR is **CI gate**

### 4.2 Tach Comparison

Tach (gauge.sh) provides module boundary enforcement:

| Feature | Tach | BEAR |
|---------|------|------|
| Configuration | `tach.toml` with `depends_on` | `bear.blocks.yaml` with block definitions |
| Visibility | `visibility: []` to isolate modules | Governed roots isolation |
| Public interface | `__init__.py` enforcement | Not enforced |
| Dynamic imports | `# tach-ignore` directive | Hard failure (P2) |
| TYPE_CHECKING | Excluded by default | **Not excluded (design gap)** |

**Key Finding**: Tach excludes `TYPE_CHECKING` imports by default. BEAR should do the same.

### 4.3 BEAR's Unique Model

BEAR's block/key model differs from both:

| Aspect | Import Linter / Tach | BEAR |
|--------|---------------------|------|
| Scope | Module-level boundaries | Block-level boundaries |
| Generated code | Not applicable | BEAR-generated modules are allowed |
| Third-party | Configurable | Forbidden in governed roots (strict profile) |
| Enforcement | Test-time / CLI | CI gate with exit codes |
| Wiring | Not applicable | Wiring manifests define block structure |

### 4.4 Recommendation: Add TYPE_CHECKING Exclusion

Both Tach and Import Linter exclude `TYPE_CHECKING` imports by default. These imports only
execute during type checking, not at runtime:

```python
from typing import TYPE_CHECKING
if TYPE_CHECKING:
    import socket  # Should NOT trigger undeclared reach
```

**Action**: Add `TYPE_CHECKING` block exclusion to all Python AST scanners.

---

## Q5: Dependency Governance vs Block Governance

### 5.1 Current Model Analysis

BEAR's strict profile (`python/service`) enforces:
1. **Block isolation**: Governed roots can only import from same-block, `_shared`, and BEAR-generated
2. **Third-party blocking**: All third-party imports from governed roots are forbidden
3. **Stdlib allowance**: Python stdlib imports are allowed

### 5.2 Sufficiency Analysis

**Question**: Is "no third-party imports" sufficient for the strict profile?

**Answer**: Yes, with caveats:

| Scenario | Coverage | Notes |
|----------|----------|-------|
| Direct third-party import | ✅ Blocked | `import requests` → THIRD_PARTY_IMPORT |
| Indirect third-party (via stdlib) | ⚠️ Not blocked | `import urllib` → allowed, but urllib may use third-party internally |
| Third-party in `_shared` | ✅ Blocked | `_shared` is also governed |
| Third-party in tests | ✅ Allowed | Test files are excluded from scanning |
| Third-party in non-governed | ✅ Allowed | Only governed roots are scanned |

**Caveat**: Stdlib modules may internally use third-party packages (e.g., `ssl` uses OpenSSL).
This is acceptable because:
1. Stdlib is part of Python's trusted base
2. BEAR's threat model is "prevent accidental boundary expansion," not "audit all transitive dependencies"

### 5.3 Future Enhancement: Block-Level Dependency Allowlists

The design correctly documents `impl.allowedDeps` as NOT_SUPPORTED for Phase P2. Future phases
could add:

```yaml
# bear.blocks.yaml
blocks:
  - name: MyBlock
    impl:
      allowedDeps:
        - requests  # Allow specific third-party package
```

This would require:
1. Parsing `allowedDeps` from IR
2. Checking imports against allowlist
3. Failing only on non-allowlisted third-party imports

**Action**: Keep as future work; current strict profile is sufficient for P2.

---

## Q6: Deterministic UX — Finding Normalization and Exit Code Mapping

### 6.1 Critical Finding: Exit Code Mapping Error in P2 Design

**Source code analysis of `CheckCommandService.java` reveals**:

```java
// scanForbiddenReflectionDispatch findings → EXIT_UNDECLARED_REACH (6)
List<UndeclaredReachFinding> reflectionDispatchFindings =
    target.scanForbiddenReflectionDispatch(projectRoot, List.of(baselineWiringManifest));
if (!reflectionDispatchFindings.isEmpty()) {
    return checkFailure(
        CliCodes.EXIT_UNDECLARED_REACH,  // ← Exit code 6, NOT 7
        diagnostics,
        "UNDECLARED_REACH",
        CliCodes.REFLECTION_DISPATCH_FORBIDDEN,
        ...
    );
}

// scanBoundaryBypass findings → EXIT_BOUNDARY_BYPASS (7)
List<BoundaryBypassFinding> bypassFindings = ...;
if (!bypassFindings.isEmpty()) {
    return checkFailure(
        CliCodes.EXIT_BOUNDARY_BYPASS,  // ← Exit code 7
        diagnostics,
        "BOUNDARY_BYPASS",
        ...
    );
}
```

**The P2 design document incorrectly states**:
- Dynamic execution escape hatches (`eval`/`exec`/`compile`) → exit code 7
- Dynamic import enforcement → exit code 7

**Actual behavior**:
- `scanForbiddenReflectionDispatch` returns `UndeclaredReachFinding` → exit code 6
- `scanBoundaryBypass` returns `BoundaryBypassFinding` → exit code 7

**Action**: Update P2 design to use exit code 6 for dynamic execution/import findings, OR
change the implementation to return `BoundaryBypassFinding` instead of `UndeclaredReachFinding`.

### 6.2 Recommended Exit Code Mapping (Corrected)

| Finding Type | Return Type | CODE | Exit Code |
|-------------|-------------|------|-----------|
| Covered power surfaces (`socket`, `http`, etc.) | `UndeclaredReachFinding` | `UNDECLARED_REACH` | 6 |
| `os.system`/`os.popen`/`os.exec*` call sites | `UndeclaredReachFinding` | `UNDECLARED_REACH` | 6 |
| Dynamic execution (`eval`/`exec`/`compile`) | `UndeclaredReachFinding` | `REFLECTION_DISPATCH_FORBIDDEN` | 6 |
| Dynamic imports (`importlib`/`__import__`/`sys.path`) | `UndeclaredReachFinding` | `REFLECTION_DISPATCH_FORBIDDEN` | 6 |
| Import containment violations | `BoundaryBypassFinding` | `BOUNDARY_BYPASS` | 7 |
| Project verification failure | `ProjectTestResult` | `PROJECT_TEST_FAILED` | 4 |
| Tool missing | `ProjectTestResult` | `TOOL_MISSING` | 74 |

### 6.3 Finding Normalization

All findings should be normalized for deterministic output:

1. **Path normalization**: Forward slashes, relative to project root
2. **Surface normalization**: Lowercase, canonical names
3. **Sorting**: By path (ascending), then surface (ascending)

```java
findings.sort(Comparator.comparing(UndeclaredReachFinding::path)
    .thenComparing(UndeclaredReachFinding::surface));
```

### 6.4 Error Envelope Format

All failures use the frozen three-line stderr envelope:

```
check: <CODE>: <path>: <detail>
CODE=<code>
PATH=<path>
REMEDIATION=<remediation>
```

This format is already implemented in `CheckCommandService.checkFailure()`.

---

## Summary of Actionable Changes

### Must-Do Before Finalizing Design

| # | Change | Requirement | Priority |
|---|--------|-------------|----------|
| 1 | Add `runpy.run_module`/`run_path` detection | Q2 | High |
| 2 | Add `TYPE_CHECKING` block exclusion | Q4 | High |
| 3 | Fix exit code mapping (6 not 7 for dynamic execution/import) | Q6 | Critical |
| 4 | Remove deprecated modules from `STDLIB_MODULES` | Q3 | Medium |
| 5 | Add `from os import system/popen` detection | Initial research | High |
| 6 | Document known detection gaps | Q1, Q2 | Medium |

### Nice-to-Have (Defer to Future)

| # | Change | Reason |
|---|--------|--------|
| 7 | `sys.modules` assignment detection | Medium complexity, exotic pattern |
| 8 | Use `sys.stdlib_module_names` via Python script | Requires Python 3.10+ |
| 9 | Block-level dependency allowlists | Out of scope for P2 |

### Confirmed Correct (No Change Needed)

| # | Design Decision | Validation |
|---|-----------------|------------|
| 10 | AST-first approach | Industry standard (Tach, Import Linter, Semgrep) |
| 11 | Direct `eval`/`exec`/`compile` detection scope | Matches Bandit B307 |
| 12 | Forbidden third-party imports model | Matches Import Linter "forbidden" contract |
| 13 | Commit-time gate model | Appropriate for Python (no runtime claims) |
| 14 | `PARTIAL` status for dynamic patterns | Honest limitation |

---

## Sources

- BEAR source code: `PythonTarget.java`, `PythonDynamicImportDetector.java`, `PythonImportBoundaryResolver.java`, `CheckCommandService.java`, `CliCodes.java`
- [PEP 690 – Lazy Imports](https://peps.python.org/pep-0690/) — Status: Rejected
- [Bandit blacklist_calls](https://bandit.readthedocs.io/en/1.7.5/blacklists/blacklist_calls.html) — B301-B323 dangerous function taxonomy
- [Python runpy documentation](https://docs.python.org/3/library/runpy.html) — run_module/run_path
- [stdlibs package](https://stdlibs.omnilib.dev/) — Historical stdlib module listings
- [sys.stdlib_module_names](https://docs.python.org/3/library/sys.html#sys.stdlib_module_names) — Python 3.10+ stdlib frozenset
- [Tach documentation](https://docs.gauge.sh/) — TYPE_CHECKING exclusion, dynamic import limitations
- [Import Linter contract types](https://import-linter.readthedocs.io/) — forbidden/independence/layers

Content was rephrased for compliance with licensing restrictions.
