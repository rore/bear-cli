---
id: future-multi-target-spec-design
title: Multi-target expansion — spec-driven design
status: queued
priority: medium
commitment: uncommitted
milestone: Future
---

## Purpose

This document defines the spec-driven design for expanding BEAR beyond JVM to Node, Python, and
React. It is a concrete architectural spec, not a product recommendation document. For the
problems, solutions, and tradeoffs across the three targets, see the companion document:
`future-multi-target-expansion-plan.md`.

The design follows BEAR's existing architecture: deterministic CLI seam, explicit `Target`
interface, JVM-identical exit behavior, and no IR schema changes.

---

## Target + Analyzer Two-Seam Model

To keep core governance deterministic as targets grow, use two explicit seams:

1. `Target` (runtime/toolchain profile owner)
2. `AnalyzerProvider` (evidence extraction owner)

`Target` remains responsible for:
- detection and target ambiguity resolution
- generated artifact layout and compile/wiring lifecycle
- governed-root definition
- project verification command execution
- mapping findings into BEAR governance lanes and exit semantics

`AnalyzerProvider` is responsible for:
- import/dependency edge extraction
- symbol ownership and cross-boundary reference evidence
- locator extraction (`file/module/symbol/span`)
- optional call/reference graph evidence where supported

This split keeps BEAR as the policy engine while allowing simple analyzers first and richer
analyzers later.

### `AnalyzerProvider` interface draft

```
interface AnalyzerProvider {
  analyzerId(): AnalyzerId
  supports(targetId, governanceProfile): boolean
  collectEvidence(projectRoot, governedRoots, options): EvidenceBundle
}
```

```
interface EvidenceBundle {
  imports: List<ImportEdge>
  dependencies: List<DependencyEdge>
  ownership: List<OwnershipFact>
  references: List<ReferenceEdge>           // optional
  findings: List<AnalyzerFinding>           // analyzer-native observations
}
```

`Target` consumes `EvidenceBundle` and maps it deterministically into BEAR checks/findings.

---

## Anchoring Constraint: What Must Not Change

Before any per-target spec, the constraints that every implementation must satisfy:

1. **IR v1 is the boundary source of truth.** No `target:` field, no per-target IR schema
   additions. IR is parsed and normalized identically for all targets.

2. **Exit code registry is frozen.** All findings map to the existing numeric set:
   - `0` — clean
   - `2` — usage error
   - `4` — project verification failure
   - `5` — boundary-expanding delta (`pr-check`)
   - `6` — undeclared reach / boundary bypass
   - `64` — unsupported target or profile
   - `74` — tooling / environment failure

3. **`CODE/PATH/REMEDIATION` envelope is frozen.** Last three stderr lines always conform.

4. **JVM behavior must remain byte-identical.** Every non-JVM implementation arrives behind the
   existing `Target` seam without reopening core orchestration.

5. **No runtime policy engine additions.** BEAR is a static, deterministic governance layer.
   Runtime sandboxing, syscall filtering, and process isolation are permanently out of scope.

6. **Generated artifacts live under `build/generated/bear/`.** All targets use the same
   BEAR-owned root; user-owned impl files are never overwritten.

---

## The Target Seam Contract

The `Target` interface (already implemented and in production for `JvmTarget`) is the only
extension point for new language support. No core changes are required to add a new target.

```
interface Target {

  // Unique target identifier, one of: jvm | node | python | react
  targetId(): TargetId

  // Code generation for one block (bear compile)
  compileSingle(ir, projectRoot, blockKey, indexContext): CompileResult

  // Code generation for all blocks (bear compile --all)
  compileAll(blocksIndex, repoRoot, selection): CompileAllResult

  // Wiring-only generation for pr-check (no user-impl touches)
  generateWiringOnlySingle(ir, projectRoot, blockKey, indexContext): WiringResult
  generateWiringOnlyAll(...): WiringAllResult

  // Static checks to run in the bear check pipeline
  targetChecks(): List<TargetCheck>

  // Project verification runner (mypy, tsc, dotnet, gradle)
  runProjectVerification(projectRoot, mode): ProjectTestStatus

  // Governed source root paths for the project
  governedRoots(projectRoot, blockKey): List<Path>

}
```

