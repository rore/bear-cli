# Phase B: Node Target — Design

Phase B introduces `NodeTarget` as the first non-JVM target. Implements scan-only capabilities:
detection, TypeScript artifact generation, governed root computation, import containment,
drift checking, and the `impl.allowedDeps` unsupported guard.

Follows the Target interface contract from Phase A. Uses `JvmTarget` as the reference pattern.

## Scope

In scope:
- Node/TypeScript project detection (ESM + pnpm + TypeScript)
- TypeScript artifact generation from IR
- Import containment scanning (static imports only)
- Drift gate for generated artifacts
- `impl.allowedDeps` unsupported guard (exit `64`)

Out of scope (Phase C+):
- Runtime execution
- Dynamic `import()` resolution
- TypeScript path alias resolution
- Workspace/monorepo layouts
- npm/yarn package managers
- CommonJS projects

---

## Architecture

`NodeTarget` integrates at the Target seam:

```
BEAR CLI Commands (compile, check, pr-check, fix)
        │
        ▼
TargetRegistry
  ├── JvmTarget  (existing)
  └── NodeTarget (Phase B)
        ├── TypeScript Artifact Generators
        ├── Governed Roots Computer
        ├── NodeImportContainmentScanner
        │     ├── NodeImportSpecifierExtractor
        │     ├── NodeDynamicImportDetector
        │     └── NodeImportBoundaryResolver
        └── Drift Gate
```

All Node-specific logic lives behind the Target interface. Kernel orchestration is target-agnostic.

---

## Target Interface Contract

Phase B implements:

| Method | Behavior |
|--------|----------|
| `targetId()` | `TargetId.NODE` |
| `defaultProfile()` | `GovernanceProfile.of(NODE, "backend-service")` |
| `compile()` | TypeScript artifact generation |
| `generateWiringOnly()` | wiring.json only |
| `parseWiringManifest()` | JSON parsing |
| `ownedGeneratedPrefixes()` | `build/generated/bear/types/`, `build/generated/bear/wiring/` |
| `considerContainmentSurfaces()` | `false` (`impl.allowedDeps` unsupported) |
| `sharedContainmentInScope()` | `false` (no shared policy in Phase B) |
| `blockDeclaresAllowedDeps()` | IR parsing for `allowedDeps` presence |
| `scanBoundaryBypass()` | delegates to `NodeImportContainmentScanner` |

Phase B stubs (throw `UnsupportedOperationException`):
- `prepareCheckWorkspace()`, `containmentSkipInfoLine()`, `preflightContainmentIfRequired()`, `verifyContainmentMarkersIfRequired()`
- `scanUndeclaredReach()`, `scanForbiddenReflectionDispatch()`, `scanPortImplContainmentBypass()`, `scanBlockPortBindings()`, `scanMultiBlockPortImplAllowedSignals()`, `runProjectVerification()`

---

## Components

### NodeTargetDetector

Detection algorithm:
```
1. Check package.json at projectRoot
   - absent → NONE
   - "type" ≠ "module" → NONE
   - "packageManager" missing or not "pnpm@..." → NONE
2. Check pnpm-lock.yaml at projectRoot — absent → NONE
3. Check tsconfig.json at projectRoot — absent → NONE
4. Check pnpm-workspace.yaml at projectRoot — present → UNSUPPORTED
5. All checks passed → SUPPORTED, targetId=NODE
```

- Detection is file-presence based; `package.json` parsed minimally (`type`, `packageManager` only).
- No version checking in Phase B.
- `tsconfig.json` content is not validated.
- Workspace detection returns `UNSUPPORTED` (not `NONE`) for clear feedback.

---

### NodeTarget

