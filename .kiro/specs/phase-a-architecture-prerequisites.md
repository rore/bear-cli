# Phase A: Architecture Prerequisites

## Overview

Architecture prerequisites that must land before any non-JVM target ships. These phases
establish the shared infrastructure that Node, Python, and all future targets depend on.
Currently, `TargetRegistry.resolve()` is hardcoded to return `JvmTarget`, `TargetId` has
only `JVM`, and there is no detection, locator, profile, or analyzer seam.

Source documents:
- `roadmap/ideas/future-multi-target-spec-design.md` (sections: Target Seam Contract, Canonical Locator Schema, Target vs Governance Profile, AnalyzerProvider interface)
- `roadmap/ideas/future-multi-target-expansion-plan.md` (section: Architecture prerequisites)
- `roadmap/ideas/future-python-implementation-context.md` (section: Architecture Seams to Implement)

## Anchoring Constraints

These constraints are frozen and apply to every phase:

1. **IR v1 is the boundary source of truth.** No `target:` field, no per-target IR additions.
2. **Exit code registry is frozen.** `0`, `2`, `4`, `5`, `6`, `64`, `74` only.
3. **CODE/PATH/REMEDIATION envelope is frozen.** Last three stderr lines always conform.
4. **JVM behavior must remain byte-identical.** Non-JVM work arrives behind the Target seam.
5. **No runtime policy engine additions.** BEAR is a static deterministic governance layer.
6. **Generated artifacts live under `build/generated/bear/`.** User-owned impl files are never overwritten.

## Functional Requirements

### FR-A1: TargetDetector Interface, DetectedTarget Result Model, and .bear/target.id Pin

**Requirement**: Define a `TargetDetector` interface that each target provides to determine
whether a given project root belongs to that target. Define a `DetectedTarget` result model
with three possible states. Define `.bear/target.id` pin file semantics for explicit target
override.

**Current State**:
- `TargetRegistry.java` (30 lines) hardcodes `targets.get(TargetId.JVM)` in `resolve()`
- No `TargetDetector` interface exists
- No pin file support exists

**Design Decisions**:
- `TargetDetector` is a single-method interface: `detect(Path projectRoot) -> DetectedTarget`
- `DetectedTarget` carries `targetId`, `confidence` (or status), and `reason` (human-readable)
- Three result states: `SUPPORTED` (high confidence match), `UNSUPPORTED` (recognized ecosystem
  but unsupported project shape, produces exit 64 with actionable remediation), `NONE` (not
  recognized, silent pass-through)
- Detectors must never silently "best guess" -- resolution is deterministic
- `.bear/target.id` contains exactly one of: `jvm`, `node`, `python`, `react` (no whitespace,
  no comments)
- When pin is present, it overrides auto-detection entirely -- no detectors run
- Invalid or unrecognized pin content fails with exit `2` using validation semantics
- Pin file is optional; auto-detection is the default path for single-target repos

**Constraints**:
- Detector results must be deterministic for the same project root
- `JvmTargetDetector` must be created wrapping the existing JVM detection logic so existing
  JVM projects continue to resolve identically

**New Types**:
- `kernel/.../target/TargetDetector.java` -- interface
- `kernel/.../target/DetectedTarget.java` -- result record or class
- `kernel/.../target/TargetPinFile.java` -- pin file reader/validator
- `kernel/.../target/jvm/JvmTargetDetector.java` -- JVM detector implementation

