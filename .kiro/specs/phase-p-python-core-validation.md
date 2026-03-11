# Phase P: Python Core Validation (Planning Only)

## Overview

Planning-only phase that locks Python scope decisions before implementation begins. No code
is produced in this phase. This phase exists to ensure Python design choices are captured and
reviewed before Node implementation finalizes patterns that Python must follow.

Python arrives after Node and the architecture prerequisites (see Implementation Phase Ordering
in `roadmap/ideas/future-python-implementation-context.md` lines 383-399).

Source documents:
- `roadmap/ideas/future-python-implementation-context.md`
- `roadmap/ideas/future-python-containment-profile.md`
- `roadmap/ideas/future-multi-target-spec-design.md` (Python Target Spec sections)

## Scope Decisions to Lock

### SD-P1: AST-First Analysis Strategy

All Python static analysis must use Python `ast` module parsing as the primary enforcement
mechanism. Regex/text heuristics are permitted only as fallback advisory signals, never as
primary governance-grade evidence.

AST is used for:
- Import extraction (`import X`, `from X import Y`, `from X import Y as Z`)
- Alias tracking (detecting aliased imports across assignment chains)
- `importlib.import_module(...)` detection
- `__import__(...)` detection
- `eval(...)`, `exec(...)`, `compile(...)` detection
- `os.system`/`os.popen`/`os.exec*` call-site patterns

This is non-negotiable: Python's dynamic nature makes regex-only approaches unreliable for
governance-grade containment claims.

Reference: `roadmap/ideas/future-python-implementation-context.md` section: Analysis Strategy: AST-First

### SD-P2: Strict vs Relaxed Python Profiles

Two concentric governance profiles sharing the same `target=python` detection:

- **`python/service` (strict, default)**: Third-party package imports from governed roots are
  blocked. Governed blocks may only import from same-block, `_shared`, BEAR-generated companions,
  and Python standard library (excluding covered power surfaces). Highest containment confidence
  available for Python. Best for greenfield block development and agent-assisted workflows.

- **`python/service-relaxed` (pragmatic, opt-in)**: Third-party package imports from governed
  roots are allowed but governed. `site-packages` power-surface scan becomes the primary
  containment mechanism. Same-block/`_shared` boundaries remain enforced; no sibling block
  imports. Best for existing Python services being incrementally brought under governance.

Ship the strict profile first. Offer the relaxed profile once the strict profile is proven.

Profile selection: `.bear/profile.id` file or auto-derived from project shape.

Reference: `roadmap/ideas/future-python-containment-profile.md` section: Concentric Profile Model

### SD-P3: Function/Class Locator Expectations

Python locators use the `CanonicalLocator` schema from Phase A:

- `symbol.kind` maps to Python AST node types:
  - `FUNCTION` -- `def` and `async def` at module level
  - `CLASS` -- `class` definitions
  - `METHOD` -- `def` inside a class body
  - `MODULE` -- top-level module (file-level locator)
- `symbol.name` is the Python identifier (function name, class name, method name)
- Decorators do not change symbol identity (the decorated function/class retains its name)
- Lambda expressions use deterministic fallback: `<anonymous@module:startLine>`
- Comprehension expressions are not individually located (they belong to the enclosing symbol)
- Nested functions use their own `FUNCTION` kind with the inner function name

Reference: `.kiro/specs/phase-a-architecture-prerequisites.md` FR-A3 (Identity and Stability Semantics)

### SD-P4: eval/exec/compile Handling

Direct call patterns detected via AST:

| Pattern | Finding | Confidence |
|---|---|---|
| `eval(...)` | `BOUNDARY_BYPASS` | `PARTIAL` |
| `exec(...)` | `BOUNDARY_BYPASS` | `PARTIAL` |
| `compile(...)` | `BOUNDARY_BYPASS` | `PARTIAL` |

Known limitations (documented, not claimed as covered):
- Aliased calls (e.g., `e = eval; e(...)`) are not detected
- String-based code generation passed to `exec` is opaque to static analysis
- `compile()` with `mode='exec'` vs `mode='eval'` is not distinguished
- These are `PARTIAL` detections, never claimed as complete coverage

Reference: `roadmap/ideas/future-python-implementation-context.md` section: Covered Power Surfaces

### SD-P5: Detector Shape (PythonTargetDetector)

`PythonTargetDetector` returns `SUPPORTED` when all of the following are present:
- `pyproject.toml` with `[build-system]` using a PEP 517-compatible backend
- `uv.lock` or `poetry.lock` committed
- `mypy.ini` or `[tool.mypy]` section in `pyproject.toml`
- `src/blocks/` directory exists

Returns `UNSUPPORTED` (exit `64`) for:
- Workspace/monorepo layout detected (e.g., `uv` workspaces, `poetry` monorepo)
- Flat layout (no `src/` directory)
- Namespace packages (missing `__init__.py`) in governed roots

Returns `NONE` for:
- No `pyproject.toml`
- No Python lock file (`uv.lock` or `poetry.lock`)
- No mypy configuration
- No `src/blocks/` directory

This is a first-slice detector. The following are intentionally `NONE` (not recognized):
- `setuptools`-only projects (no PEP 517 build-system)
- `conda`-managed projects
- `pip`-only projects (no lock file)
- `pipenv`-managed projects

Reference: `roadmap/ideas/future-python-implementation-context.md` section: Python Target Detection

## Python Forward Compatibility Notes

Every Phase A type is directly reused by Python:
- **TargetDetector**: `PythonTargetDetector` implements the same interface
- **DetectedTarget**: Same `SUPPORTED`/`UNSUPPORTED`/`NONE` model with ecosystem family
- **CanonicalLocator**: Python findings carry the same locator schema; `SymbolKind` maps
  directly to Python AST node types
- **GovernanceProfile**: `python/service` and `python/service-relaxed` profiles use the
  same `GovernanceProfile` value object
- **AnalyzerProvider**: `PythonAnalyzerProvider` (`python-ast-native`) implements the SPI
  using Python `ast` module parsing as the primary evidence source
- **AnalyzerCapabilities**: `supportsImports=true`, `supportsSymbols=true`,
  `supportsSpans=true`, `supportsDependencies=false` (from source alone; manifest parsing
  is a separate concern), `supportsOwnership=true`, `supportsReferences=false`

## References

- `roadmap/ideas/future-python-implementation-context.md` -- complete Python context summary
- `roadmap/ideas/future-python-containment-profile.md` -- containment profile (concentric profiles, governed roots, import policy)
- `roadmap/ideas/future-multi-target-spec-design.md` -- Python Target Spec sections
- `roadmap/ideas/future-multi-target-expansion-plan.md` -- Python expansion priority
- `.kiro/specs/phase-a-architecture-prerequisites.md` -- architecture types Python reuses
