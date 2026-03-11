---
id: future-multi-target-expansion-plan
title: Multi-target expansion plan — Node, Python, React
status: queued
priority: medium
commitment: uncommitted
milestone: Future
---

## Purpose

This document synthesizes the individual language containment profiles into one unified expansion
plan for the three most requested non-JVM targets. It covers the concrete problem each target
solves, the honest solution each enables, the tradeoffs involved, and the recommended priority
order for implementation. It is the companion to `future-multi-target-spec-design.md`, which
documents the concrete spec-driven architecture for the implementation phases.

## Relation to Existing Profile Documents

The individual containment profiles already exist as parked roadmap items:
- `future-node-containment-profile.md` — what Node/TypeScript containment looks like
- `future-target-adaptable-cli-node.md` — the phased Node CLI implementation plan
- `future-python-containment-profile.md` — what Python containment looks like, including the
  `site-packages` scan and branch/agent workflow gate added in the March 2026 session
- `future-react-containment-profile.md` — what React/TypeScript frontend containment looks like
- `future-dotnet-containment-profile.md` + `future-target-adaptable-cli-dotnet.md` — .NET profile
  and initial CLI plan

This document does **not** replace those profile files. It provides the cross-cutting view that
those individual files cannot: comparative tradeoffs, shared architecture decisions, and a
single recommended expansion order.

## Session Continuity Note

The Python profile was materially expanded in the March 2026 session to address two previously
open gaps: (1) the "installed packages BEAR cannot inspect at runtime" problem, addressed via a
static `site-packages` scan; (2) the "BEAR as a commit-time gate during agent-driven development"
model, addressed via the Branch and Agent Workflow section. Those additions are now part of the
canonical Python profile. This document builds on them without repeating them.

## The Core Problem Each Target Needs to Solve

BEAR's governance model rests on three properties:
1. **Deterministic generated ownership** — BEAR-owned artifacts are checked and repaired deterministically.
2. **Static import/dependency boundary enforcement** — governed code cannot silently acquire
   new capability surfaces through import paths or dependency graphs.
3. **Deterministic project verification** — a single, owned verification command confirms the
   governed code's structural integrity.

For JVM, all three properties are strongly enforceable. For non-JVM targets, the honest answer
is that property strength varies by target and must be stated without overclaiming.

## Cross-Target Architecture Gap to Address Before New Targets

The current docs describe expansion mostly as adding more `Target` implementations. That is
necessary, but not sufficient for long-term robustness once BEAR needs function/symbol-level
evidence, richer dependency edges, and optional graph-backed analysis.

Recommendation:
- keep `Target` responsible for target lifecycle concerns:
  - detection
  - generated artifact layout
  - governed roots
  - project verification execution
  - mapping target findings to BEAR governance lanes
- add a second seam (`AnalyzerProvider` / `EvidenceProvider`) responsible for evidence extraction:
  - import and dependency edges
  - symbol ownership
  - locator-level evidence (`file/module/symbol/span`)
  - optional call/reference graph where available

Why this is the right split:
- BEAR remains the deterministic governance engine
- ecosystem analyzers remain modular and replaceable
- simple/native analyzers can ship first, richer analyzers can follow without core rewrites
- future graph-backed evidence can be integrated without coupling BEAR to one analysis stack early

---

## Target: .NET / C#

BEAR has a complete parked profile and CLI initiative for .NET:
- `future-dotnet-containment-profile.md` — full capability profile
- `future-target-adaptable-cli-dotnet.md` — phased CLI implementation plan

The brief summary is reproduced here only to support the priority comparison below.

### Why .NET is ranked above Python

.NET (`dotnet-csharp-sdk-single-project-v1`) offers:
- **Explicit project/package structure** (`PackageReference`, `.csproj`, lock file) that maps
  directly onto BEAR's governance model — stronger static legibility than either Node or Python
- **Deterministic NuGet lock file** (`packages.lock.json`) already part of the standard toolchain,
  with no additional scaffolding required
- **One block per project** mapping that is simpler and less ambiguous than the Python module
  resolution model for a first slice