```java
public final class NodeTarget implements Target {
    @Override public TargetId targetId() { return TargetId.NODE; }

    @Override public GovernanceProfile defaultProfile() {
        return GovernanceProfile.of(TargetId.NODE, "backend-service");
    }

    @Override public void compile(BearIr ir, Path projectRoot, String blockKey) throws IOException {
        // 1. Compute paths
        // 2. Generate Ports.ts, Logic.ts, Wrapper.ts
        // 3. Generate wiring manifest
        // 4. Create user impl skeleton if absent
    }

    @Override public void generateWiringOnly(BearIr ir, Path projectRoot, Path outputRoot, String blockKey) {
        // Generate only wiring manifest (for pr-check)
    }

    @Override public List<BoundaryBypassFinding> scanBoundaryBypass(
            Path projectRoot, List<WiringManifest> wiringManifests, Set<String> reflectionAllowlist)
            throws IOException {
        return NodeImportContainmentScanner.scan(projectRoot, wiringManifests);
    }

    @Override public Set<String> ownedGeneratedPrefixes(String blockName) {
        String blockKey = toKebabCase(blockName);
        return Set.of(
            "build/generated/bear/types/" + blockKey + "/",
            "build/generated/bear/wiring/" + blockKey + ".wiring.json"
        );
    }

    @Override public boolean considerContainmentSurfaces(BearIr ir, Path projectRoot) {
        return false; // impl.allowedDeps unsupported in Phase B
    }

    @Override public boolean blockDeclaresAllowedDeps(Path irFile) {
        try {
            BearIr ir = parseIr(irFile);
            return ir.block().impl() != null && ir.block().impl().allowedDeps() != null;
        } catch (Exception e) { return false; }
    }
}
```

- Follows `JvmTarget` structure; generates TypeScript instead of Java.
- Uses staging directory pattern for atomic artifact updates.
- User-owned impl files are never overwritten.

---

### TypeScript Artifact Generators

**Ports.ts** — port type declarations from `effects.allow`:
```typescript
// Generated by bear compile. DO NOT EDIT.

export interface DatabasePort {
  query(input: BearValue): BearValue;
  execute(input: BearValue): BearValue;
}
```

**Logic.ts** — logic interface from contract inputs/outputs:
```typescript
// Generated by bear compile. DO NOT EDIT.

export interface LoginRequest { username: string; password: string; }
export interface LoginResult  { token: string; expiresAt: number; }

export interface AuthServiceLogic {
  login(request: LoginRequest, database: DatabasePort): LoginResult;
}
```

**Wrapper.ts** — wiring factory connecting ports, logic, and impl:
```typescript
// Generated by bear compile. DO NOT EDIT.

export class AuthService_Login {
  constructor(
    private readonly database: DatabasePort,
    private readonly logic: AuthServiceLogic
  ) {}

  execute(request: LoginRequest): LoginResult {
    return this.logic.login(request, this.database);
  }

  static of(database: DatabasePort): AuthService_Login {
    return new AuthService_Login(database, new AuthServiceImpl());
  }
}
```

**wiring.json**:
```json
{
  "version": "1",
  "blockKey": "<blockKey>",
  "targetId": "node",
  "generatedPackage": "build/generated/bear/types/<blockKey>",
  "implPackage": "src/blocks/<blockKey>/impl",
  "wrappers": [{ "operation": "<op>", "wrapperClass": "<Block>_<Op>", "wrapperPath": "..." }],
  "ports": [{ "name": "<port>", "kind": "EXTERNAL|BLOCK", "interface": "<Port>Port" }]
}
```

- ES6 module syntax throughout.
- `BearValue` is a simple key-value map type (defined in runtime).
- No TypeScript generics in Phase B.
- `DECIMAL` IR type maps to `string` (precision preservation).

---

### Governed Roots Computer

```
governedRoots(projectRoot, blockKeys):
  roots = []
  for blockKey in blockKeys:
    blockRoot = projectRoot / "src/blocks" / blockKey
    if exists(blockRoot): roots.add(blockRoot)
  sharedRoot = projectRoot / "src/blocks/_shared"
  if exists(sharedRoot): roots.add(sharedRoot)
  return roots

governedFiles(governedRoots):
  for root in governedRoots:
    for file in walkTree(root):
      if file.extension == ".ts" and not file.name.endsWith(".test.ts"):
        yield file
```

Exclusions: files outside `src/blocks/`, `*.test.ts`, `.js/.jsx/.tsx/.mjs/.cjs/.cts/.mts`, `node_modules/` (implicitly excluded by walking only `src/blocks/`).

---

### Import Containment Scanner

Three helper classes + orchestrator. Each concern is independently testable.

**NodeImportSpecifierExtractor** — parses TypeScript source; extracts specifiers with locations:

Patterns detected:
- `import { x } from "./path"`
- `import * as x from "./path"`
- `import x from "./path"`
- `import "./path"` (side-effect)
- `export { x } from "./path"`
- `export * from "./path"`

Regex-based extraction (no full TypeScript parser in Phase B). Returns specifiers with line/column.

