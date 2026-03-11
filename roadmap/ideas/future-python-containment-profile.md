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

## Concentric Profile Model

Python governance works in two concentric rings, each an independent governance profile
sharing the same `target=python` detection and generated-artifact layout:

### Inner profile: `python/service` (strict, default)

This is the narrow first-slice profile described throughout this document.

Key property:
- **third-party package imports from governed roots are forbidden**
- governed blocks may only import from same-block, `_shared`, BEAR-generated companions,
  and Python standard library (excluding covered power surfaces)

When to use:
- agent-assisted block development where block isolation is the primary goal
- repos where blocks should never directly depend on external packages

Containment confidence: highest available for Python — governed code cannot silently acquire
new capability surfaces through package imports.

### Outer profile: `python/service-relaxed` (pragmatic, opt-in)

Key property:
- **third-party package imports from governed roots are allowed but governed**
- governed blocks may import declared third-party packages; undeclared or newly added packages
  in `pr-check` still trigger `BOUNDARY_EXPANDING` classification
- the `site-packages` power-surface scan becomes the primary mechanism for surfacing capability
  exposure through allowed packages
- import containment still enforces same-block/`_shared` boundaries (no sibling block imports)
- covered power-surface detection and dynamic import blocking remain enforced

When to use:
- existing Python services with third-party dependencies in block code
- repos where the team accepts the tradeoff of allowing package imports in exchange for
  `site-packages` advisory scanning and lock-file governance

Containment confidence: lower than `python/service` — governed code can acquire new capabilities
through allowed packages, but those capabilities are surfaced (not silently hidden) via the
`site-packages` scan and dependency governance.

### Capability comparison between profiles

| Capability | `python/service` | `python/service-relaxed` |
| --- | --- | --- |
| Same-block import containment | `ENFORCED` | `ENFORCED` |
| `_shared` import containment | `ENFORCED` | `ENFORCED` |
| Sibling block imports | `ENFORCED` (blocked) | `ENFORCED` (blocked) |
| Third-party package imports | `ENFORCED` (blocked) | `GOVERNED` (allowed, delta-reviewed) |
| Covered power-surface detection | `ENFORCED` | `ENFORCED` |
| `site-packages` power scan | Advisory | **Primary containment mechanism** |
| Dynamic import blocking | `PARTIAL` | `PARTIAL` |
| Lock-file governance | `ENFORCED` | `ENFORCED` |
| Block-level dependency allowlist | `NOT_SUPPORTED` | `NOT_SUPPORTED` |

### Profile selection

- detection is always `target=python` (same `PythonTargetDetector`)
- profile is selected via `.bear/profile.id` or auto-derived from project shape
- `python/service` is the default if no profile is specified
- `python/service-relaxed` requires explicit opt-in

### Why two profiles instead of one

A single Python profile forces a binary choice between "honest but impractical for existing
repos" (strict) and "practical but weakened containment" (relaxed). Concentric profiles let
BEAR offer both honestly:
- strict profile for new greenfield blocks with maximum containment
- relaxed profile for existing services being brought under governance incrementally

Both profiles share the same detection, generated artifacts, verification command, and
`site-packages` scan infrastructure. The only difference is whether third-party imports from
governed roots are blocked or governed.

---

## Supported Python Profile

