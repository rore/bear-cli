---
id: future-python-containment-profile
title: Honest Python containment profile
status: queued
priority: medium
commitment: uncommitted
milestone: Future
---

## Goal

Define the smallest Python target profile BEAR could support without overstating determinism,
containment, or project-verification guarantees.

This document is intentionally narrow:
- no CLI behavior changes
- no `.bear/target.id` changes
- no target detection/pinning changes
- no IR schema changes
- no attempt to cover the full Python ecosystem

## Recommendation Summary

Recommendation:
- keep Python support parked until the product explicitly accepts a narrow first slice
- if BEAR pursues Python later, the honest first slice is:
  - Python 3.12+ only
  - single `pyproject.toml`-based package only
  - `uv` or `poetry` as the package manager
  - strict-mode `mypy` typecheck as the project-verification contract
  - no multi-package workspace (`uv` workspaces or `poetry` monorepo setups out of scope)
  - no import aliases or implicit namespace packages
  - `src/` layout with `src/blocks/<blockKey>/` governed roots

Why:
- JVM containment works because BEAR can combine deterministic generated ownership, static scanners,
  and a real build-tool enforcement handshake.
- Python can support deterministic generated ownership and import-boundary static scanning, but
  Python's module system and dynamic import facilities make it impossible to provide JVM-equivalent
  containment or runtime sandboxing claims.

## Supported Python Profile

Profile name:
- `python-pyproject-single-package-v1`

Required repo shape:
- one Python package root only
- required files at `projectRoot`:
  - `pyproject.toml`
  - `uv.lock` or `poetry.lock`
  - `mypy.ini` or `[tool.mypy]` section in `pyproject.toml`
- `pyproject.toml` must include:
  - `[build-system]` using a PEP 517-compatible backend (for example `hatchling`, `flit-core`, or `setuptools`)
  - `[project]` with explicit `dependencies` list
- lock file must be committed and tracked

Required layout:
- governed source files are `.py` only (no `.pyi` stub-only files in governed roots)
- `src/` layout required:
  - `src/blocks/<blockKey>/` with `__init__.py`
  - optional `src/blocks/_shared/` with `__init__.py`
- `src/blocks/<blockKey>/` is the only block-local user-authored governed root
- `src/blocks/_shared/` is the only shared user-authored governed root

Required Python profile:
- Python 3.12+
- strict `mypy` enabled for governed source roots:
  - `disallow_untyped_defs = true`
  - `ignore_missing_imports = false`
- no `TYPE_CHECKING`-guarded imports that bypass governance
- no use of `importlib.import_module`, `__import__`, or `importlib.util.spec_from_file_location`
  in governed roots

Unsupported project features in the first slice:
- `uv` workspaces or `poetry` monorepo layouts
- `src/` layout variations (flat layout out of scope)
- namespace packages without `__init__.py`
- `.pth` file manipulations or `sys.path` mutations in governed code
- custom import hooks or finders in governed roots
- Cython or C extensions in governed roots

## Governed Roots

User-authored governed roots:
- `src/blocks/<blockKey>/`
- `src/blocks/_shared/`

Generated BEAR-owned roots:
- `build/generated/bear/`
- `build/generated/bear/wiring/<blockKey>.wiring.json`

Root treatment:
- `src/blocks/<blockKey>/` is the block-local user-authored governed root
- `src/blocks/_shared/` is the only shared user-authored governed root
- `build/generated/bear/` is BEAR-owned, regenerated, and drift-checked
- tests are not governed in the first slice
- nongoverned roots include everything else:
  - `tests/`
  - `scripts/`
  - config files
  - other app code outside `src/blocks/`

Governed-root import policy:
- block code may import:
  - modules inside the same block root
  - modules inside `_shared`
  - BEAR-generated companion modules under `build/generated/bear/`
- `_shared` code may import:
  - modules inside `_shared`
  - BEAR-generated companion modules under `build/generated/bear/`
- block code may not import:
  - sibling block roots
  - nongoverned repo source roots
  - third-party packages (first-slice restriction; see dependency governance below)
  - dynamic import facilities (`importlib.import_module`, `__import__`, etc.)

## Containment Model

For Python, "containment" can only mean a limited static source-governance model.

It does not mean:
- runtime sandboxing
- runtime prevention of file/network/process access
- per-block package-manager isolation
- proof that no external capability can be reached through already-installed dependencies

It can mean:
- deterministic generated ownership
- deterministic local import-boundary enforcement inside governed roots
- deterministic repo-level dependency-governance signals
- deterministic blocking of direct covered power-surface imports

### 1. Import containment

Definition:
- governed imports must stay within the importing block's root, `_shared`, or BEAR-generated companion modules

Deterministic first-slice enforcement:
- scan `import X` and `from X import Y` statements in governed `.py` files
- resolve relative imports lexically against the module's package path
- fail when the resolved target escapes the allowed roots
- fail on direct third-party package imports from governed roots (first slice: no third-party usage)