**Acceptance Criteria**:
- AC-A1.1: `TargetDetector` interface compiles with `detect(Path projectRoot)` returning `DetectedTarget`
- AC-A1.2: `DetectedTarget` exposes `targetId()`, `status()` (SUPPORTED/UNSUPPORTED/NONE), and `reason()`
- AC-A1.3: `TargetPinFile.read(Path bearDir)` returns the parsed target ID when `.bear/target.id` is valid
- AC-A1.4: `TargetPinFile.read()` throws or returns error for invalid content, mapped to exit `2`
- AC-A1.5: `TargetPinFile.read()` returns empty/absent when `.bear/target.id` does not exist
- AC-A1.6: `JvmTargetDetector.detect()` returns `SUPPORTED` for a directory containing `build.gradle`
- AC-A1.7: `JvmTargetDetector.detect()` returns `NONE` for a directory without JVM project signals
- AC-A1.8: Unit tests pass for pin file parsing (valid, invalid, missing) and JVM detection

### FR-A2: Refactor TargetRegistry.resolve() to Use Detectors

**Requirement**: Replace the hardcoded JVM return in `TargetRegistry.resolve(Path projectRoot)`
with a detection-based resolution pipeline that supports multiple registered detectors.

**Current State**:
- `TargetRegistry` has a `Map<TargetId, Target>` but `resolve()` ignores it and returns
  `targets.get(TargetId.JVM)` unconditionally
- Constructor requires JVM target to be present

**Design Decisions**:
- Resolution pipeline order:
  1. Check for `.bear/target.id` pin file in `projectRoot/.bear/target.id`
  2. If pin present and valid, look up target by pin ID -- skip all detectors
  3. If pin absent, run all registered detectors against projectRoot
  4. Exactly one `SUPPORTED` result -> use that target
  5. Zero `SUPPORTED` results -> fail exit `64`, `CODE=TARGET_NOT_DETECTED`
  6. Multiple `SUPPORTED` results -> fail exit `64`, `CODE=TARGET_AMBIGUOUS`
  7. Any `UNSUPPORTED` result blocks resolution even if another detector returns `SUPPORTED`,
     unless `.bear/target.id` pin explicitly overrides
- `TargetRegistry` constructor takes `List<TargetDetector>` in addition to `Map<TargetId, Target>`
- JVM-only projects must continue to resolve identically (byte-identical behavior)

**Constraints**:
- Existing callers of `TargetRegistry.resolve()` must not change
- `TargetRegistry.defaultRegistry()` must include `JvmTargetDetector` and produce identical
  behavior for JVM-only projects
- Error messages for TARGET_NOT_DETECTED and TARGET_AMBIGUOUS must include actionable remediation

**Files to Modify**:
- `kernel/.../target/TargetRegistry.java` -- refactor resolve()
- May need a new `TargetResolutionException` or similar for structured error reporting

**Acceptance Criteria**:
- AC-A2.1: `TargetRegistry.resolve()` returns `JvmTarget` for existing JVM projects (byte-identical)
- AC-A2.2: `TargetRegistry.resolve()` returns the correct target when `.bear/target.id` pin is present
- AC-A2.3: `TargetRegistry.resolve()` fails with exit `64` and `CODE=TARGET_NOT_DETECTED` when
  no detector matches
- AC-A2.4: `TargetRegistry.resolve()` fails with exit `64` and `CODE=TARGET_AMBIGUOUS` when
  multiple detectors match without a pin
- AC-A2.5: Invalid pin file produces exit `2`
- AC-A2.6: All existing JVM tests pass without modification

### FR-A3: Canonical Locator Schema Types

**Requirement**: Define canonical locator types that every finding carries alongside the
human-readable `PATH=...` string. This enables structured merging, deterministic ordering,
and future analyzer integration across all targets.

**Current State**:
- `UndeclaredReachFinding` has `String path, String surface`
- `BoundaryBypassFinding` has `String rule, String path, String detail`
- `TargetCheckIssue` has `TargetCheckIssueKind kind, String path, String remediation, String legacyLine`
- No structured locator exists

**Design Decisions**:
- Canonical locator shape (from spec-design doc):
  ```
  locator:
    repository: <repoRootId>
    project: <projectOrPackageId>
    module: <repoRelativeFilePath>
    symbol:
      kind: function|class|method|component|module|unknown
      name: <symbolName|null>
    span:
      startLine: <int|null>
      startColumn: <int|null>
      endLine: <int|null>
      endColumn: <int|null>
  ```
