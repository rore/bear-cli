# Phase B: Node Target — Scan Only

Phase B adds `NodeTarget` as the first non-JVM target. After this phase, a Node/TypeScript
fixture project can compile governed blocks, pass `bear check` for drift and import containment,
fail `check` deterministically when an import escapes the block root, and preserve `pr-check`
exit behavior.

Builds on Phase A: `TargetDetector`, `DetectedTarget`, refactored `TargetRegistry`,
`CanonicalLocator`, `GovernanceProfile`.

## Anchoring Constraints

- IR v1 is the boundary source of truth. No target-specific IR extensions.
- Exit code registry is frozen: `0`, `2`, `3`, `4`, `5`, `6`, `7`, `64`, `70`, `74`.
- `CODE/PATH/REMEDIATION` envelope is frozen.
- JVM behavior must remain byte-identical. Node work arrives behind the Target seam.
- No runtime policy engine additions.
- Generated artifacts live under `build/generated/bear/`. User-owned impl is never overwritten.
- `TargetId.NODE` already exists from Phase A. Do not re-add it.

## Glossary

- `NodeTarget` — Target implementation for Node/TypeScript projects.
- `NodeTargetDetector` — Identifies supported Node/TypeScript project shapes.
- `NodeImportSpecifierExtractor` — Parses TypeScript source; extracts import/export specifiers.
- `NodeDynamicImportDetector` — Identifies `import()` expressions.
- `NodeImportBoundaryResolver` — Classifies resolved import paths against governed-root topology.
- `governed root` — Source directories under BEAR governance: `src/blocks/<blockKey>/` and `src/blocks/_shared/`.
- `BEAR-generated artifact` — TypeScript files under `build/generated/bear/`.
- `user-owned impl` — Implementation files created once by BEAR; owned and modified by the user.
- `import containment` — Enforcement preventing imports from escaping block boundaries.
- `drift gate` — Compares workspace artifacts against freshly generated output.
- `block key` — Kebab-case block identifier (e.g., `auth-service`).
- `CanonicalLocator` — Structured finding locator from Phase A.
- `GovernanceProfile` — Target-specific governance shape identifier from Phase A.

---

## Node Project Detection

`NodeTargetDetector` determines whether a project root is a supported Node/TypeScript project.

Detection signals — all must be present for `SUPPORTED`:
- `package.json` at project root with `"type": "module"` and `"packageManager": "pnpm@..."` field
- `pnpm-lock.yaml` at project root
- `tsconfig.json` at project root

Detection results:
- All signals present → `SUPPORTED`, `targetId=NODE`
- `pnpm-workspace.yaml` present → `UNSUPPORTED` (workspace layout excluded from this slice)
- Any required file absent → `NONE`

Out-of-scope project shapes (intentional scope boundaries, not detection gaps):
- CJS projects (no `"type": "module"`) → `NONE`
- npm/yarn/bun-managed projects → `NONE`
- pnpm workspace/monorepo layouts → `UNSUPPORTED`
- Projects without TypeScript → `NONE`

Acceptance criteria:
- `SUPPORTED` for directory with valid `package.json` (`type:module`, `packageManager:pnpm@...`), `pnpm-lock.yaml`, `tsconfig.json`
- `UNSUPPORTED` when `pnpm-workspace.yaml` is also present
- `NONE` when `package.json` is missing
- `NONE` when `pnpm-lock.yaml` is missing
- `NONE` when `tsconfig.json` is missing
- `NONE` for a JVM project directory (no false-positive cross-detection)
- `NONE` when `package.json` lacks `"type": "module"`
- `NONE` when `package.json` lacks `"packageManager"` with pnpm

---

## Target Registry Integration

`TargetRegistry.defaultRegistry()` registers both JVM and NODE targets.

Acceptance criteria:
- `NodeTarget` uses `TargetId.NODE` (from Phase A)
- `TargetRegistry.defaultRegistry()` registers both JVM and NODE
- `TargetRegistry.resolve()` returns `NodeTarget` for a Node fixture project root
- `TargetRegistry.resolve()` returns `JvmTarget` for a JVM project root
- All existing JVM tests pass without modification

---

## TypeScript Artifact Generation

`NodeTarget.compile()` generates BEAR-owned TypeScript artifacts and wiring manifests.

Generated artifact layout (BEAR-owned, regenerated, drift-checked):
```
build/generated/bear/
  wiring/<blockKey>.wiring.json
  types/<blockKey>/
    <BlockName>Ports.ts
    <BlockName>Logic.ts
    <BlockName>Wrapper.ts
```

User-owned (created once, never overwritten):
```
src/blocks/<blockKey>/impl/<BlockName>Impl.ts
```

Acceptance criteria:
- `compile()` generates `<BlockName>Ports.ts`, `<BlockName>Logic.ts`, `<BlockName>Wrapper.ts` under `build/generated/bear/types/<blockKey>/`
- `compile()` generates wiring manifest at `build/generated/bear/wiring/<blockKey>.wiring.json`
- `compile()` creates user-owned impl at `src/blocks/<blockKey>/impl/<BlockName>Impl.ts` if absent
- Re-running `compile()` does not overwrite existing user-owned impl
- `generateWiringOnly()` generates only the wiring manifest (no user-impl touches)
- Generated TypeScript is syntactically valid (parseable by `tsc`)