Profile name:
- `python-pyproject-single-package-v1` (maps to `python/service` inner profile)

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
- indirect reach through existing third-party packages cannot be proven absent at runtime,
  but can be partially surfaced via static `site-packages` inspection (see "Installed-package
  power-surface exposure" below)

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

## Analysis Strategy

### AST-first requirement

All Python static analysis in BEAR must use Python `ast` module parsing, not regex or text
matching, as the primary enforcement mechanism.

Use AST for:
- import extraction (`import X`, `from X import Y`, `from X import Y as Z`)
- alias tracking (renaming via `as` must not bypass governance)
- detection of `importlib.import_module(...)`, `__import__(...)`,
  `importlib.util.spec_from_file_location(...)`
- detection of `eval(...)`, `exec(...)`, `compile(...)` calls
- detection of statically visible dynamic-import patterns
- `os.system`/`os.popen`/`os.exec*` call-site pattern detection

Regex/text heuristics may exist only as fallback advisory signals for patterns the AST
cannot capture (for example, multi-line string-based `exec` usage). They must not be the
primary enforcement mechanism.

Why:
- AST parsing handles aliasing, multi-line imports, and `from X import Y as Z` correctly
- regex-based import scanning is fragile and easy to bypass with formatting changes
- Python's `ast` module is stable, standard-library, and zero-dependency

---

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

### Dynamic execution escape hatches

Direct usage of these built-in functions in governed roots should be treated as boundary bypass:
- `eval(...)` — arbitrary code execution from string
- `exec(...)` — arbitrary statement execution from string
- `compile(...)` — dynamic code compilation

Treatment:
- findings map to `CODE=BOUNDARY_BYPASS`, same lane as dynamic import facilities
- status is `PARTIAL`: BEAR detects direct call-site patterns via AST but cannot trace
  string arguments or runtime-constructed code
- these are escape hatches that can bypass any static governance BEAR provides

### Dynamic import and resolver-bypass coverage

Direct usage in governed roots should be treated as boundary bypass:
- `importlib.import_module(...)`
- `__import__(...)`
- `importlib.util.spec_from_file_location(...)`
- `sys.path` mutation in governed modules

### Installed-package power-surface exposure

Gap:
- third-party packages installed in the project's virtual environment may themselves import covered
  power surfaces, giving governed code indirect reach that BEAR's source-file scan cannot see

Static `site-packages` scan (partial coverage, honest bounds):
- when a virtual environment is present (for example `.venv/lib/pythonX.Y/site-packages/` on
  POSIX or `.venv/Lib/site-packages/` on Windows), BEAR locates the environment's configured
  `purelib`/`platlib` `site-packages` paths rather than assuming a single hard-coded layout, and
  runs a one-pass static scan of the `.py` files belonging to each installed package
- the scan applies the same covered power-surface rules used for governed roots:
  `socket`, `http`, `urllib`, `subprocess`, `multiprocessing`, `os.system`/`os.popen` patterns
- the output is a **dependency power-surface report**: a list of installed packages that directly
  or transitively import covered power surfaces in their pure-Python source
- this report is surfaced in `pr-check` when the lock file changes; it does not block the build but
  requires explicit team acknowledgement for packages newly entering the report
- packages whose entire implementation is in native extensions (`.pyd`/`.so` only; no `.py` source)
  are explicitly listed as `NOT_COVERABLE` in the report and treated as a team-accepted opaque gap

Limitations of the approach (must be stated honestly):
- native extension code cannot be statically scanned; any power reach inside `.pyd`/`.so` files
  is outside BEAR's coverage
- the scan reflects the state of the virtual environment at scan time; a stale or absent `.venv`
  produces no coverage
- transitive reach chains through multiple packages can be long; BEAR should surface direct
  first-hop exposure and cap depth at a configurable limit (default: 2 hops) to stay fast and
  transparent about scan depth limitations
- this is a signal, not a guarantee: a package can conditionally import power surfaces in code
  paths BEAR's static analysis treats as reachable even when runtime conditions would prevent them

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

## Branch and Agent Workflow

### BEAR as a commit-time boundary gate

BEAR's Python containment story is entirely pre-runtime:
- import boundary check: governs what the authored `.py` files may import
- `site-packages` scan: surfaces power-surface exposure in installed dependencies
- lock-file delta review in `pr-check`: dependency changes are boundary-expanding events

BEAR never claims to prevent execution of disallowed code at runtime. It claims to detect and
surface violations before they reach a merge, which is a weaker but honest and achievable guarantee.

### Sandbox/branch integration for agent-driven development

An AI agent working on code in a sandbox branch can run `bear check` against committed changes
without needing a running application:
- BEAR's static scanner operates purely on source files and generated artifacts
- no running server, no live process, no interpreter execution is required

The branch integration model:
1. Agent commits changes to a sandbox branch
2. `bear check` runs as a CI step (or as a local pre-push step) on the branch
3. BEAR surfaces boundary escapes, undeclared reach, and import violations as structured findings
4. Agent reads findings and fixes violations before proposing a merge
5. `bear pr-check` runs on the PR diff to surface dependency-graph changes that are boundary-expanding
6. Lock-file changes that introduce packages with covered power-surface exposure trigger the
   `site-packages` scan and produce a dependency power-surface report for team review

This model works today for JVM and transfers cleanly to Python: BEAR checks static artifacts, never
runtime state. The key property is that BEAR is a **commit-time gate**, not a runtime monitor —
it operates on the same evidence an agent can produce (committed source files and a lock file).

### What the agent can rely on

Findings BEAR can provide deterministically while the agent works:
- import-boundary violations in governed `.py` files (same-block, `_shared`, BEAR-generated only)
- direct covered power-surface imports (`socket`, `http`, `subprocess`, etc.) in governed roots
- direct dynamic import facility usage (`importlib.import_module`, `__import__`, `sys.path`
  mutation) in governed roots
- drift between committed generated artifacts under `build/generated/bear/` and expected output
- lock-file and `pyproject.toml` dependency changes classified as boundary-expanding

Findings BEAR cannot provide (agent must accept these as open gaps):
- runtime behavior of installed packages (native extension reach, conditional imports)
- violations introduced purely through packages already installed but not changed in the PR

### What "beyond runtime" means for Python

Runtime sandboxing (process isolation, syscall filtering, network namespace isolation) is out of
scope for BEAR. The honest "beyond runtime" contribution is:
- a static pre-merge gate that the agent can trigger deterministically
- a `site-packages` scan that surfaces power-surface exposure in installed pure-Python packages
  at dependency-change time
- a structured findings model that an agent can act on before a human reviewer sees the PR

This approach makes Python containment usable in an agent workflow without overclaiming runtime
enforcement guarantees the language and tooling cannot provide.

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
| `os.system`/`os.popen`/`os.exec*` patterns in governed roots | `PARTIAL` | BEAR can flag direct call-site patterns; not all `os` usage is bannable. |
| Repo-level dependency graph deltas (`pyproject.toml`, lock file) | `ENFORCED` | `pr-check` surfaces them as reviewable boundary expansion. |
| Dynamic import (`importlib.import_module`, `__import__`, `sys.path` mutation) in governed roots | `PARTIAL` | BEAR can block direct usage in governed files, but not all runtime indirection outside that scope. |
| Dynamic execution escape hatches (`eval`, `exec`, `compile`) in governed roots | `PARTIAL` | BEAR can flag direct call-site patterns via AST; cannot trace runtime-constructed code strings. |
| Installed-package power-surface scan (`site-packages` pure-Python source scan) | `PARTIAL` | BEAR surfaces which installed packages directly or transitively (up to depth limit) reach covered power surfaces; native extension files are `NOT_COVERABLE`. |
| Commit-time boundary gate for branch/agent workflows (`bear check` on committed source) | `ENFORCED` | BEAR runs statically on committed files; no runtime required; structured findings surfaced before merge. |
| Custom import hooks, finders, or loaders outside governed roots | `NOT_SUPPORTED` | No honest deterministic runtime guarantee. |
| Block-level dependency allowlist (`impl.allowedDeps` equivalent) | `NOT_SUPPORTED` | No real Python analogue in the first slice. |
| Workspace support (`uv` workspaces, poetry monorepo) | `NOT_SUPPORTED` | Single-package layout only in the first slice. |
| Namespace packages without `__init__.py` | `NOT_SUPPORTED` | They add resolution indirection BEAR should not pretend to govern. |
| Cython or C extension code in governed roots | `NOT_SUPPORTED` | Outside the static `.py`-only first slice. |
| Arbitrary test runner integration (`pytest`, `unittest`, etc.) | `NOT_SUPPORTED` | First-slice verification is typecheck only (`mypy --strict`). |
| Runtime sandboxing or runtime prevention of file/network/process access | `NOT_SUPPORTED` | BEAR is a static/deterministic governance layer, not a runtime policy engine. |
| Native extension reach inside installed `.pyd`/`.so` files | `NOT_COVERABLE` | Binary code cannot be statically scanned; team must accept this as an open gap. |

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
- the `site-packages` scan and commit-time agent workflow gate materially close the gap on
  the "installed packages BEAR cannot inspect at runtime" problem — but the native extension
  gap remains an accepted open hole that the team must acknowledge

### Smallest honest future slice

If Python is pursued later, ship the inner `python/service` profile first:
- deterministic generation + drift
- governed roots under `src/blocks/<blockKey>/` and `_shared`
- static import containment for local roots only (no third-party imports)
- direct built-in reach blocking for the covered built-ins above
- repo-level dependency-governance signaling
- `uv run mypy src/blocks/ --strict` (or `poetry run mypy`) verification
- `site-packages` pure-Python power-surface scan triggered on lock-file changes
- `bear check` as a commit-time static gate for branch/agent workflows

Then, once the inner profile is proven, offer `python/service-relaxed` as an opt-in
outer profile for existing repos that need third-party package imports in governed roots.
The relaxed profile reuses all infrastructure from the strict profile; the only change is
allowing (and governing) third-party imports instead of blocking them.

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
