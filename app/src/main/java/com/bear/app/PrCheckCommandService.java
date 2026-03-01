package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.policy.SharedAllowedDepsPolicy;
import com.bear.kernel.policy.SharedAllowedDepsPolicyException;
import com.bear.kernel.policy.SharedAllowedDepsPolicyParser;
import com.bear.kernel.target.JvmTarget;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

final class PrCheckCommandService {
    private static final IrPipeline IR_PIPELINE = new DefaultIrPipeline();
    private static final String TEMP_BASE_IR_RELATIVE = "work/base/base.bear.yaml";
    private static final String TEMP_BASE_WIRING_ROOT_RELATIVE = "generated/base";
    private static final String TEMP_HEAD_WIRING_ROOT_RELATIVE = "generated/head";
    private static final String SHARED_POLICY_RELATIVE_PATH = SharedAllowedDepsPolicy.DEFAULT_RELATIVE_PATH;
    private static volatile Path lastTempRootForTest;

    private PrCheckCommandService() {
    }

    static PrCheckResult executePrCheck(Path projectRoot, String repoRelativePath, String baseRef) {
        return executePrCheck(projectRoot, repoRelativePath, baseRef, true, ".");
    }

    static PrCheckResult executePrCheck(
        Path projectRoot,
        String repoRelativePath,
        String baseRef,
        boolean includeSharedPolicyDeltas,
        String projectRootLabel
    ) {
        Path tempRoot = null;
        try {
            lastTempRootForTest = null;
            maybeFailInternalForTest();
            JvmTarget target = new JvmTarget();

            Path headIrPath = projectRoot.resolve(repoRelativePath).normalize();
            if (!headIrPath.startsWith(projectRoot) || !Files.isRegularFile(headIrPath)) {
                return prFailure(
                    CliCodes.EXIT_IO,
                    List.of("pr-check: IO_ERROR: READ_HEAD_FAILED: " + repoRelativePath),
                    "IO_ERROR",
                    CliCodes.IO_ERROR,
                    repoRelativePath,
                    "Ensure the IR file exists at HEAD and rerun `bear pr-check`.",
                    "pr-check: IO_ERROR: READ_HEAD_FAILED: " + repoRelativePath,
                    List.of(),
                    false,
                    false
                );
            }

            BearIr head = IR_PIPELINE.parseValidateNormalize(headIrPath);
            BlockIdentityResolution headIdentity = BlockIdentityResolver.resolveSingleCommandIdentity(
                headIrPath,
                projectRoot,
                head.block().name()
            );
            String blockKey = headIdentity.blockKey();

            GitResult isRepoResult = runGitForPrCheck(projectRoot, List.of("rev-parse", "--is-inside-work-tree"), "git.repo");
            if (isRepoResult.exitCode() != 0 || !"true".equals(isRepoResult.stdout().trim())) {
                return prFailure(
                    CliCodes.EXIT_IO,
                    List.of("pr-check: IO_ERROR: NOT_A_GIT_REPO: " + projectRoot),
                    "IO_ERROR",
                    CliCodes.IO_GIT,
                    "git.repo",
                    "Run `bear pr-check` from a git working tree with a valid project path.",
                    "pr-check: IO_ERROR: NOT_A_GIT_REPO: " + projectRoot,
                    List.of(),
                    false,
                    false
                );
            }

            GitResult mergeBaseResult = runGitForPrCheck(projectRoot, List.of("merge-base", "HEAD", baseRef), "git.baseRef");
            if (mergeBaseResult.exitCode() != 0) {
                return prFailure(
                    CliCodes.EXIT_IO,
                    List.of("pr-check: IO_ERROR: MERGE_BASE_FAILED: " + baseRef),
                    "IO_ERROR",
                    CliCodes.IO_GIT,
                    "git.baseRef",
                    "Ensure base ref exists and is fetchable, then rerun `bear pr-check`.",
                    "pr-check: IO_ERROR: MERGE_BASE_FAILED: " + baseRef,
                    List.of(),
                    false,
                    false
                );
            }
            String mergeBase = mergeBaseResult.stdout().trim();
            if (mergeBase.isBlank()) {
                return prFailure(
                    CliCodes.EXIT_IO,
                    List.of("pr-check: IO_ERROR: MERGE_BASE_EMPTY: unable to resolve merge base"),
                    "IO_ERROR",
                    CliCodes.IO_GIT,
                    "git.baseRef",
                    "Ensure base ref resolves to a merge base with HEAD, then rerun `bear pr-check`.",
                    "pr-check: IO_ERROR: MERGE_BASE_EMPTY: unable to resolve merge base",
                    List.of(),
                    false,
                    false
                );
            }

            List<String> stdoutLines = new ArrayList<>();
            List<String> stderrLines = new ArrayList<>();
            BearIr base = null;
            WiringManifest baseWiring = null;
            GitResult catFileResult = runGitForPrCheck(
                projectRoot,
                List.of("cat-file", "-e", mergeBase + ":" + repoRelativePath),
                repoRelativePath
            );
            tempRoot = Files.createTempDirectory("bear-pr-check-");
            lastTempRootForTest = tempRoot;
            Path baseTempIr = tempRoot.resolve(TEMP_BASE_IR_RELATIVE);
            Path baseWiringRoot = tempRoot.resolve(TEMP_BASE_WIRING_ROOT_RELATIVE);
            Path headWiringRoot = tempRoot.resolve(TEMP_HEAD_WIRING_ROOT_RELATIVE);
            Path baseWiringManifestPath = baseWiringRoot.resolve("wiring").resolve(blockKey + ".wiring.json");
            Path headWiringManifestPath = headWiringRoot.resolve("wiring").resolve(blockKey + ".wiring.json");
            Files.createDirectories(baseTempIr.getParent());
            Files.createDirectories(baseWiringManifestPath.getParent());
            Files.createDirectories(headWiringManifestPath.getParent());

            target.generateWiringOnly(head, projectRoot, headWiringRoot, blockKey);
            WiringManifest headWiring;
            try {
                headWiring = ManifestParsers.parseWiringManifest(headWiringManifestPath);
            } catch (ManifestParseException e) {
                String line = "pr-check: MANIFEST_INVALID: " + e.reasonCode();
                return prFailure(
                    CliCodes.EXIT_VALIDATION,
                    List.of(line),
                    "VALIDATION",
                    CliCodes.MANIFEST_INVALID,
                    "generated/head/wiring/" + blockKey + ".wiring.json",
                    "Regenerate wiring metadata and rerun `bear pr-check`.",
                    line,
                    List.of(),
                    false,
                    false
                );
            }

            if (catFileResult.exitCode() != 0) {
                GitResult existsResult = runGitForPrCheck(
                    projectRoot,
                    List.of("ls-tree", "--name-only", mergeBase, "--", repoRelativePath),
                    repoRelativePath
                );
                if (existsResult.exitCode() != 0) {
                    return prFailure(
                        CliCodes.EXIT_IO,
                        List.of("pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath),
                        "IO_ERROR",
                        CliCodes.IO_GIT,
                        repoRelativePath,
                        "Ensure base ref and IR path are readable in git history, then rerun `bear pr-check`.",
                        "pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath,
                        List.of(),
                        false,
                        false
                    );
                }
                if (existsResult.stdout().trim().isEmpty()) {
                    stderrLines.add("pr-check: INFO: BASE_IR_MISSING_AT_MERGE_BASE: " + repoRelativePath + ": treated_as_empty_base");
                } else {
                    return prFailure(
                        CliCodes.EXIT_IO,
                        List.of("pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath),
                        "IO_ERROR",
                        CliCodes.IO_GIT,
                        repoRelativePath,
                        "Ensure base ref and IR path are readable in git history, then rerun `bear pr-check`.",
                        "pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath,
                        List.of(),
                        false,
                        false
                    );
                }
            } else {
                GitResult showResult = runGitForPrCheck(
                    projectRoot,
                    List.of("show", mergeBase + ":" + repoRelativePath),
                    repoRelativePath
                );
                if (showResult.exitCode() != 0) {
                    return prFailure(
                        CliCodes.EXIT_IO,
                        List.of("pr-check: IO_ERROR: BASE_IR_READ_FAILED: " + repoRelativePath),
                        "IO_ERROR",
                        CliCodes.IO_GIT,
                        repoRelativePath,
                        "Ensure base IR snapshot is readable from git history, then rerun `bear pr-check`.",
                        "pr-check: IO_ERROR: BASE_IR_READ_FAILED: " + repoRelativePath,
                        List.of(),
                        false,
                        false
                    );
                }
                Files.writeString(baseTempIr, showResult.stdout(), StandardCharsets.UTF_8);
                try {
                    base = IR_PIPELINE.parseValidateNormalize(baseTempIr);
                } catch (BearIrValidationException e) {
                    return prFailure(
                        CliCodes.EXIT_VALIDATION,
                        List.of("validation at " + repoRelativePath + ": BASE_IR_VALIDATION_FAILED"),
                        "VALIDATION",
                        CliCodes.IR_VALIDATION,
                        repoRelativePath,
                        "Fix the IR issue at the reported path and rerun `bear pr-check <ir-file> --project <path> --base <ref>`.",
                        "validation at " + repoRelativePath + ": BASE_IR_VALIDATION_FAILED",
                        List.of(),
                        false,
                        false
                    );
                }
                target.generateWiringOnly(base, projectRoot, baseWiringRoot, blockKey);
                try {
                    baseWiring = ManifestParsers.parseWiringManifest(baseWiringManifestPath);
                } catch (ManifestParseException e) {
                    String line = "pr-check: MANIFEST_INVALID: " + e.reasonCode();
                    return prFailure(
                        CliCodes.EXIT_VALIDATION,
                        List.of(line),
                        "VALIDATION",
                        CliCodes.MANIFEST_INVALID,
                        "generated/base/wiring/" + blockKey + ".wiring.json",
                        "Regenerate wiring metadata and rerun `bear pr-check`.",
                        line,
                        List.of(),
                        false,
                        false
                    );
                }
            }

            List<BoundaryBypassFinding> containmentFindings = BoundaryBypassScanner.scanPortImplContainmentBypass(
                projectRoot,
                List.of(headWiring)
            );
            if (!containmentFindings.isEmpty()) {
                List<String> detailLines = new ArrayList<>();
                for (BoundaryBypassFinding finding : containmentFindings) {
                    String line = "pr-check: BOUNDARY_BYPASS: RULE=" + finding.rule()
                        + ": " + finding.path()
                        + ": " + finding.detail();
                    detailLines.add(line);
                    stderrLines.add(line);
                }
                return prFailure(
                    CliCodes.EXIT_BOUNDARY_BYPASS,
                    stderrLines,
                    "BOUNDARY_BYPASS",
                    CliCodes.BOUNDARY_BYPASS,
                    containmentFindings.get(0).path(),
                    boundaryBypassRemediation(containmentFindings.get(0).rule()),
                    detailLines.get(0),
                    List.of(),
                    false,
                    false
                );
            }
            TreeSet<String> bypassPaths = new TreeSet<>();
            for (BoundaryBypassFinding finding : containmentFindings) {
                bypassPaths.add(finding.path());
            }
            List<String> governanceLines = new ArrayList<>();
            List<MultiBlockPortImplAllowedSignal> allowedSignals = PortImplContainmentScanner.scanMultiBlockPortImplAllowedSignals(
                projectRoot,
                List.of(headWiring)
            );
            for (MultiBlockPortImplAllowedSignal signal : allowedSignals) {
                if (bypassPaths.contains(signal.path())) {
                    continue;
                }
                String line = "pr-check: GOVERNANCE: MULTI_BLOCK_PORT_IMPL_ALLOWED: "
                    + signal.path()
                    + ": "
                    + signal.implClassFqcn()
                    + " -> "
                    + signal.generatedPackageCsv();
                governanceLines.add(line);
                stdoutLines.add(line);
            }

            List<PrDelta> deltas = new ArrayList<>(PrDeltaClassifier.computePrDeltas(base, head));
            if (includeSharedPolicyDeltas) {
                SharedPolicyDeltaComputation sharedDeltaComputation = computeSharedPolicyDeltas(projectRoot, mergeBase, projectRootLabel);
                if (sharedDeltaComputation.failureResult() != null) {
                    return sharedDeltaComputation.failureResult();
                }
                deltas.addAll(sharedDeltaComputation.deltas());
            }
            deltas.sort(Comparator
                .comparing((PrDelta delta) -> delta.clazz().order)
                .thenComparing(delta -> delta.category().order)
                .thenComparing(delta -> delta.change().order)
                .thenComparing(PrDelta::key));
            List<String> deltaLines = new ArrayList<>();
            for (PrDelta delta : deltas) {
                String line = "pr-delta: " + delta.clazz().label + ": " + delta.category().label + ": " + delta.change().label + ": " + delta.key();
                deltaLines.add(line);
                stderrLines.add(line);
            }

            boolean hasBoundary = deltas.stream().anyMatch(delta -> delta.clazz() == PrClass.BOUNDARY_EXPANDING);
            if (hasBoundary) {
                stderrLines.add("pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED");
                return prFailure(
                    CliCodes.EXIT_BOUNDARY_EXPANSION,
                    stderrLines,
                    "BOUNDARY_EXPANSION",
                    CliCodes.BOUNDARY_EXPANSION,
                    repoRelativePath,
                    "Review boundary-expanding deltas and route through explicit boundary review.",
                    "pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED",
                    deltaLines,
                    true,
                    !deltaLines.isEmpty()
                );
            }

            stdoutLines.add("pr-check: OK: NO_BOUNDARY_EXPANSION");
            return new PrCheckResult(
                CliCodes.EXIT_OK,
                List.copyOf(stdoutLines),
                stderrLines,
                null,
                null,
                null,
                null,
                null,
                deltaLines,
                false,
                !deltaLines.isEmpty(),
                List.copyOf(governanceLines)
            );
        } catch (ManifestParseException e) {
            String line = "pr-check: MANIFEST_INVALID: " + e.reasonCode();
            return prFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.MANIFEST_INVALID,
                "generated/head/wiring",
                "Regenerate wiring metadata and rerun `bear pr-check`.",
                line,
                List.of(),
                false,
                false
            );
        } catch (PrCheckGitException e) {
            return prFailure(
                CliCodes.EXIT_IO,
                List.of(e.legacyLine()),
                "IO_ERROR",
                CliCodes.IO_GIT,
                e.pathLocator(),
                "Resolve git invocation/base-reference issues and rerun `bear pr-check`.",
                e.legacyLine(),
                List.of(),
                false,
                false
            );
        } catch (BearIrValidationException e) {
            return prFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(e.formatLine()),
                "VALIDATION",
                CliCodes.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear pr-check`.",
                e.formatLine(),
                List.of(),
                false,
                false
            );
        } catch (BlockIndexValidationException e) {
            String line = "index: VALIDATION_ERROR: " + e.getMessage();
            return prFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear pr-check`.",
                line,
                List.of(),
                false,
                false
            );
        } catch (BlockIdentityResolutionException e) {
            return prFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(e.line()),
                "VALIDATION",
                CliCodes.IR_VALIDATION,
                e.path(),
                e.remediation(),
                e.line(),
                List.of(),
                false,
                false
            );
        } catch (IOException e) {
            return prFailure(
                CliCodes.EXIT_IO,
                List.of("pr-check: IO_ERROR: INTERNAL_IO"),
                "IO_ERROR",
                CliCodes.IO_ERROR,
                "internal",
                "Ensure local filesystem paths are accessible, then rerun `bear pr-check`.",
                "pr-check: IO_ERROR: INTERNAL_IO",
                List.of(),
                false,
                false
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return prFailure(
                CliCodes.EXIT_IO,
                List.of("pr-check: IO_ERROR: INTERRUPTED"),
                "IO_ERROR",
                CliCodes.IO_GIT,
                "git.repo",
                "Retry `bear pr-check`; if interruption persists, rerun in a stable shell/CI environment.",
                "pr-check: IO_ERROR: INTERRUPTED",
                List.of(),
                false,
                false
            );
        } catch (Exception e) {
            return prFailure(
                CliCodes.EXIT_INTERNAL,
                List.of("internal: INTERNAL_ERROR:"),
                "INTERNAL_ERROR",
                CliCodes.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli.",
                "internal: INTERNAL_ERROR:",
                List.of(),
                false,
                false
            );
        } finally {
            if (tempRoot != null) {
                if (!Boolean.getBoolean("bear.prcheck.test.keepTemp")) {
                    deleteRecursivelyBestEffort(tempRoot);
                }
            }
        }
    }

