# Task A1: TargetDetector Interface, DetectedTarget, and .bear/target.id Pin

## Phase Reference
Phase A: Architecture Prerequisites

## Spec Reference
`.kiro/specs/phase-a-architecture-prerequisites.md` FR-A1

## Prerequisites
None (first task)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/Target.java` (existing interface)
- `kernel/src/main/java/com/bear/kernel/target/TargetId.java` (existing enum)
- `kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java` (existing registry)
- `kernel/src/main/java/com/bear/kernel/target/jvm/JvmTarget.java` (existing JVM detection signals)

## Implementation Steps

1. Create `TargetDetector.java` interface in `kernel/src/main/java/com/bear/kernel/target/`:
   ```java
   public interface TargetDetector {
       DetectedTarget detect(Path projectRoot);
   }
   ```

2. Create `DetectedTarget.java` in `kernel/src/main/java/com/bear/kernel/target/`:
   - Record or class with fields: `TargetId targetId`, `DetectionStatus status`, `String reason`
   - `DetectionStatus` enum: `SUPPORTED`, `UNSUPPORTED`, `NONE`
   - Factory methods: `supported(TargetId, String reason)`, `unsupported(TargetId, String reason)`, `none()`

3. Create `TargetPinFile.java` in `kernel/src/main/java/com/bear/kernel/target/`:
   - `static Optional<TargetId> read(Path bearDir)` -- reads `.bear/target.id`
   - Valid content: exactly one of `jvm`, `node`, `python`, `react` (trimmed, no whitespace)
   - File not found: return `Optional.empty()`
   - Invalid content: throw exception (mapped to exit `2` by callers)
   - Handle edge cases: empty file, whitespace-only, multiple lines

4. Create `JvmTargetDetector.java` in `kernel/src/main/java/com/bear/kernel/target/jvm/`:
   - Implements `TargetDetector`
   - Returns `SUPPORTED` when `build.gradle` or `build.gradle.kts` exists at project root
   - Returns `NONE` otherwise
   - Extract detection logic from what is currently implicit in `TargetRegistry`

5. Write unit tests:
   - `TargetPinFileTest.java`: valid pin (jvm, node), invalid pin (unknown, empty, whitespace), missing file
   - `DetectedTargetTest.java`: factory methods, status checks
   - `JvmTargetDetectorTest.java`: directory with build.gradle, directory without

## Outputs
- `kernel/src/main/java/com/bear/kernel/target/TargetDetector.java`
- `kernel/src/main/java/com/bear/kernel/target/DetectedTarget.java`
- `kernel/src/main/java/com/bear/kernel/target/DetectionStatus.java` (enum)
- `kernel/src/main/java/com/bear/kernel/target/TargetPinFile.java`
- `kernel/src/main/java/com/bear/kernel/target/jvm/JvmTargetDetector.java`
- Test files in corresponding test directories

## Acceptance Criteria
- All new types compile successfully
- `TargetDetector.detect()` returns `DetectedTarget` with correct status for JVM project roots
- `TargetPinFile.read()` correctly handles valid, invalid, and missing pin files
- `JvmTargetDetector` returns `SUPPORTED` for directories with `build.gradle`
- No existing code is modified (all new files)
- All unit tests pass

## Estimated Effort
1-2 hours