`TargetDetector` contract:
```
interface TargetDetector {
  detect(projectRoot): DetectedTarget { targetId, confidence, reason }
}
```

**`DetectedTarget` result model:**
- `SUPPORTED` — detector has high confidence this project belongs to its target
- `UNSUPPORTED` — detector recognizes the ecosystem but the project shape is not supported
  (e.g. workspace layout, missing required config); produces `exit 64` with actionable remediation
- `NONE` — detector does not recognize this ecosystem at all; silent pass-through

Detectors **must not** silently "best guess" — resolution is deterministic:
- exactly one `SUPPORTED` result → use that target
- zero `SUPPORTED` results → fail `exit 64`, `CODE=TARGET_NOT_DETECTED`, with remediation
  listing recognized ecosystems and required project signals
- multiple `SUPPORTED` results without a `.bear/target.id` pin → fail `exit 64`,
  `CODE=TARGET_AMBIGUOUS`, with remediation instructing the user to add a pin file
- any `UNSUPPORTED` result blocks resolution even if another detector returns `SUPPORTED`,
  unless `.bear/target.id` pin explicitly overrides

**`.bear/target.id` pin semantics:**
- file content: exactly one of `jvm`, `node`, `python`, `react` (no whitespace, no comments)
- when present, pin overrides auto-detection entirely — no detectors run
- invalid or unrecognized pin content → fail `exit 2` using validation semantics
- pin file is optional; auto-detection is the default path for single-target repos

**Implementation prerequisite tasks (must complete before multi-target rollout):**
- [ ] Define `DetectedTarget` result model (`SUPPORTED` / `UNSUPPORTED` / `NONE`)
- [ ] Define `.bear/target.id` pin file semantics and validation
- [ ] Define ambiguity/fallback behavior (fail deterministically, never guess)
- [ ] Refactor `TargetRegistry.resolve()` to support multiple registered detectors
- [ ] Add detector tests for: single-target repo, mixed-signal repo, monorepo with pin,
      partial config, and missing config scenarios

---

## Target vs Governance Profile

Do not encode governance shape solely in target identity.

Model:
- `target`: runtime/toolchain and ecosystem (`jvm`, `node`, `python`, `react`)
- `profile`: governance contract for project shape

Initial profile examples:
- `target=node`, `profile=backend-service`
- `target=python`, `profile=service` (strict: no third-party imports in governed roots)
- `target=python`, `profile=service-relaxed` (pragmatic: third-party imports allowed, governed)
- `target=react`, `profile=feature-ui`

Benefits:
- keeps language/runtime concerns separate from governance intent
- allows multiple governance shapes within one language over time
- avoids forcing frontend feature-boundary policy into backend-oriented assumptions

---

## Canonical Locator Schema (Required Before Deeper Evidence)

Every finding must carry both:
1. stable human-readable `PATH=...`
2. structured `locator` object (internal canonical form)

Canonical locator shape:
```
locator:
  repository: <repoRootId>
  project: <projectOrPackageId>
  module: <repoRelativeFilePath>
  symbol:
    kind: function|class|method|component|module|unknown
    name: <symbolName|null>
  span:
    startLine: <int|null>
    startColumn: <int|null>
    endLine: <int|null>
    endColumn: <int|null>
```

Normalization rules:
- file paths are repo-relative and slash-normalized
- missing symbol/span information is explicit `null`, never inferred silently
- locator ordering in output remains deterministic

---

## Node / TypeScript Target Spec

### Detection

`NodeTargetDetector` returns `HIGH` confidence when all of the following are present at
`projectRoot`:
- `package.json` with `"type": "module"` and `"packageManager": "pnpm@..."` field
- `pnpm-lock.yaml`
- `tsconfig.json`

Returns `NONE` if any required file is absent.
Returns `UNSUPPORTED` (→ `exit 64`) if `pnpm-workspace.yaml` is present (workspace layout
excluded from first slice).

### Generated artifact layout

