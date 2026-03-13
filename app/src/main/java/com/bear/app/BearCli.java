package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.target.Target;
import com.bear.kernel.target.TargetRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

public final class BearCli {
    private static final IrPipeline IR_PIPELINE = new DefaultIrPipeline();
    private static final TargetRegistry TARGET_REGISTRY = TargetRegistry.defaultRegistry();
    private static final Map<String, CommandHandler> COMMAND_HANDLERS = Map.of(
        "validate", BearCliCommandHandlers::runValidate,
        "compile", BearCliCommandHandlers::runCompile,
        "fix", BearCliCommandHandlers::runFix,
        "check", BearCliCommandHandlers::runCheck,
        "unblock", BearCliCommandHandlers::runUnblock,
        "pr-check", BearCliCommandHandlers::runPrCheck
    );
    private static final String PR_CHECK_BOUNDARY_MARKER = "pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED";
    private static final String PR_CHECK_EXIT_ENVELOPE_ANOMALY = "PR_CHECK_EXIT_ENVELOPE_ANOMALY";
    private static final String PR_CHECK_ENVELOPE_PATH = "pr-check.envelope";
    private static final String PR_CHECK_ENVELOPE_REMEDIATION =
        "Capture stderr and file an issue against bear-cli (pr-check exit-envelope anomaly).";
    private static final String PR_CHECK_BOUNDARY_EXIT_OVERRIDE_PROPERTY =
        "bear.cli.test.expectedBoundaryExpansionExit.pr-check";

