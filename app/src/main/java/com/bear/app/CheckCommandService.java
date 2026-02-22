package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrNormalizer;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.ir.BearIrValidator;
import com.bear.kernel.target.JvmTarget;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class CheckCommandService {
    private static final String CHECK_BLOCKED_MARKER_RELATIVE = "build/bear/check.blocked.marker";
    private static final String CHECK_BLOCKED_REASON_LOCK = "LOCK";
    private static final String CHECK_BLOCKED_REASON_BOOTSTRAP = "BOOTSTRAP_IO";
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
                String line = "drift: MISSING_BASELINE: build/generated/bear (run: bear compile "
                    + irFile + " --project " + projectRoot + ")";
                return checkFailure(
                    CliCodes.EXIT_DRIFT,
                    List.of(line),
                    "DRIFT",
                    CliCodes.DRIFT_MISSING_BASELINE,
                    "build/generated/bear",
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    "drift: MISSING_BASELINE: build/generated/bear"
                );
            }

            tempRoot = Files.createTempDirectory("bear-check-");
            target.compile(normalized, tempRoot, blockKey);
            Path candidateRoot = tempRoot.resolve("build").resolve("generated").resolve("bear");
            Path baselineManifestPath = baselineRoot.resolve(markerRelPath);
            Path candidateManifestPath = candidateRoot.resolve(markerRelPath);
            Path baselineWiringPath = baselineRoot.resolve(wiringRelPath);
            Path candidateWiringPath = candidateRoot.resolve(wiringRelPath);

            applyCandidateManifestTestMode(candidateManifestPath);

            List<String> diagnostics = new ArrayList<>();
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
                String line = "drift: MISSING_BASELINE: build/generated/bear/" + wiringRelPath
                    + " (run: bear compile "
                    + irFile + " --project " + projectRoot + ")";
                return checkFailure(
                    CliCodes.EXIT_DRIFT,
                    List.of(line),
                    "DRIFT",
                    CliCodes.DRIFT_MISSING_BASELINE,
                    "build/generated/bear/" + wiringRelPath,
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    "drift: MISSING_BASELINE: build/generated/bear/" + wiringRelPath
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
                if (isMissingGovernedBindingField(e)) {
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
                if (isMissingGovernedBindingField(e)) {
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
                for (DriftItem item : drift) {
                    diagnostics.add("drift: " + item.type().label + ": " + item.path());
                }
                return checkFailure(
                    CliCodes.EXIT_DRIFT,
                    diagnostics,
                    "DRIFT",
                    CliCodes.DRIFT_DETECTED,
                    "build/generated/bear",
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    diagnostics.get(diagnostics.size() - 1)
                );
            }

            CheckResult containmentFailure = verifyContainmentIfRequired(normalized, projectRoot, diagnostics);
            if (containmentFailure != null) {
                return containmentFailure;
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
                    "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses.",
                    diagnostics.get(diagnostics.size() - 1)
                );
            }

            ProjectTestResult testResult = ProjectTestRunner.runProjectTests(projectRoot);
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

            clearCheckBlockedMarker(projectRoot);
            return new CheckResult(CliCodes.EXIT_OK, List.of("check: OK"), List.of(), null, null, null, null, null);
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

    private static CheckResult verifyContainmentIfRequired(BearIr ir, Path projectRoot, List<String> diagnostics) throws IOException {
        if (!hasAllowedDeps(ir)) {
            return null;
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
                "Allowed dependency containment in P2 requires Java+Gradle with wrapper at project root; remove `impl.allowedDeps` or use supported target, then rerun `bear check`.",
                line
            );
        }

        Path entrypoint = projectRoot.resolve("build/generated/bear/gradle/bear-containment.gradle");
        if (!Files.isRegularFile(entrypoint)) {
            String line = "check: CONTAINMENT_REQUIRED: SCRIPT_MISSING: build/generated/bear/gradle/bear-containment.gradle";
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_IO,
                diagnostics,
                "CONTAINMENT",
                CliCodes.CONTAINMENT_NOT_VERIFIED,
                "build/generated/bear/gradle/bear-containment.gradle",
                "Run `bear compile <ir-file> --project <path>`, ensure Gradle applies the generated containment script, then rerun `bear check`.",
                line
            );
        }

        Path required = projectRoot.resolve("build/generated/bear/config/containment-required.json");
        if (!Files.isRegularFile(required)) {
            String line = "check: CONTAINMENT_REQUIRED: INDEX_MISSING: build/generated/bear/config/containment-required.json";
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_IO,
                diagnostics,
                "CONTAINMENT",
                CliCodes.CONTAINMENT_NOT_VERIFIED,
                "build/generated/bear/config/containment-required.json",
                "Run `bear compile <ir-file> --project <path>`, then rerun `bear check`.",
                line
            );
        }

        Path marker = projectRoot.resolve("build/bear/containment/applied.marker");
        if (!Files.isRegularFile(marker)) {
            String line = "check: CONTAINMENT_REQUIRED: MARKER_MISSING: build/bear/containment/applied.marker";
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_IO,
                diagnostics,
                "CONTAINMENT",
                CliCodes.CONTAINMENT_NOT_VERIFIED,
                "build/bear/containment/applied.marker",
                "Run Gradle build once so BEAR containment compile tasks write markers, then rerun `bear check`.",
                line
            );
        }

        String expectedHash = sha256Hex(Files.readAllBytes(required));
        String markerHash = readMarkerHash(marker);
        if (markerHash == null || !markerHash.equals(expectedHash)) {
            String line = "check: CONTAINMENT_REQUIRED: MARKER_STALE: build/bear/containment/applied.marker";
            diagnostics.add(line);
            return checkFailure(
                CliCodes.EXIT_IO,
                diagnostics,
                "CONTAINMENT",
                CliCodes.CONTAINMENT_NOT_VERIFIED,
                "build/bear/containment/applied.marker",
                "Run Gradle build once after BEAR compile so containment markers refresh, then rerun `bear check`.",
                line
            );
        }

        return null;
    }

    private static boolean hasAllowedDeps(BearIr ir) {
        return ir.block().impl() != null
            && ir.block().impl().allowedDeps() != null
            && !ir.block().impl().allowedDeps().isEmpty();
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

    private static boolean isMissingGovernedBindingField(ManifestParseException e) {
        String code = e.reasonCode();
        return "MISSING_KEY_logicInterfaceFqcn".equals(code)
            || "MISSING_KEY_implFqcn".equals(code);
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
