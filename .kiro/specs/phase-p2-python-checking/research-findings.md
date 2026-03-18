# Phase P2 Research Findings: Python Governance Gaps & SOTA Solutions

## Purpose

This document synthesizes research into the hard problems Python poses to BEAR's deterministic
governance model. It identifies gaps in the current P2 design, evaluates state-of-the-art tools,
and recommends design revisions before finalizing the spec.

---

## 1. Landscape: Existing Python Boundary Enforcement Tools

### 1.1 tach (gauge.sh) — Rust-based module boundary enforcement

What it does:
- Defines module boundaries in `tach.toml` with explicit `depends_on` declarations
- `tach check` verifies imports match declared dependencies using AST analysis
- Supports visibility controls (`visibility: []` to isolate modules)
- Supports public interface enforcement via `__init__.py`
- Utility modules can be marked freely usable without explicit dependency declarations
- Checks all imports including conditional ones (except `TYPE_CHECKING` blocks by default)

Limitations relevant to BEAR:
- **Cannot catch dynamic references**: "Since Tach uses the AST to find imports and public
  members, dynamic imports (e.g. using a string path) and dynamic names (e.g. using setattr,
  locals, globals) are not supported." Uses `# tach-ignore` directive as escape hatch.
- No runtime enforcement — purely static, same as BEAR's model
- No third-party package power-surface analysis

BEAR relevance:
- Validates BEAR's AST-first approach as industry-standard for Python boundary enforcement
- Confirms that dynamic import bypass is an accepted open gap in the Python ecosystem
- tach's `depends_on` model maps conceptually to BEAR's governed-root import containment

### 1.2 import-linter — Contract-based import enforcement

What it does:
- Three contract types: **forbidden** (block specific imports), **independence** (no mutual
  imports between modules), **layers** (enforce layered architecture)
- Forbidden contracts can block external packages (e.g., `django`, `requests`)
- Checks indirect imports (transitive dependency chains)
- Supports `ignore_imports` for known exceptions
- Container-based layer contracts for repeated patterns across packages

BEAR relevance:
- import-linter's **forbidden** contract type is closest to BEAR's "third-party imports from
  governed roots are forbidden" rule
- import-linter's **independence** contract maps to BEAR's "sibling blocks cannot import each other"
- Validates that blocking external package imports from specific modules is a recognized pattern
- import-linter checks indirect imports — BEAR's Phase P2 design only checks direct imports,
  which is an honest limitation to document

### 1.3 PyTestArch / pytest-archon — Architecture testing

What they do:
- Define architectural rules as pytest tests
- Inspired by Java's ArchUnit
- Test import relationships between modules
- Enforce layered architecture, forbidden dependencies

BEAR relevance:
- These are test-time tools, not CI gate tools — BEAR's model is stronger (deterministic gate)
- Confirms the pattern of import-based architecture enforcement is well-established in Python
- BEAR doesn't need to adopt these tools but validates the approach

### 1.4 Semgrep — Pattern-based security scanning

What it does:
- Detects `eval()`, `exec()`, `compile()` usage via pattern matching
- Supports taint analysis (tracking data flow from sources to sinks)
- Python-specific rules for code injection prevention
- Can detect `subprocess.call(user_input, shell=True)` patterns

BEAR relevance:
- Semgrep's Python code injection cheat sheet confirms BEAR's covered escape hatches
  (`eval`, `exec`, `compile`) as the primary direct-call patterns
- Semgrep does NOT attempt to catch obfuscated `eval`/`exec` via `getattr`/`builtins` —
  it focuses on direct call patterns, same as BEAR's design
- Validates BEAR's approach of flagging direct patterns with `PARTIAL` status

### 1.5 hexora — Malicious code detection via AST + string evaluation

What it does:
- Tracks obfuscated `eval`/`exec` calls through `builtins`, `getattr`, `__import__`,
  `sys.modules`, `globals()`, `locals()`
- Evaluates basic string operations to de-obfuscate constant expressions
- Detects base64-encoded payloads

BEAR relevance:
- Demonstrates the depth of obfuscation possible in Python (see Section 2)
- BEAR should NOT attempt this level of analysis — it's a malware detection problem,
  not a governance problem
- Confirms BEAR's `PARTIAL` status for dynamic execution detection is honest

---

## 2. The Obfuscation Problem: Why AST-Only Detection Has Known Gaps

Research from hexora (rushter.com) and Python sandbox escape literature documents specific
bypass techniques for `eval`/`exec` detection:

### Direct bypasses BEAR's AST scanner will NOT catch:

1. **builtins module indirection**:
   ```python
   import builtins
   builtins.exec("malicious_code")
   ```

2. **Variable reassignment**:
   ```python
   import builtins
   b = builtins
   b.exec("malicious_code")
   ```

3. **`__import__` + attribute access**:
   ```python
   __import__("builtins").exec("malicious_code")
   ```

4. **getattr with string concatenation**:
   ```python
   getattr(__import__("built"+"ins"), "ex"+"ec")("malicious_code")
   ```

5. **sys.modules / globals() / locals()**:
   ```python
   import sys
   sys.modules["builtins"].exec("malicious_code")
   globals()["__builtins__"].exec("malicious_code")
   ```