BEAR-owned (regenerated, drift-checked):
```
build/generated/bear/
  wiring/<blockKey>.wiring.json
  types/<blockKey>/
    <BlockName>Ports.ts          # port type declarations from effects.allow
    <BlockName>Logic.ts          # logic interface from contract inputs/outputs
    <BlockName>Wrapper.ts        # wrapper shell with one wiring factory
```

User-owned (created once, never overwritten):
```
src/blocks/<blockKey>/
  impl/<BlockName>Impl.ts        # implementation skeleton (created once)
```

### Governed roots

```
src/blocks/<blockKey>/**/*.ts    # block-local user-authored governed root
src/blocks/_shared/**/*.ts       # optional shared user-authored governed root
```

Not governed:
```
test/**
src/**/*.test.ts
scripts/**
config files
src/ outside src/blocks/
```

### Import containment rules

Enforced by `NodeImportContainmentScanner` (implements `TargetCheck`):
- scan literal `import` and `export ... from` specifiers in governed `.ts` files
- resolve relative specifiers lexically against the importing file
- **fail** (`exit 6`, `CODE=BOUNDARY_BYPASS`) when resolved target escapes the block root or
  reaches a sibling block root or nongoverned repo source
- **fail** on non-relative user-authored imports from governed roots:
  - bare package specifiers
  - `#` package-imports aliases
  - URL-like specifiers
  - package self-name imports

### Undeclared reach rules

Enforced by `NodeUndeclaredReachScanner` (implements `TargetCheck`):
- scan `import` specifiers in governed roots for direct usage of covered built-ins
- covered set (first slice):
  - `node:http`, `http`
  - `node:https`, `https`
  - `node:net`, `net`
  - `node:child_process`, `child_process`
  - `node:fs`, `fs`, `node:fs/promises`, `fs/promises`
- **fail** (`exit 6`, `CODE=UNDECLARED_REACH`)

Dynamic import detection:
- direct `import(...)` expression in governed roots → `exit 6`, `CODE=BOUNDARY_BYPASS`, `PARTIAL`
- direct `require(...)` in governed roots → same
- direct `module.createRequire(...)` in governed roots → same

### Dependency governance (pr-check)

`NodePrCheckContributor`:
- detect `package.json` `dependencies`/`devDependencies`/`peerDependencies`/`optionalDependencies`
  additions or version changes between base and head → classify as `BOUNDARY_EXPANDING` (`exit 5`)
- detect any `pnpm-lock.yaml` change → classify as `BOUNDARY_EXPANDING`
- dependency removal → `ORDINARY`

### `impl.allowedDeps` behavior

`impl.allowedDeps` in any selected block IR → `exit 64`, `CODE=UNSUPPORTED_TARGET`:
```
REMEDIATION=Remove impl.allowedDeps for node target, or switch to JVM target.
```

### Project verification

`NodeProjectTestRunner`:
- command: `pnpm exec tsc --noEmit -p tsconfig.json`
- timeout: target-owned timeout policy (mirror JVM runner contract)
- exit mapping:
  - `tsc` exit `0` → `ProjectTestStatus.PASSED` → `exit 0`
  - `tsc` exits non-zero with type errors → `ProjectTestStatus.FAILED` → `exit 4`
  - `pnpm` not found → `ProjectTestStatus.TOOL_MISSING` → `exit 74`
  - timeout → `ProjectTestStatus.TIMEOUT` → `exit 4`

---

## Python Target Spec

### Detection

`PythonTargetDetector` returns `HIGH` confidence when all of the following are present at
`projectRoot`:
- `pyproject.toml` with a `[build-system]` table using a PEP 517-compatible backend
- `uv.lock` or `poetry.lock`
- `mypy.ini` or `[tool.mypy]` section in `pyproject.toml`
- `src/blocks/` directory exists

Returns `NONE` if any required file is absent.
Returns `UNSUPPORTED` (→ `exit 64`) if:
- `uv` workspaces or `poetry` monorepo layout is detected
- flat layout (no `src/` directory) is used
- namespace packages (missing `__init__.py`) are detected in governed roots
- `pnpm-workspace.yaml` or `package.json` are also present at the same root (ambiguity case →
  `TARGET_AMBIGUOUS` instead)

