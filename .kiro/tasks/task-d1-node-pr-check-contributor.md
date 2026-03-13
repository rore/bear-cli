# Task D1: NodePrCheckContributor

## Phase Reference
Phase D: Node Target - Dependency Governance

## Spec Reference
`.kiro/specs/phase-d-node-dependency-governance.md` FR-D1

## Prerequisites
- Task B2 complete (NodeTarget registered, can be resolved for Node projects)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java`
- Existing pr-check infrastructure in `app/` (study the JVM pr-check flow to understand integration points)
- `kernel/src/main/java/com/bear/kernel/target/jvm/` (JVM pr-check patterns)

## Implementation Steps

1. Study the existing pr-check pipeline:
   - Read the JVM pr-check flow to understand how boundary-expansion is classified
   - Identify the integration point where target-specific pr-check logic plugs in
   - Understand how base-vs-head diff is computed and accessed

2. Create `NodePrCheckContributor.java` in `kernel/src/main/java/com/bear/kernel/target/node/`:
   - Or integrate directly into NodeTarget methods used by the pr-check pipeline

3. Implement package.json dependency diff analysis:
   - Parse package.json from base and head versions
   - Compare `dependencies`, `devDependencies`, `peerDependencies`, `optionalDependencies` sections
   - Detect additions: new key in head not in base -> BOUNDARY_EXPANDING
   - Detect version changes: key exists in both but version differs -> BOUNDARY_EXPANDING
   - Detect removals: key in base but not in head -> ORDINARY (not boundary-expanding)

4. Implement pnpm-lock.yaml change detection:
   - File-level comparison: any content difference -> BOUNDARY_EXPANDING
   - This is a coarse-grained check (content hash or byte comparison)

5. Classify the overall result:
   - If any boundary-expanding change found: exit `5`
   - If only ordinary changes or no changes: exit `0`
   - Multiple boundary-expanding changes produce a single exit `5` with all findings listed

6. Produce error envelope for boundary-expanding results:
   ```
   CODE=BOUNDARY_EXPANDING
   PATH=package.json (or pnpm-lock.yaml)
   REMEDIATION=Review new or changed dependencies before merging.
   ```

7. Write tests:
   - New dependency added: exit 5
   - Dependency version bumped: exit 5
   - New devDependency added: exit 5
   - pnpm-lock.yaml changed: exit 5
   - Dependency removed: exit 0
   - No changes: exit 0
   - Changes to scripts/name/version in package.json only: exit 0
   - Multiple expanding changes: single exit 5 with multiple findings

## Outputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodePrCheckContributor.java` (or integrated into NodeTarget)
- Test files

## Acceptance Criteria
- Dependency additions produce exit 5
- Version bumps produce exit 5
- Lock file changes produce exit 5
- Removals produce exit 0
- Non-dependency package.json changes produce exit 0
- Error envelope follows CODE/PATH/REMEDIATION format
- All tests pass

## Estimated Effort
2 hours