- **`dotnet test` verification contract** that is deterministic, owned by the toolchain, and
  less sensitive to arbitrary test-runner variation than `mypy`
- **Strongest honest "beyond JVM" proof** — demonstrates BEAR is a multi-language governance
  layer, not a JVM tool, while maintaining high containment confidence

Unlike Python, .NET has no dynamic import system that undermines static containment claims and
no native-extension gap in the standard package surface. A first .NET slice can honestly claim
stronger property coverage than a first Python slice.

The .NET profile has open questions (one-block-per-project granularity, Central Package
Management support) that must be resolved before execution. Those are documented in the dedicated
profile file and are not reproduced here.

---

## Target: Node / TypeScript

### Problem

Node is the most requested non-JVM backend target. The problems to solve are:
- BEAR has no Node code-generation story; the `Target` seam exists but has no `NodeTarget` behind it
- `pnpm` dependency resolution is package-scoped, not block-scoped, so there is no JVM-equivalent
  Gradle containment init-script mechanism — a block-level dep allowlist is not honestly enforceable
- Dynamic module loading (`import()`, `require()`, `module.createRequire`) in governed roots is
  detectable statically, but loader hooks and preloaded resolver hooks outside governed roots are
  not
- The existing `future-target-adaptable-cli-node.md` phases plan is detailed but has never advanced
  to active execution — it remains parked after the target seam was completed

### Solution

The honest Node first slice is:
- `NodeTarget` implementing the existing `Target` seam interface (already designed)
- TypeScript-only governed roots: `src/blocks/<blockKey>/**/*.ts` and `src/blocks/_shared/**/*.ts`
- Static import containment: relative-only imports, no escaping block root or sibling blocks
- Repo-level dependency governance in `pr-check`: `package.json` + `pnpm-lock.yaml` deltas
  classified as `BOUNDARY_EXPANDING`
- Covered undeclared-reach detection for Node built-ins (`node:http`, `node:https`, `node:net`,
  `node:child_process`, `node:fs`) in governed roots
- Project verification via `pnpm exec tsc --noEmit -p tsconfig.json`
- No block-level `impl.allowedDeps` analogue (treated as `NOT_SUPPORTED`)

The branch/agent gate model from the Python profile applies here equally: `bear check` runs
statically on committed source files; no running Node server is required.

### Tradeoffs

| Tradeoff | Honest position |
| --- | --- |
| Block-level dependency containment | Not enforceable; `pnpm` resolution is package-scoped |
| Dynamic import detection completeness | Only direct syntax in governed files; loader hooks are out of scope |
| Runtime sandboxing | Not supported; BEAR is a static governance layer |
| Workspace support | Excluded from first slice; `pnpm-workspace.yaml` must be absent |
| TypeScript path aliases and `baseUrl` | Excluded; they introduce resolution indirection BEAR cannot govern honestly |
| Non-ESM module formats | Excluded; only `"module": "nodenext"` profile supported |

### Why Node Is the Recommended First Non-JVM Target

- The target seam was built precisely for this: `future-target-adaptable-cli-node.md` has a
  complete phased implementation plan ready to execute
- TypeScript's static module system is the closest non-JVM approximation to Java's compile-time
  import boundary enforcement
- The backend TypeScript use case maps directly onto BEAR's existing block/port/effects governance
  model without requiring IR schema changes
- Phased implementation keeps JVM behavior byte-identical and untouched throughout

---

## Target: Python

### Problem

Python is a common agent-assisted backend language, but several structural properties make it
harder to govern than JVM or even Node:
1. **Runtime installed packages**: third-party packages installed in the virtual environment may
   transitively reach covered power surfaces through their own imports — BEAR's source-file scanner
   cannot see this at all without additional infrastructure
2. **Dynamic import system**: `importlib.import_module`, `__import__`, and `sys.path` mutations
   are common, cannot be fully prevented statically, and offer runtime escape routes that BEAR's
   static checker cannot close
3. **No build-tool enforcement handshake**: Python has no honest equivalent of the JVM Gradle
   containment init-script plus marker handshake — a block-level `impl.allowedDeps` analogue
   would overclaim
