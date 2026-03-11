# Task E1: NodeProjectTestRunner

## Phase Reference
Phase E: Node Target - Project Verification

## Spec Reference
`.kiro/specs/phase-e-node-project-verification.md` FR-E1

## Prerequisites
- Task B2 complete (NodeTarget registered)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java`
- `kernel/src/main/java/com/bear/kernel/target/jvm/ProjectTestRunner.java` (JVM pattern reference)
- `kernel/src/main/java/com/bear/kernel/target/ProjectTestResult.java`
- `kernel/src/main/java/com/bear/kernel/target/ProjectTestStatus.java`

## Implementation Steps

1. Create `NodeProjectTestRunner.java` in `kernel/src/main/java/com/bear/kernel/target/node/`:

2. Implement pnpm availability check:
   - Before running tsc, verify `pnpm` is available on PATH
   - If not found: return `ProjectTestResult` with `TOOL_MISSING` status
   - This maps to exit `74`

3. Implement tsc execution:
   - Command: `pnpm exec tsc --noEmit -p tsconfig.json`
   - Working directory: projectRoot
   - Capture stdout and stderr
   - Apply timeout (configurable, default to match JVM runner default)

4. Implement exit code mapping:
   - tsc exit 0: `ProjectTestStatus.PASSED`
   - tsc non-zero with type errors: `ProjectTestStatus.FAILED`
   - Process killed by timeout: `ProjectTestStatus.TIMEOUT`

5. Implement output tailing:
   - Capture last N lines of stderr for diagnostic reporting
   - Ensure output is deterministic (strip timestamps if tsc emits any)
   - Include error count summary if available from tsc output

6. Build `ProjectTestResult`:
   - `status`: from exit mapping above
   - `output`: tailed diagnostic output from tsc
   - `attemptTrail`: record of the command executed
   - JVM-specific fields (`firstLockLine`, `cacheMode`, `fallbackToUserCache`, etc.): set to null/empty/false
   - Consider whether `ProjectTestResult` needs refactoring for target-neutral fields
     (if so, document the refactoring need but use existing record for now)

7. Wire into `NodeTarget.runProjectVerification()`:
   - Replace the Phase B "SKIPPED" behavior with actual verification
   - The `initScriptRelativePath` parameter is JVM-specific; Node runner ignores it

8. Write tests:
   - Successful tsc run (mock): PASSED, exit 0
   - Failed tsc run with type errors (mock): FAILED, exit 4
   - Missing pnpm (mock): TOOL_MISSING, exit 74
   - Timeout (mock): TIMEOUT, exit 4
   - Diagnostic output includes relevant error lines
   - ProjectTestResult fields populated correctly

## Outputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodeProjectTestRunner.java`
- Modified `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java` (wire runner)
- Test files

## Acceptance Criteria
- Successful tsc -> PASSED, exit 0
- Type errors -> FAILED, exit 4
- Missing pnpm -> TOOL_MISSING, exit 74
- Timeout -> TIMEOUT, exit 4
- Diagnostic output includes tsc error lines
- ProjectTestResult populated with correct status and output
- Runner replaces the Phase B "SKIPPED" behavior
- All tests pass

## Estimated Effort
1-2 hours
