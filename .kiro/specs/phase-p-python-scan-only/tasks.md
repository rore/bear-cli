# Tasks: Phase P — Python Target (Scan Only)

## Implementation Tasks

- [x] 1. PythonTargetDetector
  - **Context:** Read `design.md` sections: PythonTargetDetector, Detection algorithm. Reference `NodeTargetDetector` for pattern.
  - **Execution:** Implement directly. Can run in parallel with task 2. Use `context-gatherer` if unfamiliar with detector pattern.
  - **Verification:** Run tests after completion. Verify no JVM/Node test regressions. Update `docs/context/state.md`.
  - [x] 1.1 Create `PythonTargetDetector.java` implementing `TargetDetector`
    - File-presence detection: `pyproject.toml` (parse `[build-system]`, `[project]`, `[tool.mypy]`), `uv.lock` OR `poetry.lock`, `mypy.ini` OR `[tool.mypy]`, `src/blocks/`
    - Returns `SUPPORTED` / `UNSUPPORTED` (workspace, flat layout, namespace packages, ambiguous) / `NONE`
    - No Python version checking; no `mypy.ini` content validation
  - [x] 1.2 Write `PythonTargetDetectorTest.java`
    - Valid project → `SUPPORTED`; missing each required file → `NONE`; workspace → `UNSUPPORTED`; flat layout → `UNSUPPORTED`; namespace package → `UNSUPPORTED`; ambiguous (Node+Python) → `UNSUPPORTED`; JVM project → `NONE`; Node project → `NONE`
  - [x] 1.3 Write `PythonDetectionProperties.java` (plain JUnit 5 parameterized)
    - Property 1: valid Python project structure → `SUPPORTED`, `targetId=PYTHON`
    - Property 2: `TargetRegistry.resolve()` on valid Python project → `PythonTarget` instance

- [x] 2. PythonTarget skeleton + TargetRegistry registration + TargetId.PYTHON
  - **Context:** Read `design.md` sections: PythonTarget, Target Interface Contract. Reference `NodeTarget` for pattern.
  - **Execution:** Implement directly. Can run in parallel with task 1. Tasks 3-4 depend on this completing.
  - **Verification:** Run all existing JVM/Node tests - must pass without modification. Update `docs/context/state.md`.
  - [x] 2.1 Add `PYTHON` to `TargetId` enum
  - [x] 2.2 Create `PythonTarget.java` implementing `Target`
    - `targetId()` → `TargetId.PYTHON`
    - `defaultProfile()` → `GovernanceProfile.of(PYTHON, "service")`
    - `considerContainmentSurfaces()` → `false`
    - `sharedContainmentInScope()` → `false`
    - All Phase P+ methods stub with `UnsupportedOperationException`
  - [x] 2.3 Register `PythonTarget` + `PythonTargetDetector` in `TargetRegistry.defaultRegistry()`
    - Add `TargetId.PYTHON → new PythonTarget()` to the targets map
    - Add `new PythonTargetDetector()` to the detectors list
  - [x] 2.4 Verify all existing JVM/Node tests pass without modification

- [x] 3. Python artifact generation
  - **Context:** Read `design.md` sections: Python Artifact Generators, Data Models. Reference `TypeScriptArtifactGenerator` for pattern.
  - **Execution:** Implement directly. Depends on task 2 completing. Subtasks 3.1-3.4 can run in parallel. Task 12 depends on this.
  - **Verification:** Run tests after each subtask. Verify generated Python parses with Python AST. Update `docs/context/state.md`.
  - [x] 3.1 Create `PythonLexicalSupport.java`
    - Kebab-case → snake_case conversions
    - Block key → block name derivation
  - [x] 3.2 Create `PythonTypeMapper.java`
    - IR type → Python type mapping (`string`→`str`, `int`→`int`, `decimal`→`Decimal`, `bool`→`bool`)
  - [x] 3.3 Create `PythonArtifactGenerator.java`
    - `generatePorts(BearIr, Path, String)` → `<block_name>_ports.py`
    - `generateLogic(BearIr, Path, String)` → `<block_name>_logic.py`
    - `generateWrapper(BearIr, Path, String)` → `<block_name>_wrapper.py`
    - `generateUserImplSkeleton(BearIr, Path, String)` → `<block_name>_impl.py` (create once, never overwrite)
  - [x] 3.4 Create `PythonManifestGenerator.java`
    - `generateWiringManifest(BearIr, Path, String)` → `<blockKey>.wiring.json`
  - [x] 3.5 Implement `PythonTarget.compile()` and `PythonTarget.generateWiringOnly()`
    - `compile()`: generate *_ports.py, *_logic.py, *_wrapper.py, wiring.json, user impl skeleton (if absent)
    - `generateWiringOnly()`: generate only wiring.json
    - Use staging directory pattern for atomic artifact updates
  - [x] 3.6 Implement `PythonTarget.parseWiringManifest()` and `PythonTarget.ownedGeneratedPrefixes()`
  - [x] 3.7 Write `PythonArtifactGeneratorTest.java`
    - Each artifact type generated correctly; generated Python parses without errors; user impl created once, not overwritten
  - [x] 3.8 Write `ArtifactGenerationProperties.java` (plain JUnit 5 parameterized)
    - Property 3: `compile()` on any valid `BearIr` → all four artifacts at expected paths
    - Property 4: `compile()` twice without modifying user impl → user impl unchanged
    - Property 5: `generateWiringOnly()` → only wiring manifest generated
    - Property 6: all generated Python files parseable by Python AST