4. **Multi-Python runtime reach**: governed code that imports third-party packages inherits all of
   those packages' capabilities at runtime, including capabilities BEAR cannot scan if the package
   uses native extensions
5. **No committed Python CLI implementation plan**: unlike Node, Python has a profile but no
   phased implementation plan equivalent to `future-target-adaptable-cli-node.md`

### Solution

The three-layer Python solution added in March 2026 and now canonical in the profile:

**Layer 1 — Source static scan** (governs what authored `.py` files may import):
- import boundary enforcement in governed roots (`src/blocks/<blockKey>/`, `_shared/`)
- covered power-surface detection: `socket`, `http`, `urllib`, `subprocess`, `multiprocessing`,
  direct `os.system`/`os.popen`/`os.exec*` call patterns
- direct dynamic import facility blocking: `importlib.import_module`, `__import__`, `sys.path`
  mutation

**Layer 2 — `site-packages` scan** (closes the "installed packages" gap partially):
- when `.venv` is present, scan the `purelib`/`platlib` `site-packages` paths for pure-Python
  source files belonging to each installed package
- apply the same power-surface rules used for governed roots
- produce a **dependency power-surface report** in `pr-check` when the lock file changes
- packages with only native extensions (`.pyd`/`.so`) are listed as `NOT_COVERABLE` — an
  explicit accepted gap

**Layer 3 — Commit-time agent workflow gate**:
- `bear check` operates purely on committed source files and generated artifacts — no running
  process, no interpreter required
- structured findings are surfaced before merge; an agent can act on them without a human review
- `bear pr-check` reviews lock-file and `pyproject.toml` deltas as boundary-expanding events

### Concentric profiles

Python governance supports two concentric profiles sharing the same `target=python` detection
and infrastructure:

**Inner profile: `python/service`** (strict, default)
- third-party package imports from governed roots are **blocked**
- strongest containment — governed blocks cannot silently acquire new capability surfaces
- best for greenfield block development and agent-assisted workflows where block isolation
  is the primary goal

**Outer profile: `python/service-relaxed`** (pragmatic, opt-in)
- third-party package imports from governed roots are **allowed but governed**
- same-block/`_shared` boundaries remain enforced; no sibling block imports
- `site-packages` power-surface scan becomes the **primary containment mechanism**
- new packages in `pr-check` still trigger `BOUNDARY_EXPANDING`
- best for existing Python services being incrementally brought under governance

Both profiles share the same detection, generated artifacts, verification command, and
`site-packages` scan infrastructure. The only behavioral difference is whether third-party
imports from governed roots fail (strict) or are tracked and governed (relaxed).

Ship `python/service` first; offer `python/service-relaxed` once the inner profile is proven.

### Tradeoffs

| Tradeoff | Honest position |
| --- | --- |
| Block-level dependency containment | Not enforceable; `pip`/`uv`/`poetry` resolution is package-scoped |
| Native extension reach in site-packages | Not coverable; binary `.pyd`/`.so` files cannot be statically scanned |
| Dynamic import completeness | Only direct usage in governed files; runtime indirection is out of scope |
| Runtime sandboxing | Not supported; BEAR is a static governance layer |
| `site-packages` scan without a `.venv` | No coverage; stale or absent environment produces no scan output |
| `os` partial coverage | `import os` itself is benign; only call-site patterns for `os.system`/`os.popen` are flagged |

### Why Python Is Recommended Third (After .NET)

- The dynamic import system and native extension gap make Python containment confidence lower
  than both JVM and .NET
- Unlike Node, Python has no production-quality phased implementation plan yet — one would need
  to be written analogously to `future-target-adaptable-cli-node.md` before execution starts
- The `mypy --strict` verification contract is strong but less reliable than compile-time
  guarantees for import-boundary enforcement
- Despite the gaps, the three-layer solution makes Python governance honest and useful in
  agent-assisted workflows; it just should not be the first non-JVM target

---

## Target: React / TypeScript Frontend

### Problem

React governance is a different product direction from BEAR's current backend governance mission:
1. **Weakest containment story of all profiles**: runtime enforcement in a browser/renderer
   environment is impossible; a developer can always reach the network through installed packages
