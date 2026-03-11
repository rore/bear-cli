# Task B2: NODE in TargetId and NodeTarget Registration

## Phase Reference
Phase B: Node Target - Scan Only

## Spec Reference
`.kiro/specs/phase-b-node-target-scan-only.md` FR-B2

## Prerequisites
- Task A1 complete (TargetDetector, DetectedTarget)
- Task A2 complete (refactored TargetRegistry with detector support)
- Task B1 complete (NodeTargetDetector)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/TargetId.java` (current: JVM only)
- `kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java` (refactored in A2)
- `kernel/src/main/java/com/bear/kernel/target/Target.java` (interface to implement)
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTargetDetector.java` (from B1)

## Implementation Steps

1. Add `NODE("node")` to `TargetId.java`:
   ```java
   public enum TargetId {
       JVM("jvm"),
       NODE("node");
       // ... existing code
   }
   ```

2. Create `NodeTarget.java` in `kernel/src/main/java/com/bear/kernel/target/node/`:
   - Implements `Target` interface
   - `targetId()` returns `TargetId.NODE`
   - `defaultProfile()` returns `GovernanceProfile.of(TargetId.NODE, "backend-service")`
   - All other methods throw `UnsupportedOperationException("NodeTarget.<methodName> not yet implemented (Phase B3/B4/B5)")`
   - This is a stub that will be filled in by subsequent tasks

3. Update `TargetRegistry.defaultRegistry()` to register NodeTarget and NodeTargetDetector:
   - Add NODE target to the targets map
   - Add NodeTargetDetector to the detectors list
   - Keep JVM as default behavior for existing projects

4. Write unit tests:
   - `TargetId.NODE.value()` returns `"node"`
   - `NodeTarget.targetId()` returns `TargetId.NODE`
   - `TargetRegistry.defaultRegistry()` resolves JvmTarget for JVM project (unchanged)
   - `TargetRegistry.defaultRegistry()` resolves NodeTarget for Node project fixture
   - All existing JVM tests pass without modification

## Outputs
- Modified `kernel/src/main/java/com/bear/kernel/target/TargetId.java` (add NODE)
- New `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java` (stub)
- Modified `kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java` (register NODE)
- Test files

## Acceptance Criteria
- TargetId.NODE exists with value "node"
- NodeTarget implements Target interface (compiles)
- TargetRegistry.defaultRegistry() includes both JVM and NODE targets
- JVM project resolution is unchanged (all existing tests pass)
- Node fixture project resolves to NodeTarget

## Estimated Effort
1 hour
