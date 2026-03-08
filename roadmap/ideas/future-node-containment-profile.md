---
id: future-node-containment-profile
title: Honest Node/TypeScript containment profile
status: queued
priority: medium
commitment: uncommitted
milestone: Future
---

## Goal

Define the smallest Node/TypeScript target profile BEAR could support without overstating determinism or containment guarantees.

This document is intentionally narrower than the older multi-phase Node initiative sketch:
- no CLI behavior changes
- no `.bear/target.id`
- no target detection/pinning changes
- no IR schema changes
- no generic "support Node tooling" ambition

## Recommendation Summary

Recommendation:
- keep Node support parked until the product explicitly accepts a narrow first slice
- if BEAR pursues Node later, the honest first slice is:
  - TypeScript-only
  - single `pnpm` package only
  - Node ESM only
  - no workspaces
  - no import aliases or import-map indirection
  - no third-party package imports from governed roots
  - repo-level dependency governance only
  - project verification as deterministic typecheck only

Why:
- JVM containment works today because BEAR can combine deterministic generated ownership, static scanners, and a real build-tool enforcement handshake.
- Node can support deterministic generated ownership and some static source containment, but it cannot honestly claim JVM-like dependency containment or runtime sandboxing without a much narrower profile than the general ecosystem expects.

## Supported Node Profile

Profile name:
- `node-ts-pnpm-single-package-v1`

Required repo shape:
- one Node package root only
- required files at `projectRoot`:
  - `package.json`
  - `pnpm-lock.yaml`
  - `tsconfig.json`
- `package.json` must include:
  - `"type": "module"`
  - `"packageManager": "pnpm@<pinned-version>"`
- `pnpm-workspace.yaml` must be absent

Required source profile:
- governed source files are `.ts` only
- no governed `.js`, `.cjs`, `.mjs`, `.cts`, `.mts`, or `.tsx`
- single-package source layout only:
  - `src/blocks/<blockKey>/**/*.ts`
  - optional `src/blocks/_shared/**/*.ts`

Required TypeScript profile:
- `compilerOptions.module = "nodenext"`
- `compilerOptions.moduleResolution = "nodenext"`
- `compilerOptions.allowJs = false`
- `compilerOptions.baseUrl` must be absent
- `compilerOptions.paths` must be absent
- `references` must be absent

Unsupported project features in the first slice:
- `pnpm` workspaces
- TypeScript project references
- bundler-oriented module resolution
- package `imports`
- package self-name imports from governed roots
- custom loaders or resolver hooks as part of the supported contract

Test/verification profile:
- no generic `pnpm test` support in the first slice
- BEAR should not depend on arbitrary package scripts for core verification
- the first project-verification contract should be `pnpm exec tsc --noEmit -p tsconfig.json`

Rationale lock:
- BEAR should model real Node resolution, not bundler-only behavior.
- BEAR should not support aliasing features that hide whether a governed import stays inside one block, reaches `_shared`, or escapes into nongoverned code.

## Governed Roots

User-authored governed roots:
- `src/blocks/<blockKey>/**`
- `src/blocks/_shared/**`

Generated BEAR-owned roots:
- `build/generated/bear/**`
- `build/generated/bear/wiring/<blockKey>.wiring.json`

Root treatment:
- `src/blocks/<blockKey>/**` is the only block-local user-authored governed root
- `src/blocks/_shared/**` is the only shared user-authored governed root
- `build/generated/bear/**` is BEAR-owned, regenerated, and drift-checked
- tests are not governed in the first slice
- nongoverned roots include everything else:
  - `test/**`
  - `src/**/*.test.ts`
  - `scripts/**`
  - config files
  - other app code outside `src/blocks/**`

Governed-root import policy:
- block code may import:
  - files inside the same block root
  - files inside `_shared`
  - BEAR-generated companion modules emitted for that same block under `build/generated/bear/**`
- `_shared` code may import:
  - files inside `_shared`
  - BEAR-generated companion modules under `build/generated/bear/**`
- block code may not import:
  - sibling block roots
  - nongoverned repo source roots
  - package self-name aliases
  - `#` imports
  - absolute filesystem paths
  - URL specifiers

## Containment Model

For Node, "containment" can only mean a limited static source-governance model.

It does not mean:
- runtime sandboxing
- runtime prevention of file/network/process access
- per-block package-manager isolation
- proof that no external capability can be reached through already-installed dependencies

It can mean:
- deterministic generated ownership
- deterministic local import-boundary enforcement inside governed roots
- deterministic repo-level dependency-governance signals
- deterministic blocking of a narrow set of direct boundary-bypass syntax

### 1. Import containment

Definition:
- governed imports must stay within the importing block's root, `_shared`, or BEAR-generated companion modules