6. **compile + FunctionType**:
   ```python
   import types
   types.FunctionType(compile("malicious", "<string>", "exec"), globals())()
   ```

### What this means for BEAR:

BEAR's AST scanner detects **direct** `eval()`, `exec()`, `compile()` calls. This is the same
level of detection as Semgrep, Bandit, and tach. The bypasses above require intentional
obfuscation — they are not patterns that appear in normal governed block code.

**Recommendation**: Document these as known gaps with `PARTIAL` status. Do NOT attempt to
chase obfuscation patterns — that's a malware detection problem, not a governance problem.
BEAR's threat model is "prevent accidental boundary expansion by well-intentioned agent code,"
not "prevent adversarial code injection by malicious actors."

---

## 3. The Dynamic Import Problem: Deeper Than Phase P2 Design Covers

### 3.1 What Phase P2 currently detects

- `importlib.import_module(...)` — direct call
- `__import__(...)` — direct call
- `importlib.util.spec_from_file_location(...)` — direct call
- `sys.path.append/insert/assignment` — direct mutation

### 3.2 What Phase P2 does NOT detect

1. **`sys.modules` manipulation**:
   ```python
   import sys
   sys.modules["my_module"] = some_object  # Inject arbitrary module
   ```

2. **`globals()`/`locals()` import injection**:
   ```python
   globals()["socket"] = __import__("socket")
   ```

3. **Custom import hooks/finders** (already documented as NOT_SUPPORTED):
   ```python
   import sys
   sys.meta_path.append(CustomFinder())
   ```

4. **`importlib.util.module_from_spec` + `loader.exec_module`**:
   ```python
   import importlib.util
   spec = importlib.util.spec_from_file_location("mod", "/path/to/mod.py")
   mod = importlib.util.module_from_spec(spec)
   spec.loader.exec_module(mod)
   ```

5. **Indirect `__import__` via builtins**:
   ```python
   import builtins
   builtins.__import__("socket")
   ```

### 3.3 Recommendation

The current design's coverage is appropriate for Phase P2. The uncovered patterns are either:
- Already blocked by `__import__` detection (pattern 5)
- Exotic enough that they indicate intentional bypass (patterns 1-4)
- Already documented as NOT_SUPPORTED (pattern 3)

**Add to design**: Document `sys.modules` manipulation as a known gap alongside custom import
hooks. Consider adding `sys.modules` assignment detection as a future enhancement (it's a
simple AST pattern: `ast.Subscript` with `value` being `sys.modules`).

---

## 4. SAST Boundaries: What Static Analysis Cannot Do

Research from security testing literature confirms fundamental SAST limitations for Python:

### Reliably detectable (BEAR can enforce):
- Known dangerous function usage (`eval`, `exec`, `subprocess`, etc.)
- Direct import patterns (standard `import`/`from` statements)
- Call-site patterns (`os.system(...)`, `os.popen(...)`)

### Struggles with (BEAR should document as PARTIAL):
- Runtime-dependent behavior (feature flags, environment variables)
- Dynamic code execution via indirection (`getattr`, `setattr`)
- Metaclasses and decorator chains
- Monkey patching

### Cannot detect (BEAR should document as NOT_SUPPORTED):
- Business logic vulnerabilities
- Cross-service data flow
- Race conditions
- Runtime-only configurations

**Key insight**: "SAST finds code smells. Humans find real-world exploits." BEAR's honest
position is that it provides a deterministic pre-merge gate that catches the most common
boundary expansion patterns. It does not claim to catch all possible bypass techniques.

---

## 5. Gap Analysis: Current Design vs. Research Findings

### 5.1 Gaps that should be addressed in Phase P2

| Gap | Current Design | Recommendation |
|-----|---------------|----------------|
| `builtins.exec/eval` indirection | Not detected | Document as known gap; do NOT add detection (too many false positives with `builtins` module usage) |
| `sys.modules` manipulation | Not mentioned | Add to known gaps documentation in design; consider future detection |
| `getattr` + string concat bypass | Not mentioned | Document as known gap; out of scope for governance (malware detection territory) |
| Aliased imports (`from subprocess import call as safe_call`) | Detected by AST (alias tracking) | Verify AST scanner handles `as` aliases correctly — this IS covered by `ast.Import` alias tracking |
| `from os import system` (direct function import) | Not explicitly covered | **Design gap**: Current scanner checks `os.system()` call-site but may miss `from os import system; system("cmd")`. Add `from os import system/popen/exec*` to import-level detection |
| Indirect imports through allowed packages | Documented as site-packages scan (future) | Correct — keep as future work, not Phase P2 |
| `TYPE_CHECKING` guarded imports | Not mentioned | These are type-hint-only imports that don't execute at runtime. BEAR should skip them (same as tach default). Add to design. |

### 5.2 Gaps that are correctly deferred

- Custom import hooks/finders → NOT_SUPPORTED (correct)
- Namespace packages → NOT_SUPPORTED (correct)
- Block-level dependency allowlists → NOT_SUPPORTED (correct)
- Runtime sandboxing → NOT_SUPPORTED (correct)
- Native extension analysis → NOT_COVERABLE (correct)