**NodeDynamicImportDetector** — identifies `import()` expressions:

- Detects `import(...)` patterns.
- Phase B: detects but does not enforce (advisory only).

**NodeImportBoundaryResolver** — classifies resolved paths:

```java
public BoundaryDecision resolve(Path importingFile, String specifier,
        Set<Path> governedRoots, Path projectRoot) {
    // 1. Bare specifier → FAIL
    if (isBareSpecifier(specifier)) return BoundaryDecision.fail("BARE_PACKAGE_IMPORT");
    if (isAliasSpecifier(specifier)) return BoundaryDecision.fail("ALIAS_IMPORT");
    if (isUrlSpecifier(specifier))   return BoundaryDecision.fail("URL_IMPORT");

    // 2. Resolve relative specifier lexically
    Path resolved = resolveRelative(importingFile, specifier);

    // 3. BEAR-generated → PASS
    if (isBearGenerated(resolved, projectRoot)) return BoundaryDecision.pass();

    // 4. Same governed root → PASS
    Path importingRoot = findGovernedRoot(importingFile, governedRoots);
    if (resolved.startsWith(importingRoot)) return BoundaryDecision.pass();

    // 5. _shared → PASS (unless _shared imports a block)
    Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
    if (resolved.startsWith(sharedRoot)) {
        if (importingFile.startsWith(sharedRoot)) return BoundaryDecision.fail("SHARED_IMPORTS_BLOCK");
        return BoundaryDecision.pass();
    }

    // 6. All other cases → FAIL
    return BoundaryDecision.fail("BOUNDARY_BYPASS");
}
```

- Lexical resolution only (no `node_modules` traversal, no `tsconfig` paths).
- Uses `CanonicalLocator` for structured finding locators.

**NodeImportContainmentScanner** — orchestrator:

```java
public static List<BoundaryBypassFinding> scan(Path projectRoot,
        List<WiringManifest> wiringManifests) throws IOException {
    Set<Path> governedRoots = computeGovernedRoots(projectRoot, wiringManifests);
    List<Path> governedFiles = collectGovernedFiles(governedRoots);

    NodeImportSpecifierExtractor extractor = new NodeImportSpecifierExtractor();
    NodeDynamicImportDetector dynamicDetector = new NodeDynamicImportDetector();
    NodeImportBoundaryResolver resolver = new NodeImportBoundaryResolver();

    List<BoundaryBypassFinding> findings = new ArrayList<>();
    for (Path file : governedFiles) {
        String content = Files.readString(file);
        for (ImportSpecifier imp : extractor.extractImports(file, content)) {
            BoundaryDecision decision = resolver.resolve(file, imp.specifier(), governedRoots, projectRoot);
            if (decision.isFail()) findings.add(createFinding(file, imp, decision));
        }
        // Dynamic imports: detect but don't fail in Phase B
        dynamicDetector.detectDynamicImports(file, content);
    }
    return findings;
}
```

Findings are sorted by file path, then line number (deterministic output for CI).

---

### Drift Gate

```
checkDrift(projectRoot, blockKeys):
  for blockKey in blockKeys:
    ir = parseIr(projectRoot / "spec" / blockKey / "ir.bear.yaml")
    tempDir = createTempDirectory()
    NodeTarget.compile(ir, tempDir, blockKey)

    for artifact in ["build/generated/bear/types/<blockKey>/*.ts",
                     "build/generated/bear/wiring/<blockKey>.wiring.json"]:
      if not exists(workspace / artifact): yield DRIFT_MISSING_BASELINE(artifact)
      else if not contentEquals(workspace / artifact, tempDir / artifact): yield DRIFT_DETECTED(artifact)
```

- Generates to temp directory (no workspace pollution).
- Byte-for-byte comparison.
- User-owned impl files excluded.

---

## Data Models