- File paths in locators are repo-relative and slash-normalized
- Missing symbol/span information is explicit null, never inferred silently
- Locator ordering in output remains deterministic
- Existing finding types gain an optional `CanonicalLocator` field (backward-compatible)

**New Types**:
- `kernel/.../target/locator/CanonicalLocator.java` -- top-level locator record
- `kernel/.../target/locator/LocatorSymbol.java` -- symbol record (kind enum + name)
- `kernel/.../target/locator/LocatorSpan.java` -- span record (4 nullable Integer fields)
- `kernel/.../target/locator/SymbolKind.java` -- enum (FUNCTION, CLASS, METHOD, COMPONENT, MODULE, UNKNOWN)

**Acceptance Criteria**:
- AC-A3.1: `CanonicalLocator` record compiles with all fields (repository, project, module, symbol, span)
- AC-A3.2: `LocatorSymbol` and `LocatorSpan` handle null fields correctly
- AC-A3.3: `CanonicalLocator.toString()` produces deterministic, human-readable output
- AC-A3.4: Existing finding types (`UndeclaredReachFinding`, `BoundaryBypassFinding`) can
  optionally carry a `CanonicalLocator` without breaking existing construction
- AC-A3.5: Locator construction with all-null optional fields does not throw

### FR-A4: Target/Profile Separation (GovernanceProfile)

**Requirement**: Introduce a `GovernanceProfile` concept that separates target identity
(runtime/toolchain) from governance shape (project contract). This prevents encoding
governance semantics in target identity alone and enables multiple governance shapes per
language over time (e.g., Python strict vs relaxed).

**Current State**:
- Target identity and governance shape are conflated in `TargetId`
- No profile concept exists

**Design Decisions**:
- `target` = runtime/toolchain and ecosystem (`jvm`, `node`, `python`, `react`)
- `profile` = governance contract for project shape
- Initial profile examples:
  - `jvm` target, `backend-service` profile
  - `node` target, `backend-service` profile
  - `python` target, `service` profile (strict)
  - `python` target, `service-relaxed` profile (pragmatic)
  - `react` target, `feature-ui` profile
- `GovernanceProfile` is a value object combining target ID and profile string
- `Target` interface gains a `defaultProfile()` method returning the target's default profile
- Optional `.bear/profile.id` pin file for explicit profile selection (future)
- For v1, each target has exactly one active profile; multi-profile selection is deferred

**New Types**:
- `kernel/.../target/GovernanceProfile.java` -- value object (TargetId target, String profileId)

**Files to Modify**:
- `kernel/.../target/Target.java` -- add `defaultProfile()` method (default implementation returns target-specific default)

**Acceptance Criteria**:
- AC-A4.1: `GovernanceProfile` compiles and pairs a `TargetId` with a profile string
- AC-A4.2: `Target.defaultProfile()` returns a sensible default for JVM (`backend-service`)
- AC-A4.3: `GovernanceProfile.equals()` and `hashCode()` work correctly for identical and different profiles
- AC-A4.4: JVM behavior is unchanged (profile is informational, not yet used in resolution)

### FR-A5: AnalyzerProvider SPI Draft Types

**Requirement**: Define the `AnalyzerProvider` service provider interface and `EvidenceBundle`
types as draft/placeholder types. These establish the second seam (evidence extraction) that
keeps BEAR as the policy engine while allowing simple analyzers first and richer analyzers later.
No integration with Target or check pipeline in this phase.

**Current State**:
- All analysis logic lives directly in JVM scanner classes (e.g., `UndeclaredReachScanner`,
  `BoundaryBypassScanner`, `PortImplContainmentScanner` in `kernel/.../target/jvm/`)
- No separation between evidence extraction and policy evaluation

