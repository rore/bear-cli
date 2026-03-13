---
id: multi-target-foundation-phases
title: Multi-target Foundation — Phase A & B
status: in-progress
priority: high
commitment: committed
milestone: P2
---

## Purpose

This document tracks the foundational phases (A & B) for multi-target expansion. These phases
establish the architecture and first non-JVM target (Node/TypeScript) that enable future targets
(Python, .NET, React).

## Relation to Parked Multi-Target Documents

The broader multi-target vision is documented in parked roadmap items:
- `roadmap/ideas/future-multi-target-expansion-plan.md` — cross-target strategy and priority
- `roadmap/ideas/future-multi-target-spec-design.md` — architectural spec for all targets
- `roadmap/ideas/future-node-containment-profile.md` — Node containment profile
- `roadmap/ideas/future-python-containment-profile.md` — Python containment profile
- `roadmap/ideas/future-dotnet-containment-profile.md` — .NET containment profile
- `roadmap/ideas/future-react-containment-profile.md` — React containment profile

This document tracks the ACTIVE execution of Phase A and Phase B, which are the prerequisite
foundation for all future targets.

## Phase Structure

Multi-target expansion follows a phased approach:
- **Phase A**: Target detection and registry infrastructure (COMPLETE)
- **Phase B**: Node target scan-only (IN PROGRESS)
- **Phase C**: Node target runtime execution (PLANNED)
- **Phase D+**: Additional targets (Python, .NET, React) (PLANNED)

## Phase A: Target Detection and Registry (COMPLETE)

### Purpose
Establish target-agnostic detection and registry infrastructure before adding any non-JVM target.

### Scope
- `TargetDetector` interface and detection result model (`SUPPORTED`/`UNSUPPORTED`/`NONE`)
- `DetectedTarget` data model
- `TargetRegistry` refactoring for multi-target dispatch
- `.bear/target.id` pin file support for ambiguous projects
- `CanonicalLocator` for structured finding locators
- `GovernanceProfile` for target-specific governance shapes
- `TargetId.NODE` enum value (no implementation yet)

### Acceptance Criteria
- [x] `TargetDetector` interface defined
- [x] `DetectedTarget` model with `SUPPORTED`/`UNSUPPORTED`/`NONE` status
- [x] `TargetRegistry.resolve()` uses detector chain
- [x] `.bear/target.id` pin file support
- [x] `CanonicalLocator` for structured finding locators
- [x] `GovernanceProfile` model
- [x] All existing JVM tests pass without modification
- [x] JVM behavior remains byte-identical

### Deliverables
- `kernel/src/main/java/com/bear/kernel/target/TargetDetector.java`
- `kernel/src/main/java/com/bear/kernel/target/DetectedTarget.java`
- `kernel/src/main/java/com/bear/kernel/target/DetectionStatus.java`
- `kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java` (refactored)
- `kernel/src/main/java/com/bear/kernel/target/TargetPinFile.java`
- `kernel/src/main/java/com/bear/kernel/target/locator/CanonicalLocator.java`
- `kernel/src/main/java/com/bear/kernel/target/GovernanceProfile.java`
- `kernel/src/main/java/com/bear/kernel/target/TargetId.java` (added `NODE`)

### Status
✅ COMPLETE

## Phase B: Node Target — Scan Only (IN PROGRESS)

### Purpose
Implement first non-JVM target (Node/TypeScript) with scan-only capabilities: detection,
artifact generation, import containment, and drift checking. No runtime execution yet.

### Scope
- `NodeTarget` implementing `Target` interface
- `NodeTargetDetector` for ESM + pnpm + TypeScript projects
- TypeScript artifact generation (Ports.ts, Logic.ts, Wrapper.ts, wiring.json)
- Governed roots computation (`src/blocks/<blockKey>/`, `src/blocks/_shared/`)
- Import containment scanning (static imports only)
- Drift gate for generated artifacts
- `impl.allowedDeps` unsupported guard (exit 64)

### Out of Scope (Phase C)
- Runtime execution
- Dynamic `import()` resolution
- TypeScript path alias resolution
- Workspace/monorepo layouts
- npm/yarn package managers
- CommonJS projects

### Acceptance Criteria
- [ ] `NodeTarget` implements all required `Target` methods
- [ ] `NodeTargetDetector` detects valid Node/TypeScript projects
- [ ] TypeScript artifacts generated correctly (parseable by `tsc`)
- [ ] Import containment enforced (exit 7 on boundary bypass)
- [ ] Drift gate detects modified generated files (exit 5)
- [ ] `impl.allowedDeps` fails with exit 64 for Node target
- [ ] All existing JVM tests pass without modification
- [ ] JVM behavior remains byte-identical
- [ ] 36 correctness properties pass (jqwik, 100+ iterations each)
- [ ] Fixture projects compile and check successfully
- [ ] Node fixture fails `check` on boundary bypass (exit 7)
- [ ] Node fixture fails `check` on drift (exit 5)
- [ ] Node fixture with `allowedDeps` fails `check` (exit 64)

### Deliverables

