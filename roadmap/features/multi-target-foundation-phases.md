---
id: multi-target-foundation-phases
title: Multi-target Foundation — Phases A, B, P, P2
status: in-progress (Phase P2 complete)
priority: high
commitment: committed
milestone: P2
---

## Purpose

This document tracks the foundational phases (A, B, P) for multi-target expansion. These phases
establish the architecture and first two non-JVM targets (Node/TypeScript, Python) that enable
future targets (.NET, React).

## Relation to Parked Multi-Target Documents

The broader multi-target vision is documented in parked roadmap items:
- `roadmap/ideas/future-multi-target-expansion-plan.md` — cross-target strategy and priority
- `roadmap/ideas/future-multi-target-spec-design.md` — architectural spec for all targets
- `roadmap/ideas/future-node-containment-profile.md` — Node containment profile
- `roadmap/ideas/future-python-containment-profile.md` — Python containment profile
- `roadmap/ideas/future-python-implementation-context.md` — Python implementation summary
- `roadmap/ideas/future-dotnet-containment-profile.md` — .NET containment profile
- `roadmap/ideas/future-react-containment-profile.md` — React containment profile

This document tracks the ACTIVE execution of Phases A, B, and P.

## Phase Structure

Multi-target expansion follows a phased approach:
- **Phase A**: Target detection and registry infrastructure (COMPLETE)
- **Phase B**: Node target scan-only (COMPLETE)
- **Phase P**: Python target scan-only (SPEC COMPLETE, IMPLEMENTATION PENDING)
- **Phase C**: Node target runtime execution (PLANNED)
- **Phase D+**: Additional targets (.NET, React) (PLANNED)

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

## Phase B: Node Target — Scan Only (COMPLETE)

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
- [x] `NodeTarget` implements all required `Target` methods
- [x] `NodeTargetDetector` detects valid Node/TypeScript projects
- [x] TypeScript artifacts generated correctly (parseable by `tsc`)
- [x] Import containment enforced (exit 7 on boundary bypass)
- [x] Drift gate detects modified generated files (exit 5)
- [x] `impl.allowedDeps` fails with exit 64 for Node target
- [x] All existing JVM tests pass without modification
- [x] JVM behavior remains byte-identical
- [x] 96 tests passing (plain JUnit 5, 7 test classes + 6 property test classes)
- [x] Fixture projects compile and check successfully
- [x] Node fixture fails `check` on boundary bypass (exit 7)
- [x] Node fixture fails `check` on drift (exit 5)
- [x] Node fixture with `allowedDeps` fails `check` (exit 64)

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
✅ COMPLETE

Merged to `feature/multi-target-expansion` on 2026-03-13. PR #4.

### Implementation Notes
- `BoundaryDecision.pass()` renamed to `allowed()` to avoid Java method name conflict
- `_shared` boundary logic correctly detects `SHARED_IMPORTS_BLOCK`
- `writeIfDifferent` uses `StandardOpenOption.SYNC` for WSL2 filesystem caching
- Property tests use plain JUnit 5 (no jqwik/assertj in build)

## Phase P: Python Target — Scan Only (SPEC COMPLETE)

### Purpose
Implement Python target with scan-only capabilities: detection, artifact generation, import
containment (AST-based), and drift checking. No runtime execution yet. Inner profile
(`python/service`) only — strict third-party import blocking.

### Scope
- `PythonTarget` implementing `Target` interface
- `PythonTargetDetector` for `pyproject.toml` + `uv`/`poetry` + `mypy` projects
- Python artifact generation (*_ports.py, *_logic.py, *_wrapper.py, wiring.json)
- Governed roots computation (`src/blocks/<blockKey>/`, `src/blocks/_shared/`)
- Import containment scanning (static imports only, AST-based)
- Drift gate for generated artifacts
- `impl.allowedDeps` unsupported guard (exit 64)
- Inner profile only: `python/service` (third-party imports blocked)

### Out of Scope (Future Phases)
- Runtime execution (`uv run mypy` verification)
- Outer profile (`python/service-relaxed`)
- `site-packages` power-surface scan
- Dynamic import resolution (`importlib.import_module`, `__import__`)
- Undeclared reach scanning (covered power surfaces)
- Dependency governance (`pr-check` lock-file delta)
- Workspace/monorepo layouts
- Namespace packages
- Flat layout

### Acceptance Criteria
- [ ] `PythonTarget` implements all required `Target` methods
- [ ] `PythonTargetDetector` detects valid Python projects
- [ ] Python artifacts generated correctly (parseable by Python AST)
- [ ] Import containment enforced (exit 7 on boundary bypass)
- [ ] Drift gate detects modified generated files (exit 5)
- [ ] `impl.allowedDeps` fails with exit 64 for Python target
- [ ] All existing JVM tests pass without modification
- [ ] All existing Node tests pass without modification
- [ ] JVM/Node behavior remains byte-identical
- [ ] 80+ tests passing (plain JUnit 5)
- [ ] Fixture projects compile and check successfully
- [ ] Python fixture fails `check` on boundary bypass (exit 7)
- [ ] Python fixture fails `check` on drift (exit 5)
- [ ] Python fixture with `allowedDeps` fails `check` (exit 64)