    private static PrCheckResult prFailure(
        int exitCode,
        List<String> stderrLines,
        String category,
        String failureCode,
        String failurePath,
        String failureRemediation,
        String detail,
        List<String> deltaLines,
        boolean hasBoundary,
        boolean hasDeltas
    ) {
        return new PrCheckResult(
            exitCode,
            List.of(),
            List.copyOf(stderrLines),
            category,
            failureCode,
            failurePath,
            failureRemediation,
            detail,
            List.copyOf(deltaLines),
            hasBoundary,
            hasDeltas
        );
    }

    private static String boundaryBypassRemediation(String rule) {
        if ("PORT_IMPL_OUTSIDE_GOVERNED_ROOT".equals(rule)) {
            return "Move the port implementation under the owning block governed roots (block root or blocks/_shared) or refactor so app layer calls wrappers without implementing generated ports.";
        }
        if ("MULTI_BLOCK_PORT_IMPL_FORBIDDEN".equals(rule)) {
            return "Split generated-port adapters so each class implements one generated block package, or move the adapter under blocks/_shared and add `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL` within 5 non-empty lines above the class declaration.";
        }
        return "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses.";
    }

    static SharedPolicyDeltaComputation computeSharedPolicyDeltasForProjectRoot(
        Path projectRoot,
        String baseRef,
        String projectRootLabel
    ) {
        try {
            GitResult isRepoResult = runGitForPrCheck(projectRoot, List.of("rev-parse", "--is-inside-work-tree"), "git.repo");
            if (isRepoResult.exitCode() != 0 || !"true".equals(isRepoResult.stdout().trim())) {
                return SharedPolicyDeltaComputation.failure(prFailure(
                    CliCodes.EXIT_IO,
                    List.of("pr-check: IO_ERROR: NOT_A_GIT_REPO: " + projectRoot),
                    "IO_ERROR",
                    CliCodes.IO_GIT,
                    "git.repo",
                    "Run `bear pr-check` from a git working tree with a valid project path.",
                    "pr-check: IO_ERROR: NOT_A_GIT_REPO: " + projectRoot,
                    List.of(),
                    false,
                    false
                ));
            }
            GitResult mergeBaseResult = runGitForPrCheck(projectRoot, List.of("merge-base", "HEAD", baseRef), "git.baseRef");
            if (mergeBaseResult.exitCode() != 0) {
                return SharedPolicyDeltaComputation.failure(prFailure(
                    CliCodes.EXIT_IO,
                    List.of("pr-check: IO_ERROR: MERGE_BASE_FAILED: " + baseRef),
                    "IO_ERROR",
                    CliCodes.IO_GIT,
                    "git.baseRef",
                    "Ensure base ref exists and is fetchable, then rerun `bear pr-check`.",
                    "pr-check: IO_ERROR: MERGE_BASE_FAILED: " + baseRef,
                    List.of(),
                    false,
                    false
                ));
            }
            String mergeBase = mergeBaseResult.stdout().trim();
            if (mergeBase.isBlank()) {
                return SharedPolicyDeltaComputation.failure(prFailure(
                    CliCodes.EXIT_IO,
                    List.of("pr-check: IO_ERROR: MERGE_BASE_EMPTY: unable to resolve merge base"),
                    "IO_ERROR",
                    CliCodes.IO_GIT,
                    "git.baseRef",
                    "Ensure base ref resolves to a merge base with HEAD, then rerun `bear pr-check`.",
                    "pr-check: IO_ERROR: MERGE_BASE_EMPTY: unable to resolve merge base",
                    List.of(),
                    false,
                    false
                ));
            }
            return computeSharedPolicyDeltas(projectRoot, mergeBase, projectRootLabel);
        } catch (PrCheckGitException e) {
            return SharedPolicyDeltaComputation.failure(prFailure(
                CliCodes.EXIT_IO,
                List.of(e.legacyLine()),
                "IO_ERROR",
                CliCodes.IO_GIT,
                e.pathLocator(),
                "Resolve git invocation/base-reference issues and rerun `bear pr-check`.",
                e.legacyLine(),
                List.of(),
                false,
                false
            ));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SharedPolicyDeltaComputation.failure(prFailure(
                CliCodes.EXIT_IO,
                List.of("pr-check: IO_ERROR: INTERRUPTED"),
                "IO_ERROR",
                CliCodes.IO_GIT,
                "git.repo",
                "Retry `bear pr-check`; if interruption persists, rerun in a stable shell/CI environment.",
                "pr-check: IO_ERROR: INTERRUPTED",
                List.of(),
                false,
                false
            ));
        }
    }

