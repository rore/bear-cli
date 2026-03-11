# Task C2: Dynamic Import Detection

## Phase Reference
Phase C: Node Target - Undeclared Reach

## Spec Reference
`.kiro/specs/phase-c-node-undeclared-reach.md` FR-C2

## Prerequisites
- Task B5 complete (NodeImportContainmentScanner, governed root scanning)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodeImportContainmentScanner.java`
- `kernel/src/main/java/com/bear/kernel/target/BoundaryBypassFinding.java`

## Implementation Steps

1. Add dynamic import detection to `NodeImportContainmentScanner` or create a separate
   `NodeDynamicImportScanner.java`:

2. Define patterns to detect:
   - `import(` -- dynamic import expression (ESM)
   - `require(` -- CommonJS require call
   - `createRequire(` -- module.createRequire usage
   Note: these are lexical patterns, not full AST analysis

3. Scan governed `.ts` files line-by-line:
   - Skip comments (single-line `//` and multi-line `/* */`)
   - Skip string literals that happen to contain these patterns
   - Match patterns: `import(`, `require(`, `createRequire(` as standalone tokens
     (not preceded by alphanumeric characters to avoid matching `importModule(` etc.)

4. Produce findings:
   - `BoundaryBypassFinding(rule, path, detail)` for each match
   - `rule` = "DYNAMIC_IMPORT"
   - `path` = repo-relative file path
   - `detail` = the detected pattern and line number, with `PARTIAL` status indicator

5. Wire into NodeTarget.scanBoundaryBypass() or appropriate check pipeline method

6. Write tests:
   - `const m = await import('./module')` -> BOUNDARY_BYPASS finding
   - `const m = require('./module')` -> BOUNDARY_BYPASS finding
   - `const r = createRequire(import.meta.url)` -> BOUNDARY_BYPASS finding
   - `import x from './module'` -> no dynamic import finding (static import)
   - `// import('commented out')` -> no finding (in comment)
   - Dynamic import in non-governed file -> no finding
   - Finding detail includes PARTIAL status

## Outputs
- New or modified scanner in `kernel/src/main/java/com/bear/kernel/target/node/`
- Modified `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java` (wire scanner)
- Test files

## Acceptance Criteria
- import() expressions produce BOUNDARY_BYPASS findings
- require() calls produce BOUNDARY_BYPASS findings
- createRequire() calls produce BOUNDARY_BYPASS findings
- Static import statements do not trigger dynamic import detection
- Non-governed files are not scanned
- Finding detail includes PARTIAL status indicator
- All tests pass

## Estimated Effort
1 hour