**Implementation:**
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTarget.java`
- `kernel/src/main/java/com/bear/kernel/target/node/NodeTargetDetector.java`
- `kernel/src/main/java/com/bear/kernel/target/node/TypeScriptLexicalSupport.java`
- `kernel/src/main/java/com/bear/kernel/target/node/TypeScriptTypeMapper.java`
- `kernel/src/main/java/com/bear/kernel/target/node/TypeScriptArtifactGenerator.java`
- `kernel/src/main/java/com/bear/kernel/target/node/TypeScriptManifestGenerator.java`
- `kernel/src/main/java/com/bear/kernel/target/node/NodeImportSpecifierExtractor.java`
- `kernel/src/main/java/com/bear/kernel/target/node/NodeDynamicImportDetector.java`
- `kernel/src/main/java/com/bear/kernel/target/node/NodeImportBoundaryResolver.java`
- `kernel/src/main/java/com/bear/kernel/target/node/NodeImportContainmentScanner.java`
- `kernel/src/main/java/com/bear/kernel/target/node/BoundaryDecision.java`

**Tests:**
- `kernel/src/test/java/com/bear/kernel/target/node/NodeTargetTest.java`
- `kernel/src/test/java/com/bear/kernel/target/node/NodeTargetDetectorTest.java`
- `kernel/src/test/java/com/bear/kernel/target/node/TypeScriptArtifactGeneratorTest.java`
- `kernel/src/test/java/com/bear/kernel/target/node/NodeImportSpecifierExtractorTest.java`
- `kernel/src/test/java/com/bear/kernel/target/node/NodeDynamicImportDetectorTest.java`
- `kernel/src/test/java/com/bear/kernel/target/node/NodeImportBoundaryResolverTest.java`
- `kernel/src/test/java/com/bear/kernel/target/node/NodeImportContainmentScannerTest.java`
- `kernel/src/test/java/com/bear/kernel/target/node/properties/NodeDetectionProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/node/properties/ArtifactGenerationProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/node/properties/GovernedRootsProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/node/properties/ImportContainmentProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/node/properties/DriftGateProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/node/properties/AllowedDepsGuardProperties.java`

**Fixtures:**
- `kernel/src/test/resources/fixtures/node/valid-single-block/`
- `kernel/src/test/resources/fixtures/node/valid-multi-block/`
- `kernel/src/test/resources/fixtures/node/valid-with-shared/`
- `kernel/src/test/resources/fixtures/node/invalid-workspace/`
- `kernel/src/test/resources/fixtures/node/invalid-missing-lockfile/`
- `kernel/src/test/resources/fixtures/node/boundary-bypass-escape/`
- `kernel/src/test/resources/fixtures/node/boundary-bypass-sibling/`
- `kernel/src/test/resources/fixtures/node/boundary-bypass-bare-import/`

**Specs:**
- `.kiro/specs/phase-b-node-target-scan-only/requirements.md`
- `.kiro/specs/phase-b-node-target-scan-only/design.md`
- `.kiro/specs/phase-b-node-target-scan-only/tasks.md`

### Status
🚧 IN PROGRESS

Current task: Task 1 - NodeTargetDetector

### Session Notes
- Phase B spec created (requirements, design, tasks)
- Execution guidance added to tasks (Context/Execution/Verification per task)
- Session hygiene reminders added to major task verification fields
- Spec authoring templates created in `.kiro/specs/_templates/`

## Phase C: Node Target — Runtime Execution (PLANNED)

### Purpose
Add runtime execution capabilities to Node target: project verification, dynamic import
resolution, and full end-to-end workflow support.

### Scope (Preliminary)
- Project verification via `pnpm exec tsc --noEmit`
- Dynamic `import()` enforcement (Phase B detects, Phase C enforces)
- TypeScript path alias resolution (if feasible)
- Full `bear check` workflow with runtime validation
- Integration with existing CI/CD patterns

### Status
📋 PLANNED (spec not yet created)

## Phase D+: Additional Targets (PLANNED)

### Recommended Order
Based on `roadmap/ideas/future-multi-target-expansion-plan.md`:

1. **Node** (Phase B/C) — strongest non-JVM containment story
2. **Python or .NET** (strategy-dependent)
   - .NET: strongest static containment, best technical readiness
   - Python: highest commercial demand, weaker containment guarantees
3. **Remaining target** (Python or .NET)
4. **React** (last) — weakest containment, requires product direction decision

### Status
📋 PLANNED (detailed specs in `roadmap/ideas/`)

## Documentation Updates Needed

When Phase B completes:
- [ ] Update `docs/context/architecture.md` to mention multi-target support
- [ ] Update `docs/context/roadmap.md` if milestone definitions change
- [ ] Update `docs/public/OVERVIEW.md` to mention Node target support
- [ ] Update `docs/public/TERMS.md` if new terminology introduced
- [ ] Consider adding `docs/public/targets-node.md` for Node-specific guidance

## Related Documents

- `roadmap/ideas/future-multi-target-expansion-plan.md` — cross-target strategy
- `roadmap/ideas/future-multi-target-spec-design.md` — architectural spec
- `roadmap/ideas/future-node-containment-profile.md` — Node containment profile
- `.kiro/specs/phase-b-node-target-scan-only/` — Phase B detailed spec
- `docs/context/architecture.md` — core architecture principles
- `docs/context/ir-spec.md` — IR v1 contract (unchanged by multi-target)
