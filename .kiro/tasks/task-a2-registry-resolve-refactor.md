# Task A2: Refactor TargetRegistry.resolve() to Use Detectors

## Phase Reference
Phase A: Architecture Prerequisites

## Spec Reference
`.kiro/specs/phase-a-architecture-prerequisites.md` FR-A2

## Prerequisites
- Task A1 complete (TargetDetector, DetectedTarget, TargetPinFile, JvmTargetDetector)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java` (current: 30 lines, hardcoded JVM)
- `kernel/src/main/java/com/bear/kernel/target/TargetDetector.java` (from A1)
- `kernel/src/main/java/com/bear/kernel/target/DetectedTarget.java` (from A1)
- `kernel/src/main/java/com/bear/kernel/target/TargetPinFile.java` (from A1)
- `kernel/src/main/java/com/bear/kernel/target/jvm/JvmTargetDetector.java` (from A1)

## Implementation Steps

1. Modify `TargetRegistry` constructor to accept `List<TargetDetector>` alongside `Map<TargetId, Target>`:
   - Store detectors as an immutable list
   - Keep backward compatibility: if no detectors provided, default to the registered targets' implicit detection

2. Refactor `resolve(Path projectRoot)` with the detection pipeline:
   ```
   1. Compute bearDir = projectRoot.resolve(".bear")
   2. Check TargetPinFile.read(bearDir)
   3. If pin present and valid: return targets.get(pinTargetId), fail if target not registered
   4. If pin absent: run all registered detectors
   5. Collect results: filter SUPPORTED, check for UNSUPPORTED
   6. Exactly one SUPPORTED: return that target
   7. Zero SUPPORTED: throw TargetResolutionException (exit 64, TARGET_NOT_DETECTED)
   8. Multiple SUPPORTED without pin: throw TargetResolutionException (exit 64, TARGET_AMBIGUOUS)
   9. If any UNSUPPORTED result shares the same ecosystem family as a SUPPORTED result,
      block that SUPPORTED resolution (exit 64, remediation for unsupported shape)
  10. UNSUPPORTED results from unrelated ecosystem families are silently ignored
   ```

3. Create `TargetResolutionException.java` for structured error reporting:
   - Fields: `String code` (TARGET_NOT_DETECTED, TARGET_AMBIGUOUS), `String path`, `String remediation`
   - Maps to exit `64` in the CLI layer

4. Update `defaultRegistry()` to include `JvmTargetDetector` in the detector list

5. Write unit tests:
   - Single JVM project: resolve returns JvmTarget (byte-identical to current behavior)
   - Pin file overrides detection: `.bear/target.id` = "jvm" skips detectors
   - No detector matches: TargetResolutionException with TARGET_NOT_DETECTED
   - Multiple detectors match: TargetResolutionException with TARGET_AMBIGUOUS
   - Invalid pin file: exception mapped to exit 2
   - UNSUPPORTED from unrelated ecosystem (e.g., Python workspace) does not block a valid Node SUPPORTED resolution
   - Verify all existing JVM tests still pass unmodified

## Outputs
- Modified `kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java`
- New `kernel/src/main/java/com/bear/kernel/target/TargetResolutionException.java`
- Test files for new resolution logic

## Acceptance Criteria
- `TargetRegistry.resolve()` returns JvmTarget for existing JVM projects (byte-identical behavior)
- Pin file override works correctly
- TARGET_NOT_DETECTED fires with exit 64 when no detector matches
- TARGET_AMBIGUOUS fires with exit 64 when multiple detectors match
- All existing JVM tests pass without modification
- Error messages include actionable remediation text

## Estimated Effort
1-2 hours