### Generated artifact layout

BEAR-owned (regenerated, drift-checked):
```
build/generated/bear/
  wiring/<blockKey>.wiring.json
  <blockKey>/
    <block_name>_ports.py        # port type stubs from effects.allow
    <block_name>_logic.py        # logic protocol from contract inputs/outputs
    <block_name>_wrapper.py      # wrapper shell with one wiring factory
```

User-owned (created once, never overwritten):
```
src/blocks/<blockKey>/
  impl/<block_name>_impl.py      # implementation skeleton (created once)
  __init__.py                    # required for package resolution
```

### Governed roots

```
src/blocks/<blockKey>/           # block-local user-authored governed root
  __init__.py required
src/blocks/_shared/              # optional shared user-authored governed root
  __init__.py required
```

Not governed:
```
tests/
scripts/
config files
src/ outside src/blocks/
```

### Import containment rules

Python import containment behavior depends on the active governance profile:

#### `python/service` profile (strict, default)

Enforced by `PythonImportContainmentScanner` (implements `TargetCheck`):
- scan `import X` and `from X import Y` statements in governed `.py` files only (no `.pyi`)
- resolve relative imports lexically against the module's package path
- **fail** (`exit 6`, `CODE=BOUNDARY_BYPASS`) when resolved target escapes block root, reaches
  sibling block root, or reaches nongoverned repo source
- **fail** on third-party package imports from governed roots (bare package imports not under
  `src/blocks/` or standard library)
- **fail** on direct dynamic import facilities:
  - `importlib.import_module(...)` → `exit 6`, `CODE=BOUNDARY_BYPASS`
  - `__import__(...)` → same
  - `importlib.util.spec_from_file_location(...)` → same
  - `sys.path` mutation → same (`PARTIAL`)

#### `python/service-relaxed` profile (pragmatic, opt-in)

Same scanner with relaxed third-party rule:
- **same** block-boundary enforcement (no sibling blocks, no nongoverned source)
- **allow** third-party package imports from governed roots — the import is permitted but
  the package is tracked; new packages appearing in `pr-check` are `BOUNDARY_EXPANDING`
- **same** dynamic import facility blocking
- the `site-packages` power-surface scan becomes the primary signal for capability governance
  in this profile — every allowed package's power-surface exposure is surfaced advisory

### Undeclared reach rules

Enforced by `PythonUndeclaredReachScanner` (implements `TargetCheck`):
- scan `import` statements in governed roots for covered standard-library modules
- covered set (first slice):
  - `socket`
  - `http`, `http.client`, `http.server`
  - `urllib`, `urllib.request`
  - `subprocess`
  - `multiprocessing`
- **fail** (`exit 6`, `CODE=UNDECLARED_REACH`)

`os` special case:
- `import os` alone is **not** flagged (benign for `os.path` usage)
- call-site pattern scanner: flag `os.system(`, `os.popen(`, `os.exec*(` in governed `.py`
  source text → `exit 6`, `CODE=UNDECLARED_REACH`, `PARTIAL` status

### `site-packages` scan

`PythonSitePackagesPowerSurfaceScanner`:
- triggers in `pr-check` when `uv.lock` or `poetry.lock` changes between base and head
- locates the environment's `site-packages` path using the environment's configured
  `purelib`/`platlib` paths (handles both POSIX `.venv/lib/pythonX.Y/site-packages/` and
  Windows `.venv/Lib/site-packages/` layouts)
- for each installed package with discoverable `.py` source: apply the same covered power-surface
  rules as governed-root scanning
- transitive scan up to depth 2 (configurable via `.bear/python-scan.yaml`)
- output format:
  ```
  DEPENDENCY_POWER_SURFACE_REPORT
  package: requests@2.31.0 → reaches: [socket, http.client]
  package: aiohttp@3.9.1 → reaches: [socket, http.client, http.server]
  package: cryptography@41.0.0 → NOT_COVERABLE (native extension: .so only)
  ```
- packages newly entering the report since the previous lock-file snapshot require explicit
  team acknowledgement in PR review — advisory, not build-blocking