- [x] 4. Governed roots computation
  - **Context:** Read `design.md` sections: Governed Roots Computer. Reference `NodeTarget` governed roots logic.
  - **Execution:** Implement directly. Depends on task 2 completing. Can run in parallel with task 3.
  - **Verification:** Run tests after completion. Verify file filtering works correctly.
  - [x] 4.1 Implement governed roots and file collection in `PythonTarget`
    - `src/blocks/<blockKey>/` per block (with `__init__.py`); `src/blocks/_shared/` if present (with `__init__.py`)
    - Walk only `.py` files; exclude `test_*.py` and `*_test.py`; exclude `.pyi/.pyc/.pyo`
  - [x] 4.2 Write governed roots unit tests in `PythonTargetTest.java`
    - Single block, multi-block, with `_shared`, without `_shared`, test file exclusion, extension filtering, `__init__.py` requirement
  - [x] 4.3 Write `GovernedRootsProperties.java` (plain JUnit 5 parameterized)
    - Property 7: any block key with `__init__.py` → `src/blocks/<blockKey>/` in governed roots
    - Property 8: any path outside `src/blocks/` → excluded
    - Property 9: any `test_*.py` or `*_test.py` → excluded
    - Property 10: any non-`.py` extension in `src/blocks/` → excluded

- [x] 5. PythonImportExtractor (AST-based)
  - **Context:** Read `design.md` sections: Import Containment Scanner, PythonImportExtractor. AST-first requirement. No direct JVM/Node equivalent.
  - **Execution:** Implement directly. Independent, can run in parallel with tasks 1-4. Task 7-8 depend on this.
  - **Verification:** Run tests after completion. Verify all import patterns detected with correct line numbers via AST.
  - [x] 5.1 Create `PythonImportExtractor.java`
    - AST-based extraction using Python `ast` module (via ProcessBuilder or embedded Python)
    - Patterns: `import x`, `import x as y`, `from x import y`, `from x import y as z`, `from . import x`, `from .. import x`, `from .submodule import x`
    - Returns `List<ImportStatement>` with line/column numbers from AST
  - [x] 5.2 Write `PythonImportExtractorTest.java`
    - All import patterns (absolute, relative, aliased); returns correct line numbers from AST; handles empty files
  - [x] 5.3 Write extraction property in `ImportContainmentProperties.java` (plain JUnit 5 parameterized)
    - Property 19: any Python source → all static import statements extracted with locations via AST

- [x] 6. PythonDynamicImportDetector (AST-based)
  - **Context:** Read `design.md` sections: Import Containment Scanner, PythonDynamicImportDetector. Phase P: detect only, no enforcement.
  - **Execution:** Implement directly. Independent, can run in parallel with tasks 1-5. Task 8 depends on this.
  - **Verification:** Run tests after completion. Verify dynamic imports detected but don't fail.
  - [x] 6.1 Create `PythonDynamicImportDetector.java`
    - AST-based detection of `importlib.import_module(...)`, `__import__(...)`, `importlib.util.spec_from_file_location(...)` calls
    - Returns `List<DynamicImport>` with line/column numbers from AST
    - Phase P: detect only, no enforcement
  - [x] 6.2 Write `PythonDynamicImportDetectorTest.java`
    - Detects `importlib.import_module("module")`; detects `__import__("module")`; distinguishes from static imports
  - [x] 6.3 Write detection property in `ImportContainmentProperties.java` (plain JUnit 5 parameterized)
    - Property 20: any Python source with `importlib.import_module()` → all dynamic imports identified

