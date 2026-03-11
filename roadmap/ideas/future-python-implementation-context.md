---
id: future-python-implementation-context
title: Python implementation context — fast-onboarding summary
status: queued
priority: medium
commitment: uncommitted
milestone: Future
---

## Purpose

This document is the single entry point for anyone building the Python implementation specs.
It summarizes the key decisions, architecture, constraints, and open questions across all
relevant BEAR documents, and provides direct references to each source for deeper reading.

Read this first. Then follow the references only for sections you need to implement.

---

## What BEAR Is

BEAR is a deterministic governance CLI and CI gate for agentic backend development. It enforces
boundary constraints — who may import what, which capabilities are exposed, and whether the
project's structural integrity has drifted — via static, commit-time analysis. It does not
execute code, sandbox runtimes, or interpret business logic.

Key references:
- `docs/context/architecture.md` — core principles, what BEAR guarantees and does not guarantee
- `docs/context/ir-spec.md` — BEAR IR v1 canonical specification
- `docs/context/governance.md` — governance rules and enforcement model

---

## Current State of Python Planning

Python is a **parked future initiative**. It has a complete containment profile, a cross-target
expansion plan, and a spec-driven design, but no active implementation work has started.

The Python implementation must wait for these **architecture prerequisites** to land first:

1. `TargetDetector` + `.bear/target.id` prerequisite epic (Phase 1)
2. Canonical locator schema (Phase 2)
3. Target/profile separation contract (Phase 3)
4. `AnalyzerProvider` SPI (Phase 4)

Only after those phases — and after Node ships as the first non-JVM target — does Python
implementation begin.

Key references:
- `roadmap/ideas/future-multi-target-spec-design.md` §Cross-Target Implementation Phases
- `roadmap/ideas/future-multi-target-expansion-plan.md` §Recommended expansion priority

---

## Two Concentric Python Profiles

Python governance uses two concentric profiles, both sharing the same `target=python`
detection, generated artifacts, project verification, and `site-packages` scan infrastructure.
The only behavioral difference is third-party import handling.

### Inner profile: `python/service` (strict, default)

- Third-party package imports from governed roots are **blocked**
- Governed blocks may only import from same-block, `_shared`, BEAR-generated companions,
  and Python standard library (excluding covered power surfaces)
- Highest containment confidence available for Python
- Best for: greenfield block development, agent-assisted workflows

### Outer profile: `python/service-relaxed` (pragmatic, opt-in)

- Third-party package imports from governed roots are **allowed but governed**
- `site-packages` power-surface scan becomes the **primary containment mechanism**
- Same-block/`_shared` boundaries remain enforced; no sibling block imports
- Best for: existing Python services being incrementally brought under governance

Ship the inner profile first. Offer the relaxed profile once the strict profile is proven.

Key reference:
- `roadmap/ideas/future-python-containment-profile.md` §Concentric Profile Model

---

## Python Target Detection

`PythonTargetDetector` returns `SUPPORTED` when all of the following are present:
- `pyproject.toml` with `[build-system]` using a PEP 517-compatible backend
- `uv.lock` or `poetry.lock`
- `mypy.ini` or `[tool.mypy]` section in `pyproject.toml`
- `src/blocks/` directory exists

Returns `UNSUPPORTED` (→ `exit 64`) if:
- workspace/monorepo layout detected
- flat layout (no `src/` directory)
- namespace packages (missing `__init__.py`) in governed roots
- ambiguous ecosystem (Node + Python signals at same root → `TARGET_AMBIGUOUS`)

Version-aware detection: detector may validate minimum Python version from
`pyproject.toml` `requires-python`.

Key references:
- `roadmap/ideas/future-multi-target-spec-design.md` §Python Target Spec → Detection
- `roadmap/ideas/future-multi-target-spec-design.md` §The Target Seam Contract

---

## Supported Project Shape

Profile name: `python-pyproject-single-package-v1`

Required:
- Python 3.12+
- single `pyproject.toml`-based package
- `uv` or `poetry` as package manager
- `uv.lock` or `poetry.lock` committed
- `src/` layout with `src/blocks/<blockKey>/` governed roots
- `__init__.py` in all governed roots
- strict `mypy` enabled

Not supported in first slice:
- workspaces/monorepos
- flat layout
- namespace packages without `__init__.py`
- `.pth` file manipulations or `sys.path` mutations
- custom import hooks or finders
- Cython or C extensions in governed roots

Key reference:
- `roadmap/ideas/future-python-containment-profile.md` §Supported Python Profile

---

## Generated Artifact Layout

BEAR-owned (regenerated, drift-checked):
```
build/generated/bear/
  wiring/<blockKey>.wiring.json
  <blockKey>/
    <block_name>_ports.py
    <block_name>_logic.py
    <block_name>_wrapper.py
```