```java
// Detection
public record DetectedTarget(DetectionStatus status, TargetId targetId, String reason) {
    public static DetectedTarget none()                              { ... }
    public static DetectedTarget supported(TargetId id)             { ... }
    public static DetectedTarget unsupported(TargetId id, String r) { ... }
}
public enum DetectionStatus { NONE, SUPPORTED, UNSUPPORTED }

// Wiring
public record WiringManifest(String version, String blockKey, String targetId,
    String generatedPackage, String implPackage,
    List<WrapperInfo> wrappers, List<PortInfo> ports) {
    public record WrapperInfo(String operation, String wrapperClass, String wrapperPath) {}
    public record PortInfo(String name, String kind, String interfaceName) {}
}

// Findings
public record BoundaryBypassFinding(String rule, String path, String detail) {}
public record ImportSpecifier(String specifier, int lineNumber, int columnNumber) {}
public record BoundaryDecision(boolean pass, String failureReason) {
    public static BoundaryDecision pass()          { return new BoundaryDecision(true, null); }
    public static BoundaryDecision fail(String r)  { return new BoundaryDecision(false, r); }
    public boolean isFail()                        { return !pass; }
}

// Internal generation models
public record PortModel(String originalName, String interfaceName, String variableName,
    BearIr.EffectPortKind kind, List<String> methods) {}
public record OperationModel(String operationName, String requestClassName,
    String resultClassName, String wrapperClassName, String logicMethodName,
    List<FieldModel> inputs, List<FieldModel> outputs, List<PortModel> logicPorts) {}
public record FieldModel(String originalName, String memberName, String tsType, String getterName) {}
```

IR type → TypeScript type mapping:

| IR Type | TypeScript Type | Notes |
|---------|----------------|-------|
| `string` | `string` | |
| `int` | `number` | |
| `decimal` | `string` | BigDecimal → string for precision |
| `bool` | `boolean` | |

---

## Error Handling

Error envelope (frozen):
```
<error details>
CODE=<error_code>
PATH=<file_path>
REMEDIATION=<remediation_message>
```

| Category | Code | Exit | Remediation |
|----------|------|------|-------------|
| Detection | `TARGET_NOT_DETECTED` | `2` | Add `.bear/target.id` pin or fix project structure |
| Detection | `TARGET_UNSUPPORTED` | `64` | Remove `pnpm-workspace.yaml` or use single-package layout |
| Containment | `BOUNDARY_BYPASS` | `7` | Remove or relocate the import |
| Containment | `BARE_PACKAGE_IMPORT` | `7` | Remove bare import from governed code |
| Containment | `ALIAS_IMPORT` | `7` | Use relative imports instead |
| Containment | `URL_IMPORT` | `7` | Remove URL import |
| Containment | `SHARED_IMPORTS_BLOCK` | `7` | Reverse dependency direction |
| Drift | `DRIFT_DETECTED` | `5` | Run `bear fix` or `bear compile` |
| Drift | `DRIFT_MISSING_BASELINE` | `5` | Run `bear compile` |
| Unsupported | `UNSUPPORTED_TARGET` | `64` | Remove `impl.allowedDeps` or switch to JVM target |

- Detection errors fail fast (prevent all subsequent operations).
- Containment scanner collects all violations before reporting (no whack-a-mole).
- Missing optional directories (`_shared`) are silently skipped.
- `IOException` → wrap in `TargetException`, exit `74`.
- Stubbed Phase C methods → `UnsupportedOperationException`, exit `64`.

---

## Correctness Properties

Properties are tagged to requirements. PBT library: **jqwik** (Java). Minimum 100 iterations per property.

Tag format: `// Feature: phase-b-node-target-scan-only, Property N: <text>`

### Detection

1. Valid Node project structure → `SUPPORTED`, `targetId=NODE`. *(req: Node Project Detection)*
2. `TargetRegistry.resolve()` on valid Node project → `NodeTarget` instance, deterministically. *(req: Target Registry Integration)*

### Artifact Generation

3. `compile()` on any valid `BearIr` → all four artifacts generated at expected paths. *(req: TypeScript Artifact Generation)*
4. `compile()` twice without modifying user impl → user impl content unchanged. *(req: TypeScript Artifact Generation)*
5. `generateWiringOnly()` → only wiring manifest generated; no Ports.ts, Logic.ts, Wrapper.ts, or user impl. *(req: TypeScript Artifact Generation)*
6. All TypeScript files from `compile()` → parseable by `tsc` without syntax errors. *(req: TypeScript Artifact Generation)*

### Governed Roots

7. Any block key → `src/blocks/<blockKey>/` in governed roots. *(req: Governed Source Roots)*
8. Any path outside `src/blocks/` → excluded from governed roots. *(req: Governed Source Roots)*
9. Any `*.test.ts` within governed roots → excluded from governed source files. *(req: Governed Source Roots)*
10. Any file in `src/blocks/` with extension other than `.ts` → excluded from governed source. *(req: Governed Source Roots)*

