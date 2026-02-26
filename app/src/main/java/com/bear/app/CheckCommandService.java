package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrNormalizer;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.ir.BearIrValidator;
import com.bear.kernel.policy.SharedAllowedDepsPolicyException;
import com.bear.kernel.policy.SharedAllowedDepsPolicyParser;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CheckCommandService {
    private static final String CHECK_BLOCKED_MARKER_RELATIVE = "build/bear/check.blocked.marker";
    private static final String CHECK_BLOCKED_REASON_LOCK = "LOCK";
    private static final String CHECK_BLOCKED_REASON_BOOTSTRAP = "BOOTSTRAP_IO";
    private static final String GENERATED_BEAR_ROOT = "build/generated/bear";
    private static final String GENERATED_WIRING_PREFIX = GENERATED_BEAR_ROOT + "/wiring/";
    private static final String CONTAINMENT_REQUIRED_PATH = "build/generated/bear/config/containment-required.json";
    private static final String CONTAINMENT_ENTRYPOINT_PATH = "build/generated/bear/gradle/bear-containment.gradle";
    private static final String CONTAINMENT_SKIP_REASON = "no_selected_blocks_with_impl_allowedDeps";
    private static final String CONTAINMENT_MARKER_DIR = "build/bear/containment";
    private static final String SHARED_POLICY_PATH = "spec/_shared.policy.yaml";
    private static final String SHARED_SOURCE_ROOT = "src/main/java/blocks/_shared";
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
            null
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
            true
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
        Path baselineRoot = projectRoot.resolve("build").resolve("generated").resolve("bear");
        Path tempRoot = null;
        try {
            maybeFailInternalForTest();
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            JvmTarget target = new JvmTarget();

            BearIr ir = parser.parse(irFile);
            validator.validate(ir);
            BearIr normalized = normalizer.normalize(ir);
            boolean considerContainmentSurfaces = considerContainmentSurfacesOverride != null
                ? considerContainmentSurfacesOverride
                : hasAllowedDeps(normalized) || sharedContainmentInScope(projectRoot);
            BlockIdentityResolution identity = expectedBlockKey == null
                ? BlockIdentityResolver.resolveSingleCommandIdentity(irFile, projectRoot, normalized.block().name())
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
            String containmentSkipInfo = maybeContainmentSkipInfo(
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
                    String line = "check: HYGIENE_UNEXPECTED_PATHS: " + unexpectedPaths.get(0);
                    diagnostics.add(line);
                    return checkFailure(
                        CliCodes.EXIT_UNDECLARED_REACH,
                        diagnostics,
                        "HYGIENE",
                        CliCodes.HYGIENE_UNEXPECTED_PATHS,
                        unexpectedPaths.get(0),
                        "Remove unexpected tool directories or allowlist them in `.bear/policy/hygiene-allowlist.txt`, then rerun `bear check`.",
                        line
                    );
                }
            }

            List<UndeclaredReachFinding> undeclaredReach = UndeclaredReachScanner.scanUndeclaredReach(projectRoot);
            if (!undeclaredReach.isEmpty()) {
                for (UndeclaredReachFinding finding : undeclaredReach) {
                    diagnostics.add("check: UNDECLARED_REACH: " + finding.path() + ": " + finding.surface());
                }
                return checkFailure(
                    CliCodes.EXIT_UNDECLARED_REACH,
                    diagnostics,
                    "UNDECLARED_REACH",
                    CliCodes.UNDECLARED_REACH,
                    undeclaredReach.get(0).path(),
                    "Declare a port/op in IR, run bear compile, and route call through generated port interface.",
                    diagnostics.get(diagnostics.size() - 1)
                );
            }

            List<BoundaryBypassFinding> bypassFindings = BoundaryBypassScanner.scanBoundaryBypass(
                projectRoot,
                List.of(baselineWiringManifest),
                reflectionAllowlist
            );
            if (!bypassFindings.isEmpty()) {
                for (BoundaryBypassFinding finding : bypassFindings) {
                    diagnostics.add(
                        "check: BOUNDARY_BYPASS: RULE="
                            + finding.rule()
                            + ": "
                            + finding.path()
                            + ": "
                            + finding.detail()
                    );
                }
                return checkFailure(
                    CliCodes.EXIT_BOUNDARY_BYPASS,
                    diagnostics,
                    "BOUNDARY_BYPASS",
                    CliCodes.BOUNDARY_BYPASS,
                    bypassFindings.get(0).path(),
                    boundaryBypassRemediation(bypassFindings.get(0).rule()),
                    diagnostics.get(diagnostics.size() - 1)
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
                    writeCheckBlockedMarker(projectRoot, CHECK_BLOCKED_REASON_LOCK, lockLine);
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
                    ioLine
                );
            }
            if (testResult.status() == ProjectTestStatus.BOOTSTRAP_IO) {
                String bootstrapLine = testResult.firstBootstrapLine() != null
                    ? testResult.firstBootstrapLine()
                    : ProjectTestRunner.firstGradleBootstrapIoLine(testResult.output());
                String markerWriteSuffix = "";
                try {
                    writeCheckBlockedMarker(projectRoot, CHECK_BLOCKED_REASON_BOOTSTRAP, bootstrapLine);
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
                    ioLine
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
                String timeoutLine = "check: TEST_TIMEOUT: project tests exceeded " + ProjectTestRunner.testTimeoutSeconds() + "s";
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
        if (!considerContainmentSurfaces) {
            return null;
        }
        Path sharedPolicyPath = projectRoot.resolve(SHARED_POLICY_PATH);
        if (Files.isRegularFile(sharedPolicyPath)) {
            SharedAllowedDepsPolicyParser parser = new SharedAllowedDepsPolicyParser();
            try {
                parser.parse(sharedPolicyPath);
            } catch (SharedAllowedDepsPolicyException e) {
                String line = "check: POLICY_INVALID: " + e.reasonCode();
                diagnostics.add(line);
                return checkFailure(
                    CliCodes.EXIT_VALIDATION,
                    diagnostics,
                    "VALIDATION",
                    CliCodes.POLICY_INVALID,
                    SHARED_POLICY_PATH,
                    "Fix `spec/_shared.policy.yaml` (version/scope/schema and pinned allowedDeps) and rerun `bear check`.",
                    line
                );
            }
        }
        Path gradlew = projectRoot.resolve("gradlew");
        Path gradlewBat = projectRoot.resolve("gradlew.bat");
        boolean hasWrapper = Files.isRegularFile(gradlew) || Files.isRegularFile(gradlewBat);
        if (!hasWrapper) {
            String line = "check: CONTAINMENT_REQUIRED: UNSUPPORTED_TARGET: missing Gradle wrapper";
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_IO,
                diagnostics,
                "CONTAINMENT",
                CliCodes.CONTAINMENT_UNSUPPORTED_TARGET,
                "project.root",
                "Containment enforcement in P2 requires Java+Gradle with wrapper at project root; remove `impl.allowedDeps`/`spec/_shared.policy.yaml` scope usage or use supported target, then rerun `bear check`.",
                line
            );
        }

        Path entrypoint = projectRoot.resolve(CONTAINMENT_ENTRYPOINT_PATH);
        if (!Files.isRegularFile(entrypoint)) {
            String line = "drift: MISSING_BASELINE: " + CONTAINMENT_ENTRYPOINT_PATH;
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_DRIFT,
                diagnostics,
                "DRIFT",
                CliCodes.DRIFT_MISSING_BASELINE,
                CONTAINMENT_ENTRYPOINT_PATH,
                "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                line
            );
        }

        Path required = projectRoot.resolve(CONTAINMENT_REQUIRED_PATH);
        if (!Files.isRegularFile(required)) {
            String line = "drift: MISSING_BASELINE: " + CONTAINMENT_REQUIRED_PATH;
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_DRIFT,
                diagnostics,
                "DRIFT",
                CliCodes.DRIFT_MISSING_BASELINE,
                CONTAINMENT_REQUIRED_PATH,
                "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                line
            );
        }
        try {
            parseContainmentRequiredIndex(required);
        } catch (ManifestParseException e) {
            String line = "drift: CHANGED: " + CONTAINMENT_REQUIRED_PATH;
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_DRIFT,
                diagnostics,
                "DRIFT",
                CliCodes.DRIFT_DETECTED,
                CONTAINMENT_REQUIRED_PATH,
                "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                line
            );
        }

        return null;
    }

    static CheckResult verifyContainmentMarkersIfRequired(
        Path projectRoot,
        List<String> diagnostics,
        boolean considerContainmentSurfaces
    ) throws IOException {
        if (!considerContainmentSurfaces) {
            return null;
        }

        Path required = projectRoot.resolve(CONTAINMENT_REQUIRED_PATH);
        if (!Files.isRegularFile(required)) {
            String line = "drift: MISSING_BASELINE: " + CONTAINMENT_REQUIRED_PATH;
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_DRIFT,
                diagnostics,
                "DRIFT",
                CliCodes.DRIFT_MISSING_BASELINE,
                CONTAINMENT_REQUIRED_PATH,
                "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                line
            );
        }
        ContainmentRequiredIndex requiredIndex;
        try {
            requiredIndex = parseContainmentRequiredIndex(required);
        } catch (ManifestParseException e) {
            String line = "drift: CHANGED: " + CONTAINMENT_REQUIRED_PATH;
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_DRIFT,
                diagnostics,
                "DRIFT",
                CliCodes.DRIFT_DETECTED,
                CONTAINMENT_REQUIRED_PATH,
                "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                line
            );
        }

        Path marker = projectRoot.resolve(CONTAINMENT_MARKER_DIR + "/applied.marker");
        if (!Files.isRegularFile(marker)) {
            String line = "check: CONTAINMENT_REQUIRED: MARKER_MISSING: " + CONTAINMENT_MARKER_DIR + "/applied.marker";
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_IO,
                diagnostics,
                "CONTAINMENT",
                CliCodes.CONTAINMENT_NOT_VERIFIED,
                CONTAINMENT_MARKER_DIR + "/applied.marker",
                "Run Gradle build once so BEAR containment marker tasks write markers, then rerun `bear check`.",
                line
            );
        }

        String expectedHash = sha256Hex(Files.readAllBytes(required));
        String markerHash = readMarkerHash(marker);
        List<String> markerBlocks = readMarkerBlocks(marker);
        List<String> expectedBlocks = requiredIndex.blockKeys();
        if (markerHash == null || !markerHash.equals(expectedHash) || markerBlocks == null || !markerBlocks.equals(expectedBlocks)) {
            String line = "check: CONTAINMENT_REQUIRED: MARKER_STALE: " + CONTAINMENT_MARKER_DIR + "/applied.marker";
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_IO,
                diagnostics,
                "CONTAINMENT",
                CliCodes.CONTAINMENT_NOT_VERIFIED,
                CONTAINMENT_MARKER_DIR + "/applied.marker",
                "Run Gradle build once after BEAR compile so containment markers refresh, then rerun `bear check`.",
                line
            );
        }

        for (String blockKey : expectedBlocks) {
            String blockMarkerRel = CONTAINMENT_MARKER_DIR + "/" + blockKey + ".applied.marker";
            Path blockMarker = projectRoot.resolve(blockMarkerRel);
            if (!Files.isRegularFile(blockMarker)) {
                String line = "check: CONTAINMENT_REQUIRED: BLOCK_MARKER_MISSING: " + blockMarkerRel;
                diagnostics.add(line);
                return checkFailure(
                    CliCodes.EXIT_IO,
                    diagnostics,
                    "CONTAINMENT",
                    CliCodes.CONTAINMENT_NOT_VERIFIED,
                    blockMarkerRel,
                    "Run Gradle build once so BEAR containment marker tasks write markers, then rerun `bear check`.",
                    line
                );
            }
            String blockMarkerHash = readMarkerHash(blockMarker);
            String markerBlockKey = readMarkerBlockKey(blockMarker);
            if (blockMarkerHash == null || !expectedHash.equals(blockMarkerHash) || markerBlockKey == null || !blockKey.equals(markerBlockKey)) {
                String line = "check: CONTAINMENT_REQUIRED: BLOCK_MARKER_STALE: " + blockMarkerRel;
                diagnostics.add(line);
                return checkFailure(
                    CliCodes.EXIT_IO,
                    diagnostics,
                    "CONTAINMENT",
                    CliCodes.CONTAINMENT_NOT_VERIFIED,
                    blockMarkerRel,
                    "Run Gradle build once after BEAR compile so containment markers refresh, then rerun `bear check`.",
                    line
                );
            }
        }

        return null;
    }

    private static boolean hasAllowedDeps(BearIr ir) {
        return ir.block().impl() != null
            && ir.block().impl().allowedDeps() != null
            && !ir.block().impl().allowedDeps().isEmpty();
    }

    static boolean sharedContainmentInScope(Path projectRoot) {
        return Files.isRegularFile(projectRoot.resolve(SHARED_POLICY_PATH)) || hasSharedJavaSources(projectRoot);
    }

    private static boolean hasSharedJavaSources(Path projectRoot) {
        Path sharedRoot = projectRoot.resolve(SHARED_SOURCE_ROOT);
        if (!Files.isDirectory(sharedRoot)) {
            return false;
        }
        try (var stream = Files.walk(sharedRoot)) {
            return stream
                .filter(Files::isRegularFile)
                .anyMatch(path -> path.getFileName().toString().endsWith(".java"));
        } catch (IOException e) {
            return false;
        }
    }

    static boolean blockDeclaresAllowedDeps(Path irFile) {
        try {
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            BearIr ir = parser.parse(irFile);
            validator.validate(ir);
            BearIr normalized = normalizer.normalize(ir);
            return hasAllowedDeps(normalized);
        } catch (Exception ignored) {
            return false;
        }
    }

    static String containmentSkipInfoLine(String projectRootLabel, Path projectRoot, boolean considerContainmentSurfaces) {
        return maybeContainmentSkipInfo(projectRootLabel, projectRoot, considerContainmentSurfaces);
    }

    private static String maybeContainmentSkipInfo(String projectRootLabel, Path projectRoot, boolean considerContainmentSurfaces) {
        if (considerContainmentSurfaces) {
            return null;
        }
        Path required = projectRoot.resolve(CONTAINMENT_REQUIRED_PATH);
        if (!Files.isRegularFile(required)) {
            return null;
        }
        try {
            ContainmentRequiredIndex index = parseContainmentRequiredIndex(required);
            if (index.blockKeys().isEmpty()) {
                return null;
            }
            return "check: INFO: CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION: projectRoot="
                + projectRootLabel
                + ": reason="
                + CONTAINMENT_SKIP_REASON;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readMarkerHash(Path markerFile) throws IOException {
        String content = CliText.normalizeLf(Files.readString(markerFile, StandardCharsets.UTF_8));
        for (String line : content.lines().toList()) {
            if (line.startsWith("hash=")) {
                String hash = line.substring("hash=".length()).trim();
                return hash.isEmpty() ? null : hash;
            }
        }
        return null;
    }

    private static List<String> readMarkerBlocks(Path markerFile) throws IOException {
        String content = CliText.normalizeLf(Files.readString(markerFile, StandardCharsets.UTF_8));
        for (String line : content.lines().toList()) {
            if (!line.startsWith("blocks=")) {
                continue;
            }
            String raw = line.substring("blocks=".length()).trim();
            if (raw.isEmpty()) {
                return List.of();
            }
            ArrayList<String> blocks = new ArrayList<>();
            Set<String> dedupe = new java.util.HashSet<>();
            for (String token : raw.split(",")) {
                String value = token.trim();
                if (value.isEmpty()) {
                    return null;
                }
                if (!dedupe.add(value)) {
                    return null;
                }
                blocks.add(value);
            }
            return List.copyOf(blocks);
        }
        return null;
    }

    private static String readMarkerBlockKey(Path markerFile) throws IOException {
        String content = CliText.normalizeLf(Files.readString(markerFile, StandardCharsets.UTF_8));
        for (String line : content.lines().toList()) {
            if (line.startsWith("block=")) {
                String value = line.substring("block=".length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    private static ContainmentRequiredIndex parseContainmentRequiredIndex(Path requiredFile) throws IOException, ManifestParseException {
        String json = Files.readString(requiredFile, StandardCharsets.UTF_8).trim();
        if (json.isEmpty() || !json.startsWith("{") || !json.endsWith("}")) {
            throw new ManifestParseException("MALFORMED_JSON");
        }
        String blocksPayload = ManifestParsers.extractRequiredArrayPayload(json, "blocks");
        TreeSet<String> blockKeys = new TreeSet<>();
        if (!blocksPayload.isBlank()) {
            Matcher matcher = Pattern
                .compile("\\{\\\"blockKey\\\":\\\"((?:\\\\.|[^\\\\\\\"])*)\\\"")
                .matcher(blocksPayload);
            while (matcher.find()) {
                blockKeys.add(ManifestParsers.jsonUnescape(matcher.group(1)));
            }
            if (blockKeys.isEmpty()) {
                throw new ManifestParseException("INVALID_CONTAINMENT_REQUIRED_BLOCKS");
            }
        }
        return new ContainmentRequiredIndex(List.copyOf(blockKeys));
    }

    private record ContainmentRequiredIndex(List<String> blockKeys) {
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
        String attempts = testResult.attemptTrail() == null || testResult.attemptTrail().isBlank()
            ? "<none>"
            : testResult.attemptTrail().trim();
        String cacheMode = testResult.cacheMode() == null || testResult.cacheMode().isBlank()
            ? "isolated"
            : testResult.cacheMode().trim();
        String fallback = testResult.fallbackToUserCache() ? "to_user_cache" : "none";
        return "; attempts=" + attempts + "; CACHE_MODE=" + cacheMode + "; FALLBACK=" + fallback;
    }

    private static String markerWriteFailureSuffix(IOException error) {
        return "; markerWrite=failed:" + CliText.squash(error.getMessage());
    }

    private static CheckResult validateWiringManifestSemantics(WiringManifest manifest, String path) {
        if (manifest.logicInterfaceFqcn() == null || manifest.logicInterfaceFqcn().trim().isEmpty()) {
            String line = "check: MANIFEST_INVALID: missing logicInterfaceFqcn";
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.MANIFEST_INVALID,
                path,
                "Regenerate wiring manifests with governed binding fields and rerun `bear check`.",
                line
            );
        }
        if (manifest.implFqcn() == null || manifest.implFqcn().trim().isEmpty()) {
            String line = "check: MANIFEST_INVALID: missing implFqcn";
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.MANIFEST_INVALID,
                path,
                "Regenerate wiring manifests with governed binding fields and rerun `bear check`.",
                line
            );
        }
        if (!"v2".equals(manifest.schemaVersion())) {
            String line = "check: MANIFEST_INVALID: unsupported wiring schema version: " + manifest.schemaVersion();
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.MANIFEST_INVALID,
                path,
                "Regenerate wiring manifests with `bear compile` so v2 wiring metadata is present, then rerun `bear check`.",
                line
            );
        }
        if (manifest.blockRootSourceDir() == null || manifest.blockRootSourceDir().trim().isEmpty()) {
            String line = "check: MANIFEST_INVALID: missing blockRootSourceDir";
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.MANIFEST_INVALID,
                path,
                "Regenerate wiring manifests with governed block root metadata and rerun `bear check`.",
                line
            );
        }
        if (manifest.governedSourceRoots() == null || manifest.governedSourceRoots().isEmpty()) {
            String line = "check: MANIFEST_INVALID: missing governedSourceRoots";
            return checkFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.MANIFEST_INVALID,
                path,
                "Regenerate wiring manifests with governed source root metadata and rerun `bear check`.",
                line
            );
        }
        for (String semanticPort : manifest.wrapperOwnedSemanticPorts()) {
            if (manifest.logicRequiredPorts().contains(semanticPort)) {
                String line = "check: MANIFEST_INVALID: wrapperOwnedSemanticPorts overlaps logicRequiredPorts: " + semanticPort;
                return checkFailure(
                    CliCodes.EXIT_VALIDATION,
                    List.of(line),
                    "VALIDATION",
                    CliCodes.MANIFEST_INVALID,
                    path,
                    "Regenerate wiring so semantic ports are wrapper-owned only, then rerun `bear check`.",
                    line
                );
            }
        }
        return null;
    }

    private static boolean isManifestSemanticFieldError(ManifestParseException e) {
        String code = e.reasonCode();
        return "MISSING_KEY_logicInterfaceFqcn".equals(code)
            || "MISSING_KEY_implFqcn".equals(code)
            || "MISSING_KEY_logicRequiredPorts".equals(code)
            || "MISSING_KEY_wrapperOwnedSemanticPorts".equals(code)
            || "MISSING_KEY_wrapperOwnedSemanticChecks".equals(code)
            || "MISSING_KEY_blockRootSourceDir".equals(code)
            || "MISSING_KEY_governedSourceRoots".equals(code)
            || "MALFORMED_ARRAY_logicRequiredPorts".equals(code)
            || "MALFORMED_ARRAY_wrapperOwnedSemanticPorts".equals(code)
            || "MALFORMED_ARRAY_wrapperOwnedSemanticChecks".equals(code)
            || "MALFORMED_ARRAY_governedSourceRoots".equals(code)
            || "INVALID_STRING_ARRAY".equals(code)
            || "UNSUPPORTED_WIRING_SCHEMA_VERSION".equals(code)
            || "INVALID_GOVERNED_SOURCE_ROOTS".equals(code)
            || PortImplContainmentScanner.AMBIGUOUS_PORT_OWNER_REASON_CODE.equals(code)
            || "INVALID_ROOT_PATH_blockRootSourceDir".equals(code)
            || "INVALID_ROOT_PATH_governedSourceRoots".equals(code);
    }

    private static String boundaryBypassRemediation(String rule) {
        if ("PORT_IMPL_OUTSIDE_GOVERNED_ROOT".equals(rule)) {
            return "Move the port implementation under the owning block governed roots (block root or blocks/_shared) or refactor so app layer calls wrappers without implementing generated ports.";
        }
        if ("MULTI_BLOCK_PORT_IMPL_FORBIDDEN".equals(rule)) {
            return "Split generated-port adapters so each class implements one generated block package, or move the adapter under blocks/_shared and add `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL` within 5 non-empty lines above the class declaration.";
        }
        if ("SHARED_PURITY_VIOLATION".equals(rule)) {
            return "Keep `_shared.pure` deterministic: remove mutable static state/synchronized usage, move stateful code to `blocks/**/adapter/**` or `blocks/_shared/state/**`, and use allowlisted immutable constants only.";
        }
        if ("IMPL_PURITY_VIOLATION".equals(rule)) {
            return "Keep impl lane pure: remove mutable static state and synchronized usage from `blocks/**/impl/**`; route cross-call state through generated ports and adapter/state lanes.";
        }
        if ("IMPL_STATE_DEPENDENCY_BYPASS".equals(rule)) {
            return "Remove `blocks._shared.state.*` dependencies from impl lane and access state through generated port adapters.";
        }
        if ("SCOPED_IMPORT_POLICY_BYPASS".equals(rule)) {
            return "Remove forbidden package usage from guarded lane (`impl` or `_shared.pure`) and move IO/network/filesystem/concurrency integration into adapter/state lanes.";
        }
        if ("SHARED_LAYOUT_POLICY_VIOLATION".equals(rule)) {
            return "Move shared Java files under `src/main/java/blocks/_shared/pure/**` or `src/main/java/blocks/_shared/state/**`; root-level `_shared` Java files are not allowed.";
        }
        return "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses.";
    }

    private static void writeCheckBlockedMarker(Path projectRoot, String reason, String detail) throws IOException {
        Path marker = projectRoot.resolve(CHECK_BLOCKED_MARKER_RELATIVE);
        Files.createDirectories(marker.getParent());
        String safeReason = (reason == null || reason.isBlank()) ? "UNKNOWN" : reason;
        String safeDetail = (detail == null || detail.isBlank()) ? "no details" : detail.trim();
        String content = "reason=" + safeReason + "\n" + "detail=" + safeDetail + "\n";
        Files.writeString(marker, content, StandardCharsets.UTF_8);
    }

    private static void clearCheckBlockedMarker(Path projectRoot) throws IOException {
        Path marker = projectRoot.resolve(CHECK_BLOCKED_MARKER_RELATIVE);
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
        return new CheckResult(
            exitCode,
            List.of(),
            List.copyOf(stderrLines),
            category,
            failureCode,
            failurePath,
            failureRemediation,
            detail
        );
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