2. **API surface leakage**: components making direct `fetch()` calls bypass the declared port
   model — but BEAR can only flag obvious direct patterns, not all network access
3. **Frontend ≠ backend governance**: BEAR's block/port/effects model was designed for backend
   service units; applying it to React feature modules is an analogy, not a direct fit
4. **Meta-framework risk**: Next.js, Remix, Astro, and similar frameworks all break the simple
   static import model; excluding them is necessary but limits the profile's reach significantly
5. **No implementation plan**: unlike Node, there is no phased CLI implementation plan for React

### Solution

The honest React first slice (if pursued) is:
- feature-module governed roots: `src/features/<blockKey>/` and `src/shared/`
- static import containment within feature roots — no sibling feature imports
- direct `fetch()`/`XMLHttpRequest` detection in component `.tsx` files (advisory, not blocking)
- repo-level dependency governance: `package.json` + `pnpm-lock.yaml` deltas classified as
  `BOUNDARY_EXPANDING`
- project verification via `pnpm exec tsc --noEmit -p tsconfig.json`
- Vite + React only; no meta-frameworks; `compilerOptions.paths` and `baseUrl` excluded

### Tradeoffs

| Tradeoff | Honest position |
| --- | --- |
| Runtime enforcement | Not supported; browser execution environment makes it impossible |
| Block-level dependency containment | Not enforceable for the same reasons as Node |
| API boundary enforcement | PARTIAL; direct `fetch`/`XHR` patterns detectable, network access through packages is not |
| Meta-framework support | Not supported in the first slice; excludes the majority of new React projects |
| Static state management enforcement | Not supported; Redux/Zustand/etc. are too diverse to govern statically |
| Weaker product-mission fit | Frontend governance is a separate product direction; requires explicit team decision |

### Why React Is Recommended Last

- The weakest containment story of all four targets
- Requires an explicit product decision to extend BEAR's mission from backend to frontend governance
- Has the highest risk of "widening to normal React app expectations" which would immediately
  force BEAR into fuzzy heuristics or false containment claims
- No implementation plan exists; it would need to be written from scratch
- Until at least one non-JVM backend target is proven, expanding to frontend governance dilutes
  the product thesis

---

## Cross-Target Summary

### Capability comparison (honest first-slice assessment)

| Capability | JVM | Node | Python (strict) | Python (relaxed) | React |
| --- | --- | --- | --- | --- | --- |
| Deterministic generated ownership | `ENFORCED` | `ENFORCED` | `ENFORCED` | `ENFORCED` | `ENFORCED` |
| Same-block import containment | `ENFORCED` | `ENFORCED` | `ENFORCED` | `ENFORCED` | `ENFORCED` |
| Shared root import containment | `ENFORCED` | `ENFORCED` | `ENFORCED` | `ENFORCED` | `ENFORCED` |
| Third-party imports from governed roots | `ENFORCED` | `NOT_SUPPORTED` | `ENFORCED` (blocked) | `GOVERNED` (allowed, delta-reviewed) | `NOT_SUPPORTED` |
| Covered power-surface detection | `ENFORCED` | `ENFORCED` | `ENFORCED` | `ENFORCED` | `PARTIAL` |
| Block-level dependency allowlist | `ENFORCED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` |
| Repo-level dependency governance | `ENFORCED` | `ENFORCED` | `ENFORCED` | `ENFORCED` | `ENFORCED` |
| Installed-package power reach | `NOT_APPLICABLE` | `NOT_SUPPORTED` | `PARTIAL` (site-packages scan) | `PARTIAL` (primary mechanism) | `NOT_SUPPORTED` |
| Dynamic import blocking | `ENFORCED` | `PARTIAL` | `PARTIAL` | `PARTIAL` | `PARTIAL` |
| Runtime/process sandboxing | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` |
| Workspace/multi-package support | `PARTIAL` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` | `NOT_SUPPORTED` |
| Project verification | `ENFORCED` (Gradle test) | `ENFORCED` (tsc --noEmit) | `ENFORCED` (mypy --strict) | `ENFORCED` (mypy --strict) | `ENFORCED` (tsc --noEmit) |
| Agent/branch workflow gate | `ENFORCED` | `ENFORCED` | `ENFORCED` | `ENFORCED` | `ENFORCED` |

