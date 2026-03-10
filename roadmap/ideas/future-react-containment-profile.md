---
id: future-react-containment-profile
title: Honest React/TypeScript frontend containment profile
status: queued
priority: low
commitment: uncommitted
milestone: Future
---

## Goal

Define the smallest React/TypeScript frontend profile BEAR could support without overstating
determinism, containment, or project-verification guarantees.

This document is intentionally narrow:
- no CLI behavior changes
- no `.bear/target.id` changes
- no target detection/pinning changes
- no IR schema changes
- no attempt to support the full React or frontend ecosystem

## Recommendation Summary

Recommendation:
- keep React support parked until the product explicitly decides to govern frontend feature modules
- if BEAR pursues React later, the honest first slice is:
  - TypeScript only (no `.jsx`/`.js` in governed roots)
  - single `pnpm` package only
  - Vite as the build tool
  - feature-module import containment as the primary governance surface
  - API service-layer boundary governance (no direct `fetch`/`axios` in governed feature modules)
  - repo-level dependency governance

Why:
- BEAR's current model is backend-first: blocks represent backend service units with declared effects,
  ports, and dependency boundaries.
- React feature modules present an analogous governance opportunity: feature slices should not reach
  directly into sibling feature internals, and API calls should flow through declared service interfaces.
- However, the frontend containment story is materially weaker than JVM or even Node backend
  containment: runtime sandboxing is impossible, and the dependency graph is much harder to reason
  about statically.

## Positioning Note

React frontend governance is a distinct product direction from backend governance.
Before pursuing this:
- BEAR should have at least one non-JVM backend target proven (Node or Python).
- The team should explicitly decide to extend BEAR's mission to include frontend feature-module
  governance.

If BEAR does pursue this, it should be scoped as a frontend-only slice that leverages the existing
target seam without weakening the backend governance guarantees.

## Supported React Profile

Profile name:
- `react-ts-vite-pnpm-single-package-v1`

Required repo shape:
- one React application at the project root
- required files at `projectRoot`:
  - `package.json`
  - `pnpm-lock.yaml`
  - `vite.config.ts`
  - `tsconfig.json`
- `package.json` must include:
  - `"type": "module"`
  - `"packageManager": "pnpm@<pinned-version>"`
  - `react` and `react-dom` in `dependencies`
- `pnpm-workspace.yaml` must be absent

Required source profile:
- governed source files are `.ts` and `.tsx` only
- no governed `.js`, `.jsx`, `.cjs`, `.mjs`
- single-package source layout only:
  - `src/features/<blockKey>/` - the feature-module governed root
  - optional `src/shared/` - the shared utilities governed root
- each feature block owns exactly one governed directory

Required TypeScript profile:
- `compilerOptions.module = "esnext"` or `"bundler"`
- `compilerOptions.moduleResolution = "bundler"`
- `compilerOptions.strict = true`
- `compilerOptions.jsx = "react-jsx"` or `"react-jsxdev"`
- `compilerOptions.baseUrl` must be absent
- `compilerOptions.paths` must be absent
- `references` must be absent

Unsupported project features in the first slice:
- `pnpm` workspaces
- TypeScript project references
- path aliases (`compilerOptions.paths` or Vite `resolve.alias`)
- Next.js, Remix, or other meta-frameworks (Vite + React only)
- CSS-in-JS solutions that generate TypeScript at build time and affect module resolution
- Server-side rendering or server components in the first slice
- Custom Vite plugins that rewrite import resolution at runtime

## Governed Roots

BEAR-governed feature blocks map to frontend feature modules/slices.

User-authored governed roots:
- `src/features/<blockKey>/` - the feature module
- `src/shared/` - optional shared utilities

Generated BEAR-owned roots:
- `build/generated/bear/`
- `build/generated/bear/wiring/<blockKey>.wiring.json`

Root treatment:
- `src/features/<blockKey>/` is the block-local user-authored governed root
- `src/shared/` is the only shared user-authored governed root
- `build/generated/bear/` is BEAR-owned, regenerated, and drift-checked
- page-level routing files, entry points, and test files are not governed in the first slice
- nongoverned roots include everything else:
  - `src/app/` or `src/pages/` (routing layer)
  - `src/main.tsx` or `src/index.tsx` (entry point)
  - `test/` or `*.test.ts`/`*.test.tsx`
  - `public/`
  - config files

## Block Concept in React

A BEAR block in the React context represents a **feature module** — a self-contained UI feature
with its own components, hooks, state management, and API access layer.

