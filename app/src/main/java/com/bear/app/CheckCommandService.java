package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.target.JvmTarget;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class CheckCommandService {
    private static final IrPipeline IR_PIPELINE = new DefaultIrPipeline();
    private static final String GENERATED_BEAR_ROOT = "build/generated/bear";
    private static final String GENERATED_WIRING_PREFIX = GENERATED_BEAR_ROOT + "/wiring/";
    private static final String CONTAINMENT_ENTRYPOINT_PATH = "build/generated/bear/gradle/bear-containment.gradle";
    private static final String SHARED_POLICY_PATH = "spec/_shared.policy.yaml";
    private static final int WIRING_DETAIL_LIMIT = 20;
    private static final String RUNTIME_LEGACY_PREFIX = "runtime/src/main/java/com/bear/generated/runtime/";
    private static final String RUNTIME_CANONICAL_PREFIX = "src/main/java/com/bear/generated/runtime/";
    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "record", "sealed", "permits", "var", "yield", "non-sealed"
    );

    private CheckCommandService() {
    }

    static CheckResult executeCheck(
        Path irFile,
        Path projectRoot,
        boolean runReachAndTests,
        boolean strictHygiene,
        String expectedBlockKey,
        String expectedBlockLocator
    ) {
        return executeCheck(
            irFile,
            projectRoot,
            runReachAndTests,
            strictHygiene,
            expectedBlockKey,
            expectedBlockLocator,
            null,
            true,
            null,
            false
        );
    }

    static CheckResult executeCheck(
        Path irFile,
        Path projectRoot,
        boolean runReachAndTests,
        boolean strictHygiene,
        String expectedBlockKey,
        String expectedBlockLocator,
        Path explicitIndexPath
    ) {
        return executeCheck(
            irFile,
            projectRoot,
            runReachAndTests,
            strictHygiene,
            expectedBlockKey,
            expectedBlockLocator,
            null,
            true,
            explicitIndexPath,
            false
        );
    }

    static CheckResult executeCheck(
        Path irFile,
        Path projectRoot,
        boolean runReachAndTests,
        boolean strictHygiene,
        String expectedBlockKey,
        String expectedBlockLocator,
        Path explicitIndexPath,
        boolean collectAll
    ) {
        return executeCheck(
            irFile,
            projectRoot,
            runReachAndTests,
            strictHygiene,
            expectedBlockKey,
            expectedBlockLocator,
            null,
            true,
            explicitIndexPath,
            collectAll
        );
    }

    static CheckResult executeCheck(
        Path irFile,
        Path projectRoot,
        boolean runReachAndTests,
        boolean strictHygiene,
        String expectedBlockKey,
        String expectedBlockLocator,
        Boolean considerContainmentSurfacesOverride
    ) {
        return executeCheck(
            irFile,
            projectRoot,
            runReachAndTests,
            strictHygiene,
            expectedBlockKey,
            expectedBlockLocator,
            considerContainmentSurfacesOverride,
            true,
            null,
            false
        );
    }

    static CheckResult executeCheck(
        Path irFile,
        Path projectRoot,
        boolean runReachAndTests,
        boolean strictHygiene,
        String expectedBlockKey,
        String expectedBlockLocator,
        Boolean considerContainmentSurfacesOverride,
        boolean runContainmentPreflight
    ) {
        return executeCheck(
            irFile,
            projectRoot,
            runReachAndTests,
            strictHygiene,
            expectedBlockKey,
            expectedBlockLocator,
            considerContainmentSurfacesOverride,
            runContainmentPreflight,
            null,
            false
        );
    }

    static CheckResult executeCheck(
        Path irFile,
        Path projectRoot,
        boolean runReachAndTests,
        boolean strictHygiene,
        String expectedBlockKey,
        String expectedBlockLocator,
        Boolean considerContainmentSurfacesOverride,
        boolean runContainmentPreflight,
        Path explicitIndexPath
    ) {
        return executeCheck(
            irFile,
            projectRoot,
            runReachAndTests,
            strictHygiene,
            expectedBlockKey,
            expectedBlockLocator,
            considerContainmentSurfacesOverride,
            runContainmentPreflight,
            explicitIndexPath,
            false
        );
    }

    static CheckResult executeCheck(
        Path irFile,
        Path projectRoot,
        boolean runReachAndTests,
        boolean strictHygiene,
        String expectedBlockKey,
        String expectedBlockLocator,
        Boolean considerContainmentSurfacesOverride,
        boolean runContainmentPreflight,
        Path explicitIndexPath,
        boolean collectAll
    ) {
        Path baselineRoot = projectRoot.resolve("build").resolve("generated").resolve("bear");
        Path tempRoot = null;
        BlockPortGraph blockPortGraph = null;
        try {
            maybeFailInternalForTest();
            CheckResult forcedTimeout = forceProjectTestTimeoutForTest();
            if (forcedTimeout != null) {
                return forcedTimeout;
            }
            JvmTarget target = new JvmTarget();
            BearIr normalized = IR_PIPELINE.parseValidateNormalize(irFile);

            Path resolvedIndexPath = explicitIndexPath;
            if (BlockPortGraphResolver.hasBlockPortEffects(normalized)) {
                resolvedIndexPath = SingleFileIndexResolver.resolveForBlockPorts(projectRoot, explicitIndexPath, "check");
            }
            if (resolvedIndexPath != null) {
                Path indexAbsolute = resolvedIndexPath.toAbsolutePath().normalize();
                Path repoRoot = indexAbsolute.getParent();
                if (repoRoot == null) {
                    String line = "index: VALIDATION_ERROR: BLOCK_PORT_INDEX_REQUIRED: invalid --index path";
                    return checkFailure(
                        CliCodes.EXIT_VALIDATION,
                        List.of(line),
                        "VALIDATION",
                        CliCodes.IR_VALIDATION,
                        "bear.blocks.yaml",
                        "Pass a valid `--index` path and rerun the command.",
                        line
                    );
                }
                blockPortGraph = BlockPortGraphResolver.resolveAndValidate(repoRoot, indexAbsolute);
            }
            boolean considerContainmentSurfaces = considerContainmentSurfacesOverride != null
                ? considerContainmentSurfacesOverride
                : CheckContainmentStage.hasAllowedDeps(normalized) || sharedContainmentInScope(projectRoot);
            BlockIdentityResolution identity = expectedBlockKey == null
                ? BlockIdentityResolver.resolveSingleCommandIdentity(irFile, projectRoot, normalized.block().name(), resolvedIndexPath)
                : BlockIdentityResolver.resolveIndexIdentity(
                    expectedBlockKey,
                    expectedBlockLocator == null || expectedBlockLocator.isBlank()
                        ? "bear.blocks.yaml:name=" + expectedBlockKey
                        : expectedBlockLocator,
                    normalized.block().name()
                );
            String blockKey = identity.blockKey();
            String packageSegment = toGeneratedPackageSegment(normalized.block().name());
            Set<String> ownedPrefixes = Set.of(
                "src/main/java/com/bear/generated/" + packageSegment.replace('.', '/') + "/",
                "src/test/java/com/bear/generated/" + packageSegment.replace('.', '/') + "/",
                RUNTIME_LEGACY_PREFIX,
                RUNTIME_CANONICAL_PREFIX
            );
            String markerRelPath = "surfaces/" + blockKey + ".surface.json";
            String wiringRelPath = "wiring/" + blockKey + ".wiring.json";
            Path legacyMarkerPath = baselineRoot.resolve("bear.surface.json");
            if (Files.isRegularFile(legacyMarkerPath)) {
                return checkFailure(
                    CliCodes.EXIT_IO,
                    List.of("check: IO_ERROR: LEGACY_SURFACE_MARKER: build/generated/bear/bear.surface.json"),
                    "IO_ERROR",
                    CliCodes.IO_ERROR,
                    "build/generated/bear/bear.surface.json",
                    "Delete legacy marker and rerun compile for managed blocks, then rerun `bear check`.",
                    "check: IO_ERROR: LEGACY_SURFACE_MARKER: build/generated/bear/bear.surface.json"
                );
            }

            if (!DriftAnalyzer.hasOwnedBaselineFiles(baselineRoot, ownedPrefixes, markerRelPath)) {
                String line = "drift: MISSING_BASELINE: " + GENERATED_BEAR_ROOT + " (run: bear compile "
                    + irFile + " --project " + projectRoot + ")";
                String wiringMissingLine = formatWiringDriftLine("MISSING_BASELINE", toCanonicalWiringPath(wiringRelPath));
                return checkFailure(
                    CliCodes.EXIT_DRIFT,
                    List.of(line, wiringMissingLine),
                    "DRIFT",
                    CliCodes.DRIFT_MISSING_BASELINE,
                    GENERATED_BEAR_ROOT,
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    summarizeWiringDriftDetail(List.of(wiringDetailToken("MISSING_BASELINE", toCanonicalWiringPath(wiringRelPath))))
                );
            }

            tempRoot = Files.createTempDirectory("bear-check-");
            if (Files.isDirectory(projectRoot.resolve("src/main/java/blocks/_shared"))) {
                Files.createDirectories(tempRoot.resolve("src/main/java/blocks/_shared"));
            }
            target.compile(normalized, tempRoot, blockKey);
            Path candidateRoot = tempRoot.resolve("build").resolve("generated").resolve("bear");
            Path baselineManifestPath = baselineRoot.resolve(markerRelPath);
            Path candidateManifestPath = candidateRoot.resolve(markerRelPath);
            Path baselineWiringPath = baselineRoot.resolve(wiringRelPath);
            Path candidateWiringPath = candidateRoot.resolve(wiringRelPath);

            applyCandidateManifestTestMode(candidateManifestPath);

            List<String> diagnostics = new ArrayList<>();
            String containmentSkipInfo = containmentSkipInfoLine(
                projectRoot.toString().replace('\\', '/'),
                projectRoot,
                considerContainmentSurfaces
            );
            BoundaryManifest baselineManifest = null;
            if (!Files.isRegularFile(baselineManifestPath)) {
                diagnostics.add("check: BASELINE_MANIFEST_MISSING: " + baselineManifestPath);
            } else {
                try {
                    baselineManifest = ManifestParsers.parseManifest(baselineManifestPath);
                } catch (ManifestParseException e) {
                    diagnostics.add("check: BASELINE_MANIFEST_INVALID: " + e.reasonCode());
                }
            }
            if (!Files.isRegularFile(candidateManifestPath)) {
                return checkFailure(
                    CliCodes.EXIT_INTERNAL,
                    List.of("internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_MISSING"),
                    "INTERNAL_ERROR",
                    CliCodes.INTERNAL_ERROR,
                    "build/generated/bear/" + markerRelPath,
                    "Capture stderr and file an issue against bear-cli.",
                    "internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_MISSING"
                );
            }
            BoundaryManifest candidateManifest;
            try {
                candidateManifest = ManifestParsers.parseManifest(candidateManifestPath);
            } catch (ManifestParseException e) {
                return checkFailure(
                    CliCodes.EXIT_INTERNAL,
                    List.of("internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_INVALID:" + e.reasonCode()),
                    "INTERNAL_ERROR",
                    CliCodes.INTERNAL_ERROR,
                    "build/generated/bear/" + markerRelPath,
                    "Capture stderr and file an issue against bear-cli.",
                    "internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_INVALID:" + e.reasonCode()
                );
            }

            if (!Files.isRegularFile(baselineWiringPath)) {
                String canonicalWiringPath = toCanonicalWiringPath(wiringRelPath);
                String line = formatWiringDriftLine("MISSING_BASELINE", canonicalWiringPath);
                return checkFailure(
                    CliCodes.EXIT_DRIFT,
                    List.of(line),
                    "DRIFT",
                    CliCodes.DRIFT_MISSING_BASELINE,
                    canonicalWiringPath,
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    summarizeWiringDriftDetail(List.of(wiringDetailToken("MISSING_BASELINE", canonicalWiringPath)))
                );
            }
            if (!Files.isRegularFile(candidateWiringPath)) {
                return checkFailure(
                    CliCodes.EXIT_INTERNAL,
                    List.of("internal: INTERNAL_ERROR: CANDIDATE_WIRING_MANIFEST_MISSING"),
                    "INTERNAL_ERROR",
                    CliCodes.INTERNAL_ERROR,
                    "build/generated/bear/" + wiringRelPath,
                    "Capture stderr and file an issue against bear-cli.",
                    "internal: INTERNAL_ERROR: CANDIDATE_WIRING_MANIFEST_MISSING"
                );
            }
            WiringManifest baselineWiringManifest;
            try {
                baselineWiringManifest = ManifestParsers.parseWiringManifest(baselineWiringPath);
            } catch (ManifestParseException e) {
                if (isManifestSemanticFieldError(e)) {
                    String line = "check: MANIFEST_INVALID: " + e.reasonCode();
                    return checkFailure(
                        CliCodes.EXIT_VALIDATION,
                        List.of(line),
                        "VALIDATION",
                        CliCodes.MANIFEST_INVALID,
                        "build/generated/bear/" + wiringRelPath,
                        "Regenerate wiring manifests with governed binding fields and rerun `bear check`.",
                        line
                    );
                }
                return checkFailure(
                    CliCodes.EXIT_DRIFT,
                    List.of("drift: BASELINE_WIRING_MANIFEST_INVALID: " + e.reasonCode()),
                    "DRIFT",
                    CliCodes.DRIFT_MISSING_BASELINE,
                    "build/generated/bear/" + wiringRelPath,
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    "drift: BASELINE_WIRING_MANIFEST_INVALID: " + e.reasonCode()
                );
            }
            WiringManifest candidateWiringManifest;
            try {
                candidateWiringManifest = ManifestParsers.parseWiringManifest(candidateWiringPath);
            } catch (ManifestParseException e) {
                if (isManifestSemanticFieldError(e)) {
                    String line = "check: MANIFEST_INVALID: " + e.reasonCode();
                    return checkFailure(
                        CliCodes.EXIT_VALIDATION,
                        List.of(line),
                        "VALIDATION",
                        CliCodes.MANIFEST_INVALID,
                        "build/generated/bear/" + wiringRelPath,
                        "Regenerate wiring manifests with governed binding fields and rerun `bear check`.",
                        line
                    );
                }
                return checkFailure(
                    CliCodes.EXIT_INTERNAL,
                    List.of("internal: INTERNAL_ERROR: CANDIDATE_WIRING_MANIFEST_INVALID:" + e.reasonCode()),
                    "INTERNAL_ERROR",
                    CliCodes.INTERNAL_ERROR,
                    "build/generated/bear/" + wiringRelPath,
                    "Capture stderr and file an issue against bear-cli.",
                    "internal: INTERNAL_ERROR: CANDIDATE_WIRING_MANIFEST_INVALID:" + e.reasonCode()
                );
            }
            CheckResult baselineWiringValidation = validateWiringManifestSemantics(baselineWiringManifest, "build/generated/bear/" + wiringRelPath);
            if (baselineWiringValidation != null) {
                return baselineWiringValidation;
            }
            CheckResult candidateWiringValidation = validateWiringManifestSemantics(candidateWiringManifest, "build/generated/bear/" + wiringRelPath);
            if (candidateWiringValidation != null) {
                return candidateWiringValidation;
            }
            if ((normalized.block().idempotency() != null || (normalized.block().invariants() != null && !normalized.block().invariants().isEmpty()))
                && !"jvm".equals(candidateManifest.target())) {
                return checkFailure(
                    CliCodes.EXIT_VALIDATION,
                    List.of("check: MANIFEST_INVALID: unsupported semantic enforcement target: " + candidateManifest.target()),
                    "VALIDATION",
                    CliCodes.MANIFEST_INVALID,
                    "build/generated/bear/" + markerRelPath,
                    "Use a target that enforces declared semantics or remove semantic declarations.",
                    "check: MANIFEST_INVALID: unsupported semantic enforcement target: " + candidateManifest.target()
                );
            }

            List<BoundarySignal> boundarySignals = List.of();
            if (baselineManifest != null) {
                if (!baselineManifest.irHash().equals(candidateManifest.irHash())
                    || !baselineManifest.generatorVersion().equals(candidateManifest.generatorVersion())) {
                    diagnostics.add("check: BASELINE_STAMP_MISMATCH: irHash/generatorVersion differ; classification may be stale");
                }
                boundarySignals = PrDeltaClassifier.computeBoundarySignals(baselineManifest, candidateManifest);
            }
            for (BoundarySignal signal : boundarySignals) {
                diagnostics.add("boundary: EXPANSION: " + signal.type().label + ": " + signal.key());
            }

            List<DriftItem> drift = DriftAnalyzer.computeDrift(
                baselineRoot,
                candidateRoot,
                path -> path.equals(markerRelPath)
                    || path.equals(wiringRelPath)
                    || DriftAnalyzer.startsWithAny(path, ownedPrefixes)
            );
            drift.sort((left, right) -> {
                int pathCmp = left.path().compareTo(right.path());
                if (pathCmp != 0) {
                    return pathCmp;
                }
                return Integer.compare(left.type().order, right.type().order);
            });
            if (!drift.isEmpty()) {
                LinkedHashSet<String> emittedDriftLines = new LinkedHashSet<>();
                List<String> wiringDetailTokens = new ArrayList<>();
                for (DriftItem item : drift) {
                    String reason = item.type().label;
                    String path = item.path();
                    if (isWiringDriftPath(path)) {
                        String canonicalWiringPath = toCanonicalWiringPath(path);
                        wiringDetailTokens.add(wiringDetailToken(reason, canonicalWiringPath));
                        emittedDriftLines.add(formatWiringDriftLine(reason, canonicalWiringPath));
                        continue;
                    }
                    emittedDriftLines.add("drift: " + reason + ": " + path);
                }
                diagnostics.addAll(emittedDriftLines);
                String detail = diagnostics.get(diagnostics.size() - 1);
                String wiringDetail = summarizeWiringDriftDetail(wiringDetailTokens);
                if (!wiringDetail.isBlank()) {
                    detail = wiringDetail;
                }
                return checkFailure(
                    CliCodes.EXIT_DRIFT,
                    diagnostics,
                    "DRIFT",
                    CliCodes.DRIFT_DETECTED,
                    GENERATED_BEAR_ROOT,
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    detail
                );
            }

            if (runContainmentPreflight) {
                CheckResult containmentFailure = preflightContainmentIfRequired(projectRoot, diagnostics, considerContainmentSurfaces);
                if (containmentFailure != null) {
                    return containmentFailure;
                }
            }

            if (!runReachAndTests) {
                return new CheckResult(CliCodes.EXIT_OK, List.of(), List.of(), null, null, null, null, null);
            }

            Set<String> reflectionAllowlist = PolicyAllowlistParser.parseExactPathAllowlist(
                projectRoot,
                PolicyAllowlistParser.REFLECTION_ALLOWLIST_PATH
            );
            if (strictHygiene) {
                Set<String> hygieneAllowlist = PolicyAllowlistParser.parseExactPathAllowlist(
                    projectRoot,
                    PolicyAllowlistParser.HYGIENE_ALLOWLIST_PATH
                );
                List<String> unexpectedPaths = HygieneScanner.scanUnexpectedPaths(projectRoot, hygieneAllowlist);
                if (!unexpectedPaths.isEmpty()) {
                    List<String> selectedUnexpectedPaths = collectAll
                        ? List.copyOf(unexpectedPaths)
                        : List.of(unexpectedPaths.get(0));
                    ArrayList<AgentDiagnostics.AgentProblem> problems = new ArrayList<>();
                    for (String unexpectedPath : selectedUnexpectedPaths) {
                        String line = "check: HYGIENE_UNEXPECTED_PATHS: " + unexpectedPath;
                        diagnostics.add(line);
                        problems.add(AgentDiagnostics.problem(
                            AgentDiagnostics.AgentCategory.GOVERNANCE,
                            CliCodes.HYGIENE_UNEXPECTED_PATHS,
                            CliCodes.HYGIENE_UNEXPECTED_PATHS,
                            null,
                            AgentDiagnostics.AgentSeverity.ERROR,
                            blockKey,
                            unexpectedPath,
                            null,
                            CliCodes.HYGIENE_UNEXPECTED_PATHS,
                            line,
                            Map.of()
                        ));
                    }
                    String firstLine = "check: HYGIENE_UNEXPECTED_PATHS: " + selectedUnexpectedPaths.get(0);
                    return checkFailure(
                        CliCodes.EXIT_UNDECLARED_REACH,
                        diagnostics,
                        "HYGIENE",
                        CliCodes.HYGIENE_UNEXPECTED_PATHS,
                        selectedUnexpectedPaths.get(0),
                        "Remove unexpected tool directories or allowlist them in `.bear/policy/hygiene-allowlist.txt`, then rerun `bear check`.",
                        firstLine,
                        problems
                    );
                }
            }

            List<UndeclaredReachFinding> undeclaredReach = UndeclaredReachScanner.scanUndeclaredReach(projectRoot);
            if (!undeclaredReach.isEmpty()) {
                List<UndeclaredReachFinding> selectedUndeclaredReach = collectAll
                    ? List.copyOf(undeclaredReach)
                    : List.of(undeclaredReach.get(0));
                ArrayList<AgentDiagnostics.AgentProblem> problems = new ArrayList<>();
                for (UndeclaredReachFinding finding : selectedUndeclaredReach) {
                    String line = "check: UNDECLARED_REACH: " + finding.path() + ": " + finding.surface();
                    diagnostics.add(line);
                    problems.add(AgentDiagnostics.problem(
                        AgentDiagnostics.AgentCategory.GOVERNANCE,
                        CliCodes.UNDECLARED_REACH,
                        CliCodes.UNDECLARED_REACH,
                        null,
                        AgentDiagnostics.AgentSeverity.ERROR,
                        blockKey,
                        finding.path(),
                        null,
                        CliCodes.UNDECLARED_REACH,
                        line,
                        Map.of("identityKey", finding.path() + "|" + finding.surface())
                    ));
                }
                return checkFailure(
                    CliCodes.EXIT_UNDECLARED_REACH,
                    diagnostics,
                    "UNDECLARED_REACH",
                    CliCodes.UNDECLARED_REACH,
                    selectedUndeclaredReach.get(0).path(),
                    "Declare a port/op in IR, run bear compile, and route call through generated port interface.",
                    diagnostics.get(diagnostics.size() - 1),
                    problems
                );
            }

            List<UndeclaredReachFinding> reflectionDispatchFindings =
                GovernedReflectionDispatchScanner.scanForbiddenReflectionDispatch(projectRoot, List.of(baselineWiringManifest));
            if (!reflectionDispatchFindings.isEmpty()) {
                List<UndeclaredReachFinding> selectedReflectionFindings = collectAll
                    ? List.copyOf(reflectionDispatchFindings)
                    : List.of(reflectionDispatchFindings.get(0));
                ArrayList<AgentDiagnostics.AgentProblem> problems = new ArrayList<>();
                for (UndeclaredReachFinding finding : selectedReflectionFindings) {
                    String line = "check: UNDECLARED_REACH: " + finding.path() + ": " + finding.surface();
                    diagnostics.add(line);
                    problems.add(AgentDiagnostics.problem(
                        AgentDiagnostics.AgentCategory.GOVERNANCE,
                        CliCodes.REFLECTION_DISPATCH_FORBIDDEN,
                        CliCodes.REFLECTION_DISPATCH_FORBIDDEN,
                        null,
                        AgentDiagnostics.AgentSeverity.ERROR,
                        blockKey,
                        finding.path(),
                        null,
                        CliCodes.REFLECTION_DISPATCH_FORBIDDEN,
                        line,
                        Map.of("identityKey", finding.path() + "|" + finding.surface())
                    ));
                }
                return checkFailure(
                    CliCodes.EXIT_UNDECLARED_REACH,
                    diagnostics,
                    "UNDECLARED_REACH",
                    CliCodes.REFLECTION_DISPATCH_FORBIDDEN,
                    selectedReflectionFindings.get(0).path(),
                    "Remove reflection/method-handle dynamic dispatch from governed roots and route through declared generated boundaries.",
                    diagnostics.get(diagnostics.size() - 1),
                    problems
                );
            }

            TreeSet<String> inboundTargetWrapperFqcns = blockPortGraph == null
                ? new TreeSet<>()
                : BlockPortGraphResolver.inboundTargetWrapperFqcns(
                    blockPortGraph,
                    Set.of(baselineWiringManifest.blockKey())
                );

            List<BoundaryBypassFinding> bypassFindings = new ArrayList<>();
            bypassFindings.addAll(BoundaryBypassScanner.scanBoundaryBypass(
                projectRoot,
                List.of(baselineWiringManifest),
                reflectionAllowlist
            ));
            bypassFindings.addAll(BlockPortBindingEnforcer.scan(
                projectRoot,
                List.of(baselineWiringManifest),
                inboundTargetWrapperFqcns
            ));
            bypassFindings.sort(
                Comparator.comparing(BoundaryBypassFinding::path)
                    .thenComparing(BoundaryBypassFinding::rule)
                    .thenComparing(BoundaryBypassFinding::detail)
            );
            if (!bypassFindings.isEmpty()) {
                List<BoundaryBypassFinding> selectedBypassFindings = collectAll
                    ? List.copyOf(bypassFindings)
                    : List.of(bypassFindings.get(0));
                ArrayList<AgentDiagnostics.AgentProblem> problems = new ArrayList<>();
                for (BoundaryBypassFinding finding : selectedBypassFindings) {
                    String line = "check: BOUNDARY_BYPASS: RULE="
                        + finding.rule()
                        + ": "
                        + finding.path()
                        + ": "
                        + finding.detail();
                    diagnostics.add(line);
                    problems.add(AgentDiagnostics.problem(
                        AgentDiagnostics.AgentCategory.GOVERNANCE,
                        CliCodes.BOUNDARY_BYPASS,
                        finding.rule(),
                        null,
                        AgentDiagnostics.AgentSeverity.ERROR,
                        blockKey,
                        finding.path(),
                        null,
                        finding.rule(),
                        line,
                        Map.of("identityKey", finding.path() + "|" + finding.rule() + "|" + finding.detail())
                    ));
                }
                return checkFailure(
                    CliCodes.EXIT_BOUNDARY_BYPASS,
                    diagnostics,
                    "BOUNDARY_BYPASS",
                    CliCodes.BOUNDARY_BYPASS,
                    selectedBypassFindings.get(0).path(),
                    boundaryBypassRemediation(selectedBypassFindings.get(0).rule()),
                    diagnostics.get(diagnostics.size() - 1),
                    problems
                );
            }

            ProjectTestResult testResult = ProjectTestRunner.runProjectTests(
                projectRoot,
                considerContainmentSurfaces ? CONTAINMENT_ENTRYPOINT_PATH : null
            );
            if (testResult.status() == ProjectTestStatus.LOCKED) {
                String lockLine = testResult.firstLockLine() != null
                    ? testResult.firstLockLine()
                    : ProjectTestRunner.firstGradleLockLine(testResult.output());
                String markerWriteSuffix = "";
                try {
                    writeCheckBlockedMarker(projectRoot, CheckBlockedMarker.REASON_LOCK, lockLine);
                } catch (IOException markerWriteError) {
                    markerWriteSuffix = markerWriteFailureSuffix(markerWriteError);
                }
                String ioLine = lockLine == null
                    ? "io: IO_ERROR: PROJECT_TEST_LOCK: Gradle wrapper lock detected"
                    : "io: IO_ERROR: PROJECT_TEST_LOCK: " + lockLine;
                ioLine += testDiagnosticsSuffix(testResult);
                if (!markerWriteSuffix.isBlank()) {
                    ioLine += markerWriteSuffix;
                }
                diagnostics.add(ioLine);
                diagnostics.addAll(CliText.tailLines(testResult.output()));
                return checkFailure(
                    CliCodes.EXIT_IO,
                    diagnostics,
                    "IO_ERROR",
                    CliCodes.IO_ERROR,
                    "project.tests",
                    "Use BEAR-selected GRADLE_USER_HOME mode, run `bear unblock --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    ioLine,
                    List.of(defaultProblem(
                        CliCodes.IO_ERROR,
                        "project.tests",
                        ioLine,
                        blockKey,
                        null,
                        "PROJECT_TEST_LOCK"
                    ))
                );
            }
            if (testResult.status() == ProjectTestStatus.BOOTSTRAP_IO) {
                String bootstrapLine = testResult.firstBootstrapLine() != null
                    ? testResult.firstBootstrapLine()
                    : ProjectTestRunner.firstGradleBootstrapIoLine(testResult.output());
                String markerWriteSuffix = "";
                try {
                    writeCheckBlockedMarker(projectRoot, CheckBlockedMarker.REASON_BOOTSTRAP, bootstrapLine);
                } catch (IOException markerWriteError) {
                    markerWriteSuffix = markerWriteFailureSuffix(markerWriteError);
                }
                String ioLine = bootstrapLine == null
                    ? "io: IO_ERROR: PROJECT_TEST_BOOTSTRAP: Gradle wrapper bootstrap/unzip failed"
                    : "io: IO_ERROR: PROJECT_TEST_BOOTSTRAP: " + bootstrapLine;
                ioLine += testDiagnosticsSuffix(testResult);
                if (!markerWriteSuffix.isBlank()) {
                    ioLine += markerWriteSuffix;
                }
                diagnostics.add(ioLine);
                diagnostics.addAll(CliText.tailLines(testResult.output()));
                return checkFailure(
                    CliCodes.EXIT_IO,
                    diagnostics,
                    "IO_ERROR",
                    CliCodes.IO_ERROR,
                    "project.tests",
                    "Use BEAR-selected GRADLE_USER_HOME mode, run `bear unblock --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    ioLine,
                    List.of(defaultProblem(
                        CliCodes.IO_ERROR,
                        "project.tests",
                        ioLine,
                        blockKey,
                        null,
                        "PROJECT_TEST_BOOTSTRAP"
                    ))
                );
            }
            if (testResult.status() == ProjectTestStatus.SHARED_DEPS_VIOLATION) {
                String sharedLine = testResult.firstSharedDepsViolationLine() != null
                    ? testResult.firstSharedDepsViolationLine()
                    : ProjectTestRunner.firstSharedDepsViolationLine(testResult.output());
                String violationLine = "check: CONTAINMENT_REQUIRED: SHARED_DEPS_VIOLATION: "
                    + projectRoot.toString().replace('\\', '/')
                    + ":_shared";
                if (sharedLine != null && !sharedLine.isBlank()) {
                    violationLine += ": " + sharedLine;
                }
                diagnostics.add(violationLine);
                diagnostics.addAll(CliText.tailLines(testResult.output()));
                return checkFailure(
                    CliCodes.EXIT_IO,
                    diagnostics,
                    "CONTAINMENT",
                    CliCodes.CONTAINMENT_NOT_VERIFIED,
                    SHARED_POLICY_PATH,
                    "Add dependency to `spec/_shared.policy.yaml` with exact pinned version, or remove external dependency usage from `src/main/java/blocks/_shared/**`, then rerun `bear check`.",
                    violationLine
                );
            }
            if (testResult.status() == ProjectTestStatus.COMPILE_FAILURE) {
                String markerLine = ProjectTestRunner.firstCompileFailureLine(testResult.output());
                if (isContainmentCompileMismatch(testResult, markerLine)) {
                    String containmentLine = "check: CONTAINMENT_REQUIRED: CONTAINMENT_METADATA_MISMATCH: project compile preflight failed";
                    if (markerLine != null && !markerLine.isBlank()) {
                        containmentLine += "; line: " + markerLine;
                    }
                    containmentLine += phaseTaskSuffix(testResult);
                    diagnostics.add(containmentLine);
                    diagnostics.addAll(CliText.tailLines(testResult.output()));
                    return checkFailure(
                        CliCodes.EXIT_TEST_FAILURE,
                        diagnostics,
                        "CONTAINMENT",
                        CliCodes.CONTAINMENT_NOT_VERIFIED,
                        "project.tests",
                        "Run `bear compile --all --project <path>` once, rerun the same `bear check`, and if the same containment/classpath signature persists stop and escalate.",
                        containmentLine,
                        List.of(defaultProblem(
                            CliCodes.CONTAINMENT_NOT_VERIFIED,
                            "project.tests",
                            containmentLine,
                            blockKey,
                            null,
                            CliCodes.CONTAINMENT_METADATA_MISMATCH
                        ))
                    );
                }
                String compileLine = "check: COMPILE_FAILURE: project compile preflight failed";
                if (markerLine != null && !markerLine.isBlank()) {
                    compileLine += "; line: " + markerLine;
                }
                compileLine += phaseTaskSuffix(testResult);
                diagnostics.add(compileLine);
                diagnostics.addAll(CliText.tailLines(testResult.output()));
                return checkFailure(
                    CliCodes.EXIT_TEST_FAILURE,
                    diagnostics,
                    "TEST_FAILURE",
                    CliCodes.COMPILE_FAILURE,
                    "project.tests",
                    "Fix compile errors and rerun `bear check <ir-file> --project <path>`.",
                    compileLine
                );
            }
            if (testResult.status() == ProjectTestStatus.FAILED) {
                diagnostics.add("check: TEST_FAILED: project tests failed");
                diagnostics.addAll(CliText.tailLines(testResult.output()));
                return checkFailure(
                    CliCodes.EXIT_TEST_FAILURE,
                    diagnostics,
                    "TEST_FAILURE",
                    CliCodes.TEST_FAILURE,
                    "project.tests",
                    "Fix project tests and rerun `bear check <ir-file> --project <path>`.",
                    "check: TEST_FAILED: project tests failed"
                );
            }
            if (testResult.status() == ProjectTestStatus.INVARIANT_VIOLATION) {
                String markerLine = ProjectTestRunner.firstInvariantViolationLine(testResult.output());
                String line = markerLine == null
                    ? "check: TEST_FAILED: invariant violation detected"
                    : "check: TEST_FAILED: " + markerLine;
                diagnostics.add(line);
                diagnostics.addAll(CliText.tailLines(testResult.output()));
                return checkFailure(
                    CliCodes.EXIT_TEST_FAILURE,
                    diagnostics,
                    "TEST_FAILURE",
                    CliCodes.INVARIANT_VIOLATION,
                    "project.tests",
                    "Fix invariant violation and rerun `bear check <ir-file> --project <path>`.",
                    line
                );
            }
            if (testResult.status() == ProjectTestStatus.TIMEOUT) {
                String timeoutLine = "check: TEST_TIMEOUT: project tests exceeded " + ProjectTestRunner.testTimeoutSeconds() + "s" + phaseTaskSuffix(testResult);
                diagnostics.add(timeoutLine);
                diagnostics.addAll(CliText.tailLines(testResult.output()));
                return checkFailure(
                    CliCodes.EXIT_TEST_FAILURE,
                    diagnostics,
                    "TEST_FAILURE",
                    CliCodes.TEST_TIMEOUT,
                    "project.tests",
                    "Reduce test runtime or increase timeout, then rerun `bear check <ir-file> --project <path>`.",
                    timeoutLine
                );
            }

            CheckResult containmentMarkerFailure = verifyContainmentMarkersIfRequired(projectRoot, diagnostics, considerContainmentSurfaces);
            if (containmentMarkerFailure != null) {
                return containmentMarkerFailure;
            }

            clearCheckBlockedMarker(projectRoot);
            ArrayList<String> successLines = new ArrayList<>();
            if (containmentSkipInfo != null) {
                successLines.add(containmentSkipInfo);
            }
            successLines.add("check: OK");
            return new CheckResult(CliCodes.EXIT_OK, List.copyOf(successLines), List.of(), null, null, null, null, null);
        } catch (BearIrValidationException e) {
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(e.formatLine()),
                "VALIDATION",
                CliCodes.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear check <ir-file> --project <path>`.",
                e.formatLine()
            );
        } catch (BlockIndexValidationException e) {
            String line = "index: VALIDATION_ERROR: " + e.getMessage();
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear check`.",
                line
            );
        } catch (BlockIdentityResolutionException e) {
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(e.line()),
                "VALIDATION",
                CliCodes.IR_VALIDATION,
                e.path(),
                e.remediation(),
                e.line()
            );
        } catch (ManifestParseException e) {
            String line = "check: MANIFEST_INVALID: " + e.reasonCode();
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.MANIFEST_INVALID,
                "build/generated/bear/wiring",
                "Regenerate wiring manifests with governed binding fields and rerun `bear check`.",
                line
            );
        } catch (PolicyValidationException e) {
            String line = "policy: VALIDATION_ERROR: " + e.getMessage();
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.POLICY_INVALID,
                e.policyPath(),
                "Fix the policy contract file and rerun `bear check`.",
                line
            );
        } catch (IOException e) {
            return checkFailure(
                CliCodes.EXIT_IO,
                List.of("io: IO_ERROR: " + e.getMessage()),
                "IO_ERROR",
                CliCodes.IO_ERROR,
                "project.root",
                "Ensure project paths are accessible (including Gradle wrapper), then rerun `bear check`.",
                "io: IO_ERROR: " + e.getMessage()
            );
        } catch (Exception e) {
            return checkFailure(
                CliCodes.EXIT_INTERNAL,
                List.of("internal: INTERNAL_ERROR:"),
                "INTERNAL_ERROR",
                CliCodes.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli.",
                "internal: INTERNAL_ERROR:"
            );
        } finally {
            if (tempRoot != null) {
                deleteRecursivelyBestEffort(tempRoot);
            }
        }
    }

    static String summarizeWiringDriftDetail(List<String> wiringDetailTokens) {
        if (wiringDetailTokens == null || wiringDetailTokens.isEmpty()) {
            return "";
        }
        List<String> ordered = new ArrayList<>();
        LinkedHashSet<String> deduped = new LinkedHashSet<>(wiringDetailTokens);
        ordered.addAll(deduped);
        ordered.sort(Comparator
            .comparing(CheckCommandService::wiringTokenPath)
            .thenComparingInt(token -> wiringReasonOrder(wiringTokenReason(token))));
        int limit = Math.min(WIRING_DETAIL_LIMIT, ordered.size());
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            String token = ordered.get(i);
            lines.add(formatWiringDriftLine(wiringTokenReason(token), wiringTokenPath(token)));
        }
        String summary = String.join(" | ", lines);
        if (ordered.size() > WIRING_DETAIL_LIMIT) {
            summary += " | (+" + (ordered.size() - WIRING_DETAIL_LIMIT) + " more)";
        }
        return summary;
    }

    static CheckResult preflightContainmentIfRequired(
        Path projectRoot,
        List<String> diagnostics,
        boolean considerContainmentSurfaces
    ) throws IOException {
        return CheckContainmentStage.preflightContainmentIfRequired(projectRoot, diagnostics, considerContainmentSurfaces);
    }

    static CheckResult verifyContainmentMarkersIfRequired(
        Path projectRoot,
        List<String> diagnostics,
        boolean considerContainmentSurfaces
    ) throws IOException {
        return CheckContainmentStage.verifyContainmentMarkersIfRequired(projectRoot, diagnostics, considerContainmentSurfaces);
    }

    static boolean sharedContainmentInScope(Path projectRoot) {
        return CheckContainmentStage.sharedContainmentInScope(projectRoot);
    }

    static boolean blockDeclaresAllowedDeps(Path irFile) {
        return CheckContainmentStage.blockDeclaresAllowedDeps(irFile);
    }

    static String containmentSkipInfoLine(String projectRootLabel, Path projectRoot, boolean considerContainmentSurfaces) {
        return CheckContainmentStage.containmentSkipInfoLine(projectRootLabel, projectRoot, considerContainmentSurfaces);
    }

    private static void applyCandidateManifestTestMode(Path candidateManifestPath) throws IOException {
        String mode = System.getProperty("bear.check.test.candidateManifestMode");
        if (mode == null || mode.isBlank()) {
            return;
        }
        if ("missing".equals(mode)) {
            Files.deleteIfExists(candidateManifestPath);
            return;
        }
        if ("invalid".equals(mode)) {
            Files.writeString(candidateManifestPath, "{", StandardCharsets.UTF_8);
            return;
        }
        if (mode.startsWith("target:")) {
            String forcedTarget = mode.substring("target:".length());
            String json = Files.readString(candidateManifestPath, StandardCharsets.UTF_8);
            Files.writeString(
                candidateManifestPath,
                json.replace("\"target\":\"jvm\"", "\"target\":\"" + forcedTarget + "\""),
                StandardCharsets.UTF_8
            );
        }
    }

    private static String testDiagnosticsSuffix(ProjectTestResult testResult) {
        return CheckDiagnosticsFormatter.testDiagnosticsSuffix(testResult);
    }

    private static String phaseTaskSuffix(ProjectTestResult testResult) {
        return CheckDiagnosticsFormatter.phaseTaskSuffix(testResult);
    }

    private static String markerWriteFailureSuffix(IOException error) {
        return CheckDiagnosticsFormatter.markerWriteFailureSuffix(error);
    }

    private static boolean isContainmentCompileMismatch(ProjectTestResult testResult, String markerLine) {
        return containsContainmentCompileSignal(markerLine)
            || containsContainmentCompileSignal(testResult.output())
            || containsContainmentCompileSignal(testResult.lastObservedTask());
    }

    private static boolean containsContainmentCompileSignal(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("compileBearImpl__shared")
            || text.contains("build/generated/bear/gradle/bear-containment.gradle")
            || text.contains("bear-containment.gradle")
            || text.contains("CONTAINMENT_REQUIRED:");
    }

    private static CheckResult validateWiringManifestSemantics(WiringManifest manifest, String path) {
        return CheckManifestValidation.validateWiringManifestSemantics(manifest, path);
    }

    private static boolean isManifestSemanticFieldError(ManifestParseException e) {
        return CheckManifestValidation.isManifestSemanticFieldError(e);
    }

    private static String boundaryBypassRemediation(String rule) {
        return CheckManifestValidation.boundaryBypassRemediation(rule);
    }

    private static void writeCheckBlockedMarker(Path projectRoot, String reason, String detail) throws IOException {
        Path marker = projectRoot.resolve(CheckBlockedMarker.RELATIVE_PATH);
        Files.createDirectories(marker.getParent());
        String safeReason = (reason == null || reason.isBlank()) ? "UNKNOWN" : reason;
        String safeDetail = (detail == null || detail.isBlank()) ? "no details" : detail.trim();
        String content = "reason=" + safeReason + "\n" + "detail=" + safeDetail + "\n";
        Files.writeString(marker, content, StandardCharsets.UTF_8);
    }

    private static void clearCheckBlockedMarker(Path projectRoot) throws IOException {
        Path marker = projectRoot.resolve(CheckBlockedMarker.RELATIVE_PATH);
        Files.deleteIfExists(marker);
    }

    private static String toGeneratedPackageSegment(String raw) {
        String normalized = raw.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
        if (normalized.isEmpty()) {
            return "block";
        }
        StringBuilder out = new StringBuilder();
        String[] parts = normalized.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('.');
            }
            String segment = parts[i];
            if (Character.isDigit(segment.charAt(0))) {
                segment = "_" + segment;
            }
            if (JAVA_KEYWORDS.contains(segment)) {
                segment = segment + "_";
            }
            out.append(segment);
        }
        return out.toString();
    }

    private static void maybeFailInternalForTest() {
        String key = "bear.cli.test.failInternal.check";
        if ("true".equals(System.getProperty(key))) {
            throw new IllegalStateException("INJECTED_INTERNAL_check");
        }
    }

    private static CheckResult forceProjectTestTimeoutForTest() {
        if (!"true".equalsIgnoreCase(System.getProperty("bear.check.test.forceTimeoutOutcome"))) {
            return null;
        }
        String timeoutLine = "check: TEST_TIMEOUT: project tests exceeded " + ProjectTestRunner.testTimeoutSeconds() + "s";
        return checkFailure(
            CliCodes.EXIT_TEST_FAILURE,
            List.of(timeoutLine),
            "TEST_FAILURE",
            CliCodes.TEST_TIMEOUT,
            "project.tests",
            "Reduce test runtime or increase timeout, then rerun `bear check <ir-file> --project <path>`.",
            timeoutLine
        );
    }

    private static boolean isWiringDriftPath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        return normalized.startsWith("wiring/") && normalized.endsWith(".wiring.json");
    }

    private static String toCanonicalWiringPath(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith(GENERATED_WIRING_PREFIX)) {
            return normalized;
        }
        if (normalized.startsWith("wiring/")) {
            return GENERATED_BEAR_ROOT + "/" + normalized;
        }
        return normalized;
    }

    private static String formatWiringDriftLine(String reason, String canonicalWiringPath) {
        return "drift: " + reason + ": " + canonicalWiringPath;
    }

    private static String wiringDetailToken(String reason, String canonicalPath) {
        return reason + "|" + canonicalPath;
    }

    private static String wiringTokenReason(String token) {
        int sep = token.indexOf('|');
        if (sep <= 0) {
            return token;
        }
        return token.substring(0, sep);
    }

    private static String wiringTokenPath(String token) {
        int sep = token.indexOf('|');
        if (sep < 0 || sep + 1 >= token.length()) {
            return "";
        }
        return token.substring(sep + 1);
    }

    private static int wiringReasonOrder(String reason) {
        return switch (reason) {
            case "MISSING_BASELINE" -> 0;
            case "REMOVED" -> 1;
            case "CHANGED" -> 2;
            case "ADDED" -> 3;
            default -> 99;
        };
    }

    private static CheckResult checkFailure(
        int exitCode,
        List<String> stderrLines,
        String category,
        String failureCode,
        String failurePath,
        String failureRemediation,
        String detail
    ) {
        return checkFailure(
            exitCode,
            stderrLines,
            category,
            failureCode,
            failurePath,
            failureRemediation,
            detail,
            List.of(defaultProblem(failureCode, failurePath, detail, null, null, failureCode))
        );
    }

    private static CheckResult checkFailure(
        int exitCode,
        List<String> stderrLines,
        String category,
        String failureCode,
        String failurePath,
        String failureRemediation,
        String detail,
        List<AgentDiagnostics.AgentProblem> problems
    ) {
        return new CheckResult(
            exitCode,
            List.of(),
            List.copyOf(stderrLines),
            category,
            failureCode,
            failurePath,
            failureRemediation,
            detail,
            List.copyOf(problems)
        );
    }

    private static AgentDiagnostics.AgentProblem defaultProblem(
        String failureCode,
        String failurePath,
        String detail,
        String blockId,
        String ruleId,
        String reasonKey
    ) {
        AgentDiagnostics.AgentCategory category = isGovernanceFailureCode(failureCode)
            ? AgentDiagnostics.AgentCategory.GOVERNANCE
            : AgentDiagnostics.AgentCategory.INFRA;
        String normalizedPath = FailureEnvelopeEmitter.normalizeLocator(failurePath);
        Map<String, String> evidence = RepeatableRuleRegistry.requiresIdentityKey(ruleId)
            ? Map.of("identityKey", nullToEmpty(normalizedPath) + "|" + nullToEmpty(ruleId) + "|" + nullToEmpty(detail))
            : Map.of();
        return AgentDiagnostics.problem(
            category,
            failureCode,
            ruleId,
            reasonKey,
            AgentDiagnostics.AgentSeverity.ERROR,
            blockId,
            normalizedPath,
            null,
            ruleId != null ? ruleId : reasonKey,
            detail == null ? "" : detail,
            evidence
        );
    }

    private static boolean isGovernanceFailureCode(String failureCode) {
        if (failureCode == null) {
            return false;
        }
        return CliCodes.BOUNDARY_BYPASS.equals(failureCode)
            || CliCodes.UNDECLARED_REACH.equals(failureCode)
            || CliCodes.REFLECTION_DISPATCH_FORBIDDEN.equals(failureCode)
            || CliCodes.HYGIENE_UNEXPECTED_PATHS.equals(failureCode)
            || CliCodes.BOUNDARY_EXPANSION.equals(failureCode)
            || GovernanceRuleRegistry.PUBLIC_RULE_IDS.contains(failureCode);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void deleteRecursivelyBestEffort(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort only: check result must not depend on cleanup success.
                }
            });
        } catch (IOException ignored) {
            // Best effort only.
        }
    }

}