User-owned (created once, never overwritten):
```
src/blocks/<blockKey>/
  impl/<block_name>_impl.py
  __init__.py
```

Key reference:
- `roadmap/ideas/future-multi-target-spec-design.md` §Python Target Spec → Generated artifact layout

---

## Governed Roots and Import Policy

Governed:
- `src/blocks/<blockKey>/` — block-local, `__init__.py` required
- `src/blocks/_shared/` — optional shared root, `__init__.py` required

Not governed:
- `tests/`, `scripts/`, config files, `src/` outside `src/blocks/`

Import policy (strict profile):
- ✅ same-block imports
- ✅ `_shared` imports
- ✅ BEAR-generated companions under `build/generated/bear/`
- ✅ Python standard library (excluding covered power surfaces)
- ❌ sibling block imports → `BOUNDARY_BYPASS`
- ❌ nongoverned repo source → `BOUNDARY_BYPASS`
- ❌ third-party packages → `BOUNDARY_BYPASS`
- ❌ dynamic imports → `BOUNDARY_BYPASS`

Import policy (relaxed profile):
- same as strict except third-party imports are **allowed but tracked**

Key references:
- `roadmap/ideas/future-python-containment-profile.md` §Governed Roots
- `roadmap/ideas/future-multi-target-spec-design.md` §Python Target Spec → Import containment rules

---

## Analysis Strategy: AST-First

All Python static analysis must use Python `ast` module parsing as the primary enforcement
mechanism. Regex/text heuristics are permitted only as fallback advisory signals.

AST is used for:
- import extraction (`import X`, `from X import Y`, `from X import Y as Z`)
- alias tracking
- `importlib.import_module(...)`, `__import__(...)` detection
- `eval(...)`, `exec(...)`, `compile(...)` detection
- `os.system`/`os.popen`/`os.exec*` call-site patterns

Key reference:
- `roadmap/ideas/future-python-containment-profile.md` §Analysis Strategy

---

## Covered Power Surfaces

### Standard-library undeclared reach (first slice)

| Module | Treatment |
| --- | --- |
| `socket` | `UNDECLARED_REACH` |
| `http` / `http.client` / `http.server` | `UNDECLARED_REACH` |
| `urllib` / `urllib.request` | `UNDECLARED_REACH` |
| `subprocess` | `UNDECLARED_REACH` |
| `multiprocessing` | `UNDECLARED_REACH` |
| `os.system`/`os.popen`/`os.exec*` patterns | `UNDECLARED_REACH` (`PARTIAL`) |
| `import os` alone | **Not flagged** (benign for `os.path`) |

### Dynamic execution escape hatches

| Pattern | Treatment |
| --- | --- |
| `eval(...)` | `BOUNDARY_BYPASS` (`PARTIAL`) |
| `exec(...)` | `BOUNDARY_BYPASS` (`PARTIAL`) |
| `compile(...)` | `BOUNDARY_BYPASS` (`PARTIAL`) |

### Dynamic import facilities

| Pattern | Treatment |
| --- | --- |
| `importlib.import_module(...)` | `BOUNDARY_BYPASS` |
| `__import__(...)` | `BOUNDARY_BYPASS` |
| `importlib.util.spec_from_file_location(...)` | `BOUNDARY_BYPASS` |
| `sys.path` mutation | `BOUNDARY_BYPASS` (`PARTIAL`) |

Key references:
- `roadmap/ideas/future-python-containment-profile.md` §Undeclared Reach and Boundary Bypass Coverage
- `roadmap/ideas/future-multi-target-spec-design.md` §Python Target Spec → Undeclared reach rules

---

## `site-packages` Scan

`PythonSitePackagesPowerSurfaceScanner`:
- triggers in `pr-check` when lock file changes
- locates `site-packages` via `purelib`/`platlib` paths (cross-platform)
- scans pure-Python `.py` source in installed packages
- applies same covered power-surface rules as governed roots
- transitive scan depth: 2 (configurable via `.bear/python-scan.yaml`)
- produces a dependency power-surface report (advisory, not build-blocking)
- native extension packages (`.pyd`/`.so` only) listed as `NOT_COVERABLE`

In the relaxed profile, this scan becomes the **primary containment mechanism** for
third-party package governance.

Key references:
- `roadmap/ideas/future-python-containment-profile.md` §Installed-package power-surface exposure
- `roadmap/ideas/future-multi-target-spec-design.md` §Python Target Spec → `site-packages` scan

---

## Dependency Governance

Repo-level only. No block-level `impl.allowedDeps` analogue (treated as `NOT_SUPPORTED`).

Boundary-expanding in `pr-check`:
- `[project] dependencies` changes
- `[project.optional-dependencies]` changes
- `[tool.uv.dev-dependencies]` or `[tool.poetry.dev-dependencies]` changes
- any lock-file change (`uv.lock` or `poetry.lock`)

