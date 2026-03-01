package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.target.JvmTarget;

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
        try {
            maybeFailInternalForTest("fix");
            JvmTarget target = new JvmTarget();
            BearIr normalized = IR_PIPELINE.parseValidateNormalize(irFile);
            BlockIdentityResolution identity = expectedBlockKey == null
                ? BlockIdentityResolver.resolveSingleCommandIdentity(irFile, projectRoot, normalized.block().name())
                : BlockIdentityResolver.resolveIndexIdentity(
                    expectedBlockKey,
                    expectedBlockLocator == null || expectedBlockLocator.isBlank()
                        ? "bear.blocks.yaml:name=" + expectedBlockKey
                        : expectedBlockLocator,
                    normalized.block().name()
                );

            target.compile(normalized, projectRoot, identity.blockKey());
            return new FixResult(CliCodes.EXIT_OK, List.of("fix: OK"), List.of(), null, null, null, null, null);
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
        try {
            maybeFailInternalForTest("compile");
            JvmTarget target = new JvmTarget();
            BearIr normalized = IR_PIPELINE.parseValidateNormalize(irFile);
            BlockIdentityResolution identity = expectedBlockKey == null
                ? BlockIdentityResolver.resolveSingleCommandIdentity(irFile, projectRoot, normalized.block().name())
                : BlockIdentityResolver.resolveIndexIdentity(
                    expectedBlockKey,
                    expectedBlockLocator == null || expectedBlockLocator.isBlank()
                        ? "bear.blocks.yaml:name=" + expectedBlockKey
                        : expectedBlockLocator,
                    normalized.block().name()
                );
            target.compile(normalized, projectRoot, identity.blockKey());
            return new CompileResult(CliCodes.EXIT_OK, List.of("compiled: OK"), List.of(), null, null, null, null, null);
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
        return CheckCommandService.executeCheck(
            irFile,
            projectRoot,
            runReachAndTests,
            strictHygiene,
            expectedBlockKey,
            expectedBlockLocator
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
            result.governanceLines()
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
        return PrCheckCommandService.executePrCheck(projectRoot, repoRelativePath, baseRef);
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

    static List<BlockIndexEntry> selectBlocks(BlockIndex index, Set<String> onlyNames) {
        List<BlockIndexEntry> sorted = new ArrayList<>(index.blocks());
        sorted.sort(Comparator.comparing(BlockIndexEntry::name));
        if (onlyNames == null || onlyNames.isEmpty()) {
            return sorted;
        }
        Set<String> known = new HashSet<>();
        for (BlockIndexEntry entry : sorted) {
            known.add(entry.name());
        }
        for (String name : onlyNames) {
            if (!known.contains(name)) {
                return null;
            }
        }
        List<BlockIndexEntry> selected = new ArrayList<>();
        for (BlockIndexEntry entry : sorted) {
            if (onlyNames.contains(entry.name())) {
                selected.add(entry);
            }
        }
        return selected;
    }

    static List<String> computeOrphanMarkersRepoWide(Path repoRoot, BlockIndex index) throws IOException {
        Set<String> expected = new HashSet<>();
        for (BlockIndexEntry entry : index.blocks()) {
            if (!entry.enabled()) {
                continue;
            }
            expected.add(Path.of(entry.projectRoot())
                .resolve("build")
                .resolve("generated")
                .resolve("bear")
                .resolve("surfaces")
                .resolve(entry.name() + ".surface.json")
                .normalize()
                .toString()
                .replace('\\', '/'));
        }

        List<String> found = new ArrayList<>();
        try (var stream = Files.walk(repoRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String rel = repoRoot.relativize(path).toString().replace('\\', '/');
                if (rel.contains("build/generated/bear/surfaces/") && rel.endsWith(".surface.json")) {
                    found.add(rel);
                }
            });
        }
        found.sort(String::compareTo);
        List<String> orphan = new ArrayList<>();
        for (String marker : found) {
            if (!expected.contains(marker)) {
                orphan.add(marker);
            }
        }
        return orphan;
    }

    static List<String> computeOrphanMarkersInManagedRoots(Path repoRoot, List<BlockIndexEntry> selected) throws IOException {
        Map<String, Set<String>> expectedByRoot = new TreeMap<>();
        for (BlockIndexEntry entry : selected) {
            if (!entry.enabled()) {
                continue;
            }
            expectedByRoot.computeIfAbsent(entry.projectRoot(), ignored -> new HashSet<>())
                .add(entry.name() + ".surface.json");
        }

        List<String> orphan = new ArrayList<>();
        for (Map.Entry<String, Set<String>> rootEntry : expectedByRoot.entrySet()) {
            Path surfacesDir = repoRoot.resolve(rootEntry.getKey())
                .resolve("build")
                .resolve("generated")
                .resolve("bear")
                .resolve("surfaces");
            if (!Files.isDirectory(surfacesDir)) {
                continue;
            }
            try (var stream = Files.list(surfacesDir)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    String fileName = path.getFileName().toString();
                    if (!fileName.endsWith(".surface.json")) {
                        return;
                    }
                    if (!rootEntry.getValue().contains(fileName)) {
                        String rel = repoRoot.relativize(path).toString().replace('\\', '/');
                        orphan.add(rel);
                    }
                });
            }
        }
        orphan.sort(String::compareTo);
        return orphan;
    }

    static List<String> computeLegacyMarkersRepoWide(Path repoRoot) throws IOException {
        List<String> legacy = new ArrayList<>();
        try (var stream = Files.walk(repoRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String rel = repoRoot.relativize(path).toString().replace('\\', '/');
                if (rel.endsWith("build/generated/bear/bear.surface.json")) {
                    legacy.add(rel);
                }
            });
        }
        legacy.sort(String::compareTo);
        return legacy;
    }

    static List<String> computeLegacyMarkersInManagedRoots(Path repoRoot, List<BlockIndexEntry> selected) {
        Set<String> managedRoots = new HashSet<>();
        for (BlockIndexEntry entry : selected) {
            if (entry.enabled()) {
                managedRoots.add(entry.projectRoot());
            }
        }
        List<String> legacy = new ArrayList<>();
        for (String root : managedRoots) {
            Path marker = repoRoot.resolve(root)
                .resolve("build")
                .resolve("generated")
                .resolve("bear")
                .resolve("bear.surface.json");
            if (Files.isRegularFile(marker)) {
                legacy.add(repoRoot.relativize(marker).toString().replace('\\', '/'));
            }
        }
        legacy.sort(String::compareTo);
        return legacy;
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
            List.of()
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
            null,
            base.classification(),
            base.deltaLines(),
            base.governanceLines()
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
            result.governanceLines()
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

    private static List<BoundarySignal> computeBoundarySignals(BoundaryManifest baseline, BoundaryManifest candidate) {
        return PrDeltaClassifier.computeBoundarySignals(baseline, candidate);
    }

    private static List<PrDelta> computePrDeltas(BearIr baseIr, BearIr headIr) {
        return PrDeltaClassifier.computePrDeltas(baseIr, headIr);
    }

    private static void addAllowedDepDeltas(List<PrDelta> deltas, Map<String, String> base, Map<String, String> head) {
        PrDeltaClassifier.addAllowedDepDeltas(deltas, base, head);
    }

    private static void addIdempotencyDeltas(
        List<PrDelta> deltas,
        BearIr.Idempotency base,
        BearIr.Idempotency head
    ) {
        PrDeltaClassifier.addIdempotencyDeltas(deltas, base, head);
    }

    private static void addContractDeltas(
        List<PrDelta> deltas,
        Map<String, BearIr.FieldType> base,
        Map<String, BearIr.FieldType> head,
        boolean input
    ) {
        PrDeltaClassifier.addContractDeltas(deltas, base, head, input);
    }

    private static String typeToken(BearIr.FieldType type) {
        return PrDeltaClassifier.typeToken(type);
    }

    private static PrSurface toPrSurface(BearIr ir) {
        return PrDeltaClassifier.toPrSurface(ir);
    }

    private static PrSurface emptyPrSurface() {
        return PrDeltaClassifier.emptyPrSurface();
    }

    static String squash(String text) {
        return CliText.squash(text);
    }

    private static BoundaryManifest parseManifest(Path path) throws IOException, ManifestParseException {
        return ManifestParsers.parseManifest(path);
    }

    private static WiringManifest parseWiringManifest(Path path) throws IOException, ManifestParseException {
        return ManifestParsers.parseWiringManifest(path);
    }

    private static String extractRequiredString(String json, String key) throws ManifestParseException {
        return ManifestParsers.extractRequiredString(json, key);
    }

    private static String extractRequiredArrayPayload(String json, String key) throws ManifestParseException {
        return ManifestParsers.extractRequiredArrayPayload(json, key);
    }

    private static String extractOptionalArrayPayload(String json, String key) throws ManifestParseException {
        return ManifestParsers.extractOptionalArrayPayload(json, key);
    }

    private static Map<String, TreeSet<String>> parseCapabilities(String payload) throws ManifestParseException {
        return ManifestParsers.parseCapabilities(payload);
    }

    private static TreeSet<String> parseInvariants(String payload) throws ManifestParseException {
        return ManifestParsers.parseInvariants(payload);
    }

    private static Map<String, String> parseAllowedDeps(String payload) throws ManifestParseException {
        return ManifestParsers.parseAllowedDeps(payload);
    }

    private static List<String> parseStringArray(String payload) throws ManifestParseException {
        return ManifestParsers.parseStringArray(payload);
    }

    private static String jsonUnescape(String value) {
        return ManifestParsers.jsonUnescape(value);
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
        String content = normalizeLf(Files.readString(markerFile, StandardCharsets.UTF_8));
        for (String line : content.lines().toList()) {
            if (line.startsWith("hash=")) {
                String hash = line.substring("hash=".length()).trim();
                return hash.isEmpty() ? null : hash;
            }
        }
        return null;
    }

    private static Map<String, byte[]> readRegularFiles(Path root) throws IOException {
        return DriftAnalyzer.readRegularFiles(root);
    }

    private static List<UndeclaredReachFinding> scanUndeclaredReach(Path projectRoot)
        throws IOException, PolicyValidationException {
        return UndeclaredReachScanner.scanUndeclaredReach(projectRoot);
    }

    private static boolean isUndeclaredReachExcluded(String relPath) {
        return UndeclaredReachScanner.isUndeclaredReachExcluded(relPath);
    }

    private static List<BoundaryBypassFinding> scanBoundaryBypass(Path projectRoot, List<WiringManifest> manifests)
        throws IOException, ManifestParseException, PolicyValidationException {
        return BoundaryBypassScanner.scanBoundaryBypass(projectRoot, manifests);
    }

    private static boolean isBoundaryScanExcluded(String relPath) {
        return BoundaryBypassScanner.isBoundaryScanExcluded(relPath);
    }

    private static String firstDirectImplUsageToken(String source) {
        return BoundaryBypassScanner.firstDirectImplUsageToken(source);
    }

    private static String firstTopLevelNullPortWiringToken(
        String source,
        Set<String> governedEntrypointFqcns,
        Map<String, Integer> governedSimpleNameCounts
    ) {
        return BoundaryBypassScanner.firstTopLevelNullPortWiringToken(source, governedEntrypointFqcns, governedSimpleNameCounts);
    }

    private static boolean isGovernedEntrypointType(
        String typeName,
        Set<String> governedEntrypointFqcns,
        Map<String, Integer> governedSimpleNameCounts
    ) {
        return BoundaryBypassScanner.isGovernedEntrypointType(typeName, governedEntrypointFqcns, governedSimpleNameCounts);
    }

    private static String simpleName(String fqcn) {
        return BoundaryBypassScanner.simpleName(fqcn);
    }

    private static List<String> parseTopLevelArguments(String source, int openParenIndex) {
        return BoundaryBypassScanner.parseTopLevelArguments(source, openParenIndex);
    }

    private static Set<String> parsePortSuppressions(String source) {
        return BoundaryBypassScanner.parsePortSuppressions(source);
    }

    private static boolean referencesPortAsReceiver(String source, String portParam) {
        return BoundaryBypassScanner.referencesPortAsReceiver(source, portParam);
    }

    private static boolean passesPortAsInvocationArgument(String source, String portParam) {
        return BoundaryBypassScanner.passesPortAsInvocationArgument(source, portParam);
    }

    private static String normalizeToken(String token) {
        return BoundaryBypassScanner.normalizeToken(token);
    }

    private static String stripJavaCommentsStringsAndChars(String source) {
        return BoundaryBypassScanner.stripJavaCommentsStringsAndChars(source);
    }

    static CheckBlockedState readCheckBlockedState(Path projectRoot) {
        Path marker = projectRoot.resolve(CheckBlockedMarker.RELATIVE_PATH);
        if (!Files.isRegularFile(marker)) {
            return CheckBlockedState.notBlocked();
        }
        try {
            String content = Files.readString(marker, StandardCharsets.UTF_8);
            String reason = null;
            String detail = null;
            for (String rawLine : normalizeLf(content).lines().toList()) {
                String line = rawLine.trim();
                if (line.startsWith("reason=")) {
                    reason = line.substring("reason=".length()).trim();
                } else if (line.startsWith("detail=")) {
                    detail = line.substring("detail=".length()).trim();
                }
            }
            if (reason == null || reason.isBlank()) {
                reason = "UNKNOWN";
            }
            if (detail == null || detail.isBlank()) {
                detail = "no details";
            }
            return new CheckBlockedState(true, reason, detail);
        } catch (IOException e) {
            return new CheckBlockedState(true, "UNKNOWN", squash(e.getMessage()));
        }
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

    private static boolean hasOwnedBaselineFiles(Path baselineRoot, Set<String> ownedPrefixes, String markerRelPath) throws IOException {
        return DriftAnalyzer.hasOwnedBaselineFiles(baselineRoot, ownedPrefixes, markerRelPath);
    }

    private static boolean startsWithAny(String value, Set<String> prefixes) {
        return DriftAnalyzer.startsWithAny(value, prefixes);
    }

    private static ProjectTestResult runProjectTests(Path projectRoot) throws IOException, InterruptedException {
        return ProjectTestRunner.runProjectTests(projectRoot);
    }

    private static boolean isGradleWrapperLockOutput(String output) {
        return ProjectTestRunner.isGradleWrapperLockOutput(output);
    }

    private static boolean isGradleWrapperBootstrapIoOutput(String output) {
        return ProjectTestRunner.isGradleWrapperBootstrapIoOutput(output);
    }

    private static String firstGradleLockLine(String output) {
        return ProjectTestRunner.firstGradleLockLine(output);
    }

    private static String firstGradleBootstrapIoLine(String output) {
        return ProjectTestRunner.firstGradleBootstrapIoLine(output);
    }

    private static String firstRelevantProjectTestFailureLine(String output) {
        return ProjectTestRunner.firstRelevantProjectTestFailureLine(output);
    }

    private static String shortTailSummary(String output, int maxLines) {
        return CliText.shortTailSummary(output, maxLines);
    }

    private static String projectTestDetail(String base, String firstLine, String tail) {
        return ProjectTestRunner.projectTestDetail(base, firstLine, tail);
    }

    private static String attemptTrailSuffix(String attemptTrail) {
        if (attemptTrail == null || attemptTrail.isBlank()) {
            return "";
        }
        return "; attempts=" + attemptTrail.trim();
    }

    private static String markerWriteFailureSuffix(IOException error) {
        return "; markerWrite=failed:" + squash(error.getMessage());
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

    private static Path resolveWrapper(Path projectRoot) throws IOException {
        return ProjectTestRunner.resolveWrapper(projectRoot);
    }

    private static boolean isWindows() {
        return ProjectTestRunner.isWindows();
    }

    private static int testTimeoutSeconds() {
        return ProjectTestRunner.testTimeoutSeconds();
    }

    private static void printTail(PrintStream err, String output) {
        List<String> lines = normalizeLf(output).lines().toList();
        int start = Math.max(0, lines.size() - 40);
        for (int i = start; i < lines.size(); i++) {
            err.println(lines.get(i));
        }
    }

    private static String normalizeLf(String text) {
        return CliText.normalizeLf(text);
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

    private static boolean hasRegularFiles(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream.anyMatch(Files::isRegularFile);
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

    static void printUsage(PrintStream out) {
        out.println("Usage: bear validate <file>");
        out.println("       bear compile <ir-file> --project <path>");
        out.println("       bear compile --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]");
        out.println("       bear fix <ir-file> --project <path>");
        out.println("       bear fix --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]");
        out.println("       bear check <ir-file> --project <path> [--strict-hygiene]");
        out.println("       bear check --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans] [--strict-hygiene]");
        out.println("       bear unblock --project <path>");
        out.println("       bear pr-check <ir-file> --project <path> --base <ref>");
        out.println("       bear pr-check --all --project <repoRoot> --base <ref> [--blocks <path>] [--only <csv>] [--strict-orphans]");
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

    private static String normalizeLocator(String raw) {
        return FailureEnvelopeEmitter.normalizeLocator(raw);
    }
}