Deterministic first-slice enforcement:
- scan literal `import` and `export ... from` specifiers in governed `.ts` files
- resolve relative specifiers lexically against the importing file
- fail when the resolved target escapes the allowed roots
- fail on non-relative user-authored imports from governed roots

Meaning of "non-relative" here:
- bare package specifiers
- package self-name imports
- `#` package-imports aliases
- URL-like specifiers

Product-honest consequence:
- first-slice governed Node blocks do not get direct third-party package imports
- BEAR governs local block composition, not arbitrary module-resolution graphs

### 2. Dependency governance

Definition:
- BEAR may surface repo/package dependency-graph change, but it does not provide block-level dependency allowlisting in Node v1

Deterministic first-slice contract:
- governance is repo-level only
- no Node equivalent of JVM `impl.allowedDeps`
- dependency additions or version changes are reviewable repo deltas, not per-block allowances

### 3. Runtime/external-power containment

Definition:
- BEAR may statically flag direct usage of selected Node power surfaces, but it does not enforce runtime containment

Deterministic first-slice contract:
- direct covered built-in module imports in governed roots can be flagged
- direct dynamic module-loading syntax in governed roots can be flagged
- indirect reach through existing third-party packages cannot be proven absent

### 4. Generated/owned artifact containment

Definition:
- same BEAR two-file ownership model as JVM

Deterministic first-slice contract:
- BEAR owns `build/generated/bear/**`
- user-owned impl files remain preserved
- `check` should treat generated drift exactly as a BEAR-owned artifact mismatch, not as a heuristic source check

## Dependency Governance Model

### Scope decision

Use repo/package-level dependency governance only.

Do not add in the first slice:
- block-level package allowlists
- a Node `impl.allowedDeps` analogue
- per-block lockfile subsets
- generated package-manager patching

Why:
- `pnpm` dependency resolution is package/repo scoped, not block scoped
- BEAR has no honest Node equivalent to the Gradle containment init-script plus marker handshake used on JVM
- a fake block-level Node allowlist would overclaim enforcement BEAR cannot actually provide

### `package.json` and lockfile treatment in `pr-check`

Boundary-expanding:
- add/change under:
  - `dependencies`
  - `devDependencies`
  - `optionalDependencies`
  - `peerDependencies`
  - `pnpm.overrides`
- any `pnpm-lock.yaml` change

Ordinary:
- dependency removal

Why lockfile changes are still boundary-relevant:
- they change the resolved dependency graph even when the top-level manifest looks unchanged

### `impl.allowedDeps`

Decision:
- `impl.allowedDeps` has no meaningful first-slice Node equivalent
- treat it as `NOT_SUPPORTED` for Node

Future direction:
- do not add a Node analogue unless BEAR later gains a real deterministic package-isolation mechanism

## Undeclared Reach and Boundary Bypass Coverage

The realistic first covered set is smaller than JVM and must be stated explicitly.

### Covered built-in power surfaces

Direct governed-root imports of these built-ins should be treated as undeclared reach:
- `node:http`
- `http`
- `node:https`
- `https`
- `node:net`
- `net`
- `node:child_process`
- `child_process`
- `node:fs`
- `fs`
- `node:fs/promises`
- `fs/promises`

Why these first:
- they correspond to obvious network/process/filesystem power
- they are direct Node built-ins, so the scanner can classify them without dependency-graph guesswork

### Dynamic module-loading and resolver-bypass coverage

Direct usage in governed roots should be treated as boundary bypass:
- `import(...)`
- `require(...)`
- `module.createRequire(...)`
- resolver/loader registration APIs

Status note:
- only direct syntax/API usage inside governed roots is realistically coverable
- BEAR cannot prove the absence of preloaded loaders, CLI-level hooks, or equivalent runtime indirection outside governed roots

### Third-party packages

First-slice rule:
- third-party package imports from governed roots are forbidden

This covers examples such as:
- messaging libraries
- DB clients
- SDKs

Why:
- if governed roots may import arbitrary packages, BEAR has no honest block-level dependency-containment story
- forbidding those imports is narrower but truthful

## Project Verification Model

### First-slice verification command

Use:
- `pnpm exec tsc --noEmit -p tsconfig.json`

Do not use as the first-slice contract:
- `pnpm test`
- arbitrary package scripts
- framework-specific runners

Why:
- `pnpm run` and npm `scripts` are intentionally arbitrary command dispatch
- BEAR needs one deterministic, target-owned verification contract

### Failure mapping

`exit 0`:
- typecheck completed successfully

`exit 4` (`project verification failure` semantics):
- `tsc` completed and reported type errors
- verification timed out after BEAR's target-owned timeout policy

`exit 64` (`unsupported target` semantics):
- required profile file is missing
- repo declares a Node shape outside the supported profile
- workspace/project-reference/alias/import-map features are present