### 5.3 Design strengths confirmed by research

- AST-first approach is industry standard (tach, import-linter, Semgrep all use it)
- Direct `eval`/`exec`/`compile` detection matches Semgrep/Bandit coverage level
- `PARTIAL` status for dynamic patterns is honest and appropriate
- Forbidden third-party imports from governed roots is a recognized pattern (import-linter)
- Commit-time gate model is the right abstraction for Python (no runtime claims)

---

## 6. Recommended Design Revisions

### 6.1 Add `from os import system/popen` detection (Requirements 3, Design gap)

Current design only detects `os.system()` call-site patterns. But:
```python
from os import system
system("cmd")  # This bypasses the current os.* call-site check
```

**Fix**: Add `from os import system`, `from os import popen`, and `from os import exec*`
to the import-level detection in `PythonUndeclaredReachScanner`. When `os` sub-functions
are imported directly, treat them as covered power-surface imports.

### 6.2 Add `TYPE_CHECKING` block exclusion

```python
from typing import TYPE_CHECKING
if TYPE_CHECKING:
    import socket  # This should NOT trigger undeclared reach
```

**Fix**: All Python AST scanners should skip imports inside `if TYPE_CHECKING:` blocks.
This is standard practice (tach does it by default). These imports only execute during
type checking, not at runtime.

### 6.3 Document known bypass gaps explicitly in design

Add a "Known Detection Gaps" section to the design document listing:
1. `builtins.exec/eval` indirection — not detected, accepted gap
2. `getattr` + string concatenation — not detected, malware detection territory
3. `sys.modules` manipulation — not detected, future consideration
4. `globals()`/`locals()` import injection — not detected, accepted gap
5. `compile` + `FunctionType` — not detected, accepted gap
6. Aliased dangerous function calls via variable reassignment — not detected, accepted gap

### 6.4 Add `from subprocess import *` / wildcard import detection

Wildcard imports from covered power-surface modules should be detected:
```python
from socket import *  # Brings all socket functions into scope
```

This is already handled by `ast.ImportFrom` with `node.module == 'socket'`, but should
be explicitly tested.

### 6.5 Verify relative import handling

Relative imports (`from . import module`, `from ..shared import util`) should be handled
correctly by the existing `PythonImportContainmentScanner`. Verify that the undeclared reach
scanner correctly ignores relative imports (they can't reach standard library modules).

---

## 7. Research Prompt for Deeper Investigation

If the team wants to go deeper on any of these topics, here's a focused prompt:

```
I'm building a deterministic Python governance CLI tool (like ArchUnit for Java, but for
Python import boundaries). The tool runs as a pre-merge CI gate, not at runtime.

Current coverage:
- AST-based import boundary enforcement (governed roots can only import from same-block,
  _shared, and BEAR-generated modules)
- Direct eval/exec/compile call detection
- Direct importlib.import_module/__import__/sys.path mutation detection
- Covered power-surface import detection (socket, http, subprocess, etc.)

I need to understand:
1. What are the most common LEGITIMATE uses of importlib.import_module in Python service
   code? (To calibrate false positive risk)
2. How does mypy --strict interact with dynamic imports? Does mypy flag them?
3. Are there Python typing PEPs that would let a static tool infer import boundaries
   from type annotations?
4. What's the state of PEP 690 (lazy imports) and how would it affect AST-based import
   scanning?
5. How do uv workspaces handle per-package dependency isolation, and could BEAR leverage
   that for block-level dependency governance in a future phase?
```

---

## 8. Summary of Actionable Changes

### Must-do before finalizing design:
1. Add `from os import system/popen/exec*` to import-level detection
2. Add `TYPE_CHECKING` block exclusion to all scanners
3. Add "Known Detection Gaps" section to design document
4. Verify wildcard import detection for covered modules
5. Verify relative import handling in undeclared reach scanner

### Nice-to-have (can be deferred):
6. `sys.modules` assignment detection
7. `builtins.exec/eval` detection (high false-positive risk)

### Confirmed correct (no change needed):
8. AST-first approach
9. `PARTIAL` status for dynamic patterns
10. Direct eval/exec/compile detection scope
11. Forbidden third-party imports model
12. Commit-time gate model (no runtime claims)

---

Sources:
- [tach documentation](https://docs.gauge.sh/) — module boundary enforcement
- [import-linter contract types](https://import-linter.readthedocs.io/en/v1.2.1/contract_types.html) — forbidden/independence/layers
- [Semgrep Python code injection cheat sheet](https://semgrep.dev/docs/cheat-sheets/python-code-injection/) — eval/exec detection patterns
- [hexora / rushter.com](https://rushter.com/blog/python-code-exec/) — obfuscation techniques for eval/exec
- [SAST Boundaries](https://securitytesting.nocomplexity.com/module5/sast-boundaries) — static analysis limitations
- [Python sandbox escape research](https://moshekaplan.com/posts/2012-10-26-escaping-python-sandboxes/) — keyword scanning bypass techniques

Content was rephrased for compliance with licensing restrictions.
