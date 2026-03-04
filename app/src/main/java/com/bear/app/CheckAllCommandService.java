package com.bear.app;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class CheckAllCommandService {
    private static final String CHECK_BLOCKED_REASON_LOCK = "LOCK";
    private static final String CHECK_BLOCKED_REASON_BOOTSTRAP = "BOOTSTRAP_IO";
    private static final String CONTAINMENT_ENTRYPOINT_PATH = "build/generated/bear/gradle/bear-containment.gradle";
    private static final String HEARTBEAT_SECONDS_PROPERTY = "bear.check.all.heartbeatSeconds";
    private static final long HEARTBEAT_POLL_MILLIS = 200L;

    private CheckAllCommandService() {
    }

    static int runCheckAll(String[] args, PrintStream out, PrintStream err) {
        AllCheckOptions options = AllModeOptionParser.parseAllCheckOptions(args, err);
        if (options == null) {
            return CliCodes.EXIT_USAGE;
        }
        Integer missingIndexExit = AllModeIndexPreflight.failIfMissing(options.blocksPath(), err);
        if (missingIndexExit != null) {
            return missingIndexExit;
        }

        BlockIndex index;
        try {
            index = new BlockIndexParser().parse(options.repoRoot(), options.blocksPath(), true);
        } catch (BlockIndexValidationException e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_VALIDATION,
                "index: VALIDATION_ERROR: " + e.getMessage(),
                CliCodes.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear check --all`."
            );
        } catch (IOException e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_IO,
                "io: IO_ERROR: " + CliText.squash(e.getMessage()),
                CliCodes.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure `bear.blocks.yaml` is readable and rerun `bear check --all`."
            );
        }

        List<BlockIndexEntry> selected = AllModeBlockDiscovery.selectBlocks(index, options.onlyNames());
        if (selected == null) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: unknown block in --only",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Use only block names declared in `bear.blocks.yaml`."
            );
        }

        TreeSet<String> participatingBlockKeys = new TreeSet<>();
        for (BlockIndexEntry block : selected) {
            if (block.enabled()) {
                participatingBlockKeys.add(block.name());
            }
        }

        BlockPortGraph blockPortGraph;
        try {
            blockPortGraph = BlockPortGraphResolver.resolveAndValidate(
                options.repoRoot(),
                options.blocksPath(),
                participatingBlockKeys
            );
        } catch (BlockIndexValidationException e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_VALIDATION,
                "index: VALIDATION_ERROR: " + e.getMessage(),
                CliCodes.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear check --all`."
            );
        } catch (BlockIdentityResolutionException | com.bear.kernel.ir.BearIrValidationException e) {
            String line = "index: VALIDATION_ERROR: " + CliText.squash(e.getMessage());
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_VALIDATION,
                line,
                CliCodes.IR_VALIDATION,
                "bear.blocks.yaml",
                "Fix the referenced IR/index error and rerun `bear check --all`."
            );
        } catch (IOException e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_IO,
                "io: IO_ERROR: " + CliText.squash(e.getMessage()),
                CliCodes.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure indexed IR paths are readable and rerun `bear check --all`."
            );
        }

        if (!options.agent()) {
            out.println("check-all: START project=.");
        }

        try {
            List<String> legacyMarkers = options.strictOrphans()
                ? AllModeBlockDiscovery.computeLegacyMarkersRepoWide(options.repoRoot())
                : AllModeBlockDiscovery.computeLegacyMarkersInManagedRoots(options.repoRoot(), selected);
            if (!legacyMarkers.isEmpty()) {
                return BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_IO,
                    "check: IO_ERROR: LEGACY_SURFACE_MARKER: " + legacyMarkers.get(0),
                    CliCodes.IO_ERROR,
                    legacyMarkers.get(0),
                    "Delete legacy marker paths and recompile managed blocks, then rerun `bear check --all`."
                );
            }

            List<String> orphanMarkers = options.strictOrphans()
                ? AllModeBlockDiscovery.computeOrphanMarkersRepoWide(options.repoRoot(), index)
                : AllModeBlockDiscovery.computeOrphanMarkersInManagedRoots(options.repoRoot(), selected);
            if (!orphanMarkers.isEmpty()) {
                return BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_IO,
                    "check: IO_ERROR: ORPHAN_MARKER: " + orphanMarkers.get(0),
                    CliCodes.IO_ERROR,
                    orphanMarkers.get(0),
                    "Add missing block entries to `bear.blocks.yaml` or remove stale generated BEAR artifacts."
                );
            }
            if (options.strictHygiene()) {
                Set<String> hygieneAllowlist = PolicyAllowlistParser.parseExactPathAllowlist(
                    options.repoRoot(),
                    PolicyAllowlistParser.HYGIENE_ALLOWLIST_PATH
                );
                List<String> unexpectedPaths = HygieneScanner.scanUnexpectedPaths(options.repoRoot(), hygieneAllowlist);
                if (!unexpectedPaths.isEmpty()) {
                    return BearCli.failWithLegacy(
                        err,
                        CliCodes.EXIT_UNDECLARED_REACH,
                        "check: HYGIENE_UNEXPECTED_PATHS: " + unexpectedPaths.get(0),
                        CliCodes.HYGIENE_UNEXPECTED_PATHS,
                        unexpectedPaths.get(0),
                        "Remove unexpected tool directories or allowlist them in `.bear/policy/hygiene-allowlist.txt`, then rerun `bear check --all`."
                    );
                }
            }
        } catch (IOException e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_IO,
                "check: IO_ERROR: ORPHAN_SCAN_FAILED: " + CliText.squash(e.getMessage()),
                CliCodes.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure repo paths are readable and rerun `bear check --all`."
            );
        } catch (PolicyValidationException e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_VALIDATION,
                "policy: VALIDATION_ERROR: " + CliText.squash(e.getMessage()),
                CliCodes.POLICY_INVALID,
                e.policyPath(),
                "Fix the policy contract file and rerun `bear check --all`."
            );
        }

        List<BlockExecutionResult> blockResults = new ArrayList<>();
        Map<String, Boolean> rootContainmentRequiredBySelection = new TreeMap<>();
        for (BlockIndexEntry block : selected) {
            if (!block.enabled()) {
                continue;
            }
            rootContainmentRequiredBySelection.putIfAbsent(block.projectRoot(), false);
            if (rootContainmentRequiredBySelection.get(block.projectRoot())) {
                continue;
            }
            Path irPath = options.repoRoot().resolve(block.ir()).normalize();
            if (CheckCommandService.blockDeclaresAllowedDeps(irPath)) {
                rootContainmentRequiredBySelection.put(block.projectRoot(), true);
            }
        }
        for (Map.Entry<String, Boolean> entry : new ArrayList<>(rootContainmentRequiredBySelection.entrySet())) {
            if (entry.getValue()) {
                continue;
            }
            Path rootPath = options.repoRoot().resolve(entry.getKey()).normalize();
            if (CheckCommandService.sharedContainmentInScope(rootPath)) {
                rootContainmentRequiredBySelection.put(entry.getKey(), true);
            }
        }
        boolean failed = false;
        boolean failFastTriggered = false;
        for (BlockIndexEntry block : selected) {
            if (!options.agent()) {
                emitBlockStart(out, block);
            }
            if (!block.enabled()) {
                blockResults.add(BearCli.skipBlock(block, "DISABLED"));
                continue;
            }
            if (options.failFast() && failed) {
                failFastTriggered = true;
                blockResults.add(BearCli.skipBlock(block, "FAIL_FAST_ABORT"));
                continue;
            }

            CheckResult checkResult = CheckCommandService.executeCheck(
                options.repoRoot().resolve(block.ir()).normalize(),
                options.repoRoot().resolve(block.projectRoot()).normalize(),
                false,
                false,
                block.name(),
                BearCli.indexLocator(block),
                rootContainmentRequiredBySelection.getOrDefault(block.projectRoot(), false),
                false,
                null,
                options.collectAll()
            );
            BlockExecutionResult blockResult = BearCli.toCheckBlockResult(block, checkResult);
            blockResults.add(blockResult);
            if (blockResult.status() == BlockStatus.FAIL) {
                failed = true;
            }
        }

        Map<String, Integer> firstPassIndexByRoot = new HashMap<>();
        for (int i = 0; i < blockResults.size(); i++) {
            BlockExecutionResult blockResult = blockResults.get(i);
            if (blockResult.status() != BlockStatus.PASS) {
                continue;
            }
            firstPassIndexByRoot.putIfAbsent(blockResult.project(), i);
        }
        for (Map.Entry<String, Boolean> entry : rootContainmentRequiredBySelection.entrySet()) {
            if (entry.getValue()) {
                continue;
            }
            Integer idx = firstPassIndexByRoot.get(entry.getKey());
            if (idx == null) {
                continue;
            }
            Path root = options.repoRoot().resolve(entry.getKey()).normalize();
            String infoLine = CheckCommandService.containmentSkipInfoLine(entry.getKey(), root, false);
            if (infoLine == null || infoLine.isBlank()) {
                continue;
            }
            BlockExecutionResult base = blockResults.get(idx);
            String mergedDetail = base.detail() == null || base.detail().isBlank()
                ? infoLine
                : base.detail() + " | " + infoLine;
            blockResults.set(idx, withDetail(base, mergedDetail));
        }

        Map<String, List<Integer>> rootPassIndexes = new TreeMap<>();
        for (int i = 0; i < blockResults.size(); i++) {
            BlockExecutionResult blockResult = blockResults.get(i);
            if (blockResult.status() != BlockStatus.PASS) {
                continue;
            }
            rootPassIndexes.computeIfAbsent(blockResult.project(), ignored -> new ArrayList<>()).add(i);
        }

        int rootReachFailed = 0;
        int rootTestFailed = 0;
        int rootTestSkippedDueToReach = 0;
        for (Map.Entry<String, List<Integer>> entry : rootPassIndexes.entrySet()) {
            Path root = options.repoRoot().resolve(entry.getKey()).normalize();
            try {
                Set<String> reflectionAllowlist = PolicyAllowlistParser.parseExactPathAllowlist(
                    root,
                    PolicyAllowlistParser.REFLECTION_ALLOWLIST_PATH
                );
                if (options.strictHygiene()) {
                    Set<String> hygieneAllowlist = PolicyAllowlistParser.parseExactPathAllowlist(
                        root,
                        PolicyAllowlistParser.HYGIENE_ALLOWLIST_PATH
                    );
                    List<String> unexpectedPaths = HygieneScanner.scanUnexpectedPaths(root, hygieneAllowlist);
                    if (!unexpectedPaths.isEmpty()) {
                        for (int idx : entry.getValue()) {
                            blockResults.set(idx, BearCli.rootFailure(
                                blockResults.get(idx),
                                CliCodes.EXIT_UNDECLARED_REACH,
                                "HYGIENE",
                                CliCodes.HYGIENE_UNEXPECTED_PATHS,
                                unexpectedPaths.get(0),
                                "check: HYGIENE_UNEXPECTED_PATHS: " + unexpectedPaths.get(0),
                                "Remove unexpected tool directories or allowlist them in `.bear/policy/hygiene-allowlist.txt`, then rerun `bear check --all`."
                            ));
                        }
                        continue;
                    }
                }

                List<UndeclaredReachFinding> undeclaredReach = UndeclaredReachScanner.scanUndeclaredReach(root);
                if (!undeclaredReach.isEmpty()) {
                    rootReachFailed++;
                    rootTestSkippedDueToReach++;
                    String locator = undeclaredReach.get(0).path();
                    String detail = "root-level undeclared reach in projectRoot " + entry.getKey();
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, BearCli.rootFailure(
                            blockResults.get(idx),
                            CliCodes.EXIT_UNDECLARED_REACH,
                            "UNDECLARED_REACH",
                            CliCodes.UNDECLARED_REACH,
                            locator,
                            detail,
                            "Declare a port/op in IR, run bear compile, and route call through generated port interface."
                        ));
                    }
                    continue;
                }

                List<WiringManifest> wiringManifests = new ArrayList<>();
                TreeSet<String> rootBlockKeys = new TreeSet<>();
                for (int idx : entry.getValue()) {
                    String blockKey = blockResults.get(idx).name();
                    rootBlockKeys.add(blockKey);
                    Path wiringPath = root.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
                    wiringManifests.add(ManifestParsers.parseWiringManifest(wiringPath));
                }

                List<UndeclaredReachFinding> reflectionDispatchFindings =
                    GovernedReflectionDispatchScanner.scanForbiddenReflectionDispatch(root, wiringManifests);
                if (!reflectionDispatchFindings.isEmpty()) {
                    rootReachFailed++;
                    rootTestSkippedDueToReach++;
                    String locator = reflectionDispatchFindings.get(0).path();
                    String detail = "check: UNDECLARED_REACH: "
                        + reflectionDispatchFindings.get(0).path()
                        + ": "
                        + reflectionDispatchFindings.get(0).surface();
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, BearCli.rootFailure(
                            blockResults.get(idx),
                            CliCodes.EXIT_UNDECLARED_REACH,
                            "UNDECLARED_REACH",
                            CliCodes.REFLECTION_DISPATCH_FORBIDDEN,
                            locator,
                            detail,
                            "Remove reflection/method-handle dynamic dispatch from governed roots and route through declared generated boundaries."
                        ));
                    }
                    continue;
                }

                TreeSet<String> inboundTargetWrapperFqcns = BlockPortGraphResolver.inboundTargetWrapperFqcns(
                    blockPortGraph,
                    rootBlockKeys
                );

                List<BoundaryBypassFinding> bypassFindings = new ArrayList<>();
                bypassFindings.addAll(BoundaryBypassScanner.scanBoundaryBypass(
                    root,
                    wiringManifests,
                    reflectionAllowlist
                ));
                bypassFindings.addAll(BlockPortBindingEnforcer.scan(
                    root,
                    wiringManifests,
                    inboundTargetWrapperFqcns
                ));
                bypassFindings.sort(
                    java.util.Comparator.comparing(BoundaryBypassFinding::path)
                        .thenComparing(BoundaryBypassFinding::rule)
                        .thenComparing(BoundaryBypassFinding::detail)
                );
                if (!bypassFindings.isEmpty()) {
                    BoundaryBypassFinding first = bypassFindings.get(0);
                    String firstLine = "check: BOUNDARY_BYPASS: RULE=" + first.rule() + ": " + first.path() + ": " + first.detail();
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, BearCli.rootFailure(
                            blockResults.get(idx),
                            CliCodes.EXIT_BOUNDARY_BYPASS,
                            "BOUNDARY_BYPASS",
                            CliCodes.BOUNDARY_BYPASS,
                            first.path(),
                            firstLine,
                            boundaryBypassRemediation(first.rule())
                        ));
                    }
                    continue;
                }

                boolean considerContainmentSurfaces = rootContainmentRequiredBySelection.getOrDefault(entry.getKey(), false);
                ArrayList<String> containmentPreflightDiagnostics = new ArrayList<>();
                CheckResult containmentPreflightFailure = CheckCommandService.preflightContainmentIfRequired(
                    root,
                    containmentPreflightDiagnostics,
                    considerContainmentSurfaces
                );
                if (containmentPreflightFailure != null) {
                    String detail = containmentPreflightFailure.detail() != null && !containmentPreflightFailure.detail().isBlank()
                        ? containmentPreflightFailure.detail()
                        : containmentPreflightDiagnostics.isEmpty() ? "check: CONTAINMENT_REQUIRED: preflight failed" : containmentPreflightDiagnostics.get(0);
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, BearCli.rootFailure(
                            blockResults.get(idx),
                            containmentPreflightFailure.exitCode(),
                            containmentPreflightFailure.category(),
                            containmentPreflightFailure.failureCode(),
                            containmentPreflightFailure.failurePath(),
                            detail,
                            containmentPreflightFailure.failureRemediation()
                        ));
                    }
                    continue;
                }
                String rootProject = normalizePathToken(entry.getKey());
                out.println("check-all: ROOT_TEST_START project=" + rootProject);
                RootTestHeartbeat heartbeat = RootTestHeartbeat.start(
                    out,
                    rootProject,
                    heartbeatIntervalSeconds()
                );
                ProjectTestResult testResult = null;
                int rootTestExit = CliCodes.EXIT_INTERNAL;
                try {
                    testResult = ProjectTestRunner.runProjectTests(
                        root,
                        considerContainmentSurfaces ? CONTAINMENT_ENTRYPOINT_PATH : null
                    );
                    rootTestExit = rootTestExitCode(testResult);
                } finally {
                    heartbeat.stop();
                    out.println("check-all: ROOT_TEST_DONE project=" + rootProject + " exit=" + rootTestExit);
                }
                if (testResult.status() == ProjectTestStatus.LOCKED) {
                    String lockLine = testResult.firstLockLine() != null
                        ? testResult.firstLockLine()
                        : ProjectTestRunner.firstGradleLockLine(testResult.output());
                    String markerWriteSuffix = "";
                    try {
                        BearCli.writeCheckBlockedMarker(root, CHECK_BLOCKED_REASON_LOCK, lockLine);
                    } catch (IOException markerWriteError) {
                        markerWriteSuffix = markerWriteFailureSuffix(markerWriteError);
                    }
                    String detail = ProjectTestRunner.projectTestDetail(
                        "root-level project test runner lock in projectRoot " + entry.getKey(),
                        lockLine,
                        null
                    );
                    detail += testDiagnosticsSuffix(testResult);
                    if (!markerWriteSuffix.isBlank()) {
                        detail += markerWriteSuffix;
                    }
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, BearCli.rootFailure(
                            blockResults.get(idx),
                            CliCodes.EXIT_IO,
                            "IO_ERROR",
                            CliCodes.IO_ERROR,
                            "project.tests",
                            detail,
                            "Use BEAR-selected GRADLE_USER_HOME mode, run `bear unblock --project <path>`, then rerun `bear check --all --project <repoRoot>`."
                        ));
                    }
                } else if (testResult.status() == ProjectTestStatus.BOOTSTRAP_IO) {
                    String bootstrapLine = testResult.firstBootstrapLine() != null
                        ? testResult.firstBootstrapLine()
                        : ProjectTestRunner.firstGradleBootstrapIoLine(testResult.output());
                    String markerWriteSuffix = "";
                    try {
                        BearCli.writeCheckBlockedMarker(root, CHECK_BLOCKED_REASON_BOOTSTRAP, bootstrapLine);
                    } catch (IOException markerWriteError) {
                        markerWriteSuffix = markerWriteFailureSuffix(markerWriteError);
                    }
                    String detail = ProjectTestRunner.projectTestDetail(
                        "root-level project test bootstrap IO failure in projectRoot " + entry.getKey(),
                        bootstrapLine,
                        CliText.shortTailSummary(testResult.output(), 3)
                    );
                    detail += testDiagnosticsSuffix(testResult);
                    if (!markerWriteSuffix.isBlank()) {
                        detail += markerWriteSuffix;
                    }
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, BearCli.rootFailure(
                            blockResults.get(idx),
                            CliCodes.EXIT_IO,
                            "IO_ERROR",
                            CliCodes.IO_ERROR,
                            "project.tests",
                            detail,
                            "Use BEAR-selected GRADLE_USER_HOME mode, run `bear unblock --project <path>`, then rerun `bear check --all --project <repoRoot>`."
                        ));
                    }
                } else if (testResult.status() == ProjectTestStatus.SHARED_DEPS_VIOLATION) {
                    String sharedLine = testResult.firstSharedDepsViolationLine() != null
                        ? testResult.firstSharedDepsViolationLine()
                        : ProjectTestRunner.firstSharedDepsViolationLine(testResult.output());
                    String detail = "check: CONTAINMENT_REQUIRED: SHARED_DEPS_VIOLATION: "
                        + entry.getKey()
                        + ":_shared";
                    if (sharedLine != null && !sharedLine.isBlank()) {
                        detail += ": " + sharedLine;
                    }
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, BearCli.rootFailure(
                            blockResults.get(idx),
                            CliCodes.EXIT_IO,
                            "CONTAINMENT",
                            CliCodes.CONTAINMENT_NOT_VERIFIED,
                            "spec/_shared.policy.yaml",
                            detail,
                            "Add dependency to `spec/_shared.policy.yaml` with exact pinned version, or remove external dependency usage from `src/main/java/blocks/_shared/**`, then rerun `bear check --all`."
                        ));
                    }
                } else if (testResult.status() == ProjectTestStatus.INVARIANT_VIOLATION) {
                    rootTestFailed++;
                    String markerLine = ProjectTestRunner.firstInvariantViolationLine(testResult.output());
                    String detail = ProjectTestRunner.projectTestDetail(
                        "root-level invariant violation in projectRoot " + entry.getKey(),
                        markerLine,
                        CliText.shortTailSummary(testResult.output(), 3)
                    );
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, BearCli.rootFailure(
                            blockResults.get(idx),
                            CliCodes.EXIT_TEST_FAILURE,
                            "TEST_FAILURE",
                            CliCodes.INVARIANT_VIOLATION,
                            "project.tests",
                            detail,
                            "Fix invariant violation and rerun `bear check --all`."
                        ));
                    }
                } else if (testResult.status() == ProjectTestStatus.COMPILE_FAILURE) {
                    rootTestFailed++;
                    String markerLine = ProjectTestRunner.firstCompileFailureLine(testResult.output());
                    String detail = "root-level compile preflight failed for projectRoot " + entry.getKey();
                    if (markerLine != null && !markerLine.isBlank()) {
                        detail += "; line=" + markerLine;
                    }
                    detail += phaseTaskSuffix(testResult);
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, BearCli.rootFailure(
                            blockResults.get(idx),
                            CliCodes.EXIT_TEST_FAILURE,
                            "TEST_FAILURE",
                            CliCodes.COMPILE_FAILURE,
                            "project.tests",
                            detail,
                            "Fix compile errors and rerun `bear check --all`."
                        ));
                    }
                } else if (testResult.status() == ProjectTestStatus.FAILED) {
                    rootTestFailed++;
                    String detail = ProjectTestRunner.projectTestDetail(
                        "root-level project tests failed for projectRoot " + entry.getKey(),
                        ProjectTestRunner.firstRelevantProjectTestFailureLine(testResult.output()),
                        CliText.shortTailSummary(testResult.output(), 3)
                    );
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, BearCli.rootFailure(
                            blockResults.get(idx),
                            CliCodes.EXIT_TEST_FAILURE,
                            "TEST_FAILURE",
                            CliCodes.TEST_FAILURE,
                            "project.tests",
                            detail,
                            "Fix project tests and rerun `bear check --all`."
                        ));
                    }
                } else if (testResult.status() == ProjectTestStatus.TIMEOUT) {
                    rootTestFailed++;
                    String detail = ProjectTestRunner.projectTestDetail(
                        "root-level project tests timed out for projectRoot " + entry.getKey(),
                        ProjectTestRunner.firstRelevantProjectTestFailureLine(testResult.output()),
                        CliText.shortTailSummary(testResult.output(), 3)
                    ) + phaseTaskSuffix(testResult);
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, BearCli.rootFailure(
                            blockResults.get(idx),
                            CliCodes.EXIT_TEST_FAILURE,
                            "TEST_FAILURE",
                            CliCodes.TEST_TIMEOUT,
                            "project.tests",
                            detail,
                            "Reduce test runtime or increase timeout, then rerun `bear check --all`."
                        ));
                    }
                } else if (testResult.status() == ProjectTestStatus.PASSED) {
                    ArrayList<String> containmentDiagnostics = new ArrayList<>();
                    CheckResult containmentMarkerFailure = CheckCommandService.verifyContainmentMarkersIfRequired(
                        root,
                        containmentDiagnostics,
                        considerContainmentSurfaces
                    );
                    if (containmentMarkerFailure != null) {
                        String detail = containmentMarkerFailure.detail() != null && !containmentMarkerFailure.detail().isBlank()
                            ? containmentMarkerFailure.detail()
                            : containmentDiagnostics.isEmpty() ? "check: CONTAINMENT_REQUIRED: verification failed" : containmentDiagnostics.get(0);
                        for (int idx : entry.getValue()) {
                            blockResults.set(idx, BearCli.rootFailure(
                                blockResults.get(idx),
                                containmentMarkerFailure.exitCode(),
                                containmentMarkerFailure.category(),
                                containmentMarkerFailure.failureCode(),
                                containmentMarkerFailure.failurePath(),
                                detail,
                                containmentMarkerFailure.failureRemediation()
                            ));
                        }
                        continue;
                    }
                    BearCli.clearCheckBlockedMarker(root);
                }
            } catch (IOException e) {
                for (int idx : entry.getValue()) {
                    blockResults.set(idx, BearCli.rootFailure(
                        blockResults.get(idx),
                        CliCodes.EXIT_IO,
                        "IO_ERROR",
                        CliCodes.IO_ERROR,
                        "project.root",
                        "io: IO_ERROR: " + CliText.squash(e.getMessage()),
                        "Ensure project paths are accessible (including Gradle wrapper), then rerun `bear check --all`."
                    ));
                }
            } catch (PolicyValidationException e) {
                for (int idx : entry.getValue()) {
                    blockResults.set(idx, BearCli.rootFailure(
                        blockResults.get(idx),
                        CliCodes.EXIT_VALIDATION,
                        "VALIDATION",
                        CliCodes.POLICY_INVALID,
                        e.policyPath(),
                        "policy: VALIDATION_ERROR: " + CliText.squash(e.getMessage()),
                        "Fix the policy contract file and rerun `bear check --all`."
                    ));
                }
            } catch (ManifestParseException e) {
                for (int idx : entry.getValue()) {
                    if (isManifestSemanticFieldError(e)) {
                        blockResults.set(idx, BearCli.rootFailure(
                            blockResults.get(idx),
                            CliCodes.EXIT_VALIDATION,
                            "VALIDATION",
                            CliCodes.MANIFEST_INVALID,
                            "build/generated/bear/wiring/" + blockResults.get(idx).name() + ".wiring.json",
                            "check: MANIFEST_INVALID: " + e.reasonCode(),
                            "Regenerate wiring manifests with governed binding fields and rerun `bear check --all`."
                        ));
                    } else {
                        blockResults.set(idx, BearCli.rootFailure(
                            blockResults.get(idx),
                            CliCodes.EXIT_DRIFT,
                            "DRIFT",
                            CliCodes.DRIFT_MISSING_BASELINE,
                            "build/generated/bear/wiring/" + blockResults.get(idx).name() + ".wiring.json",
                            "drift: BASELINE_WIRING_MANIFEST_INVALID: " + e.reasonCode(),
                            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check --all`."
                        ));
                    }
                }
            } catch (InterruptedException e) {
                for (int idx : entry.getValue()) {
                    blockResults.set(idx, BearCli.rootFailure(
                        blockResults.get(idx),
                        CliCodes.EXIT_INTERNAL,
                        "INTERNAL_ERROR",
                        CliCodes.INTERNAL_ERROR,
                        "internal",
                        "internal: INTERNAL_ERROR:",
                        "Capture stderr and file an issue against bear-cli."
                    ));
                }
            }
        }

        RepoAggregationResult summary = AllModeAggregation.aggregateCheckResults(
            blockResults,
            failFastTriggered,
            rootReachFailed,
            rootTestFailed,
            rootTestSkippedDueToReach
        );
        if (options.agent()) {
            ArrayList<AgentDiagnostics.AgentProblem> problems = new ArrayList<>();
            for (BlockExecutionResult blockResult : blockResults) {
                if (blockResult.problems() != null) {
                    problems.addAll(blockResult.problems());
                }
                if (blockResult.status() == BlockStatus.FAIL && (blockResult.problems() == null || blockResult.problems().isEmpty())) {
                    problems.add(AgentDiagnostics.problem(
                        blockResult.blockCode() != null && (
                            CliCodes.BOUNDARY_BYPASS.equals(blockResult.blockCode())
                                || CliCodes.UNDECLARED_REACH.equals(blockResult.blockCode())
                                || CliCodes.REFLECTION_DISPATCH_FORBIDDEN.equals(blockResult.blockCode())
                        ) ? AgentDiagnostics.AgentCategory.GOVERNANCE : AgentDiagnostics.AgentCategory.INFRA,
                        blockResult.blockCode() == null ? CliCodes.REPO_MULTI_BLOCK_FAILED : blockResult.blockCode(),
                        null,
                        blockResult.blockCode() == null ? CliCodes.REPO_MULTI_BLOCK_FAILED : blockResult.blockCode(),
                        AgentDiagnostics.AgentSeverity.ERROR,
                        blockResult.name(),
                        blockResult.blockPath(),
                        null,
                        blockResult.blockCode() == null ? CliCodes.REPO_MULTI_BLOCK_FAILED : blockResult.blockCode(),
                        blockResult.detail() == null ? "" : blockResult.detail(),
                        Map.of()
                    ));
                }
            }
            AgentDiagnostics.AgentPayload payload = AgentDiagnostics.payload(
                AgentCommandContext.forCheckAll(options),
                summary.exitCode(),
                problems,
                true
            );
            out.println(AgentDiagnostics.toJson(payload));
            return summary.exitCode();
        }

        List<String> lines = AllModeRenderer.renderCheckAllOutput(blockResults, summary);
        if (summary.exitCode() == CliCodes.EXIT_OK) {
            CliText.printLines(out, lines);
            out.println("check-all: DONE project=. exit=" + CliCodes.EXIT_OK);
            return CliCodes.EXIT_OK;
        }
        CliText.printLines(err, lines);
        out.println("check-all: DONE project=. exit=" + summary.exitCode());
        return BearCli.fail(
            err,
            summary.exitCode(),
            CliCodes.REPO_MULTI_BLOCK_FAILED,
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    private static boolean isManifestSemanticFieldError(ManifestParseException e) {
        return CheckManifestValidation.isManifestSemanticFieldError(e);
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

    private static String phaseTaskSuffix(ProjectTestResult testResult) {
        return CheckDiagnosticsFormatter.phaseTaskSuffix(testResult);
    }

    private static String markerWriteFailureSuffix(IOException error) {
        return "; markerWrite=failed:" + CliText.squash(error.getMessage());
    }

    private static void emitBlockStart(PrintStream out, BlockIndexEntry block) {
        out.println("check-all: BLOCK_START name="
            + block.name()
            + " ir="
            + normalizePathToken(block.ir()));
    }

    private static String normalizePathToken(String path) {
        return path == null ? "." : path.replace('\\', '/');
    }

    private static int heartbeatIntervalSeconds() {
        String raw = System.getProperty(HEARTBEAT_SECONDS_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return 30;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed <= 0 ? 30 : parsed;
        } catch (NumberFormatException ignored) {
            return 30;
        }
    }

    private static int rootTestExitCode(ProjectTestResult result) {
        if (result == null) {
            return CliCodes.EXIT_INTERNAL;
        }
        if (result.status() == ProjectTestStatus.PASSED) {
            return CliCodes.EXIT_OK;
        }
        if (result.status() == ProjectTestStatus.TIMEOUT) {
            return 124;
        }
        return 1;
    }

    private record RootTestHeartbeat(
        PrintStream out,
        String projectRoot,
        int intervalSeconds,
        AtomicBoolean stopped,
        Thread worker
    ) {
        static RootTestHeartbeat start(PrintStream out, String projectRoot, int intervalSeconds) {
            AtomicBoolean stopped = new AtomicBoolean(false);
            String normalizedProjectRoot = normalizePathToken(projectRoot);
            Thread worker = new Thread(
                () -> runLoop(out, normalizedProjectRoot, intervalSeconds, stopped),
                "bear-check-all-heartbeat-" + normalizedProjectRoot
            );
            worker.setDaemon(true);
            worker.start();
            return new RootTestHeartbeat(out, normalizedProjectRoot, intervalSeconds, stopped, worker);
        }

        void stop() {
            stopped.set(true);
            worker.interrupt();
            try {
                worker.join(TimeUnit.SECONDS.toMillis(1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private static void runLoop(PrintStream out, String projectRoot, int intervalSeconds, AtomicBoolean stopped) {
            long startNanos = System.nanoTime();
            long nextHeartbeat = intervalSeconds;
            while (!stopped.get()) {
                long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos);
                while (!stopped.get() && elapsedSeconds >= nextHeartbeat) {
                    out.println(
                        "check-all: HEARTBEAT seconds="
                            + nextHeartbeat
                            + " phase=root_test project="
                            + projectRoot
                    );
                    nextHeartbeat += intervalSeconds;
                }
                try {
                    Thread.sleep(HEARTBEAT_POLL_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
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
        if ("STATE_STORE_OP_MISUSE".equals(rule)) {
            return "In adapter lane, do not mix update-path logic with state-create calls in the same method; split create vs update semantics and preserve explicit not-found behavior.";
        }
        if ("STATE_STORE_NOOP_UPDATE".equals(rule)) {
            return "In `_shared/state`, update-path methods must not silently return on missing state; raise explicit not-found behavior instead.";
        }
        return "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses.";
    }

    private static BlockExecutionResult withDetail(BlockExecutionResult base, String detail) {
        return new BlockExecutionResult(
            base.name(),
            base.ir(),
            base.project(),
            base.status(),
            base.exitCode(),
            base.category(),
            base.blockCode(),
            base.blockPath(),
            detail,
            base.blockRemediation(),
            base.reason(),
            base.classification(),
            base.deltaLines(),
            base.governanceLines()
        );
    }
}