    private BearCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        String command = args.length == 0 ? "help" : args[0];
        if ("help".equals(command) || "-h".equals(command) || "--help".equals(command)) {
            printUsage(out);
            return CliCodes.EXIT_OK;
        }
        CommandHandler handler = COMMAND_HANDLERS.get(command);
        if (handler != null) {
            return handler.handle(args, out, err);
        }
        return failWithLegacy(
            err,
            CliCodes.EXIT_USAGE,
            "usage: UNKNOWN_COMMAND: unknown command: " + command,
            CliCodes.USAGE_UNKNOWN_COMMAND,
            "cli.command",
            "Run `bear --help` and use a supported command."
        );
    }

    static int emitCheckResult(CheckResult result, PrintStream out, PrintStream err) {
        printLines(out, result.stdoutLines());
        printLines(err, result.stderrLines());
        if (result.exitCode() == CliCodes.EXIT_OK) {
            return CliCodes.EXIT_OK;
        }
        return fail(
            err,
            result.exitCode(),
            result.failureCode(),
            result.failurePath(),
            result.failureRemediation()
        );
    }

    static int emitFixResult(FixResult result, PrintStream out, PrintStream err) {
        printLines(out, result.stdoutLines());
        printLines(err, result.stderrLines());
        if (result.exitCode() == CliCodes.EXIT_OK) {
            return CliCodes.EXIT_OK;
        }
        return fail(
            err,
            result.exitCode(),
            result.failureCode(),
            result.failurePath(),
            result.failureRemediation()
        );
    }

    static int emitCompileResult(CompileResult result, PrintStream out, PrintStream err) {
        printLines(out, result.stdoutLines());
        printLines(err, result.stderrLines());
        if (result.exitCode() == CliCodes.EXIT_OK) {
            return CliCodes.EXIT_OK;
        }
        return fail(
            err,
            result.exitCode(),
            result.failureCode(),
            result.failurePath(),
            result.failureRemediation()
        );
    }

    static int emitPrCheckResult(PrCheckResult result, PrintStream out, PrintStream err) {
        printLines(out, result.stdoutLines());
        printLines(err, result.stderrLines());
        if (result.exitCode() == CliCodes.EXIT_OK) {
            return CliCodes.EXIT_OK;
        }
        return fail(
            err,
            result.exitCode(),
            result.failureCode(),
            result.failurePath(),
            result.failureRemediation()
        );
    }

    static FixResult executeFix(Path irFile, Path projectRoot, String expectedBlockKey, String expectedBlockLocator) {
        return executeFix(irFile, projectRoot, expectedBlockKey, expectedBlockLocator, null);
    }

    static FixResult executeFix(
        Path irFile,
        Path projectRoot,
        String expectedBlockKey,
        String expectedBlockLocator,
        Path explicitIndexPath
    ) {
        try {
            maybeFailInternalForTest("fix");
            Target target = TARGET_REGISTRY.resolve(projectRoot);
            BearIr normalized = IR_PIPELINE.parseValidateNormalize(irFile);
            Path resolvedIndexPath = explicitIndexPath;
            if (BlockPortGraphResolver.hasBlockPortEffects(normalized)) {
                resolvedIndexPath = SingleFileIndexResolver.resolveForBlockPorts(projectRoot, explicitIndexPath, "fix");
                Path repoRoot = resolvedIndexPath.getParent();
                BlockPortGraphResolver.resolveAndValidate(repoRoot, resolvedIndexPath);
            }
            BlockIdentityResolution identity = expectedBlockKey == null
                ? BlockIdentityResolver.resolveSingleCommandIdentity(irFile, projectRoot, normalized.block().name(), resolvedIndexPath)
                : BlockIdentityResolver.resolveIndexIdentity(
                    expectedBlockKey,
                    expectedBlockLocator == null || expectedBlockLocator.isBlank()
                        ? "bear.blocks.yaml:name=" + expectedBlockKey
                        : expectedBlockLocator,
                    normalized.block().name()
                );

            target.compile(normalized, projectRoot, identity.blockKey());
            return new FixResult(CliCodes.EXIT_OK, List.of("fix: OK"), List.of(), null, null, null, null, null);
        } catch (com.bear.kernel.target.TargetResolutionException e) {
            return fixFailure(
                e.exitCode(),
                List.of(e.code() + ": " + e.path()),
                e.code(),
                e.code(),
                e.path(),
                e.remediation(),
                e.code() + ": " + e.path()
            );
        } catch (BearIrValidationException e) {
            return fixFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(e.formatLine()),
                "VALIDATION",
                CliCodes.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear fix <ir-file> --project <path>`.",
                e.formatLine()
            );
        } catch (BlockIndexValidationException e) {
            return fixFailure(
                CliCodes.EXIT_VALIDATION,
                List.of("index: VALIDATION_ERROR: " + e.getMessage()),
                "VALIDATION",
                CliCodes.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear fix`.",
                "index: VALIDATION_ERROR: " + e.getMessage()
            );
        } catch (BlockIdentityResolutionException e) {
            return fixFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(e.line()),
                "VALIDATION",
                CliCodes.IR_VALIDATION,
                e.path(),
                e.remediation(),
                e.line()
            );
        } catch (IOException e) {
            return fixFailure(
                CliCodes.EXIT_IO,
                List.of("io: IO_ERROR: " + e.getMessage()),
                "IO_ERROR",
                CliCodes.IO_ERROR,
                "project.root",
                "Ensure the IR/project paths are readable and writable, then rerun `bear fix`.",
                "io: IO_ERROR: " + e.getMessage()
            );
        } catch (Exception e) {
            return fixFailure(
                CliCodes.EXIT_INTERNAL,
                List.of("internal: INTERNAL_ERROR:"),
                "INTERNAL_ERROR",
                CliCodes.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli.",
                "internal: INTERNAL_ERROR:"
            );
        }
    }

    static CompileResult executeCompile(Path irFile, Path projectRoot, String expectedBlockKey, String expectedBlockLocator) {
        return executeCompile(irFile, projectRoot, expectedBlockKey, expectedBlockLocator, null);
    }

    static CompileResult executeCompile(
        Path irFile,
        Path projectRoot,
        String expectedBlockKey,
        String expectedBlockLocator,
        Path explicitIndexPath
    ) {
        try {
            maybeFailInternalForTest("compile");
            Target target = TARGET_REGISTRY.resolve(projectRoot);
            BearIr normalized = IR_PIPELINE.parseValidateNormalize(irFile);
            Path resolvedIndexPath = explicitIndexPath;
            if (BlockPortGraphResolver.hasBlockPortEffects(normalized)) {
                resolvedIndexPath = SingleFileIndexResolver.resolveForBlockPorts(projectRoot, explicitIndexPath, "compile");
                Path repoRoot = resolvedIndexPath.getParent();
                BlockPortGraphResolver.resolveAndValidate(repoRoot, resolvedIndexPath);
            }
            BlockIdentityResolution identity = expectedBlockKey == null
                ? BlockIdentityResolver.resolveSingleCommandIdentity(irFile, projectRoot, normalized.block().name(), resolvedIndexPath)
                : BlockIdentityResolver.resolveIndexIdentity(
                    expectedBlockKey,
                    expectedBlockLocator == null || expectedBlockLocator.isBlank()
                        ? "bear.blocks.yaml:name=" + expectedBlockKey
                        : expectedBlockLocator,
                    normalized.block().name()
                );
            target.compile(normalized, projectRoot, identity.blockKey());
            return new CompileResult(CliCodes.EXIT_OK, List.of("compiled: OK"), List.of(), null, null, null, null, null);
        } catch (com.bear.kernel.target.TargetResolutionException e) {
            return compileFailure(
                e.exitCode(),
                List.of(e.code() + ": " + e.path()),
                e.code(),
                e.code(),
                e.path(),
                e.remediation(),
                e.code() + ": " + e.path()
            );
        } catch (BearIrValidationException e) {
            return compileFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(e.formatLine()),
                "VALIDATION",
                CliCodes.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear compile <ir-file> --project <path>`.",
                e.formatLine()
            );
        } catch (BlockIndexValidationException e) {
            return compileFailure(
                CliCodes.EXIT_VALIDATION,
                List.of("index: VALIDATION_ERROR: " + e.getMessage()),
                "VALIDATION",
                CliCodes.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear compile`.",
                "index: VALIDATION_ERROR: " + e.getMessage()
            );
        } catch (BlockIdentityResolutionException e) {
            return compileFailure(
                CliCodes.EXIT_VALIDATION,
                List.of(e.line()),
                "VALIDATION",
                CliCodes.IR_VALIDATION,
                e.path(),
                e.remediation(),
                e.line()
            );
        } catch (IOException e) {
            return compileFailure(
                CliCodes.EXIT_IO,
                List.of("io: IO_ERROR: " + e.getMessage()),
                "IO_ERROR",
                CliCodes.IO_ERROR,
                "project.root",
                "Ensure the IR/project paths are readable and writable, then rerun `bear compile`.",
                "io: IO_ERROR: " + e.getMessage()
            );
        } catch (Exception e) {
            return compileFailure(
                CliCodes.EXIT_INTERNAL,
                List.of("internal: INTERNAL_ERROR:"),
                "INTERNAL_ERROR",
                CliCodes.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli.",
                "internal: INTERNAL_ERROR:"
            );
        }
    }

    static CheckResult executeCheck(
        Path irFile,
        Path projectRoot,
        boolean runReachAndTests,
        boolean strictHygiene,
        String expectedBlockKey,
        String expectedBlockLocator
    ) {
        return executeCheck(irFile, projectRoot, runReachAndTests, strictHygiene, expectedBlockKey, expectedBlockLocator, null, false);
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
        return CheckCommandService.executeCheck(
            irFile,
            projectRoot,
            runReachAndTests,
            strictHygiene,
            expectedBlockKey,
            expectedBlockLocator,
            explicitIndexPath,
            collectAll
        );
    }

    static PrCheckResult enforcePrCheckExitEnvelope(PrCheckResult result, String pathLocator) {
        if (!hasBoundaryExpansionMarker(result)) {
            return result;
        }
        int expectedExit = expectedBoundaryExpansionExitCode();
        if (result.exitCode() == expectedExit) {
            return result;
        }
        String anomalyLine = "pr-check: INTERNAL_ERROR: " + PR_CHECK_EXIT_ENVELOPE_ANOMALY
            + ": marker=" + PR_CHECK_BOUNDARY_MARKER
            + "; observedExit=" + result.exitCode()
            + "; expectedExit=" + expectedExit;
        ArrayList<String> stderr = new ArrayList<>(result.stderrLines());
        stderr.add(anomalyLine);
        return new PrCheckResult(
            CliCodes.EXIT_INTERNAL,
            result.stdoutLines(),
            List.copyOf(stderr),
            "INTERNAL_ERROR",
            CliCodes.INTERNAL_ERROR,
            PR_CHECK_ENVELOPE_PATH,
            PR_CHECK_ENVELOPE_REMEDIATION,
            anomalyLine,
            result.deltaLines(),
            result.hasBoundary(),
            result.hasDeltas(),
            result.governanceLines(),
            result.problems(),
            null
        );
    }

    private static boolean hasBoundaryExpansionMarker(PrCheckResult result) {
        return containsBoundaryMarker(result.stderrLines()) || containsBoundaryMarker(result.stdoutLines());
    }

    private static boolean containsBoundaryMarker(List<String> lines) {
        for (String line : lines) {
            if (line != null && line.contains(PR_CHECK_BOUNDARY_MARKER)) {
                return true;
            }
        }
        return false;
    }

    private static int expectedBoundaryExpansionExitCode() {
        String raw = System.getProperty(PR_CHECK_BOUNDARY_EXIT_OVERRIDE_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return CliCodes.EXIT_BOUNDARY_EXPANSION;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return CliCodes.EXIT_BOUNDARY_EXPANSION;
        }
    }

    static PrCheckResult executePrCheck(Path projectRoot, String repoRelativePath, String baseRef) {
        return executePrCheck(projectRoot, repoRelativePath, baseRef, null, false);
    }

    static PrCheckResult executePrCheck(Path projectRoot, String repoRelativePath, String baseRef, Path explicitIndexPath) {
        return executePrCheck(projectRoot, repoRelativePath, baseRef, explicitIndexPath, false);
    }

    static PrCheckResult executePrCheck(
        Path projectRoot,
        String repoRelativePath,
        String baseRef,
        Path explicitIndexPath,
        boolean collectAll
    ) {
        return PrCheckCommandService.executePrCheck(projectRoot, repoRelativePath, baseRef, explicitIndexPath, collectAll);
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

    private static FixResult fixFailure(
        int exitCode,
        List<String> stderrLines,
        String category,
        String failureCode,
        String failurePath,
        String failureRemediation,
        String detail
    ) {
        return new FixResult(
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

    private static CompileResult compileFailure(
        int exitCode,
        List<String> stderrLines,
        String category,
        String failureCode,
        String failurePath,
        String failureRemediation,
        String detail
    ) {
        return new CompileResult(
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

    private static List<String> tailLines(String output) {
        return CliText.tailLines(output);
    }

    private static void printLines(PrintStream stream, List<String> lines) {
        CliText.printLines(stream, lines);
    }

    private static AllCheckOptions parseAllCheckOptions(String[] args, PrintStream err) {
        return AllModeOptionParser.parseAllCheckOptions(args, err);
    }

    private static AllFixOptions parseAllFixOptions(String[] args, PrintStream err) {
        return AllModeOptionParser.parseAllFixOptions(args, err);
    }

    private static AllPrCheckOptions parseAllPrCheckOptions(String[] args, PrintStream err) {
        return AllModeOptionParser.parseAllPrCheckOptions(args, err);
    }

    private static Path resolveBlocksPath(Path repoRoot, String blocksArg) {
        return AllModeOptionParser.resolveBlocksPath(repoRoot, blocksArg);
    }

    private static Set<String> parseOnlyNames(String onlyArg) {
        return AllModeOptionParser.parseOnlyNames(onlyArg);
    }

    static BlockExecutionResult skipBlock(BlockIndexEntry block, String reason) {
        return new BlockExecutionResult(
            block.name(),
            block.ir(),
            block.projectRoot(),
            BlockStatus.SKIP,
            CliCodes.EXIT_OK,
            null,
            null,
            null,
            null,
            null,
            reason,
            null,
            List.of()
        );
    }

    static BlockExecutionResult toCheckBlockResult(BlockIndexEntry block, CheckResult result) {
        if (result.exitCode() == CliCodes.EXIT_OK) {
            String detail = result.detail() == null || result.detail().isBlank() ? null : result.detail().trim();
            return new BlockExecutionResult(
                block.name(),
                block.ir(),
                block.projectRoot(),
                BlockStatus.PASS,
                CliCodes.EXIT_OK,
                null,
                null,
                null,
                detail,
                null,
                null,
                null,
                List.of()
            );
        }
        return new BlockExecutionResult(
            block.name(),
            block.ir(),
            block.projectRoot(),
            BlockStatus.FAIL,
            result.exitCode(),
            result.category(),
            result.failureCode(),
            normalizeLocator(result.failurePath()),
            squash(result.detail()),
            result.failureRemediation(),
            null,
            null,
            List.of(),
            List.of(),
            result.problems()
        );
    }

    static BlockExecutionResult toFixBlockResult(BlockIndexEntry block, FixResult result) {
        if (result.exitCode() == CliCodes.EXIT_OK) {
            return new BlockExecutionResult(
                block.name(),
                block.ir(),
                block.projectRoot(),
                BlockStatus.PASS,
                CliCodes.EXIT_OK,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
            );
        }
        return new BlockExecutionResult(
            block.name(),
            block.ir(),
            block.projectRoot(),
            BlockStatus.FAIL,
            result.exitCode(),
            result.category(),
            result.failureCode(),
            normalizeLocator(result.failurePath()),
            squash(result.detail()),
            result.failureRemediation(),
            null,
            null,
            List.of()
        );
    }

    static BlockExecutionResult toCompileBlockResult(BlockIndexEntry block, CompileResult result) {
        if (result.exitCode() == CliCodes.EXIT_OK) {
            return new BlockExecutionResult(
                block.name(),
                block.ir(),
                block.projectRoot(),
                BlockStatus.PASS,
                CliCodes.EXIT_OK,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
            );
        }
        return new BlockExecutionResult(
            block.name(),
            block.ir(),
            block.projectRoot(),
            BlockStatus.FAIL,
            result.exitCode(),
            result.category(),
            result.failureCode(),
            normalizeLocator(result.failurePath()),
            squash(result.detail()),
            result.failureRemediation(),
            null,
            null,
            List.of()
        );
    }

    static BlockExecutionResult rootFailure(
        BlockExecutionResult base,
        int exitCode,
        String category,
        String blockCode,
        String blockPath,
        String detail,
        String remediation
    ) {
        return rootFailure(base, exitCode, category, blockCode, blockPath, detail, remediation, null);
    }

    static BlockExecutionResult rootFailure(
        BlockExecutionResult base,
        int exitCode,
        String category,
        String blockCode,
        String blockPath,
        String detail,
        String remediation,
        String reasonKey
    ) {
        AgentDiagnostics.AgentCategory agentCategory = isGovernanceCode(blockCode)
            ? AgentDiagnostics.AgentCategory.GOVERNANCE
            : AgentDiagnostics.AgentCategory.INFRA;
        String normalizedBlockCode = blockCode == null ? CliCodes.REPO_MULTI_BLOCK_FAILED : blockCode;
        String normalizedReasonKey = reasonKey == null || reasonKey.isBlank() ? normalizedBlockCode : reasonKey;
        String normalizedPath = normalizeLocator(blockPath);
        Map<String, String> evidence = RepeatableRuleRegistry.requiresIdentityKey(normalizedBlockCode)
            ? Map.of("identityKey", nullToEmpty(normalizedPath) + "|" + nullToEmpty(normalizedBlockCode) + "|" + nullToEmpty(detail))
            : Map.of();
        AgentDiagnostics.AgentProblem problem = AgentDiagnostics.problem(
            agentCategory,
            normalizedBlockCode,
            agentCategory == AgentDiagnostics.AgentCategory.GOVERNANCE ? normalizedBlockCode : null,
            agentCategory == AgentDiagnostics.AgentCategory.INFRA ? normalizedReasonKey : null,
            AgentDiagnostics.AgentSeverity.ERROR,
            base.name(),
            normalizedPath,
            null,
            agentCategory == AgentDiagnostics.AgentCategory.GOVERNANCE ? normalizedBlockCode : normalizedReasonKey,
            detail == null ? "" : detail,
            evidence
        );
        return new BlockExecutionResult(
            base.name(),
            base.ir(),
            base.project(),
            BlockStatus.FAIL,
            exitCode,
            category,
            blockCode,
            normalizeLocator(blockPath),
            squash(detail),
            remediation,
            agentCategory == AgentDiagnostics.AgentCategory.INFRA ? normalizedReasonKey : null,
            base.classification(),
            base.deltaLines(),
            base.governanceLines(),
            List.of(problem)
        );
    }

    static String validateIndexIrNameMatch(Path irFile, String expectedBlockKey, String expectedBlockLocator) {
        try {
            BearIr normalized = IR_PIPELINE.parseValidateNormalize(irFile);
            BlockIdentityResolver.resolveIndexIdentity(
                expectedBlockKey,
                expectedBlockLocator == null || expectedBlockLocator.isBlank()
                    ? "bear.blocks.yaml:name=" + expectedBlockKey
                    : expectedBlockLocator,
                normalized.block().name()
            );
            return null;
        } catch (BlockIdentityResolutionException e) {
            return e.line();
        } catch (Exception e) {
            return "unable to validate block name mapping: " + squash(e.getMessage());
        }
    }

    static String indexLocator(BlockIndexEntry block) {
        return BlockIdentityResolver.formatIndexLocator(block);
    }

    static BlockExecutionResult toPrBlockResult(BlockIndexEntry block, PrCheckResult result) {
        if (result.exitCode() == CliCodes.EXIT_OK) {
            String classification = result.hasDeltas() ? "ORDINARY" : "NO_CHANGES";
            return new BlockExecutionResult(
                block.name(),
                block.ir(),
                block.projectRoot(),
                BlockStatus.PASS,
                CliCodes.EXIT_OK,
                null,
                null,
                null,
                null,
                null,
                null,
                classification,
                result.deltaLines(),
                result.governanceLines()
            );
        }
        String classification = result.exitCode() == CliCodes.EXIT_BOUNDARY_EXPANSION ? "BOUNDARY_EXPANDING" : null;
        return new BlockExecutionResult(
            block.name(),
            block.ir(),
            block.projectRoot(),
            BlockStatus.FAIL,
            result.exitCode(),
            result.category(),
            result.failureCode(),
            normalizeLocator(result.failurePath()),
            squash(result.detail()),
            result.failureRemediation(),
            null,
            classification,
            result.deltaLines(),
            result.governanceLines(),
            result.problems()
        );
    }

    private static RepoAggregationResult aggregateCheckResults(
        List<BlockExecutionResult> results,
        boolean failFastTriggered,
        int rootReachFailed,
        int rootTestFailed,
        int rootTestSkippedDueToReach
    ) {
        return AllModeAggregation.aggregateCheckResults(results, failFastTriggered, rootReachFailed, rootTestFailed, rootTestSkippedDueToReach);
    }

    private static RepoAggregationResult aggregatePrResults(List<BlockExecutionResult> results) {
        return AllModeAggregation.aggregatePrResults(results);
    }

    private static RepoAggregationResult aggregateFixResults(List<BlockExecutionResult> results, boolean failFastTriggered) {
        return AllModeAggregation.aggregateFixResults(results, failFastTriggered);
    }

    private static int severityRankCheck(int code) {
        return AllModeAggregation.severityRankCheck(code);
    }

    private static int severityRankPr(int code) {
        return AllModeAggregation.severityRankPr(code);
    }

    private static int severityRankFix(int code) {
        return AllModeAggregation.severityRankFix(code);
    }

    private static List<String> renderCheckAllOutput(List<BlockExecutionResult> results, RepoAggregationResult summary) {
        return AllModeRenderer.renderCheckAllOutput(results, summary);
    }

    private static List<String> renderPrAllOutput(List<BlockExecutionResult> results, RepoAggregationResult summary) {
        return AllModeRenderer.renderPrAllOutput(results, summary);
    }

    private static List<String> renderFixAllOutput(List<BlockExecutionResult> results, RepoAggregationResult summary) {
        return AllModeRenderer.renderFixAllOutput(results, summary);
    }

    private static List<DriftItem> computeDrift(
        Path baselineRoot,
        Path candidateRoot,
        java.util.function.Predicate<String> includePath
    ) throws IOException {
        return DriftAnalyzer.computeDrift(baselineRoot, candidateRoot, includePath);
    }

    static String squash(String text) {
        return CliText.squash(text);
    }

    static void writeCheckBlockedMarker(Path projectRoot, String reason, String detail) throws IOException {
        Path marker = projectRoot.resolve(CheckBlockedMarker.RELATIVE_PATH);
        Files.createDirectories(marker.getParent());
        String safeReason = (reason == null || reason.isBlank()) ? "UNKNOWN" : reason;
        String safeDetail = (detail == null || detail.isBlank()) ? "no details" : detail.trim();
        String content = "reason=" + safeReason + "\n" + "detail=" + safeDetail + "\n";
        Files.writeString(marker, content, StandardCharsets.UTF_8);
    }

    static void clearCheckBlockedMarker(Path projectRoot) throws IOException {
        Path marker = projectRoot.resolve(CheckBlockedMarker.RELATIVE_PATH);
        Files.deleteIfExists(marker);
    }

    static String markerAttributes(Path marker) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(marker, BasicFileAttributes.class);
            boolean readonly = !Files.isWritable(marker);
            boolean hidden;
            try {
                hidden = Files.isHidden(marker);
            } catch (IOException ignored) {
                hidden = false;
            }
            return "readonly=" + readonly + ",hidden=" + hidden + ",size=" + attrs.size();
        } catch (IOException e) {
            return "unavailable";
        }
    }

    static void maybeInjectUnblockDeleteFailureForTest(int attempt) throws IOException {
        String raw = System.getProperty("bear.cli.test.unblock.failDeletes");
        if (raw == null || raw.isBlank()) {
            return;
        }
        String normalized = raw.trim().toLowerCase();
        if ("always".equals(normalized)) {
            throw new IOException("INJECTED_UNBLOCK_DELETE_FAILURE");
        }
        try {
            int count = Integer.parseInt(normalized);
            if (attempt <= count) {
                throw new IOException("INJECTED_UNBLOCK_DELETE_FAILURE");
            }
        } catch (NumberFormatException ignored) {
            // Ignore invalid test hook values.
        }
    }

    private static String normalizeLf(String text) {
        return CliText.normalizeLf(text);
    }

    static void printUsage(PrintStream out) {
        out.println("Usage: bear validate <file>");
        out.println("       bear compile <ir-file> --project <path> [--index <path>]");
        out.println("       bear compile --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]");
        out.println("       bear fix <ir-file> --project <path> [--index <path>]");
        out.println("       bear fix --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]");
        out.println("       bear check <ir-file> --project <path> [--strict-hygiene] [--index <path>] [--collect=all] [--agent]");
        out.println("       bear check --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans] [--strict-hygiene] [--collect=all] [--agent]");
        out.println("       bear unblock --project <path>");
        out.println("       bear pr-check <ir-file> --project <path> --base <ref> [--index <path>] [--collect=all] [--agent]");
        out.println("       bear pr-check --all --project <repoRoot> --base <ref> [--blocks <path>] [--only <csv>] [--strict-orphans] [--collect=all] [--agent]");
        out.println("       bear --help");
    }

    static void maybeFailInternalForTest(String command) {
        String key = "bear.cli.test.failInternal." + command;
        if ("true".equals(System.getProperty(key))) {
            throw new IllegalStateException("INJECTED_INTERNAL_" + command);
        }
    }

    static int failWithLegacy(
        PrintStream err,
        int exitCode,
        String legacyLine,
        String code,
        String pathLocator,
        String remediation
    ) {
        return FailureEnvelopeEmitter.failWithLegacy(err, exitCode, legacyLine, code, pathLocator, remediation);
    }

    static int fail(PrintStream err, int exitCode, String code, String pathLocator, String remediation) {
        return FailureEnvelopeEmitter.fail(err, exitCode, code, pathLocator, remediation);
    }

    private static boolean isGovernanceCode(String code) {
        if (code == null) {
            return false;
        }
        return code.equals(CliCodes.BOUNDARY_BYPASS)
            || code.equals(CliCodes.BOUNDARY_EXPANSION)
            || code.equals(CliCodes.UNDECLARED_REACH)
            || code.equals(CliCodes.REFLECTION_DISPATCH_FORBIDDEN)
            || code.equals(CliCodes.HYGIENE_UNEXPECTED_PATHS)
            || code.equals(CliCodes.PORT_IMPL_OUTSIDE_GOVERNED_ROOT)
            || code.equals(CliCodes.MULTI_BLOCK_PORT_IMPL_FORBIDDEN)
            || GovernanceRuleRegistry.PUBLIC_RULE_IDS.contains(code);
    }
    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private static String normalizeLocator(String raw) {
        return FailureEnvelopeEmitter.normalizeLocator(raw);
    }
}