### Import Containment

11. Relative import resolving within same block root → no findings. *(req: Import Containment Enforcement)*
12. Relative import resolving to `src/blocks/_shared/` → no findings. *(req: Import Containment Enforcement)*
13. Relative import resolving to `build/generated/bear/` → no findings. *(req: Import Containment Enforcement)*
14. Relative import escaping block root → finding, exit `7`, `CODE=BOUNDARY_BYPASS`. *(req: Import Containment Enforcement)*
15. Import resolving to sibling block → finding, exit `7`, `CODE=BOUNDARY_BYPASS`. *(req: Import Containment Enforcement)*
16. Bare package specifier from governed root → finding, exit `7`, `CODE=BOUNDARY_BYPASS`. *(req: Import Containment Enforcement)*
17. `#` alias import from governed root → finding, exit `7`, `CODE=BOUNDARY_BYPASS`. *(req: Import Containment Enforcement)*
18. Any `BOUNDARY_BYPASS` finding → includes repo-relative path and import specifier. *(req: Import Containment Enforcement)*
19. `_shared` file importing a block root → finding, exit `7`, `CODE=BOUNDARY_BYPASS`. *(req: Import Containment Enforcement)*

### Scanner Components

20. Any TypeScript source → `NodeImportSpecifierExtractor` extracts all static import/export specifiers with locations. *(req: Concern Separation)*
21. Any TypeScript source with `import()` → `NodeDynamicImportDetector` identifies all dynamic import expressions. *(req: Concern Separation)*
22. Resolved path within same block root → `NodeImportBoundaryResolver` returns `PASS`. *(req: Concern Separation)*
23. Resolved path within `_shared` → `NodeImportBoundaryResolver` returns `PASS`. *(req: Concern Separation)*
24. Resolved path within `build/generated/bear/` → `NodeImportBoundaryResolver` returns `PASS`. *(req: Concern Separation)*
25. Resolved path in sibling block → `NodeImportBoundaryResolver` returns `FAIL`. *(req: Concern Separation)*
26. Resolved path in nongoverned source → `NodeImportBoundaryResolver` returns `FAIL`. *(req: Concern Separation)*
27. Resolved path escaping block root → `NodeImportBoundaryResolver` returns `FAIL`. *(req: Concern Separation)*
28. Any `FAIL` from `NodeImportBoundaryResolver` → uses `CanonicalLocator` for structured locator. *(req: Concern Separation)*

### Drift Gate

29. `compile()` then immediate drift check → no findings. *(req: Drift Gate)*
30. Generated file modified after `compile()` → `DRIFT_DETECTED`. *(req: Drift Gate)*
31. User-owned impl modified → no drift findings. *(req: Drift Gate)*

### AllowedDeps Guard

32. Block IR with `impl.allowedDeps`, target=Node → `check` fails, exit `64`, `CODE=UNSUPPORTED_TARGET`. *(req: impl.allowedDeps Unsupported Guard)*
33. Block IR with `impl.allowedDeps`, target=Node → error output includes IR file path. *(req: impl.allowedDeps Unsupported Guard)*
34. Block IR with `impl.allowedDeps`, target=Node → `pr-check` operates normally. *(req: impl.allowedDeps Unsupported Guard)*

### Pretty Printer

35. Any generated TypeScript AST → output parseable by `tsc`. *(req: TypeScript Pretty Printer)*
36. Any generated TypeScript AST → consistent indentation and line breaks across invocations. *(req: TypeScript Pretty Printer)*

---

## Testing Strategy

### Unit Tests

```
kernel/src/test/java/com/bear/kernel/target/node/
  NodeTargetDetectorTest.java
  NodeTargetTest.java
  NodeImportSpecifierExtractorTest.java
  NodeDynamicImportDetectorTest.java
  NodeImportBoundaryResolverTest.java
  NodeImportContainmentScannerTest.java
  TypeScriptArtifactGeneratorTest.java
```

Key cases per class:
- `NodeTargetDetectorTest`: valid project → `SUPPORTED`; missing each required file → `NONE`; workspace → `UNSUPPORTED`; JVM project → `NONE`
- `NodeTargetTest`: `targetId()` returns `NODE`; `compile()` creates expected artifacts; user impl created once, not overwritten; stubs throw `UnsupportedOperationException`
- `NodeImportSpecifierExtractorTest`: all six import/export patterns; returns line numbers
- `NodeDynamicImportDetectorTest`: detects `import("./path")`; distinguishes from static imports
- `NodeImportBoundaryResolverTest`: all pass/fail cases including `_shared` → block direction
- `NodeImportContainmentScannerTest`: clean project → no findings; bypass → finding with exit `7`; multiple violations collected
- `TypeScriptArtifactGeneratorTest`: each artifact type; generated TypeScript parses without errors