---

## Governed Source Roots

`NodeTarget` computes governed source roots for import containment and drift checking.

Governed:
```
src/blocks/<blockKey>/**/*.ts
src/blocks/_shared/**/*.ts      (optional; absent is not an error)
```

Not governed:
- Files outside `src/blocks/`
- `*.test.ts` files within governed roots
- `.js`, `.jsx`, `.tsx`, `.mjs`, `.cjs`, `.cts`, `.mts` files

Acceptance criteria:
- Governed roots include `src/blocks/<blockKey>/` for each block
- Governed roots include `src/blocks/_shared/` when it exists
- Governed roots exclude `src/blocks/_shared/` when absent (no error)
- Files outside `src/blocks/` are excluded
- `*.test.ts` files are excluded from governed source
- Only `.ts` files are governed; `.js`, `.jsx`, `.tsx`, `.mjs`, `.cjs`, `.cts`, `.mts` are excluded

---

## Import Containment Enforcement

`NodeImportContainmentScanner` enforces import boundaries in governed TypeScript files.

Pass conditions:
- Import within the same block root
- Import from `_shared` root
- Import of a BEAR-generated companion under `build/generated/bear/`

Fail conditions (exit `7`, `CODE=BOUNDARY_BYPASS`):
- Relative import that escapes the block root
- Import reaching a sibling block root
- Bare package specifier (e.g., `import x from 'lodash'`)
- `#` alias import
- URL-like specifier (e.g., `import x from 'https://...'`)
- Package self-name import
- `_shared` file importing from a block root

Acceptance criteria:
- Clean import within same block → pass
- Import from `_shared` → pass
- Import of BEAR-generated companion → pass
- Relative import escaping block root → fail, exit `7`, `CODE=BOUNDARY_BYPASS`
- Import reaching sibling block → fail, exit `7`, `CODE=BOUNDARY_BYPASS`
- Bare package import → fail, exit `7`, `CODE=BOUNDARY_BYPASS`
- `#` alias import → fail, exit `7`, `CODE=BOUNDARY_BYPASS`
- URL-like specifier → fail, exit `7`, `CODE=BOUNDARY_BYPASS`
- Package self-name import → fail, exit `7`, `CODE=BOUNDARY_BYPASS`
- `_shared` importing a block root → fail, exit `7`, `CODE=BOUNDARY_BYPASS`
- Findings include repo-relative path and import specifier in the locator

---

## Concern Separation (Scanner Internals)

The scanner separates three concerns into distinct helper classes to prevent monolithic growth.

`NodeImportSpecifierExtractor` — parses TypeScript source; extracts import/export specifiers:
- Static `import ... from`
- `export ... from`
- Side-effect `import`
- Returns specifiers with source locations

`NodeDynamicImportDetector` — identifies `import()` expressions:
- Detects `import()` expressions
- Flags dynamic imports separately from static imports (advisory in Phase B)

`NodeImportBoundaryResolver` — classifies resolved paths against governed-root topology:
- Same-block path → `PASS`
- `_shared` path → `PASS`
- BEAR-generated artifact path → `PASS`
- Sibling block path → `FAIL`
- Nongoverned source path → `FAIL`
- Escaped block root path → `FAIL`
- Uses `CanonicalLocator` for structured finding locators

---

## Drift Gate

Drift gate compares workspace artifacts against freshly generated output.

Checked artifacts:
- `build/generated/bear/types/<blockKey>/*.ts`
- `build/generated/bear/wiring/<blockKey>.wiring.json`

Not checked:
- User-owned impl files (`src/blocks/<blockKey>/impl/*.ts`)

Acceptance criteria:
- Clean state (freshly compiled) → no drift findings
- Modified generated `.ts` file → `DRIFT_DETECTED`
- Missing generated file → `DRIFT_MISSING_BASELINE`
- User-owned impl modifications → no drift findings

---

## impl.allowedDeps Unsupported Guard

When a block IR contains `impl.allowedDeps` and the resolved target is Node, `check` fails.

Failure envelope:
```
CODE=UNSUPPORTED_TARGET
PATH=<ir-file>
REMEDIATION=Remove impl.allowedDeps for node target, or switch to JVM target.
```

Acceptance criteria:
- Block without `impl.allowedDeps` → passes the guard
- Block with `impl.allowedDeps` under Node target → fails, exit `64`, `CODE=UNSUPPORTED_TARGET`
- Error output includes `CODE=UNSUPPORTED_TARGET`, IR file path, and remediation message
- `pr-check` operates normally regardless of `impl.allowedDeps`
- Same block with `impl.allowedDeps` under JVM target → continues to work

---

## TypeScript Pretty Printer

Generated TypeScript files use consistent formatting.

Acceptance criteria:
- Consistent indentation and line breaks across all generated files
- Output is parseable by `tsc` without syntax errors
- Generate → parse → generate produces equivalent output (round-trip stability)