Block boundary mapping:
- `block.effects.allow` ports map to declared API service objects or state store interfaces
- `block.operations` map to the block's public entry points (page components, hooks exported from
  the feature's `index.ts`)
- boundary bypass = a component directly calling `fetch()` or importing from a sibling feature
  module's internals instead of using the feature's public `index.ts` export

This governance model encourages explicit, reviewable feature boundaries and controlled API access
instead of ad-hoc direct calls scattered across components.

## Containment Model

For React, "containment" is the weakest of all profiles and must be stated honestly.

It does not mean:
- runtime sandboxing of any kind
- runtime prevention of network access, DOM manipulation, or external API calls
- enforcement of API call routing at runtime
- proof that no power flows through transitive dependencies

It can mean:
- deterministic generated ownership
- deterministic local import-boundary enforcement inside governed feature roots
- deterministic repo-level dependency-governance signals
- static detection of direct banned API-call patterns in governed roots

### 1. Import containment

Definition:
- governed feature module code must stay within its own feature root, `src/shared/`, or
  BEAR-generated companion modules

Deterministic first-slice enforcement:
- scan literal `import` and `export ... from` specifiers in governed `.ts`/`.tsx` files
- resolve relative specifiers lexically against the importing file
- fail when the resolved target escapes the allowed roots
- fail on direct imports from sibling feature roots

Allowed in governed roots:
- relative imports within the same feature root
- relative imports reaching `src/shared/`
- imports of BEAR-generated companion modules under `build/generated/bear/`
- imports from `react`, `react-dom` (only allowed external packages in the first slice)

Not allowed in governed roots:
- relative imports escaping into sibling feature roots
- relative imports into `src/app/`, `src/pages/`, or entry point files
- arbitrary third-party package imports (except `react`/`react-dom` and declared port packages)
- path alias imports (disallowed by the profile requirement above)

### 2. API boundary governance (frontend-specific)

Definition:
- governed feature components should not make direct network calls; instead they should use
  declared API service objects that represent the "ports" of the block

Deterministic first-slice contract:
- detect direct `fetch(...)` or `new XMLHttpRequest(...)` calls in governed component files
  (`.tsx` files only; not in `*Service.ts` or `*Api.ts` files within the same block root)
- this is `PARTIAL` enforcement: BEAR can flag the most common direct patterns, but cannot
  prevent all forms of network access

Rationale:
- this encourages centralizing API calls in declared service files within each feature block,
  which maps to BEAR's "port" concept and makes boundary expansion visible in PRs

### 3. Dependency governance

Same as the Node profile:
- repo/package-level dependency governance only
- no block-level dependency allowlisting in the first slice

### 4. Generated/owned artifact containment

Definition:
- same BEAR two-file ownership model as JVM and Node

Deterministic first-slice contract:
- BEAR owns `build/generated/bear/`
- user-owned impl files remain preserved
- `check` treats generated drift as a BEAR-owned artifact mismatch

## Dependency Governance Model

### `package.json` and lock-file treatment in `pr-check`

Boundary-expanding:
- add/change under:
  - `dependencies`
  - `devDependencies`
  - `optionalDependencies`
  - `peerDependencies`
- any `pnpm-lock.yaml` change

Ordinary:
- dependency removal

### `impl.allowedDeps`

Decision:
- `impl.allowedDeps` has no meaningful first-slice React equivalent
- treat it as `NOT_SUPPORTED` for React

## Project Verification Model

### First-slice verification command

Use:
- `pnpm exec tsc --noEmit -p tsconfig.json`

Do not use as the first-slice contract:
- `pnpm test`
- `vitest run`
- arbitrary package scripts

Why:
- `tsc --noEmit` provides a deterministic, repeatable typecheck outcome
- it mirrors the same approach used in the Node backend profile
- using `vitest run` as the verification contract would require BEAR to depend on
  test infrastructure choices and browser environment availability

### Failure mapping

`exit 0`:
- typecheck completed successfully

`exit 4` (`project verification failure` semantics):
- `tsc` completed and reported type errors
- verification timed out after BEAR's target-owned timeout policy

`exit 64` (`unsupported target` semantics):
- required profile file (`vite.config.ts`, `tsconfig.json`, `pnpm-lock.yaml`) is missing
- repo declares a React shape outside the supported profile (path aliases, workspaces, etc.)
- a meta-framework structure is detected

`exit 74` (`tooling/environment failure` semantics):
- `pnpm` executable is missing
- `typescript` cannot be executed
- dependencies are not installed

## Capability Matrix