    private static SharedPolicyDeltaComputation computeSharedPolicyDeltas(
        Path projectRoot,
        String mergeBase,
        String projectRootLabel
    ) {
        SharedAllowedDepsPolicyParser policyParser = new SharedAllowedDepsPolicyParser();
        String label = (projectRootLabel == null || projectRootLabel.isBlank()) ? "." : projectRootLabel;
        SharedAllowedDepsPolicy headPolicy;
        try {
            headPolicy = policyParser.parse(projectRoot.resolve(SHARED_POLICY_RELATIVE_PATH));
        } catch (SharedAllowedDepsPolicyException e) {
            String line = "pr-check: POLICY_INVALID: " + e.reasonCode();
            return SharedPolicyDeltaComputation.failure(prFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(line),
                "VALIDATION",
                CliCodes.POLICY_INVALID,
                SHARED_POLICY_RELATIVE_PATH,
                "Fix `spec/_shared.policy.yaml` (version/scope/schema and pinned allowedDeps) and rerun `bear pr-check`.",
                line,
                List.of(),
                false,
                false
            ));
        } catch (IOException e) {
            return SharedPolicyDeltaComputation.failure(prFailure(
                CliCodes.EXIT_IO,
                List.of("pr-check: IO_ERROR: INTERNAL_IO"),
                "IO_ERROR",
                CliCodes.IO_ERROR,
                "internal",
                "Ensure local filesystem paths are accessible, then rerun `bear pr-check`.",
                "pr-check: IO_ERROR: INTERNAL_IO",
                List.of(),
                false,
                false
            ));
        }