- packages with `.pyd`/`.so`-only implementation are listed as `NOT_COVERABLE`

### Dependency governance (pr-check)

`PythonPrCheckContributor`:
- detect `pyproject.toml` changes under:
  - `[project] dependencies`
  - `[project.optional-dependencies]`
  - `[tool.uv.dev-dependencies]` or `[tool.poetry.dev-dependencies]`
  → classify as `BOUNDARY_EXPANDING` (`exit 5`)
- detect any `uv.lock` or `poetry.lock` change → classify as `BOUNDARY_EXPANDING`
- dependency removal → `ORDINARY`

### `impl.allowedDeps` behavior

`impl.allowedDeps` in any selected block IR → `exit 64`, `CODE=UNSUPPORTED_TARGET`:
```
REMEDIATION=Remove impl.allowedDeps for python target; block-level dependency allowlisting
            has no Python equivalent in the first slice. Use pyproject.toml for repo-level
            dependency governance.
```

### Project verification

`PythonProjectTestRunner`:
- command: `uv run mypy src/blocks/ --strict` (falls back to `poetry run mypy src/blocks/ --strict`
  if `uv` not found but `poetry` is present)
- timeout: target-owned timeout policy (mirror JVM runner contract)
- exit mapping:
  - `mypy` exit `0` → `ProjectTestStatus.PASSED` → `exit 0`
  - `mypy` exits with type errors → `ProjectTestStatus.FAILED` → `exit 4`
  - `uv`/`poetry` not found → `ProjectTestStatus.TOOL_MISSING` → `exit 74`
  - `mypy` not installed → `ProjectTestStatus.TOOL_MISSING` → `exit 74`
  - timeout → `ProjectTestStatus.TIMEOUT` → `exit 4`

### Scope guardrail (do not overclaim)

Python first slice is intentionally limited to:
- governed-root imports
- dangerous power-surface detection
- lock-file and dependency-delta governance
- advisory installed-package scan
- explicit opaque native-extension gap

Out of scope in first slice:
- deep runtime behavior proofs
- complete transitive semantic reach proofs
- reliable analysis of native or dynamically generated behavior

---

## React / TypeScript Frontend Target Spec

**Prerequisite**: this target requires an explicit product decision to extend BEAR's mission to
frontend feature-module governance. Do not implement until that decision is made and at least one
non-JVM backend target (Node or .NET) is proven.

### Detection

`ReactTargetDetector` returns `HIGH` confidence when all of the following are present:
- `package.json` with `"type": "module"`, pinned `pnpm` packageManager, and `react`/`react-dom` in
  `dependencies`
- `pnpm-lock.yaml`
- `vite.config.ts` (Vite required; Next.js/Remix → `UNSUPPORTED`)
- `tsconfig.json`

Returns `UNSUPPORTED` (→ `exit 64`) if:
- `next.config.*`, `remix.config.*`, or Astro config files are present
- `pnpm-workspace.yaml` is present
- `compilerOptions.paths` or `compilerOptions.baseUrl` are set

### Generated artifact layout

BEAR-owned (regenerated, drift-checked):
```
build/generated/bear/
  wiring/<blockKey>.wiring.json
  types/<blockKey>/
    <BlockName>FeaturePorts.ts   # API service interface declarations from effects.allow
    <BlockName>FeatureLogic.ts   # feature logic interface (hooks/components entry contract)
    <BlockName>FeatureWrapper.ts # wiring shell
```

User-owned (created once, never overwritten):
```
src/features/<blockKey>/
  impl/<BlockName>FeatureImpl.tsx  # implementation skeleton (created once)
  index.ts                         # public feature entry point
```

### Governed roots

```
src/features/<blockKey>/         # block-local user-authored governed root
src/shared/                      # optional shared user-authored governed root
```

Not governed:
```
src/app/ or src/pages/           # routing layer
src/main.tsx or src/index.tsx    # entry point
test/ or *.test.ts               # test files
public/
config files
```

### Import containment rules