Meaning of "third-party" here:
- any package that is not the current project's own modules or Python standard library
- bare package imports not under `src/blocks/` or `src/blocks/_shared/`

Product-honest consequence:
- first-slice governed Python blocks do not get direct third-party package imports
- BEAR governs local block composition, not arbitrary module-resolution graphs

### 2. Dependency governance

Definition:
- BEAR may surface repo/package dependency-graph change, but it does not provide block-level
  dependency allowlisting in Python v1

Deterministic first-slice contract:
- governance is repo-level only
- no Python equivalent of JVM `impl.allowedDeps`
- dependency additions or version changes are reviewable repo deltas, not per-block allowances

Future direction:
- if a real per-block dependency isolation mechanism emerges (for example virtual environment per block
  with `uv` tooling), a block-level allowlist analogue could be reconsidered

### 3. Runtime/external-power containment

Definition:
- BEAR may statically flag direct usage of selected Python power surfaces in governed roots, but it
  does not enforce runtime containment

Deterministic first-slice contract:
- direct covered built-in module imports in governed roots can be flagged
- direct dynamic import facilities in governed roots can be flagged
- indirect reach through existing third-party packages cannot be proven absent

### 4. Generated/owned artifact containment

Definition:
- same BEAR two-file ownership model as JVM

Deterministic first-slice contract:
- BEAR owns `build/generated/bear/`
- user-owned impl files remain preserved
- `check` treats generated drift as a BEAR-owned artifact mismatch, not a heuristic source check

## Dependency Governance Model

### Scope decision

Use repo/package-level dependency governance only.

Do not add in the first slice:
- block-level package allowlists
- a Python `impl.allowedDeps` analogue
- per-block virtual environment isolation
- generated package-manager patching

Why:
- `pip`/`uv`/`poetry` dependency resolution is package/project scoped, not block scoped
- BEAR has no honest Python equivalent to the Gradle containment init-script plus marker
  handshake used on JVM
- a fake block-level Python allowlist would overclaim enforcement BEAR cannot actually provide

### `pyproject.toml` and lock-file treatment in `pr-check`

Boundary-expanding:
- add/change under:
  - `[project] dependencies`
  - `[project.optional-dependencies]`
  - `[tool.uv.dev-dependencies]` or `[tool.poetry.dev-dependencies]`
- any lock-file change (`uv.lock` or `poetry.lock`)

Ordinary:
- dependency removal

Why lock-file changes are still boundary-relevant:
- they change the resolved dependency graph even when the top-level manifest looks unchanged

### `impl.allowedDeps`

Decision:
- `impl.allowedDeps` has no meaningful first-slice Python equivalent
- treat it as `NOT_SUPPORTED` for Python

Future direction:
- do not add a Python analogue unless BEAR later gains a real deterministic package-isolation mechanism

## Undeclared Reach and Boundary Bypass Coverage

The realistic first covered set is smaller than JVM and must be stated explicitly.

### Covered built-in power surfaces

Direct governed-root imports of these standard-library modules should be treated as undeclared reach:
- `socket`
- `http` / `http.client` / `http.server`
- `urllib` / `urllib.request`
- `subprocess`
- `os` (for `os.system`, `os.popen`, `os.exec*` patterns only; not for `os.path`)
- `multiprocessing`

Why these first:
- they correspond to obvious network/process power
- they are Python standard-library built-ins, so the scanner can classify them without
  dependency-graph guesswork
- `os` is special: BEAR should flag `os.system`/`os.popen`/`os.exec*` call patterns in governed
  roots, not the bare `import os` statement (which is used for `os.path` and other benign operations)

### Dynamic import and resolver-bypass coverage

Direct usage in governed roots should be treated as boundary bypass:
- `importlib.import_module(...)`
- `__import__(...)`
- `importlib.util.spec_from_file_location(...)`
- `sys.path` mutation in governed modules

### Third-party packages

First-slice rule:
- third-party package imports from governed roots are forbidden

This covers examples such as:
- HTTP client libraries (`requests`, `httpx`, `aiohttp`)
- DB clients (`sqlalchemy`, `asyncpg`, `psycopg`)
- messaging libraries (`pika`, `aiokafka`)
- SDKs

Why:
- if governed roots may import arbitrary packages, BEAR has no honest block-level
  dependency-containment story
- forbidding those imports is narrower but truthful

## Project Verification Model

### First-slice verification command

Use:
- `uv run mypy src/blocks/ --strict` (or `poetry run mypy src/blocks/ --strict`)

Do not use as the first-slice contract:
- `pytest` (test runner is arbitrary)
- arbitrary package scripts

Why:
- `mypy --strict` provides a deterministic, repeatable typecheck outcome
- it mirrors the `pnpm exec tsc --noEmit` approach used in the Node profile
- using `pytest` as the verification contract would require BEAR to depend on arbitrary
  test infrastructure choices

### Failure mapping