| Area | Status | Honest first-slice meaning |
| --- | --- | --- |
| Deterministic generated ownership under `build/generated/bear/` | `ENFORCED` | BEAR owns generated React feature artifacts and drift-checks them. |
| Two-file preservation for user impl files | `ENFORCED` | Generated/user-owned split remains stable. |
| Same-feature relative import containment | `ENFORCED` | Governed code may stay inside its own feature root. |
| `src/shared/` import containment | `ENFORCED` | Governed code may reach only `src/shared/`, not sibling features or nongoverned roots. |
| Imports from sibling feature roots | `ENFORCED` | Fail as boundary bypass. |
| Path alias imports from governed roots | `ENFORCED` (profile-level) | Disallowed by the required TypeScript/Vite profile. |
| Repo-level dependency graph deltas (`package.json`, `pnpm-lock.yaml`) | `ENFORCED` | `pr-check` surfaces them as reviewable boundary expansion. |
| Direct `fetch()`/`XMLHttpRequest` calls in governed component files | `PARTIAL` | BEAR can flag obvious direct patterns, not all network reach. |
| Third-party package imports from governed roots (except `react`/`react-dom`) | `ENFORCED` | Fail as boundary bypass. |
| Runtime sandboxing or runtime prevention of network/DOM access | `NOT_SUPPORTED` | BEAR is a static governance layer, not a runtime policy engine. |
| Block-level dependency allowlist (`impl.allowedDeps` equivalent) | `NOT_SUPPORTED` | No real React analogue in the first slice. |
| Workspace support | `NOT_SUPPORTED` | `pnpm-workspace.yaml` is outside the first-slice profile. |
| TypeScript `paths`, `baseUrl`, Vite aliases | `NOT_SUPPORTED` | Disallowed by the required TypeScript/Vite profile. |
| Meta-frameworks (Next.js, Remix, etc.) | `NOT_SUPPORTED` | Vite + React only in the first slice. |
| Server-side rendering or server components | `NOT_SUPPORTED` | Client-side React only in the first slice. |
| Arbitrary test runner integration (`vitest`, `jest`, `playwright`) | `NOT_SUPPORTED` | First-slice verification is typecheck only. |

## Recommendation

### Should BEAR pursue React now?

Recommendation:
- no, do not pursue React until at least one non-JVM backend target is proven
- React is the lowest-priority target among the candidates (Node backend, .NET, Python, React)

Reasons:
1. Frontend governance is a separate product direction from BEAR's current backend mission.
2. The containment story for React is the weakest: no runtime enforcement is possible, and
   most governance reduces to import-path checking that developers can easily route around.
3. The realistic value proposition (preventing ad-hoc API calls and cross-feature imports) is
   useful but narrower than the backend governance that BEAR was designed for.
4. Widening to "normal React app" expectations would immediately force BEAR into heuristics and
   false containment claims.

### If React is pursued later

Require an explicit product decision that accepts all of the following:
1. Frontend governance is in scope for BEAR.
2. The team accepts the weaker containment story compared to JVM/backend targets.
3. The supported profile is strictly `react-ts-vite-pnpm-single-package-v1` (no meta-frameworks).
4. The initial slice delivers only: generation + drift, import containment, API boundary signaling,
   and dependency governance.

### Smallest honest future slice

If React is pursued, ship only this:
- deterministic generation + drift for feature module skeletons
- governed roots under `src/features/<blockKey>/` and `src/shared/`
- static import containment for local feature roots
- direct `fetch()`/`XMLHttpRequest` detection in component files
- repo-level dependency-governance signaling
- `pnpm exec tsc --noEmit -p tsconfig.json` verification

### Explicit deferrals

Defer all of these:
- meta-frameworks (Next.js, Remix, Astro)
- server-side rendering or server components
- workspaces
- path aliases
- third-party component library imports inside governed roots
- React-specific state management enforcement (Redux, Zustand, etc.)
- arbitrary test-runner support
- any IR expansion for frontend semantics
- React Native or mobile targets

## External Grounding

The profile above is based on current primary docs:
- Vite project structure: [vitejs.dev/guide](https://vitejs.dev/guide/)
- TypeScript `bundler` module resolution: [typescriptlang.org/tsconfig/moduleResolution.html](https://www.typescriptlang.org/tsconfig/moduleResolution.html)
- pnpm package manager: [pnpm.io](https://pnpm.io/)
- React project conventions: [react.dev/learn/start-a-new-react-project](https://react.dev/learn/start-a-new-react-project)
- Feature-sliced design (common React feature-module convention): [feature-sliced.design](https://feature-sliced.design/)