Enforced by `ReactImportContainmentScanner` (implements `TargetCheck`):
- scan `import` and `export ... from` specifiers in governed `.ts`/`.tsx` files
- resolve relative specifiers lexically
- **fail** (`exit 6`, `CODE=BOUNDARY_BYPASS`) when resolved target reaches a sibling feature root
  or nongoverned source
- allowed from governed roots: `react`, `react-dom` only (no other bare package imports)
- path aliases (`compilerOptions.paths`, `baseUrl`, Vite `resolve.alias`) → `exit 64`,
  `CODE=UNSUPPORTED_TARGET` (excluded by the required profile)

### API boundary signaling (frontend-specific)

Enforced by `ReactApiBoundaryScanner` (implements `TargetCheck`, `PARTIAL` status):
- scan governed `.tsx` files (component files only; not `*Service.ts` or `*Api.ts` within the
  same block root) for direct `fetch(` or `new XMLHttpRequest(` call patterns
- **flag** (`exit 6`, `CODE=BOUNDARY_BYPASS`) — advisory: encourages routing API calls through
  declared service files within each feature block, mapping to BEAR's "port" concept
- status is `PARTIAL`: only the most direct call patterns are detected

React emphasis:
- primary enforcement units are module/feature ownership boundaries and service access boundaries
- function/component-level evidence is supplementary and should not become the primary contract

### Dependency governance (pr-check)

`ReactPrCheckContributor`:
- same as Node: `package.json` dependency additions/changes and `pnpm-lock.yaml` changes →
  `BOUNDARY_EXPANDING` (`exit 5`)

### `impl.allowedDeps` behavior

Same as Node: `impl.allowedDeps` → `exit 64`, `CODE=UNSUPPORTED_TARGET`.

### Project verification

`ReactProjectTestRunner`:
- command: `pnpm exec tsc --noEmit -p tsconfig.json`
- same exit mapping as Node runner

---

## Cross-Target Implementation Phases

### Phase ordering (recommended)

Architecture prerequisites (must land before any non-JVM target ships):

```
Phase 0:  Contract freeze — regression harness for JVM byte-identical behavior (already done)
Phase 1:  TargetDetector + .bear/target.id prerequisite epic
            — define DetectedTarget result model (SUPPORTED/UNSUPPORTED/NONE)
            — define .bear/target.id pin semantics and validation
            — define ambiguity/fallback behavior (deterministic, never guess)
            — refactor TargetRegistry.resolve() for multiple registered detectors
            — add detector tests (single-target, mixed-signal, monorepo, partial config)
Phase 2:  Finalize canonical locator schema (PATH + structured locator)
Phase 3:  Finalize target/profile separation contract
Phase 4:  Add AnalyzerProvider SPI with simple/native analyzers
```

First target implementation (always Node):

```
Phase 5:  NodeTarget — scan-only (import containment + drift)              [see full spec above]
Phase 6:  NodeTarget — undeclared reach (covered Node built-ins)
Phase 7:  NodeTarget — dependency governance (pr-check)
Phase 8:  NodeTarget — project verification (pnpm exec tsc --noEmit)
Phase 9:  NodeTarget — agent docs overlay
```

Second and third target implementations (order is strategy-dependent — see note below):

```
Phase 10: Second target — scan-only (import containment + drift)
Phase 11: Second target — undeclared reach + dynamic import/call blocking
Phase 12: Second target — dependency governance (pr-check)
Phase 13: Second target — project verification
Phase 14: Second target — agent docs overlay
Phase 15–19: Third target — same phase structure
```

React (last, requires explicit product decision):

```
Phase 20: ReactTarget — feature-boundary profile first; only after explicit product decision and
          at least one non-JVM backend target is proven
```

#### Post-Node ordering: technical readiness vs market priority

The second non-JVM target after Node should be chosen based on both technical readiness and
product strategy, not purely on static-analysis strength:

**Technical readiness order** (strongest containment confidence first):
```
.NET → Python → React
```
.NET has the strongest static project/package model and no dynamic-import escape route. On
pure technical grounds, .NET ships a more honest containment story than Python.

