# Task B3: Node Compile Output (TypeScript Artifact Generation)

## Phase Reference
Phase B: Node Target - Scan Only

## Spec Reference
`.kiro/specs/phase-b-node-target-scan-only.md` FR-B3

## Prerequisites
- Task B2 complete (NodeTarget stub registered in TargetRegistry)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java` (stub from B2)
- `kernel/src/main/java/com/bear/kernel/target/jvm/JvmTarget.java` (pattern reference for compile flow)
- `kernel/src/main/java/com/bear/kernel/target/WiringManifest.java` (existing manifest record)
- `kernel/src/main/java/com/bear/kernel/ir/BearIr.java` (IR input for compile)

## Implementation Steps

1. Implement `NodeTarget.compile(BearIr ir, Path projectRoot, String blockKey)`:
   - Compute block name from blockKey (PascalCase from kebab-case or as-is)
   - Generate `build/generated/bear/types/<blockKey>/<BlockName>Ports.ts`:
     - TypeScript interface declaring port types derived from `ir.effects().allow()` entries
   - Generate `build/generated/bear/types/<blockKey>/<BlockName>Logic.ts`:
     - TypeScript interface for logic contract from IR inputs/outputs
   - Generate `build/generated/bear/types/<blockKey>/<BlockName>Wrapper.ts`:
     - Wrapper shell with wiring factory connecting ports, logic, and impl
   - Generate `build/generated/bear/wiring/<blockKey>.wiring.json`:
     - Wiring manifest adapted for TypeScript paths
   - Create user-owned `src/blocks/<blockKey>/impl/<BlockName>Impl.ts` only if absent

2. Implement `NodeTarget.generateWiringOnly(BearIr ir, Path projectRoot, Path outputRoot, String blockKey)`:
   - Generate only the wiring manifest (no user-impl creation)
   - Used by pr-check

3. Implement `NodeTarget.parseWiringManifest(Path path)`:
   - Parse the JSON wiring manifest and return a WiringManifest record
   - Reuse existing JSON parsing patterns from JvmTarget

4. Implement `NodeTarget.ownedGeneratedPrefixes(String blockName)`:
   - Return the set of BEAR-owned generated file prefixes for Node target

5. Create helper classes as needed:
   - `NodeRenderUnits.java` for TypeScript code generation templates
   - `NodeLexicalSupport.java` for blockKey-to-PascalCase conversion

6. Write tests:
   - Compile a minimal IR and verify generated file structure
   - Verify generated TypeScript is syntactically reasonable
   - Verify wiring manifest JSON structure
   - Verify user-owned impl created only when absent
   - Verify re-compile does not overwrite existing user impl

## Outputs
- Modified `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java` (compile methods implemented)
- New `kernel/src/main/java/com/bear/kernel/target/node/NodeRenderUnits.java` (or similar)
- New `kernel/src/main/java/com/bear/kernel/target/node/NodeLexicalSupport.java` (or similar)
- Test files with golden file fixtures

## Acceptance Criteria
- compile() generates Ports.ts, Logic.ts, Wrapper.ts under build/generated/bear/types/<blockKey>/
- compile() generates wiring manifest at build/generated/bear/wiring/<blockKey>.wiring.json
- compile() creates user-owned impl at src/blocks/<blockKey>/impl/<BlockName>Impl.ts when absent
- Re-compile does not overwrite existing user-owned impl
- generateWiringOnly() generates only the wiring manifest
- Generated TypeScript files are syntactically valid
- All tests pass

## Estimated Effort
2 hours
