# Task B5: NodeImportContainmentScanner

## Phase Reference
Phase B: Node Target - Scan Only

## Spec Reference
`.kiro/specs/phase-b-node-target-scan-only.md` FR-B5

## Prerequisites
- Task B4 complete (governed roots computation)
- Task B3 complete (generated artifact paths known)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java` (governed roots)
- `kernel/src/main/java/com/bear/kernel/target/BoundaryBypassFinding.java`
- `kernel/src/main/java/com/bear/kernel/target/jvm/BoundaryBypassScanner.java` (JVM pattern reference)
- `kernel/src/main/java/com/bear/kernel/target/jvm/BoundaryLanePolicyScanner.java` (JVM pattern reference)

## Implementation Steps

1. Create `NodeImportContainmentScanner.java` in `kernel/src/main/java/com/bear/kernel/target/node/`:
   - Takes governed roots and block root paths as input
   - Scans all `.ts` files in governed roots
   - Orchestrates three helper classes: `NodeImportSpecifierExtractor`, `NodeDynamicImportDetector`, and `NodeImportBoundaryResolver`

2a. Create `NodeImportSpecifierExtractor.java` (helper class or inner class):
    - Parse `import ... from 'specifier'` statements
    - Parse `export ... from 'specifier'` statements
    - Parse `import 'specifier'` (side-effect imports)
    - Extract the specifier string from each import/export statement
    - Use line-by-line scanning or simple regex (no full TS parser needed)
    - Return a list of extracted specifiers with source file and line number

2b. Create `NodeDynamicImportDetector.java` (helper class or inner class):
    - Detect `import(...)` dynamic import expressions
    - Return detection-only findings (these cannot be statically resolved)
    - Separate from static import extraction to keep concerns isolated

2c. Create `NodeImportBoundaryResolver.java` (helper class or inner class):
    - Takes a resolved specifier path and the governed-root topology
    - Classifies the resolved path: same-block (PASS), _shared (PASS), BEAR-generated (PASS),
      sibling-block (FAIL), nongoverned (FAIL), escaped (FAIL)
    - Returns the boundary decision for each resolved import

3. Implement specifier classification:
   - **Relative specifier** (starts with `./` or `../`): resolve lexically against importing file
   - **Bare specifier** (no prefix): package import -- always FAIL from governed roots
   - **`#` specifier**: package-imports alias -- always FAIL
   - **URL specifier** (contains `://`): always FAIL
   - **Self-name import** (matches package name from package.json): always FAIL

4. Implement relative specifier resolution and boundary checking:
   - Resolve relative path from importing file's directory
   - Normalize the resolved path
   - Check if resolved path is within:
     - Same block root -> PASS
     - _shared root -> PASS
     - build/generated/bear/ (BEAR companions) -> PASS
     - Another block root (sibling) -> FAIL: BOUNDARY_BYPASS
     - Outside src/blocks/ (nongoverned) -> FAIL: BOUNDARY_BYPASS
     - Escapes block root upward -> FAIL: BOUNDARY_BYPASS

5. Produce findings:
   - `BoundaryBypassFinding(rule, path, detail)` for each violation
   - `rule` = "IMPORT_CONTAINMENT"
   - `path` = repo-relative path of the importing file
   - `detail` = the offending import specifier and where it resolves to

6. Wire scanner into `NodeTarget.scanBoundaryBypass()` method

7. Write tests:
   - Import within same block -> passes
   - Import from _shared -> passes
   - Import of BEAR-generated companion -> passes
   - Relative import escaping block root -> BOUNDARY_BYPASS
   - Import reaching sibling block -> BOUNDARY_BYPASS
   - Bare package import -> BOUNDARY_BYPASS
   - `#` alias import -> BOUNDARY_BYPASS
   - URL import -> BOUNDARY_BYPASS

## Outputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodeImportContainmentScanner.java`
- `kernel/src/main/java/com/bear/kernel/target/node/NodeImportSpecifierExtractor.java`
- `kernel/src/main/java/com/bear/kernel/target/node/NodeDynamicImportDetector.java`
- `kernel/src/main/java/com/bear/kernel/target/node/NodeImportBoundaryResolver.java`
- Modified `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java` (wire scanner)
- Test files with TypeScript fixture files

## Acceptance Criteria
- Clean same-block imports pass
- _shared imports pass
- BEAR-generated companion imports pass
- Escaping relative imports produce BOUNDARY_BYPASS findings
- Sibling block imports produce BOUNDARY_BYPASS findings
- All bare/alias/URL/self-name imports produce BOUNDARY_BYPASS findings
- Findings include repo-relative file path and offending specifier
- All tests pass

## Estimated Effort
2 hours
