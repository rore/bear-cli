# Task A5: AnalyzerProvider SPI Draft Types

## Phase Reference
Phase A: Architecture Prerequisites

## Spec Reference
`.kiro/specs/phase-a-architecture-prerequisites.md` FR-A5

## Prerequisites
- Task A4 complete (GovernanceProfile type exists for supports() method parameter)
- Task A1 complete (TargetId exists)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/TargetId.java`
- `kernel/src/main/java/com/bear/kernel/target/GovernanceProfile.java` (from A4)
- `kernel/src/main/java/com/bear/kernel/target/jvm/UndeclaredReachScanner.java` (pattern reference)
- `kernel/src/main/java/com/bear/kernel/target/jvm/BoundaryBypassScanner.java` (pattern reference)

## Implementation Steps

1. Create `kernel/src/main/java/com/bear/kernel/target/analyzer/` package

2. Create `AnalyzerId.java`:
   - Simple value type (record) wrapping a String identifier
   - e.g., `"jvm-source-native"`, `"node-import-native"`, `"python-ast-native"`

3. Create `AnalysisOptions.java`:
   - Record with fields for analysis configuration
   - Initial fields: `boolean includeReferences` (default false), `int maxDepth` (default 1)

4. Create evidence edge types:
   - `ImportEdge.java`: record with `String sourceModule`, `String targetModule`, `String specifier`, `CanonicalLocator locator`
   - `DependencyEdge.java`: record with `String packageName`, `String version`, `String scope` (dependencies/devDependencies/etc.)
   - `OwnershipFact.java`: record with `String module`, `String ownerBlock`, `CanonicalLocator locator`
   - `ReferenceEdge.java`: record with `String sourceSymbol`, `String targetSymbol`, `CanonicalLocator locator` (optional/future)
   - `AnalyzerFinding.java`: record with `String code`, `String message`, `CanonicalLocator locator`

5. Create `EvidenceBundle.java`:
   - Record with fields: `List<ImportEdge> imports`, `List<DependencyEdge> dependencies`, `List<OwnershipFact> ownership`, `List<ReferenceEdge> references`, `List<AnalyzerFinding> findings`
   - Static factory: `empty()` returning bundle with all empty lists
   - Builder pattern or static factory for convenient construction

6. Create `AnalyzerProvider.java` interface:
   ```java
   public interface AnalyzerProvider {
       AnalyzerId analyzerId();
       boolean supports(TargetId targetId, GovernanceProfile profile);
       EvidenceBundle collectEvidence(Path projectRoot, List<Path> governedRoots, AnalysisOptions options);
   }
   ```

7. Write unit tests:
   - EvidenceBundle.empty() construction
   - EvidenceBundle with populated lists
   - ImportEdge, DependencyEdge, OwnershipFact construction
   - AnalyzerId equality
   - Verify all types are draft-only: no integration with Target or command pipeline

## Outputs
- `kernel/src/main/java/com/bear/kernel/target/analyzer/AnalyzerProvider.java`
- `kernel/src/main/java/com/bear/kernel/target/analyzer/AnalyzerId.java`
- `kernel/src/main/java/com/bear/kernel/target/analyzer/AnalysisOptions.java`
- `kernel/src/main/java/com/bear/kernel/target/analyzer/EvidenceBundle.java`
- `kernel/src/main/java/com/bear/kernel/target/analyzer/ImportEdge.java`
- `kernel/src/main/java/com/bear/kernel/target/analyzer/DependencyEdge.java`
- `kernel/src/main/java/com/bear/kernel/target/analyzer/OwnershipFact.java`
- `kernel/src/main/java/com/bear/kernel/target/analyzer/ReferenceEdge.java`
- `kernel/src/main/java/com/bear/kernel/target/analyzer/AnalyzerFinding.java`
- Test files

## Acceptance Criteria
- All types compile successfully
- AnalyzerProvider interface has all three methods with correct signatures
- EvidenceBundle.empty() returns a bundle with all empty lists without exception
- No existing code paths are modified (all additive)
- A test can construct an EvidenceBundle, add edges, and read them back
- Types use CanonicalLocator from Task A3 where applicable

## Estimated Effort
1-2 hours