`impl.allowedDeps` in any block IR → `exit 64`, `CODE=UNSUPPORTED_TARGET`.

Key references:
- `roadmap/ideas/future-python-containment-profile.md` §Dependency Governance Model
- `roadmap/ideas/future-multi-target-spec-design.md` §Python Target Spec → Dependency governance

---

## Project Verification

Command: `uv run mypy src/blocks/ --strict`
Fallback: `poetry run mypy src/blocks/ --strict`

Exit mapping:
- `mypy` exit `0` → `ProjectTestStatus.PASSED` → `exit 0`
- `mypy` type errors → `ProjectTestStatus.FAILED` → `exit 4`
- `uv`/`poetry` not found → `ProjectTestStatus.TOOL_MISSING` → `exit 74`
- `mypy` not installed → `ProjectTestStatus.TOOL_MISSING` → `exit 74`
- timeout → `ProjectTestStatus.TIMEOUT` → `exit 4`

Key reference:
- `roadmap/ideas/future-multi-target-spec-design.md` §Python Target Spec → Project verification

---

## Architecture Seams to Implement

### 1. Target seam (`PythonTarget`)

Implements the existing `Target` interface:
- `targetId()` → `python`
- `compileSingle(...)` / `compileAll(...)` — generate Python ports/logic/wrapper
- `generateWiringOnlySingle(...)` / `generateWiringOnlyAll(...)` — wiring-only generation
- `targetChecks()` — return `PythonImportContainmentScanner`, `PythonUndeclaredReachScanner`,
  `PythonDynamicExecutionScanner`
- `runProjectVerification(...)` — run `mypy --strict`
- `governedRoots(...)` — return `src/blocks/<blockKey>/`

Key reference:
- `roadmap/ideas/future-multi-target-spec-design.md` §The Target Seam Contract

### 2. Analyzer seam (`PythonAnalyzerProvider`)

Implements the `AnalyzerProvider` SPI:
- `analyzerId()` → `python-ast-native`
- `supports(targetId=python, profile)` → `true`
- `collectEvidence(...)` → `EvidenceBundle` with imports, dependencies, ownership facts

Key reference:
- `roadmap/ideas/future-multi-target-spec-design.md` §Target + Analyzer Two-Seam Model

### 3. Detector (`PythonTargetDetector`)

Returns `SUPPORTED`/`UNSUPPORTED`/`NONE` based on project signals.

Key reference:
- `roadmap/ideas/future-multi-target-spec-design.md` §Python Target Spec → Detection

---

## Anchoring Constraints (Must Not Change)

1. **IR v1 is the boundary source of truth.** No `target:` field, no per-target IR additions.
2. **Exit code registry is frozen.** `0`, `2`, `4`, `5`, `6`, `64`, `74`.
3. **`CODE/PATH/REMEDIATION` envelope is frozen.**
4. **JVM behavior must remain byte-identical.**
5. **No runtime policy engine additions.**
6. **Generated artifacts live under `build/generated/bear/`.**

Key reference:
- `roadmap/ideas/future-multi-target-spec-design.md` §Anchoring Constraint: What Must Not Change

---

## Agent Workflow Integration

BEAR as a commit-time gate for Python agent-driven development:

```
1. Agent commits changes to sandbox branch
2. bear check --project <repoRoot>
   → static scan of committed .py files
   → no interpreter, no running process required
3. Agent reads findings and fixes violations
4. bear pr-check --project <repoRoot> --base <base-ref>
   → reviews pyproject.toml and lock-file deltas
   → triggers site-packages scan if lock file changed
   → exit 5 if boundary-expanding, exit 0 if clean
5. Human reviewer sees structured boundary signal before merge
```

The agent does not need to know the target. The CLI resolves it and surfaces the same
envelope format regardless of language.

Key references:
- `roadmap/ideas/future-python-containment-profile.md` §Branch and Agent Workflow
- `roadmap/ideas/future-multi-target-spec-design.md` §Agent Workflow Integration

---

## What Python Cannot Honestly Claim

- No runtime sandboxing or process isolation
- No per-block dependency allowlist (`impl.allowedDeps`)
- No complete transitive semantic reach proof
- No analysis of native extensions (`.pyd`/`.so`) — `NOT_COVERABLE`
- No guaranteed completeness for `eval`/`exec`/`compile` detection (direct patterns only)
- Dynamic import detection is `PARTIAL` (direct governed-root usage only)
- `site-packages` scan covers pure-Python source only; native extensions are opaque

Key reference:
- `roadmap/ideas/future-python-containment-profile.md` §Containment Model

---

## Implementation Phase Ordering

Python arrives after Node and the architecture prerequisites:

