# Task C1: NodeUndeclaredReachScanner

## Phase Reference
Phase C: Node Target - Undeclared Reach

## Spec Reference
`.kiro/specs/phase-c-node-undeclared-reach.md` FR-C1

## Prerequisites
- Task B4 complete (governed roots computation)
- Task B5 complete (import specifier extraction pattern established)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java`
- `kernel/src/main/java/com/bear/kernel/target/node/NodeImportContainmentScanner.java` (import extraction pattern)
- `kernel/src/main/java/com/bear/kernel/target/UndeclaredReachFinding.java` (record: path, surface)
- `kernel/src/main/java/com/bear/kernel/target/jvm/UndeclaredReachScanner.java` (JVM pattern reference)

## Implementation Steps

1. Create `NodeUndeclaredReachScanner.java` in `kernel/src/main/java/com/bear/kernel/target/node/`:

2. Define the covered built-in set as a static constant:
   ```java
   private static final Set<String> COVERED_BUILTINS = Set.of(
       "node:http", "http",
       "node:https", "https",
       "node:net", "net",
       "node:child_process", "child_process",
       "node:fs", "fs",
       "node:fs/promises", "fs/promises"
   );
   ```

3. Scan governed `.ts` files for import specifiers matching the covered set:
   - Reuse the import specifier extraction logic from NodeImportContainmentScanner
   - Check each extracted specifier against COVERED_BUILTINS
   - Both `node:` prefixed and bare forms are detected

4. Produce findings:
   - `UndeclaredReachFinding(path, surface)` for each match
   - `path` = repo-relative file path
   - `surface` = the covered module name (e.g., "node:http" or "http")
   - Findings are deterministically ordered: by file path, then by import specifier

5. Wire scanner into `NodeTarget.scanUndeclaredReach()` method

6. Write tests:
   - `import http from 'node:http'` -> finding with surface "node:http"
   - `import http from 'http'` -> finding with surface "http"
   - `import { createServer } from 'node:https'` -> finding with surface "node:https"
   - `import fs from 'node:fs'` -> finding
   - `import { readFile } from 'fs/promises'` -> finding
   - `import path from 'node:path'` -> no finding (not in covered set)
   - `import express from 'express'` -> no finding (third-party, handled by containment)
   - Import in non-governed file -> no finding
   - Multiple findings in one file: deterministic ordering
   - Verify exit code is 6 when findings present

## Outputs
- `kernel/src/main/java/com/bear/kernel/target/node/NodeUndeclaredReachScanner.java`
- Modified `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java` (wire scanner)
- Test files with TypeScript fixture files

## Acceptance Criteria
- Each covered built-in (both node: and bare forms) is detected
- Non-covered modules do not produce findings
- Non-governed files are not scanned
- Findings are deterministically ordered
- Exit code is 6 when undeclared reach is detected
- All tests pass

## Estimated Effort
1-2 hours
