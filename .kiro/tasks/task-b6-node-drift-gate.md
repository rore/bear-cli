# Task B6: Node Drift Gate

## Phase Reference
Phase B: Node Target - Scan Only

## Spec Reference
`.kiro/specs/phase-b-node-target-scan-only.md` FR-B6

## Prerequisites
- Task B3 complete (Node compile generates artifacts)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java` (compile output)
- `kernel/src/main/java/com/bear/kernel/target/TargetCheckIssue.java`
- `kernel/src/main/java/com/bear/kernel/target/TargetCheckIssueKind.java` (DRIFT_DETECTED, DRIFT_MISSING_BASELINE)

## Implementation Steps

1. Implement drift checking in `NodeTarget.prepareCheckWorkspace()`:
   - Copy generated artifacts to a temp workspace for comparison
   - The check pipeline will compare workspace artifacts against freshly generated output

2. Drift-checked file set:
   - `build/generated/bear/types/<blockKey>/<BlockName>Ports.ts`
   - `build/generated/bear/types/<blockKey>/<BlockName>Logic.ts`
   - `build/generated/bear/types/<blockKey>/<BlockName>Wrapper.ts`
   - `build/generated/bear/wiring/<blockKey>.wiring.json`

3. Drift detection outcomes:
   - Files match: no drift (check passes)
   - File content differs: `DRIFT_DETECTED` TargetCheckIssue
   - Expected generated file missing: `DRIFT_MISSING_BASELINE` TargetCheckIssue

4. User-owned files (`src/blocks/<blockKey>/impl/<BlockName>Impl.ts`) must NOT be drift-checked

5. Reuse or follow the existing JVM drift checking pattern from the check pipeline

6. Write tests:
   - Clean state after compile: no drift
   - Modify a generated .ts file: DRIFT_DETECTED
   - Delete a generated file: DRIFT_MISSING_BASELINE
   - Modify user-owned impl: no drift finding (not checked)

## Outputs
- Modified `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java` (drift methods)
- Test files

## Acceptance Criteria
- Freshly compiled state produces zero drift findings
- Modified generated file triggers DRIFT_DETECTED
- Missing generated file triggers DRIFT_MISSING_BASELINE
- User-owned impl files are not drift-checked
- All tests pass

## Estimated Effort
1 hour
