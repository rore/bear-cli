# Task A4: Target/Profile Separation (GovernanceProfile)

## Phase Reference
Phase A: Architecture Prerequisites

## Spec Reference
`.kiro/specs/phase-a-architecture-prerequisites.md` FR-A4

## Prerequisites
- Task A1 complete (TargetId enum exists with target identifiers)

## Inputs
- `kernel/src/main/java/com/bear/kernel/target/TargetId.java`
- `kernel/src/main/java/com/bear/kernel/target/Target.java`
- `kernel/src/main/java/com/bear/kernel/target/jvm/JvmTarget.java`

## Implementation Steps

1. Create `GovernanceProfile.java` in `kernel/src/main/java/com/bear/kernel/target/`:
   - Value object (record) with fields: `TargetId target`, `String profileId`
   - Override equals/hashCode (automatic with records)
   - toString: `"target/profileId"` format (e.g., `"jvm/backend-service"`)
   - Factory method: `of(TargetId target, String profileId)`

2. Add `defaultProfile()` method to `Target.java` interface:
   - Default implementation: `default GovernanceProfile defaultProfile() { return GovernanceProfile.of(targetId(), "default"); }`
   - This is a default method so existing implementations (JvmTarget) are not forced to override immediately

3. Override `defaultProfile()` in `JvmTarget`:
   - Return `GovernanceProfile.of(TargetId.JVM, "backend-service")`

4. Document known future profiles as constants or in comments:
   - `jvm/backend-service` (current JVM behavior)
   - `node/backend-service` (Node Phase B)
   - `python/service` (Python strict)
   - `python/service-relaxed` (Python pragmatic)
   - `react/feature-ui` (React future)

5. Write unit tests:
   - GovernanceProfile construction and equality
   - GovernanceProfile.toString() format
   - JvmTarget.defaultProfile() returns jvm/backend-service
   - Target.defaultProfile() default returns target/default for unoverridden implementations
   - Different profiles for same target are not equal

## Outputs
- `kernel/src/main/java/com/bear/kernel/target/GovernanceProfile.java`
- Modified `kernel/src/main/java/com/bear/kernel/target/Target.java` (added default method)
- Modified `kernel/src/main/java/com/bear/kernel/target/jvm/JvmTarget.java` (override defaultProfile)
- Test files

## Acceptance Criteria
- GovernanceProfile pairs TargetId with profile string correctly
- Target.defaultProfile() is a default method (no breaking changes to existing implementations)
- JvmTarget.defaultProfile() returns jvm/backend-service
- GovernanceProfile.equals() distinguishes same-target/different-profile correctly
- JVM behavior is unchanged (profile is informational only in this phase)
- All existing tests pass

## Estimated Effort
1 hour