### Property Tests

```
kernel/src/test/java/com/bear/kernel/target/node/properties/
  NodeDetectionProperties.java
  ArtifactGenerationProperties.java
  GovernedRootsProperties.java
  ImportContainmentProperties.java
  DriftGateProperties.java
```

Generators:
```java
@Provide Arbitrary<Path> validNodeProjects() { /* temp dir with required files */ }
@Provide Arbitrary<BearIr> validBearIrBlocks() { /* random block name, inputs, outputs */ }
@Provide Arbitrary<String> relativeImportSpecifiers() { /* starts with ./ or ../ */ }
@Provide Arbitrary<String> barePackageSpecifiers() { /* no leading . or / */ }
```

### Integration Tests

Fixture projects:
```
kernel/src/test/resources/fixtures/node/
  valid-single-block/
  valid-multi-block/
  valid-with-shared/
  invalid-workspace/
  invalid-missing-lockfile/
  boundary-bypass-escape/
  boundary-bypass-sibling/
  boundary-bypass-bare-import/
```

End-to-end scenarios:
1. Detect → compile → check (clean)
2. Detect → compile → modify generated file → check (drift)
3. Detect → compile → add boundary bypass → check (fail, exit `7`)
4. Detect with `allowedDeps` → check (exit `64`)
5. JVM project → resolves to `JvmTarget` (no interference)

### Regression

- All existing JVM tests pass without modification.
- JVM project detection does not trigger `NodeTargetDetector`.
- Node project detection does not trigger `JvmTargetDetector`.

---

## Implementation Sequence

**Execution Strategy:** See tasks.md for parallel execution opportunities and task dependencies.

**Key Principles:**
- Implement directly (no subagent delegation for spec tasks)
- Use `context-gatherer` only if exploring unfamiliar JVM patterns (one-time)
- Run tests after each component completion
- Verify JVM regression tests pass after each major milestone

**Sequence:**

1. `NodeTargetDetector` + detection unit tests + detection property tests
2. `NodeTarget` skeleton + `TargetRegistry` registration + verify JVM tests pass
3. TypeScript lexical support (kebab-case, PascalCase, camelCase, type mapping)
4. `Ports.ts`, `Logic.ts`, `Wrapper.ts`, `wiring.json` generators + artifact tests
5. Governed roots computation + file filtering + governed roots tests
6. `NodeImportSpecifierExtractor` + extraction tests
7. `NodeDynamicImportDetector` + detection tests
8. `NodeImportBoundaryResolver` + resolution tests
9. `NodeImportContainmentScanner` (orchestrator) + scanner integration tests
10. Drift gate + drift tests
11. `impl.allowedDeps` guard + guard tests
12. Fixture projects + end-to-end integration tests + performance benchmarks

---

## File Structure

New files:
```
kernel/src/main/java/com/bear/kernel/target/node/
  NodeTarget.java
  NodeTargetDetector.java
  NodeImportSpecifierExtractor.java
  NodeDynamicImportDetector.java
  NodeImportBoundaryResolver.java
  NodeImportContainmentScanner.java
  TypeScriptLexicalSupport.java
  TypeScriptTypeMapper.java
  TypeScriptArtifactGenerator.java
  TypeScriptManifestGenerator.java
```

Modified files:
```
kernel/src/main/java/com/bear/kernel/target/TargetRegistry.java
  — register NodeTarget + NodeTargetDetector in defaultRegistry()
```

Note: `TargetId.NODE` already exists from Phase A. No changes to `TargetId.java`.

---

## Validation Criteria

Phase B is complete when:
- All 36 correctness properties pass (100+ iterations each)
- All unit tests pass
- All integration tests pass
- All existing JVM tests pass without modification
- Fixture projects compile and check successfully
- Node fixture fails `check` on boundary bypass (exit `7`)
- Node fixture fails `check` on drift (exit `5`)
- Node fixture with `allowedDeps` fails `check` (exit `64`)
- JVM fixture behavior unchanged