        SharedAllowedDepsPolicy basePolicy = SharedAllowedDepsPolicy.empty();
        try {
            GitResult catFileResult = runGitForPrCheck(
                projectRoot,
                List.of("cat-file", "-e", mergeBase + ":" + SHARED_POLICY_RELATIVE_PATH),
                SHARED_POLICY_RELATIVE_PATH
            );
            if (catFileResult.exitCode() != 0) {
                GitResult existsResult = runGitForPrCheck(
                    projectRoot,
                    List.of("ls-tree", "--name-only", mergeBase, "--", SHARED_POLICY_RELATIVE_PATH),
                    SHARED_POLICY_RELATIVE_PATH
                );
                if (existsResult.exitCode() != 0) {
                    return SharedPolicyDeltaComputation.failure(prFailure(
                        CliCodes.EXIT_IO,
                        List.of("pr-check: IO_ERROR: BASE_POLICY_LOOKUP_FAILED: " + SHARED_POLICY_RELATIVE_PATH),
                        "IO_ERROR",
                        CliCodes.IO_GIT,
                        SHARED_POLICY_RELATIVE_PATH,
                        "Ensure base ref and shared policy path are readable in git history, then rerun `bear pr-check`.",
                        "pr-check: IO_ERROR: BASE_POLICY_LOOKUP_FAILED: " + SHARED_POLICY_RELATIVE_PATH,
                        List.of(),
                        false,
                        false
                    ));
                }
            } else {
                GitResult showResult = runGitForPrCheck(
                    projectRoot,
                    List.of("show", mergeBase + ":" + SHARED_POLICY_RELATIVE_PATH),
                    SHARED_POLICY_RELATIVE_PATH
                );
                if (showResult.exitCode() != 0) {
                    return SharedPolicyDeltaComputation.failure(prFailure(
                        CliCodes.EXIT_IO,
                        List.of("pr-check: IO_ERROR: BASE_POLICY_READ_FAILED: " + SHARED_POLICY_RELATIVE_PATH),
                        "IO_ERROR",
                        CliCodes.IO_GIT,
                        SHARED_POLICY_RELATIVE_PATH,
                        "Ensure base shared policy snapshot is readable from git history, then rerun `bear pr-check`.",
                        "pr-check: IO_ERROR: BASE_POLICY_READ_FAILED: " + SHARED_POLICY_RELATIVE_PATH,
                        List.of(),
                        false,
                        false
                    ));
                }
                try {
                    basePolicy = policyParser.parseContent(showResult.stdout(), SHARED_POLICY_RELATIVE_PATH + "@base");
                } catch (SharedAllowedDepsPolicyException e) {
                    String line = "pr-check: POLICY_INVALID: " + e.reasonCode();
                    return SharedPolicyDeltaComputation.failure(prFailure(
                        CliCodes.EXIT_VALIDATION,
                        List.of(line),
                        "VALIDATION",
                        CliCodes.POLICY_INVALID,
                        SHARED_POLICY_RELATIVE_PATH,
                        "Fix `spec/_shared.policy.yaml` (version/scope/schema and pinned allowedDeps) and rerun `bear pr-check`.",
                        line,
                        List.of(),
                        false,
                        false
                    ));
                }
            }
        } catch (PrCheckGitException e) {
            return SharedPolicyDeltaComputation.failure(prFailure(
                CliCodes.EXIT_IO,
                List.of(e.legacyLine()),
                "IO_ERROR",
                CliCodes.IO_GIT,
                e.pathLocator(),
                "Resolve git invocation/base-reference issues and rerun `bear pr-check`.",
                e.legacyLine(),
                List.of(),
                false,
                false
            ));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SharedPolicyDeltaComputation.failure(prFailure(
                CliCodes.EXIT_IO,
                List.of("pr-check: IO_ERROR: INTERRUPTED"),
                "IO_ERROR",
                CliCodes.IO_GIT,
                "git.repo",
                "Retry `bear pr-check`; if interruption persists, rerun in a stable shell/CI environment.",
                "pr-check: IO_ERROR: INTERRUPTED",
                List.of(),
                false,
                false
            ));
        }