```
Phase 0:  Contract freeze (already done)
Phase 1:  TargetDetector + .bear/target.id
Phase 2:  Canonical locator schema
Phase 3:  Target/profile separation
Phase 4:  AnalyzerProvider SPI
Phases 5–9:  Node implementation
Phase 10: PythonTarget — scan-only (import containment + drift)
Phase 11: PythonTarget — undeclared reach + dynamic import/exec blocking
Phase 12: PythonTarget — dependency governance (pr-check) + site-packages scan
Phase 13: PythonTarget — project verification (mypy --strict)
Phase 14: PythonTarget — agent docs overlay
```

Key reference:
- `roadmap/ideas/future-multi-target-spec-design.md` §Cross-Target Implementation Phases

---

## Acceptance Test Template

Each Python implementation phase must provide fixture tests covering:
1. Compile a governed block from a minimal fixture repo
2. Pass `check` (clean: drift `PASS`, all target checks `PASS`)
3. Fail `check` deterministically when one import escapes the block root
4. Fail `check` deterministically when one covered power surface is imported
5. Return `exit 5` in `pr-check` when a dependency is added to `pyproject.toml`
6. Return `exit 0` in `pr-check` when only dependency removals are present
7. Preserve user-owned impl files across a `compile` re-run

Key reference:
- `roadmap/ideas/future-multi-target-spec-design.md` §Per-target feature acceptance test template

---

## Spec Review Checklist (Before Implementation Starts)

- [ ] Dedicated phased CLI implementation plan exists (analogous to `future-target-adaptable-cli-node.md`)
- [ ] Containment profile document is current (`future-python-containment-profile.md`)
- [ ] `PythonTargetDetector` conflict rules with all existing detectors are defined
- [ ] Generated artifact layout does not overlap with JVM or other targets
- [ ] Covered power-surface set is explicitly bounded and documented
- [ ] At least one per-target acceptance fixture test is specified
- [ ] `impl.allowedDeps` behavior is explicitly specified (`NOT_SUPPORTED`)
- [ ] Project verification command, timeout, and failure mapping are fully specified
- [ ] Agent/branch workflow section is present in the profile doc
- [ ] `.bear/target.id` pin value `python` is registered in the target registry
- [ ] Target/profile contract (`python/service`, `python/service-relaxed`) is defined
- [ ] Canonical locator mapping exists for all Python findings
- [ ] `AnalyzerProvider` implementation strategy is defined (AST-native first)

Key reference:
- `roadmap/ideas/future-multi-target-spec-design.md` §Spec Review Checklist

---

## Complete Reference Index

| Document | What it covers | Lines |
| --- | --- | --- |
| `roadmap/ideas/future-python-containment-profile.md` | Full Python containment profile: concentric profiles, governed roots, import policy, containment model, dependency governance, undeclared reach, site-packages scan, agent workflow, capability matrix | ~580 |
| `roadmap/ideas/future-multi-target-spec-design.md` | Spec-driven design: Target/Analyzer seams, detector contract, locator schema, per-target specs (Node/Python/React), phase ordering, acceptance tests | ~760 |
| `roadmap/ideas/future-multi-target-expansion-plan.md` | Cross-target plan: per-target problem/solution/tradeoffs, capability matrix, expansion priority, architecture prerequisites | ~430 |
| `roadmap/ideas/future-node-containment-profile.md` | Node/TypeScript containment profile (comparator for Python design decisions) | ~390 |
| `roadmap/ideas/future-dotnet-containment-profile.md` | .NET containment profile (comparator: strongest non-JVM target) | ~250 |
| `roadmap/ideas/future-react-containment-profile.md` | React frontend containment profile (comparator: weakest target, separate product direction) | ~355 |
| `roadmap/ideas/future-target-adaptable-cli-node.md` | Node phased CLI plan (template for writing a Python phased plan) | ~222 |
| `roadmap/ideas/future-idea-families.md` | Broad future ideas including Go target, `bear init` command | ~180 |
| `docs/context/architecture.md` | BEAR core architecture, principles, guarantees | ~141 |
| `docs/context/ir-spec.md` | BEAR IR v1 specification | ~207 |
| `docs/context/governance.md` | Governance rules | — |

---

## Open Work Before Python Can Start

1. **Write a phased Python CLI implementation plan** analogous to `future-target-adaptable-cli-node.md`
2. **Complete architecture prerequisites** (Phases 1–4)
3. **Ship Node** as the first non-JVM target (Phases 5–9)
4. **Make the product strategy decision**: Python vs .NET as the second non-JVM target
5. **Resolve open Python questions**:
   - exact `mypy` strict-mode config validation rules
   - `site-packages` scan caching and advisory semantics
   - `.bear/profile.id` file format and validation
   - interaction between `python/service-relaxed` third-party tracking and `pr-check`
     boundary-expanding classification