`exit 74` (`tooling/environment failure` semantics):
- `pnpm` executable is missing
- `typescript` cannot be executed
- dependencies are not installed
- the verification command fails for environment/bootstrap reasons rather than reported type errors

## Capability Matrix

| Area | Status | Honest first-slice meaning |
| --- | --- | --- |
| Deterministic generated ownership under `build/generated/bear/**` | `ENFORCED` | BEAR owns generated Node artifacts and drift-checks them. |
| Two-file preservation for user impl files | `ENFORCED` | BEAR creates skeletons once and preserves user-owned impl files. |
| Same-block relative import containment | `ENFORCED` | Governed code may stay inside its own block root. |
| `_shared` import containment | `ENFORCED` | Governed code may reach only `_shared`, not sibling blocks or nongoverned roots. |
| Imports from sibling blocks or nongoverned repo code | `ENFORCED` | Fail as boundary bypass. |
| Bare package imports from governed roots | `ENFORCED` | Fail as boundary bypass; no first-slice third-party package usage inside governed roots. |
| Covered built-in imports (`http`, `https`, `net`, `child_process`, `fs`) | `ENFORCED` | Fail as undeclared reach. |
| Repo-level dependency graph deltas (`package.json`, `pnpm-lock.yaml`) | `ENFORCED` | `pr-check` surfaces them as reviewable boundary expansion. |
| Dynamic import / `require` / `createRequire` direct usage in governed roots | `PARTIAL` | BEAR can block direct usage in governed files, but not all runtime indirection outside that scope. |
| Resolver hooks, preloaded loaders, CLI-level import indirection outside governed roots | `NOT_SUPPORTED` | No honest deterministic runtime guarantee. |
| Block-level dependency allowlist (`impl.allowedDeps` equivalent) | `NOT_SUPPORTED` | No real Node analogue in the first slice. |
| Workspace support | `NOT_SUPPORTED` | `pnpm-workspace.yaml` is outside the first-slice profile. |
| TypeScript `paths`, `baseUrl`, project references, package `imports` | `NOT_SUPPORTED` | They add resolution indirection BEAR should not pretend to govern. |
| Arbitrary test runner integration (`pnpm test`, Jest, Vitest, etc.) | `NOT_SUPPORTED` | First-slice verification is typecheck only. |
| Runtime sandboxing or runtime prevention of file/network/process access | `NOT_SUPPORTED` | BEAR is a static/deterministic governance layer, not a runtime policy engine. |

## Recommendation

### Should BEAR pursue Node now?

Recommendation:
- do not make Node the next execution feature after the target seam
- keep it parked unless the team explicitly wants this narrow profile

Reason:
- the smallest honest Node slice is viable, but materially weaker and more restrictive than JVM support
- widening it to "normal Node app" expectations would immediately force BEAR into fuzzy heuristics or false containment claims

### Smallest honest future slice

If Node is pursued later, ship only this:
- deterministic generation + drift
- governed roots under `src/blocks/<blockKey>` and `_shared`
- static import containment for local roots only
- direct built-in reach blocking for the covered built-ins above
- repo-level dependency-governance signaling
- `pnpm exec tsc --noEmit -p tsconfig.json` verification

### Explicit deferrals

Defer all of these:
- workspaces
- project references
- path aliases
- package `imports`
- third-party package imports inside governed roots
- Node dependency allowlists
- runtime loader/governance claims
- arbitrary test-runner support
- any IR expansion for Node dependency semantics

## External Grounding

The profile above is based on current primary docs:
- Node ECMAScript modules: [nodejs.org/api/esm.html](https://nodejs.org/api/esm.html)
- Node module APIs and loader hooks: [nodejs.org/api/module.html](https://nodejs.org/api/module.html)
- Node packages and package `imports`: [nodejs.org/api/packages.html](https://nodejs.org/api/packages.html)
- TypeScript `moduleResolution`: [typescriptlang.org/tsconfig/moduleResolution.html](https://www.typescriptlang.org/tsconfig/moduleResolution.html)
- TypeScript `paths`: [typescriptlang.org/tsconfig/paths.html](https://www.typescriptlang.org/tsconfig/paths.html)
- TypeScript `baseUrl`: [typescriptlang.org/tsconfig/baseUrl.html](https://www.typescriptlang.org/tsconfig/baseUrl.html)
- TypeScript project references: [typescriptlang.org/docs/handbook/project-references.html](https://www.typescriptlang.org/docs/handbook/project-references.html)
- pnpm workspaces: [pnpm.io/workspaces](https://pnpm.io/workspaces)
- pnpm run behavior: [pnpm.io/cli/run](https://pnpm.io/cli/run)
- npm package scripts: [docs.npmjs.com/.../package-json#scripts](https://docs.npmjs.com/cli/v10/configuring-npm/package-json#scripts)
