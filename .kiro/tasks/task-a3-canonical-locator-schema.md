# Task A3: Canonical Locator Schema Types

## Phase Reference
Phase A: Architecture Prerequisites

## Spec Reference
`.kiro/specs/phase-a-architecture-prerequisites.md` FR-A3

## Prerequisites
None (independent of A1/A2, can be done in parallel)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/UndeclaredReachFinding.java` (record: path, surface)
- `kernel/src/main/java/com/bear/kernel/target/BoundaryBypassFinding.java` (record: rule, path, detail)
- `kernel/src/main/java/com/bear/kernel/target/TargetCheckIssue.java` (record: kind, path, remediation, legacyLine)

## Implementation Steps

1. Create `kernel/src/main/java/com/bear/kernel/target/locator/` package

2. Create `SymbolKind.java` enum:
   - Values: `FUNCTION`, `CLASS`, `METHOD`, `COMPONENT`, `MODULE`, `UNKNOWN`

3. Create `LocatorSymbol.java` record:
   - Fields: `SymbolKind kind`, `String name` (nullable)

4. Create `LocatorSpan.java` record:
   - Fields: `Integer startLine`, `Integer startColumn`, `Integer endLine`, `Integer endColumn`
   - All fields nullable (explicit null, never inferred)

5. Create `CanonicalLocator.java` record:
   - Fields: `String repository`, `String project`, `String module`, `LocatorSymbol symbol`, `LocatorSpan span`
   - `module` is repo-relative, slash-normalized file path
   - `symbol` and `span` are nullable
   - Override `toString()` for deterministic human-readable output

6. Add optional `CanonicalLocator` to existing finding types (backward-compatible):
   - Option A: Add new constructors to existing records that accept an optional locator
   - Option B: Create extended versions (e.g., `LocatedUndeclaredReachFinding`) wrapping originals
   - Prefer Option A if Java records support compact canonical constructors with defaults

7. Write unit tests:
   - CanonicalLocator construction with all fields populated
   - CanonicalLocator construction with null symbol and span
   - LocatorSymbol with null name
   - LocatorSpan with all-null fields
   - Deterministic toString output
   - File path normalization (backslash to forward slash)

## Outputs
- `kernel/src/main/java/com/bear/kernel/target/locator/CanonicalLocator.java`
- `kernel/src/main/java/com/bear/kernel/target/locator/LocatorSymbol.java`
- `kernel/src/main/java/com/bear/kernel/target/locator/LocatorSpan.java`
- `kernel/src/main/java/com/bear/kernel/target/locator/SymbolKind.java`
- Test files in corresponding test directories

## Acceptance Criteria
- All locator types compile with correct field types and nullability
- `CanonicalLocator.toString()` produces deterministic output
- Null symbol and span fields are handled without exceptions
- Existing finding types can optionally carry a `CanonicalLocator` without breaking existing code
- All unit tests pass

## Estimated Effort
1 hour