### Recommended expansion priority

```
JVM (done) → Architecture prerequisites → Node backend → [Python or .NET] → [remaining] → React frontend (last)
```

#### Architecture prerequisites (must land before any non-JVM target)

1. **TargetDetector + `.bear/target.id` prerequisite epic**: `TargetRegistry.resolve()` must
   stop being effectively JVM-only; detector result model (`SUPPORTED`/`UNSUPPORTED`/`NONE`),
   `.bear/target.id` pin semantics, and ambiguity behavior must be defined and tested before
   multi-target rollout. Conflicting signals without a pin must fail deterministically with
   actionable remediation — never silently "best guess."
2. **Finalize shared locator model**: canonical locator schema for all findings
3. **Finalize target/profile separation**: decouple language/runtime from governance shape
4. **Add AnalyzerProvider SPI**: evidence extraction seam with simple/native analyzers first

#### First implementation: Node

Node is always the first non-JVM target. The target seam was built for it; the implementation
plan exists; TypeScript's static module system is the closest non-JVM approximation to Java's
import-boundary enforcement.

#### Second and third implementations: strategy-dependent

After Node, the second target should be chosen based on both technical readiness and product
strategy:

**Technical readiness order** (strongest containment confidence first):
```
.NET → Python
```
.NET has the strongest static project/package model and no dynamic-import escape route. On
pure technical grounds, .NET ships a more honest containment story.

**Market priority order** (highest commercial/user demand first):
```
Python → .NET
```
Python matters more commercially for AI-assisted development and LLM-heavy repos. Shipping
Python second — even with its weaker containment guarantees — may deliver more product value
earlier.

Both orders are architecturally valid because the prerequisite seams (detector, locator,
profile, analyzer) are target-agnostic. Choose based on product strategy.

#### Last: React

React is last regardless of ordering strategy. It requires an explicit product decision,
has the weakest containment story, and represents a different product direction (frontend
governance vs backend governance).

### Shared architecture decisions (all targets)

All targets must:
- implement the existing `Target` interface (`targetId`, `compileSingle`, `compileAll`,
  `generateWiringOnlySingle`, `generateWiringOnlyAll`, `targetChecks`, `runProjectVerification`,
  `governedRoots`) — no core changes required
- provide a `TargetDetector` returning `SUPPORTED`, `UNSUPPORTED`, or `NONE` — detector
  ambiguity must fail deterministically with actionable remediation, never silently guess
- support `.bear/target.id` pin override for multi-ecosystem or ambiguous repos
- use the same `TargetDetector`/`TargetRegistry` dispatch model — one detection point per command
- produce BEAR-owned artifacts under `build/generated/bear/` — same ownership model as JVM
- map all findings to the existing exit codes and `CODE/PATH/REMEDIATION` envelope
- leave IR v1 schema unchanged — no `target:` field, no per-target IR additions
- preserve JVM behavior byte-identical throughout; all non-JVM work lands behind the seam
- separate target identity from governance profile identity:
  - target examples: `node`, `python`, `react`
  - profile examples: `backend-service`, `service`, `feature-ui`
  - selection model: `target=<runtime/toolchain>`, `profile=<governance-shape>`

### Canonical locator/scope model (cross-target requirement)

Before deeper multi-target evidence work, define one canonical locator model shared by all
findings:
- repository
- package/project
- module/file
- symbol (function/class/component)
- block/span

Every finding should carry:
- stable human-readable `PATH=...` for CLI and CI output
- structured locator payload internally for deterministic merging and future analyzer integration

---

## What This Plan Does Not Cover

- `.NET` — covered in dedicated profile and CLI initiative files; not duplicated here
- IR schema expansion — out of scope for all non-JVM first slices
- Runtime policy engines or process-level sandboxing — explicitly excluded from BEAR's scope
- Production readiness of any specific target — each target requires a separate phased
  implementation plan document before execution begins