`exit 0`:
- typecheck completed successfully

`exit 4` (`project verification failure` semantics):
- `mypy` completed and reported type errors
- verification timed out after BEAR's target-owned timeout policy

`exit 64` (`unsupported target` semantics):
- required profile file is missing
- repo declares a Python shape outside the supported profile
- dynamic import facilities or namespace packages are present

`exit 74` (`tooling/environment failure` semantics):
- `uv` or `poetry` executable is missing
- `mypy` cannot be executed
- dependencies are not installed
- the verification command fails for environment/bootstrap reasons rather than reported type errors

## Capability Matrix

| Area | Status | Honest first-slice meaning |
| --- | --- | --- |
| Deterministic generated ownership under `build/generated/bear/` | `ENFORCED` | BEAR owns generated Python artifacts and drift-checks them. |
| Two-file preservation for user impl files | `ENFORCED` | BEAR creates skeletons once and preserves user-owned impl files. |
| Same-block relative import containment | `ENFORCED` | Governed code may stay inside its own block root. |
| `_shared` import containment | `ENFORCED` | Governed code may reach only `_shared`, not sibling blocks or nongoverned roots. |
| Imports from sibling blocks or nongoverned repo code | `ENFORCED` | Fail as boundary bypass. |
| Third-party package imports from governed roots | `ENFORCED` | Fail as boundary bypass; no first-slice third-party package usage inside governed roots. |
| Covered built-in imports (`socket`, `http`, `urllib`, `subprocess`, `multiprocessing`) | `ENFORCED` | Fail as undeclared reach. |
| `os.system`/`os.popen`/`os.exec*` patterns in governed roots | `PARTIAL` | BEAR can flag direct call-site patterns; not all `os` usage is banneable. |
| Repo-level dependency graph deltas (`pyproject.toml`, lock file) | `ENFORCED` | `pr-check` surfaces them as reviewable boundary expansion. |
| Dynamic import (`importlib.import_module`, `__import__`, `sys.path` mutation) in governed roots | `PARTIAL` | BEAR can block direct usage in governed files, but not all runtime indirection outside that scope. |
| Custom import hooks, finders, or loaders outside governed roots | `NOT_SUPPORTED` | No honest deterministic runtime guarantee. |
| Block-level dependency allowlist (`impl.allowedDeps` equivalent) | `NOT_SUPPORTED` | No real Python analogue in the first slice. |
| Workspace support (`uv` workspaces, poetry monorepo) | `NOT_SUPPORTED` | Single-package layout only in the first slice. |
| Namespace packages without `__init__.py` | `NOT_SUPPORTED` | They add resolution indirection BEAR should not pretend to govern. |
| Cython or C extension code in governed roots | `NOT_SUPPORTED` | Outside the static `.py`-only first slice. |
| Arbitrary test runner integration (`pytest`, `unittest`, etc.) | `NOT_SUPPORTED` | First-slice verification is typecheck only (`mypy --strict`). |
| Runtime sandboxing or runtime prevention of file/network/process access | `NOT_SUPPORTED` | BEAR is a static/deterministic governance layer, not a runtime policy engine. |

## Recommendation

### Should BEAR pursue Python now?

Recommendation:
- do not make Python the next execution feature
- keep it parked unless the team explicitly wants this narrow profile

Reason:
- the smallest honest Python slice is viable, but the language's dynamic import capabilities
  make it harder to provide the same level of containment confidence as JVM or even .NET
- Python's static analysis story (`mypy`) is strong enough for the verification contract but
  less reliable than Java's compile-time guarantees for import-boundary enforcement

### Smallest honest future slice

If Python is pursued later, ship only this:
- deterministic generation + drift
- governed roots under `src/blocks/<blockKey>/` and `_shared`
- static import containment for local roots only
- direct built-in reach blocking for the covered built-ins above
- repo-level dependency-governance signaling
- `uv run mypy src/blocks/ --strict` (or `poetry run mypy`) verification

### Explicit deferrals

Defer all of these:
- workspaces/monorepos
- namespace packages
- dynamic imports (`importlib`, `__import__`)
- third-party package imports inside governed roots
- Python dependency allowlists
- runtime loader/governance claims
- arbitrary test-runner support
- any IR expansion for Python dependency semantics

## External Grounding

The profile above is based on current primary docs:
- Python import system: [docs.python.org/3/reference/import.html](https://docs.python.org/3/reference/import.html)
- `pyproject.toml` spec (PEP 621): [peps.python.org/pep-0621](https://peps.python.org/pep-0621/)
- `uv` project management: [docs.astral.sh/uv/concepts/projects](https://docs.astral.sh/uv/concepts/projects/)
- mypy strict mode: [mypy.readthedocs.io/en/stable/command_line.html](https://mypy.readthedocs.io/en/stable/command_line.html)
- Python namespace packages (PEP 420): [peps.python.org/pep-0420](https://peps.python.org/pep-0420/)
