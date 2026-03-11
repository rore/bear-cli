# Task B4: Node Governed Roots

## Phase Reference
Phase B: Node Target - Scan Only

## Spec Reference
`.kiro/specs/phase-b-node-target-scan-only.md` FR-B4

## Prerequisites
- Task B2 complete (NodeTarget stub exists)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java`
- `kernel/src/main/java/com/bear/kernel/target/WiringManifest.java` (governedSourceRoots field)

## Implementation Steps

1. Implement governed root methods in `NodeTarget`:
   - Compute governed roots based on blockKey and project root
   - Block root: `src/blocks/<blockKey>/`
   - Shared root: `src/blocks/_shared/` (optional, only if directory exists)
   - File filter: `**/*.ts` (TypeScript files only)
   - Exclude: `**/*.test.ts`, `**/*.spec.ts` (test files)

2. Implement `NodeTarget.sharedContainmentInScope(Path projectRoot)`:
   - Return true if `src/blocks/_shared/` directory exists at projectRoot

3. Implement `NodeTarget.considerContainmentSurfaces(BearIr ir, Path projectRoot)`:
   - Return true (Node target always considers containment in governed roots)

4. Ensure governed roots are used consistently by:
   - Wiring manifest `governedSourceRoots` field
   - Import containment scanner (Task B5)
   - Undeclared reach scanner (Task C1)

5. Write tests:
   - Single block: governed roots include src/blocks/<blockKey>/
   - With _shared: governed roots include src/blocks/_shared/
   - Without _shared: governed roots exclude _shared
   - Files outside src/blocks/ not included
   - .test.ts files excluded from governance

## Outputs
- Modified `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java` (governed root methods)
- Test files with fixture directory structures

## Acceptance Criteria
- Governed roots for single block correctly computed
- _shared directory included when present, excluded when absent
- Test files (*.test.ts) excluded from governance
- Non-src/blocks/ files excluded
- All tests pass

## Estimated Effort
1 hour