### Deliverables

**Implementation:**
- `kernel/src/main/java/com/bear/kernel/target/python/PythonTarget.java`
- `kernel/src/main/java/com/bear/kernel/target/python/PythonTargetDetector.java`
- `kernel/src/main/java/com/bear/kernel/target/python/PythonLexicalSupport.java`
- `kernel/src/main/java/com/bear/kernel/target/python/PythonTypeMapper.java`
- `kernel/src/main/java/com/bear/kernel/target/python/PythonArtifactGenerator.java`
- `kernel/src/main/java/com/bear/kernel/target/python/PythonManifestGenerator.java`
- `kernel/src/main/java/com/bear/kernel/target/python/PythonImportExtractor.java` (AST-based)
- `kernel/src/main/java/com/bear/kernel/target/python/PythonDynamicImportDetector.java` (AST-based)
- `kernel/src/main/java/com/bear/kernel/target/python/PythonImportBoundaryResolver.java`
- `kernel/src/main/java/com/bear/kernel/target/python/PythonImportContainmentScanner.java`

**Tests:**
- `kernel/src/test/java/com/bear/kernel/target/python/PythonTargetTest.java`
- `kernel/src/test/java/com/bear/kernel/target/python/PythonTargetDetectorTest.java`
- `kernel/src/test/java/com/bear/kernel/target/python/PythonArtifactGeneratorTest.java`
- `kernel/src/test/java/com/bear/kernel/target/python/PythonImportExtractorTest.java`
- `kernel/src/test/java/com/bear/kernel/target/python/PythonDynamicImportDetectorTest.java`
- `kernel/src/test/java/com/bear/kernel/target/python/PythonImportBoundaryResolverTest.java`
- `kernel/src/test/java/com/bear/kernel/target/python/PythonImportContainmentScannerTest.java`
- `kernel/src/test/java/com/bear/kernel/target/python/properties/PythonDetectionProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/python/properties/ArtifactGenerationProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/python/properties/GovernedRootsProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/python/properties/ImportContainmentProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/python/properties/DriftGateProperties.java`

**Fixtures:**
- `kernel/src/test/resources/fixtures/python/valid-single-block/`
- `kernel/src/test/resources/fixtures/python/valid-multi-block/`
- `kernel/src/test/resources/fixtures/python/valid-with-shared/`
- `kernel/src/test/resources/fixtures/python/invalid-workspace/`
- `kernel/src/test/resources/fixtures/python/invalid-flat-layout/`
- `kernel/src/test/resources/fixtures/python/invalid-namespace-package/`
- `kernel/src/test/resources/fixtures/python/boundary-bypass-escape/`
- `kernel/src/test/resources/fixtures/python/boundary-bypass-sibling/`
- `kernel/src/test/resources/fixtures/python/boundary-bypass-third-party/`

**Specs:**
- `.kiro/specs/phase-p-python-scan-only/requirements.md`
- `.kiro/specs/phase-p-python-scan-only/design.md`
- `.kiro/specs/phase-p-python-scan-only/tasks.md`

### Status
📝 SPEC COMPLETE (implementation pending)

Spec created on 2026-03-13. Implementation branch: TBD.

### Design Notes
- AST-first analysis strategy using Python `ast` module
- Inner profile only (`python/service`): strict third-party import blocking
- 33 correctness properties, 11 implementation tasks
- Reuses `BoundaryDecision` model from Node implementation
- `TargetId.PYTHON` enum value to be added

## Phase P2: Python Target — Full Check Pipeline (COMPLETE)

### Purpose
Complete the Python target check pipeline with undeclared reach scanning, dynamic execution
detection, dynamic import enforcement, and project verification (mypy integration).

### Scope
- Shared `TargetManifestParsers` for wiring manifest parsing
- `TargetRegistry` deterministic resolution (no silent JVM fallback)
- `PythonUndeclaredReachScanner` for covered power surfaces (socket, http, subprocess, etc.)
- `PythonDynamicExecutionScanner` for eval/exec/compile detection
- `PythonDynamicImportEnforcer` for importlib/sys.path mutation detection
- `PythonProjectVerificationRunner` for mypy integration (uv/poetry)
- Integration test fixtures for all scanner types

