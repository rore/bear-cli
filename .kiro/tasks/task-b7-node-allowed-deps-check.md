# Task B7: impl.allowedDeps Unsupported Check

## Phase Reference
Phase B: Node Target - Scan Only

## Spec Reference
`.kiro/specs/phase-b-node-target-scan-only.md` FR-B7

## Prerequisites
- Task B2 complete (NodeTarget registered)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java`
- `kernel/src/main/java/com/bear/kernel/ir/BearIr.java` (to check for impl.allowedDeps)
- `kernel/src/main/java/com/bear/kernel/target/Target.java` (blockDeclaresAllowedDeps method)

## Implementation Steps

1. Implement `NodeTarget.blockDeclaresAllowedDeps(Path irFile)`:
   - Parse the IR file and check for `impl.allowedDeps` section
   - Return true if present, false otherwise
   - This method is called by the check pipeline to guard against unsupported features

2. The check pipeline behavior when `blockDeclaresAllowedDeps` returns true for Node:
   - Fail with exit `64`
   - Error envelope:
     ```
     CODE=UNSUPPORTED_TARGET
     PATH=<ir-file-path>
     REMEDIATION=Remove impl.allowedDeps for node target, or switch to JVM target.
     ```

3. Ensure `pr-check` is not affected: pr-check does not call this guard

4. Write tests:
   - IR file without impl.allowedDeps: check proceeds normally
   - IR file with impl.allowedDeps under Node target: fails exit 64
   - Verify error envelope content (CODE, PATH, REMEDIATION)
   - Same IR with allowedDeps under JVM target: no failure (JVM supports it)

## Outputs
- Modified `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java` (blockDeclaresAllowedDeps)
- Test files

## Acceptance Criteria
- Block without impl.allowedDeps passes check under Node target
- Block with impl.allowedDeps fails with exit 64 under Node target
- Error output includes CODE=UNSUPPORTED_TARGET, IR file path, and remediation message
- pr-check is unaffected
- JVM target continues to support allowedDeps normally
- All tests pass

## Estimated Effort
1 hour
