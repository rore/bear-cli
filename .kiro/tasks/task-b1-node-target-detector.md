# Task B1: NodeTargetDetector

## Phase Reference
Phase B: Node Target - Scan Only

## Spec Reference
`.kiro/specs/phase-b-node-target-scan-only.md` FR-B1

## Prerequisites
- Task A1 complete (TargetDetector interface, DetectedTarget)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/TargetDetector.java` (from A1)
- `kernel/src/main/java/com/bear/kernel/target/DetectedTarget.java` (from A1)
- `kernel/src/main/java/com/bear/kernel/target/TargetId.java` (needs NODE, but can reference future)

## Implementation Steps

1. Create `kernel/src/main/java/com/bear/kernel/target/node/` package

2. Create `NodeTargetDetector.java`:
   - Implements `TargetDetector`
   - `detect(Path projectRoot)` logic:
     a. Check for `pnpm-workspace.yaml` -- if present, return `UNSUPPORTED` with reason "Workspace layout not supported in first slice"
     b. Check for `package.json` -- if absent, return `NONE`
     c. Parse `package.json` minimally: check `"type"` field equals `"module"`, check `"packageManager"` field starts with `"pnpm@"`
     d. If package.json exists but fields are wrong, return `NONE`
     e. Check for `pnpm-lock.yaml` -- if absent, return `NONE`
     f. Check for `tsconfig.json` -- if absent, return `NONE`
     g. All checks pass: return `SUPPORTED` with targetId=NODE and reason "Node/TypeScript project detected"

3. Keep JSON parsing minimal: use available JSON library or simple string matching for the two fields

4. Write unit tests using temp directories:
   - Full valid Node project: all files present, correct fields -> SUPPORTED
   - Missing package.json -> NONE
   - package.json without "type": "module" -> NONE
   - package.json without "packageManager": "pnpm@..." -> NONE
   - Missing pnpm-lock.yaml -> NONE
   - Missing tsconfig.json -> NONE
   - pnpm-workspace.yaml present -> UNSUPPORTED
   - JVM project (build.gradle but no package.json) -> NONE

## First-Slice Scope Documentation

Add class-level Javadoc to `NodeTargetDetector.java` documenting:
- This is a first-slice detector covering ESM + pnpm + TypeScript projects only
- Intentional exclusions: CJS (NONE), npm (NONE), yarn (NONE), bun (NONE), workspaces (UNSUPPORTED), no TypeScript (NONE)
- Future slices may broaden detection to additional project shapes

## Outputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTargetDetector.java`
- Test file for NodeTargetDetector

## Acceptance Criteria
- Detector returns SUPPORTED only when all three files are present with correct package.json fields
- Detector returns UNSUPPORTED when pnpm-workspace.yaml is present
- Detector returns NONE for any missing required file or incorrect package.json content
- No false positive on JVM project directories
- All tests pass

## Estimated Effort
1 hour