### Acceptance Criteria
- [x] `TargetManifestParsers` moved to shared `com.bear.kernel.target` package
- [x] `TargetRegistry` throws `TARGET_NOT_DETECTED` when no detector matches
- [x] `PythonTarget.parseWiringManifest` delegates to shared parser
- [x] `PythonTarget.prepareCheckWorkspace` creates `_shared` directory if present
- [x] Containment pipeline stubs return null/no-op (Python doesn't use JVM markers)
- [x] Port and binding check stubs return empty lists
- [x] `PythonUndeclaredReachScanner` detects covered module imports and os.system/exec calls
- [x] `PythonDynamicExecutionScanner` detects eval/exec/compile calls
- [x] `PythonDynamicImportEnforcer` detects importlib and sys.path mutations
- [x] `PythonProjectVerificationRunner` runs mypy via uv/poetry
- [x] All 16 correctness properties pass (100+ iterations each)
- [x] All existing JVM tests pass without modification
- [x] All existing Node tests pass without modification
- [x] Integration test fixtures verify exit codes

### Deliverables

**Implementation:**
- `kernel/src/main/java/com/bear/kernel/target/TargetManifestParsers.java` (moved from jvm/)
- `kernel/src/main/java/com/bear/kernel/target/python/PythonUndeclaredReachScanner.java`
- `kernel/src/main/java/com/bear/kernel/target/python/PythonDynamicExecutionScanner.java`
- `kernel/src/main/java/com/bear/kernel/target/python/PythonDynamicImportEnforcer.java`
- `kernel/src/main/java/com/bear/kernel/target/python/PythonProjectVerificationRunner.java`

**Tests:**
- `kernel/src/test/java/com/bear/kernel/target/TargetRegistryDetectionTest.java`
- `kernel/src/test/java/com/bear/kernel/target/properties/TargetRegistryResolutionProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/python/PythonTargetCheckMethodsTest.java`
- `kernel/src/test/java/com/bear/kernel/target/python/PythonUndeclaredReachScannerTest.java`
- `kernel/src/test/java/com/bear/kernel/target/python/PythonDynamicExecutionScannerTest.java`
- `kernel/src/test/java/com/bear/kernel/target/python/PythonDynamicImportEnforcerTest.java`
- `kernel/src/test/java/com/bear/kernel/target/python/PythonProjectVerificationRunnerTest.java`
- `kernel/src/test/java/com/bear/kernel/target/python/PythonCheckIntegrationTest.java`
- `kernel/src/test/java/com/bear/kernel/target/python/properties/WiringManifestParsingProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/python/properties/CheckWorkspaceProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/python/properties/UndeclaredReachProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/python/properties/DynamicExecutionProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/python/properties/DynamicImportEnforcementProperties.java`
- `kernel/src/test/java/com/bear/kernel/target/python/properties/ProjectVerificationProperties.java`

**Fixtures:**
- `kernel/src/test/resources/fixtures/python/check-clean/`
- `kernel/src/test/resources/fixtures/python/check-undeclared-reach/`
- `kernel/src/test/resources/fixtures/python/check-os-system/`
- `kernel/src/test/resources/fixtures/python/check-from-os-import/`
- `kernel/src/test/resources/fixtures/python/check-dynamic-exec/`
- `kernel/src/test/resources/fixtures/python/check-dynamic-import/`
- `kernel/src/test/resources/fixtures/python/check-sys-path-mutation/`
- `kernel/src/test/resources/fixtures/python/check-type-checking-excluded/`

**Specs:**
- `.kiro/specs/phase-p2-python-checking/requirements.md`
- `.kiro/specs/phase-p2-python-checking/design.md`
- `.kiro/specs/phase-p2-python-checking/tasks.md`

### Status
✅ COMPLETE

Completed on 2026-03-18. 13 tasks, 16 correctness properties validated.

### Implementation Notes
- `TargetManifestParsers` moved to shared package, `parseWiringManifest` made public
- `TargetRegistry` silent JVM fallback removed — throws `TARGET_NOT_DETECTED` instead
- Python scanners use embedded Python scripts via ProcessBuilder
- `TYPE_CHECKING` block exclusion implemented in all Python AST scanners
- Test files (`test_*.py`, `*_test.py`) excluded from scanning
- `PythonProjectVerificationRunner` prefers `uv` over `poetry`, handles missing mypy gracefully

## Phase C: Node Target — Runtime Execution (PLANNED)
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

When Phase P completes:
- [ ] Update `docs/context/architecture.md` to mention Python target support
- [ ] Update `docs/public/OVERVIEW.md` to mention Python target support
- [ ] Consider adding `docs/public/targets-python.md` for Python-specific guidance

## Related Documents

- `roadmap/ideas/future-multi-target-expansion-plan.md` — cross-target strategy
- `roadmap/ideas/future-multi-target-spec-design.md` — architectural spec
- `roadmap/ideas/future-python-containment-profile.md` — Python containment profile
- `roadmap/ideas/future-python-implementation-context.md` — Python implementation summary
- `.kiro/specs/phase-p-python-scan-only/` — Phase P detailed spec
- `.kiro/specs/phase-b-node-target-scan-only/` — Phase B detailed spec (reference)
- `docs/context/architecture.md` — core architecture principles
- `docs/context/ir-spec.md` — IR v1 contract (unchanged by multi-target)