**Design Decisions**:
- `AnalyzerProvider` interface (from spec-design doc):
  - `analyzerId()` returns `AnalyzerId`
  - `supports(TargetId targetId, GovernanceProfile profile)` returns boolean
  - `collectEvidence(Path projectRoot, List<Path> governedRoots, AnalysisOptions options)` returns `EvidenceBundle`
- `EvidenceBundle` contains:
  - `List<ImportEdge> imports`
  - `List<DependencyEdge> dependencies`
  - `List<OwnershipFact> ownership`
  - `List<ReferenceEdge> references` (optional, for future use)
  - `List<AnalyzerFinding> findings` (analyzer-native observations)
- Target consumes EvidenceBundle and maps it deterministically into BEAR checks/findings
- These are draft types -- compile and unit-test, but do not wire into any command path yet

**New Types**:
- `kernel/.../target/analyzer/AnalyzerProvider.java` -- interface
- `kernel/.../target/analyzer/AnalyzerId.java` -- value type
- `kernel/.../target/analyzer/EvidenceBundle.java` -- record
- `kernel/.../target/analyzer/ImportEdge.java` -- record
- `kernel/.../target/analyzer/DependencyEdge.java` -- record
- `kernel/.../target/analyzer/OwnershipFact.java` -- record
- `kernel/.../target/analyzer/ReferenceEdge.java` -- record
- `kernel/.../target/analyzer/AnalyzerFinding.java` -- record
- `kernel/.../target/analyzer/AnalysisOptions.java` -- options object

**Acceptance Criteria**:
- AC-A5.1: `AnalyzerProvider` interface compiles with all three methods
- AC-A5.2: `EvidenceBundle` record compiles with all fields and supports empty construction
- AC-A5.3: All evidence edge types (`ImportEdge`, `DependencyEdge`, `OwnershipFact`) compile
- AC-A5.4: No existing code paths are modified -- these are additive draft types only
- AC-A5.5: A simple unit test can construct an `EvidenceBundle` and read back its contents

## Python Forward Compatibility

Every type defined in Phase A is target-agnostic and directly reused by Python:
- **TargetDetector**: `PythonTargetDetector` will implement the same interface, checking for
  `pyproject.toml`, `uv.lock`/`poetry.lock`, `mypy.ini`, and `src/blocks/`
- **DetectedTarget**: Python uses the same SUPPORTED/UNSUPPORTED/NONE model. UNSUPPORTED
  covers workspace layout, flat layout, namespace packages, and ambiguous ecosystems.
- **.bear/target.id**: Pin value `python` will be registered
- **CanonicalLocator**: Python findings carry the same locator schema; `SymbolKind` includes
  `FUNCTION`, `CLASS`, `METHOD` which map directly to Python AST node types
- **GovernanceProfile**: Python requires two profiles (`python/service` strict and
  `python/service-relaxed`) sharing the same target, making the profile separation essential
- **AnalyzerProvider**: `PythonAnalyzerProvider` (`python-ast-native`) will implement the SPI
  using Python `ast` module parsing as the primary evidence source

Reference: `roadmap/ideas/future-python-implementation-context.md` sections: Architecture Seams
to Implement, Two Concentric Python Profiles, Python Target Detection.

## References

- `roadmap/ideas/future-multi-target-spec-design.md` -- Target Seam Contract (lines 98-182), Canonical Locator Schema (lines 206-232), Target vs Governance Profile (lines 185-204), AnalyzerProvider interface (lines 46-66)
- `roadmap/ideas/future-multi-target-expansion-plan.md` -- Architecture prerequisites (lines 334-344)
- `roadmap/ideas/future-python-implementation-context.md` -- Architecture Seams to Implement (lines 293-325), Implementation Phase Ordering (lines 383-399)
- Current implementation: `kernel/src/main/java/com/bear/kernel/target/TargetId.java`, `TargetRegistry.java`, `Target.java`