- [x] 7. PythonImportBoundaryResolver
  - **Context:** Read `design.md` sections: Import Containment Scanner, PythonImportBoundaryResolver. Uses `CanonicalLocator` from Phase A.
  - **Execution:** Implement directly. Depends on task 5 completing (needs ImportStatement model). Task 8 depends on this.
  - **Verification:** Run tests after completion. Verify all pass/fail cases work correctly.
  - [x] 7.1 Create `PythonImportBoundaryResolver.java`
    - Relative import → lexical resolution; BEAR-generated → `ALLOWED`; same block root → `ALLOWED`; `_shared` → `ALLOWED` (unless `_shared` imports block → `FAIL(SHARED_IMPORTS_BLOCK)`); all other → `FAIL(BOUNDARY_BYPASS)`
    - Absolute import → stdlib → `ALLOWED` (Phase P: allow stdlib); third-party → `FAIL(THIRD_PARTY_IMPORT)`
    - Uses `CanonicalLocator` for structured finding locators
  - [x] 7.2 Write `PythonImportBoundaryResolverTest.java`
    - All pass/fail cases: same-block, `_shared`, generated artifact, sibling block, escaping, third-party, stdlib, `_shared`→block
  - [x] 7.3 Write resolution properties in `ImportContainmentProperties.java` (plain JUnit 5 parameterized)
    - Properties 21–27: all pass/fail classification cases + `CanonicalLocator` usage

- [x] 8. PythonImportContainmentScanner
  - **Context:** Read `design.md` sections: Import Containment Scanner, PythonImportContainmentScanner (orchestrator). Reference `NodeImportContainmentScanner` for pattern.
  - **Execution:** Implement directly. Depends on tasks 5, 6, 7 completing. Orchestrates all three components.
  - **Verification:** Run tests after completion. Verify findings sorted deterministically. Update `docs/context/state.md`.
  - [x] 8.1 Create `PythonImportContainmentScanner.java`
    - Orchestrates `PythonImportExtractor`, `PythonDynamicImportDetector`, `PythonImportBoundaryResolver`
    - Computes governed roots from wiring manifests; collects governed files; scans each file
    - Dynamic imports: detect but do not fail in Phase P
    - Findings sorted by file path, then line number
  - [x] 8.2 Implement `PythonTarget.scanBoundaryBypass()` delegating to `PythonImportContainmentScanner.scan()`
  - [x] 8.3 Write `PythonImportContainmentScannerTest.java`
    - Clean project → no findings; boundary bypass → finding with exit `7`; multiple violations collected; finding includes path and module name
  - [x] 8.4 Write containment properties in `ImportContainmentProperties.java` (plain JUnit 5 parameterized)
    - Properties 11–18: all pass/fail containment cases + finding locator completeness

- [x] 9. Drift gate
  - **Context:** Read `design.md` sections: Drift Gate. Reference `NodeTarget` drift checking for pattern.
  - **Execution:** Implement directly. Depends on task 3 completing (needs artifact generation). Can run in parallel with tasks 10-11.
  - **Verification:** Run tests after completion. Verify byte-for-byte comparison works.
  - [x] 9.1 Implement drift checking in `PythonTarget`
    - Generate fresh artifacts to temp directory; byte-for-byte compare against workspace
    - `DRIFT_DETECTED` on mismatch; `DRIFT_MISSING_BASELINE` on missing file
    - Exclude user-owned impl files
  - [x] 9.2 Write drift gate tests in `PythonTargetTest.java`
    - Clean state → no findings; modified generated file → `DRIFT_DETECTED`; missing file → `DRIFT_MISSING_BASELINE`; user impl modified → no findings
  - [x] 9.3 Write `DriftGateProperties.java` (plain JUnit 5 parameterized)
    - Property 28: `compile()` then immediate drift check → no findings
    - Property 29: generated file modified → `DRIFT_DETECTED`
    - Property 30: user-owned impl modified → no drift findings