        List<PrDelta> deltas = computeSharedPolicyPrDeltas(basePolicy.asMap(), headPolicy.asMap(), label);
        return SharedPolicyDeltaComputation.success(deltas);
    }

    private static List<PrDelta> computeSharedPolicyPrDeltas(
        Map<String, String> base,
        Map<String, String> head,
        String projectRootLabel
    ) {
        TreeSet<String> coordinates = new TreeSet<>();
        coordinates.addAll(base.keySet());
        coordinates.addAll(head.keySet());
        ArrayList<PrDelta> deltas = new ArrayList<>();
        for (String ga : coordinates) {
            String keyPrefix = projectRootLabel + ":_shared:" + ga;
            boolean inBase = base.containsKey(ga);
            boolean inHead = head.containsKey(ga);
            if (!inBase) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.ALLOWED_DEPS,
                    PrChange.ADDED,
                    keyPrefix + "@" + head.get(ga)
                ));
                continue;
            }
            if (!inHead) {
                deltas.add(new PrDelta(
                    PrClass.ORDINARY,
                    PrCategory.ALLOWED_DEPS,
                    PrChange.REMOVED,
                    keyPrefix + "@" + base.get(ga)
                ));
                continue;
            }
            if (!base.get(ga).equals(head.get(ga))) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.ALLOWED_DEPS,
                    PrChange.CHANGED,
                    keyPrefix + "@" + base.get(ga) + "->" + head.get(ga)
                ));
            }
        }
        deltas.sort(Comparator
            .comparing((PrDelta delta) -> delta.clazz().order)
            .thenComparing(delta -> delta.category().order)
            .thenComparing(delta -> delta.change().order)
            .thenComparing(PrDelta::key));
        return List.copyOf(deltas);
    }

    private static GitResult runGit(Path projectRoot, List<String> gitArgs) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(projectRoot.toString());
        command.addAll(gitArgs);

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            throw new IOException("GIT_TIMEOUT: " + String.join(" ", gitArgs));
        }

        String stdout;
        String stderr;
        try (InputStream out = process.getInputStream(); InputStream err = process.getErrorStream()) {
            stdout = new String(out.readAllBytes(), StandardCharsets.UTF_8);
            stderr = new String(err.readAllBytes(), StandardCharsets.UTF_8);
        }
        return new GitResult(process.exitValue(), CliText.normalizeLf(stdout), CliText.normalizeLf(stderr));
    }

    private static GitResult runGitForPrCheck(Path projectRoot, List<String> gitArgs, String pathLocator)
        throws PrCheckGitException, InterruptedException {
        try {
            return runGit(projectRoot, gitArgs);
        } catch (IOException e) {
            throw new PrCheckGitException("pr-check: IO_ERROR: INTERNAL_IO: " + CliText.squash(e.getMessage()), pathLocator);
        }
    }

    private static void maybeFailInternalForTest() {
        String key = "bear.cli.test.failInternal.pr-check";
        if ("true".equals(System.getProperty(key))) {
            throw new IllegalStateException("INJECTED_INTERNAL_pr-check");
        }
    }

    static Path consumeLastTempRootForTest() {
        Path value = lastTempRootForTest;
        lastTempRootForTest = null;
        return value;
    }

    record SharedPolicyDeltaComputation(List<PrDelta> deltas, boolean hasBoundary, PrCheckResult failureResult) {
        static SharedPolicyDeltaComputation success(List<PrDelta> deltas) {
            boolean hasBoundary = deltas.stream().anyMatch(delta -> delta.clazz() == PrClass.BOUNDARY_EXPANDING);
            return new SharedPolicyDeltaComputation(List.copyOf(deltas), hasBoundary, null);
        }

        static SharedPolicyDeltaComputation failure(PrCheckResult result) {
            return new SharedPolicyDeltaComputation(List.of(), false, result);
        }
    }

    private static void deleteRecursivelyBestEffort(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
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

    private record GitResult(int exitCode, String stdout, String stderr) {
    }

    private static final class PrCheckGitException extends Exception {
        private final String legacyLine;
        private final String pathLocator;

        private PrCheckGitException(String legacyLine, String pathLocator) {
            super(legacyLine);
            this.legacyLine = legacyLine;
            this.pathLocator = pathLocator;
        }

        private String legacyLine() {
            return legacyLine;
        }

        private String pathLocator() {
            return pathLocator;
        }
    }
}