**Market priority order** (highest commercial/user demand first):
```
Python → .NET → React
```
Python matters more commercially for AI-assisted development and LLM-heavy repos, which are
BEAR's primary near-term adoption surface. Shipping Python second — even with its weaker
containment guarantees — may deliver more product value earlier.

**Recommendation:** choose the second target based on product strategy. Both orders are
architecturally valid because the prerequisite seams (detector, locator, profile, analyzer)
are target-agnostic. The spec for each target is defined above regardless of execution order.

### What stays the same across all phases

- `TargetRegistry.resolve(projectRoot)` — one dispatch point per command, unchanged
- `TargetDetector` result model — `SUPPORTED`/`NONE`/`UNSUPPORTED`, unchanged
- IR normalization and validation — target-agnostic, unchanged
- Exit codes — frozen registry, unchanged
- `CODE/PATH/REMEDIATION` envelope — frozen, unchanged
- BEAR-owned generated root (`build/generated/bear/`) — consistent across all targets
- User-owned impl preservation — consistent across all targets

### Per-target feature acceptance test template

Each target implementation must provide one golden fixture test that:
1. Compiles a governed block from a minimal fixture repo
2. Passes `check` (clean: drift `PASS`, all target checks `PASS`)
3. Fails `check` deterministically when one import escapes the block root
4. Fails `check` deterministically when one covered power surface is imported
5. Returns `exit 5` in `pr-check` when a dependency is added to the manifest
6. Returns `exit 0` in `pr-check` when only dependency removals are present
7. Preserves user-owned impl files across a `compile` re-run

---

## Agent Workflow Integration (All Targets)

Since BEAR is a static, commit-time governance layer, the agent/sandbox workflow is consistent
across all targets and does not require any per-target changes to CLI orchestration:

```
1. Agent commits changes to a sandbox branch
2. bear check --project <repoRoot>
   → BEAR scans committed source files statically
   → No running server, no interpreter, no build daemon required
   → Structured findings surfaced: import violations, power-surface hits, generated drift
3. Agent reads findings and fixes violations
4. bear pr-check --project <repoRoot> --base <base-ref>
   → BEAR reviews IR delta for boundary-expanding changes
   → For Python: triggers site-packages scan if lock file changed
   → For Node/React: reviews pnpm-lock.yaml delta
   → Exit 5 if boundary-expanding, exit 0 if clean
5. Human reviewer sees a structured, machine-surfaced boundary signal before merge
```

The agent does not need to know which target is in use. The CLI resolves the target, runs the
correct checks, and surfaces the same envelope format regardless of whether the repo is JVM,
Node, Python, or React. This is the key product property: **the governance contract is
target-agnostic even though the enforcement is target-specific**.

---

## Advisory `bear ir-suggest` (Future, Non-Authoritative)

A future `bear ir-suggest` command may be useful for multi-target bootstrap:
- emits draft IR and boundary suggestions with confidence and supporting evidence
- never promotes inferred structure to authoritative governance automatically
- requires explicit human or agent acceptance before IR becomes canonical

This preserves BEAR's declared-governance model while still helping bootstrap new targets.

---

## Spec Review Checklist

Before any new target implementation begins, confirm:
- [ ] A dedicated phased CLI implementation plan exists (like `future-target-adaptable-cli-node.md`)
- [ ] The containment profile document is current (like `future-python-containment-profile.md`)
- [ ] The target's `TargetDetector` conflict rules with all existing detectors are defined
- [ ] The generated artifact layout is specified and does not overlap with JVM or other targets
- [ ] The covered power-surface set is explicitly bounded and documented
- [ ] At least one per-target acceptance fixture test is in the spec
- [ ] The `impl.allowedDeps` behavior is explicitly specified (`NOT_SUPPORTED` or analogue)
- [ ] Project verification command, timeout policy, and failure mapping are fully specified
- [ ] Agent/branch workflow section is present in the profile doc
- [ ] `.bear/target.id` pin value for the new target is registered in the target registry
- [ ] Target/profile contract is defined for the target
- [ ] Canonical locator mapping exists for all target findings
- [ ] AnalyzerProvider implementation strategy is defined (simple/native first)