- [x] 10. impl.allowedDeps unsupported guard
  - **Context:** Read `design.md` sections: Error Handling, impl.allowedDeps guard. Reference `requirements.md` for exit codes.
  - **Execution:** Implement directly. Depends on task 2 completing (needs PythonTarget skeleton). Can run in parallel with tasks 9, 11.
  - **Verification:** Run tests after completion. Verify exit 64 for Python+allowedDeps, JVM/Node unaffected.
  - [x] 10.1 Implement `PythonTarget.blockDeclaresAllowedDeps()` — parse IR for `impl.allowedDeps` presence
  - [x] 10.2 Implement guard in check pipeline: if `blockDeclaresAllowedDeps()` and target=Python → fail, exit `64`, `CODE=UNSUPPORTED_TARGET`, IR file path, remediation message
  - [x] 10.3 Verify `pr-check` is unaffected by `impl.allowedDeps`
  - [x] 10.4 Write guard tests in `PythonTargetTest.java`
    - Block without `allowedDeps` → passes; block with `allowedDeps` → exit `64`; error output includes code, path, remediation; `pr-check` unaffected; JVM/Node with `allowedDeps` → continues to work
  - [x] 10.5 Write properties in `ImportContainmentProperties.java` or separate file (plain JUnit 5 parameterized)
    - Property 31: `impl.allowedDeps` + Python → exit `64`, `CODE=UNSUPPORTED_TARGET`
    - Property 32: error output includes IR file path
    - Property 33: `pr-check` operates normally

- [x] 11. Fixture projects + integration tests
  - **Context:** Read `design.md` sections: Testing Strategy, Integration Tests, Fixture projects. Reference `kernel/src/test/resources/fixtures/node/` for pattern.
  - **Execution:** Implement directly. Depends on tasks 1-10 completing (needs all components).
  - **Verification:** Run full test suite. Verify zero JVM/Node test failures. Update `docs/context/state.md` - Phase P complete.
  - [x] 11.1 Create fixture projects under `kernel/src/test/resources/fixtures/python/`
    - `valid-single-block/` — minimal valid Python project with one block
    - `valid-multi-block/` — two blocks
    - `valid-with-shared/` — one block + `_shared`
    - `invalid-workspace/` — `uv.workspace` in `pyproject.toml`
    - `invalid-flat-layout/` — no `src/` directory
    - `invalid-namespace-package/` — missing `__init__.py` in `src/blocks/<blockKey>/`
    - `boundary-bypass-escape/` — relative import escaping block root
    - `boundary-bypass-sibling/` — import reaching sibling block
    - `boundary-bypass-third-party/` — third-party package import from governed root
  - [x] 11.2 Write end-to-end integration tests
    - Detect → compile → check (clean)
    - Detect → compile → modify generated file → check (drift, exit `5`)
    - Detect → compile → add boundary bypass → check (fail, exit `7`)
    - Detect with `allowedDeps` → check (exit `64`)
    - JVM project → resolves to `JvmTarget` (no interference)
    - Node project → resolves to `NodeTarget` (no interference)
  - [x] 11.3 Run full JVM/Node regression test suite; confirm zero failures

## Task Dependencies

```
1 (PythonTargetDetector) ─┐
                          ├─→ 11 (Fixtures + Integration)
2 (PythonTarget skeleton) ─┤
                          ├─→ 3 (Artifact generation) ─→ 9 (Drift gate) ─┐
                          │                                               │
                          ├─→ 4 (Governed roots) ─────────────────────────┤
                          │                                               ├─→ 11
                          ├─→ 10 (AllowedDeps guard) ─────────────────────┤
                          │                                               │
5 (PythonImportExtractor) ─┤                                             │
                          ├─→ 7 (PythonImportBoundaryResolver) ─┐        │
6 (PythonDynamicImportDetector) ─┘                              ├─→ 8 ───┘
                                                                │  (PythonImportContainmentScanner)
                                                                └─────────┘
```

## Parallel Execution Opportunities

- Tasks 1 and 2 can run in parallel
- Tasks 3, 4, 5, 6 can run in parallel after task 2 completes
- Tasks 9, 10 can run in parallel after their dependencies complete
- Task 11 requires all other tasks to complete

## Session Hygiene Reminders

After completing major milestones (tasks 2, 8, 11):
- Update `docs/context/state.md`: `Last Updated`, `Current Focus`, `Next Concrete Task`, short `Session Notes`
- Keep `Session Notes` within `ContextDocsConsistencyTest` budgets
- Move oldest notes to `docs/context/archive/archive-state-history.md` if approaching cap

## Verification Checklist

Phase P is complete when:
- [ ] All 33 correctness properties pass (100+ iterations each)
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] All existing JVM tests pass without modification
- [ ] All existing Node tests pass without modification
- [ ] Fixture projects compile and check successfully
- [ ] Python fixture fails `check` on boundary bypass (exit `7`)
- [ ] Python fixture fails `check` on drift (exit `5`)
- [ ] Python fixture with `allowedDeps` fails `check` (exit `64`)
- [ ] JVM/Node fixture behavior unchanged
