package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrNormalizer;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.ir.BearIrValidator;
import com.bear.kernel.ir.BearIrYamlEmitter;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BearCli {
    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "record", "sealed", "permits", "var", "yield", "non-sealed"
    );

    private BearCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        System.exit(exitCode);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        String command = args.length == 0 ? "help" : args[0];
        return switch (command) {
            case "help", "-h", "--help" -> {
                printUsage(out);
                yield ExitCode.OK;
            }
            case "validate" -> runValidate(args, out, err);
            case "compile" -> runCompile(args, out, err);
            case "fix" -> runFix(args, out, err);
            case "check" -> runCheck(args, out, err);
            case "pr-check" -> runPrCheck(args, out, err);
            default -> failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: UNKNOWN_COMMAND: unknown command: " + command,
                FailureCode.USAGE_UNKNOWN_COMMAND,
                "cli.command",
                "Run `bear --help` and use a supported command."
            );
        };
    }

    private static int runValidate(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }

        if (args.length != 2) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear validate <file>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear validate <file>` with exactly one IR file path."
            );
        }

        Path file = Path.of(args[1]);
        try {
            maybeFailInternalForTest("validate");
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            BearIrYamlEmitter emitter = new BearIrYamlEmitter();

            BearIr ir = parser.parse(file);
            validator.validate(ir);
            BearIr normalized = normalizer.normalize(ir);

            out.print(emitter.toCanonicalYaml(normalized));
            return ExitCode.OK;
        } catch (BearIrValidationException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                e.formatLine(),
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear validate <file>`."
            );
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "io: IO_ERROR: " + file,
                FailureCode.IO_ERROR,
                "input.ir",
                "Ensure the IR file exists and is readable, then rerun `bear validate <file>`."
            );
        } catch (Exception e) {
            return failWithLegacy(
                err,
                ExitCode.INTERNAL,
                "internal: INTERNAL_ERROR:",
                FailureCode.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli."
            );
        }
    }

    private static int runCompile(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }

        if (args.length != 4 || !"--project".equals(args[2])) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear compile <ir-file> --project <path>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear compile <ir-file> --project <path>` with the expected arguments."
            );
        }

        Path irFile = Path.of(args[1]);
        Path projectRoot = Path.of(args[3]);

        try {
            maybeFailInternalForTest("compile");
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            JvmTarget target = new JvmTarget();

            BearIr ir = parser.parse(irFile);
            validator.validate(ir);
            BearIr normalized = normalizer.normalize(ir);
            target.compile(normalized, projectRoot);

            out.println("compiled: OK");
            return ExitCode.OK;
        } catch (BearIrValidationException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                e.formatLine(),
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear compile <ir-file> --project <path>`."
            );
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "io: IO_ERROR: " + e.getMessage(),
                FailureCode.IO_ERROR,
                "project.root",
                "Ensure the IR/project paths are readable and writable, then rerun `bear compile`."
            );
        } catch (Exception e) {
            return failWithLegacy(
                err,
                ExitCode.INTERNAL,
                "internal: INTERNAL_ERROR:",
                FailureCode.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli."
            );
        }
    }

    private static int runFix(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }

        if (args.length >= 2 && "--all".equals(args[1])) {
            return runFixAll(args, out, err);
        }

        if (args.length != 4 || !"--project".equals(args[2])) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear fix <ir-file> --project <path>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear fix <ir-file> --project <path>` with the expected arguments."
            );
        }

        Path irFile = Path.of(args[1]);
        Path projectRoot = Path.of(args[3]);
        FixResult result = executeFix(irFile, projectRoot, null);
        return emitFixResult(result, out, err);
    }

    private static int runFixAll(String[] args, PrintStream out, PrintStream err) {
        AllFixOptions options = parseAllFixOptions(args, err);
        if (options == null) {
            return ExitCode.USAGE;
        }

        BlockIndex index;
        try {
            index = new BlockIndexParser().parse(options.repoRoot(), options.blocksPath());
        } catch (BlockIndexValidationException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                "index: VALIDATION_ERROR: " + e.getMessage(),
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear fix --all`."
            );
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "io: IO_ERROR: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure `bear.blocks.yaml` is readable and rerun `bear fix --all`."
            );
        }

        List<BlockIndexEntry> selected = selectBlocks(index, options.onlyNames());
        if (selected == null) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: unknown block in --only",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Use only block names declared in `bear.blocks.yaml`."
            );
        }

        try {
            List<String> legacyMarkers = options.strictOrphans()
                ? computeLegacyMarkersRepoWide(options.repoRoot())
                : computeLegacyMarkersInManagedRoots(options.repoRoot(), selected);
            if (!legacyMarkers.isEmpty()) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "fix: IO_ERROR: LEGACY_SURFACE_MARKER: " + legacyMarkers.get(0),
                    FailureCode.IO_ERROR,
                    legacyMarkers.get(0),
                    "Delete legacy marker paths and recompile managed blocks, then rerun `bear fix --all`."
                );
            }

            List<String> orphanMarkers = options.strictOrphans()
                ? computeOrphanMarkersRepoWide(options.repoRoot(), index)
                : computeOrphanMarkersInManagedRoots(options.repoRoot(), selected);
            if (!orphanMarkers.isEmpty()) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "fix: IO_ERROR: ORPHAN_MARKER: " + orphanMarkers.get(0),
                    FailureCode.IO_ERROR,
                    orphanMarkers.get(0),
                    "Add missing block entries to `bear.blocks.yaml` or remove stale generated BEAR artifacts."
                );
            }
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "fix: IO_ERROR: ORPHAN_SCAN_FAILED: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure repo paths are readable and rerun `bear fix --all`."
            );
        }

        List<BlockExecutionResult> blockResults = new ArrayList<>();
        boolean failed = false;
        boolean failFastTriggered = false;
        for (BlockIndexEntry block : selected) {
            if (!block.enabled()) {
                blockResults.add(skipBlock(block, "DISABLED"));
                continue;
            }
            if (options.failFast() && failed) {
                failFastTriggered = true;
                blockResults.add(skipBlock(block, "FAIL_FAST_ABORT"));
                continue;
            }

            FixResult fixResult = executeFix(
                options.repoRoot().resolve(block.ir()).normalize(),
                options.repoRoot().resolve(block.projectRoot()).normalize(),
                block.name()
            );
            BlockExecutionResult blockResult = toFixBlockResult(block, fixResult);
            blockResults.add(blockResult);
            if (blockResult.status() == BlockStatus.FAIL) {
                failed = true;
            }
        }

        RepoAggregationResult summary = aggregateFixResults(blockResults, failFastTriggered);
        List<String> lines = renderFixAllOutput(blockResults, summary);
        if (summary.exitCode() == ExitCode.OK) {
            printLines(out, lines);
            return ExitCode.OK;
        }
        printLines(err, lines);
        return fail(
            err,
            summary.exitCode(),
            FailureCode.REPO_MULTI_BLOCK_FAILED,
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    private static int runCheck(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }

        if (args.length >= 2 && "--all".equals(args[1])) {
            return runCheckAll(args, out, err);
        }

        if (args.length != 4 || !"--project".equals(args[2])) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear check <ir-file> --project <path>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear check <ir-file> --project <path>` with the expected arguments."
            );
        }

        Path irFile = Path.of(args[1]);
        Path projectRoot = Path.of(args[3]);
        CheckResult result = executeCheck(irFile, projectRoot, true, null);
        return emitCheckResult(result, out, err);
    }

    private static int runPrCheck(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }
        if (args.length >= 2 && "--all".equals(args[1])) {
            return runPrCheckAll(args, out, err);
        }
        if (args.length != 6 || !"--project".equals(args[2]) || !"--base".equals(args[4])) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear pr-check <ir-file> --project <path> --base <ref>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear pr-check <ir-file> --project <path> --base <ref>` with the expected arguments."
            );
        }

        String irArg = args[1];
        Path irPath = Path.of(irArg);
        if (irPath.isAbsolute()) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: ir-file must be repo-relative",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative `ir-file` path for `bear pr-check`."
            );
        }
        Path normalizedRelative = irPath.normalize();
        if (normalizedRelative.startsWith("..") || normalizedRelative.toString().isBlank()) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: ir-file must be repo-relative",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative `ir-file` path for `bear pr-check`."
            );
        }

        Path projectRoot = Path.of(args[3]).toAbsolutePath().normalize();
        String baseRef = args[5];
        String repoRelativePath = normalizedRelative.toString().replace('\\', '/');
        Path headIrPath = projectRoot.resolve(repoRelativePath).normalize();
        if (!headIrPath.startsWith(projectRoot)) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: ir-file must be repo-relative",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative `ir-file` path for `bear pr-check`."
            );
        }
        PrCheckResult result = executePrCheck(projectRoot, repoRelativePath, baseRef);
        return emitPrCheckResult(result, out, err);
    }

    private static int runCheckAll(String[] args, PrintStream out, PrintStream err) {
        AllCheckOptions options = parseAllCheckOptions(args, err);
        if (options == null) {
            return ExitCode.USAGE;
        }

        BlockIndex index;
        try {
            index = new BlockIndexParser().parse(options.repoRoot(), options.blocksPath());
        } catch (BlockIndexValidationException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                "index: VALIDATION_ERROR: " + e.getMessage(),
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear check --all`."
            );
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "io: IO_ERROR: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure `bear.blocks.yaml` is readable and rerun `bear check --all`."
            );
        }

        List<BlockIndexEntry> selected = selectBlocks(index, options.onlyNames());
        if (selected == null) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: unknown block in --only",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Use only block names declared in `bear.blocks.yaml`."
            );
        }

        try {
            List<String> legacyMarkers = options.strictOrphans()
                ? computeLegacyMarkersRepoWide(options.repoRoot())
                : computeLegacyMarkersInManagedRoots(options.repoRoot(), selected);
            if (!legacyMarkers.isEmpty()) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "check: IO_ERROR: LEGACY_SURFACE_MARKER: " + legacyMarkers.get(0),
                    FailureCode.IO_ERROR,
                    legacyMarkers.get(0),
                    "Delete legacy marker paths and recompile managed blocks, then rerun `bear check --all`."
                );
            }

            List<String> orphanMarkers = options.strictOrphans()
                ? computeOrphanMarkersRepoWide(options.repoRoot(), index)
                : computeOrphanMarkersInManagedRoots(options.repoRoot(), selected);
            if (!orphanMarkers.isEmpty()) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "check: IO_ERROR: ORPHAN_MARKER: " + orphanMarkers.get(0),
                    FailureCode.IO_ERROR,
                    orphanMarkers.get(0),
                    "Add missing block entries to `bear.blocks.yaml` or remove stale generated BEAR artifacts."
                );
            }
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "check: IO_ERROR: ORPHAN_SCAN_FAILED: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure repo paths are readable and rerun `bear check --all`."
            );
        }

        List<BlockExecutionResult> blockResults = new ArrayList<>();
        boolean failed = false;
        boolean failFastTriggered = false;
        for (BlockIndexEntry block : selected) {
            if (!block.enabled()) {
                blockResults.add(skipBlock(block, "DISABLED"));
                continue;
            }
            if (options.failFast() && failed) {
                failFastTriggered = true;
                blockResults.add(skipBlock(block, "FAIL_FAST_ABORT"));
                continue;
            }

            CheckResult checkResult = executeCheck(
                options.repoRoot().resolve(block.ir()).normalize(),
                options.repoRoot().resolve(block.projectRoot()).normalize(),
                false,
                block.name()
            );
            BlockExecutionResult blockResult = toCheckBlockResult(block, checkResult);
            blockResults.add(blockResult);
            if (blockResult.status() == BlockStatus.FAIL) {
                failed = true;
            }
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
                List<UndeclaredReachFinding> undeclaredReach = scanUndeclaredReach(root);
                if (!undeclaredReach.isEmpty()) {
                    rootReachFailed++;
                    rootTestSkippedDueToReach++;
                    String locator = undeclaredReach.get(0).path();
                    String detail = "root-level undeclared reach in projectRoot " + entry.getKey();
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, rootFailure(
                            blockResults.get(idx),
                            ExitCode.UNDECLARED_REACH,
                            "UNDECLARED_REACH",
                            FailureCode.UNDECLARED_REACH,
                            locator,
                            detail,
                            "Declare a port/op in IR, run bear compile, and route call through generated port interface."
                        ));
                    }
                    continue;
                }

                ProjectTestResult testResult = runProjectTests(root);
                if (testResult.status == ProjectTestStatus.LOCKED) {
                    String detail = "root-level project test runner lock in projectRoot " + entry.getKey();
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, rootFailure(
                            blockResults.get(idx),
                            ExitCode.IO,
                            "IO_ERROR",
                            FailureCode.IO_ERROR,
                            "project.tests",
                            detail,
                            "Release Gradle wrapper lock or set isolated GRADLE_USER_HOME, then rerun `bear check --all`."
                        ));
                    }
                } else if (testResult.status == ProjectTestStatus.FAILED) {
                    rootTestFailed++;
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, rootFailure(
                            blockResults.get(idx),
                            ExitCode.TEST_FAILURE,
                            "TEST_FAILURE",
                            FailureCode.TEST_FAILURE,
                            "project.tests",
                            "root-level project tests failed for projectRoot " + entry.getKey(),
                            "Fix project tests and rerun `bear check --all`."
                        ));
                    }
                } else if (testResult.status == ProjectTestStatus.TIMEOUT) {
                    rootTestFailed++;
                    for (int idx : entry.getValue()) {
                        blockResults.set(idx, rootFailure(
                            blockResults.get(idx),
                            ExitCode.TEST_FAILURE,
                            "TEST_FAILURE",
                            FailureCode.TEST_TIMEOUT,
                            "project.tests",
                            "root-level project tests timed out for projectRoot " + entry.getKey(),
                            "Reduce test runtime or increase timeout, then rerun `bear check --all`."
                        ));
                    }
                }
            } catch (IOException e) {
                for (int idx : entry.getValue()) {
                    blockResults.set(idx, rootFailure(
                        blockResults.get(idx),
                        ExitCode.IO,
                        "IO_ERROR",
                        FailureCode.IO_ERROR,
                        "project.root",
                        "io: IO_ERROR: " + squash(e.getMessage()),
                        "Ensure project paths are accessible (including Gradle wrapper), then rerun `bear check --all`."
                    ));
                }
            } catch (InterruptedException e) {
                for (int idx : entry.getValue()) {
                    blockResults.set(idx, rootFailure(
                        blockResults.get(idx),
                        ExitCode.INTERNAL,
                        "INTERNAL_ERROR",
                        FailureCode.INTERNAL_ERROR,
                        "internal",
                        "internal: INTERNAL_ERROR:",
                        "Capture stderr and file an issue against bear-cli."
                    ));
                }
            }
        }

        RepoAggregationResult summary = aggregateCheckResults(
            blockResults,
            failFastTriggered,
            rootReachFailed,
            rootTestFailed,
            rootTestSkippedDueToReach
        );
        List<String> lines = renderCheckAllOutput(blockResults, summary);
        if (summary.exitCode() == ExitCode.OK) {
            printLines(out, lines);
            return ExitCode.OK;
        }
        printLines(err, lines);
        return fail(
            err,
            summary.exitCode(),
            FailureCode.REPO_MULTI_BLOCK_FAILED,
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    private static int runPrCheckAll(String[] args, PrintStream out, PrintStream err) {
        AllPrCheckOptions options = parseAllPrCheckOptions(args, err);
        if (options == null) {
            return ExitCode.USAGE;
        }

        BlockIndex index;
        try {
            index = new BlockIndexParser().parse(options.repoRoot(), options.blocksPath());
        } catch (BlockIndexValidationException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                "index: VALIDATION_ERROR: " + e.getMessage(),
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear pr-check --all`."
            );
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "io: IO_ERROR: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure `bear.blocks.yaml` is readable and rerun `bear pr-check --all`."
            );
        }

        List<BlockIndexEntry> selected = selectBlocks(index, options.onlyNames());
        if (selected == null) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: unknown block in --only",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Use only block names declared in `bear.blocks.yaml`."
            );
        }

        try {
            List<String> legacyMarkers = options.strictOrphans()
                ? computeLegacyMarkersRepoWide(options.repoRoot())
                : computeLegacyMarkersInManagedRoots(options.repoRoot(), selected);
            if (!legacyMarkers.isEmpty()) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "pr-check: IO_ERROR: LEGACY_SURFACE_MARKER: " + legacyMarkers.get(0),
                    FailureCode.IO_ERROR,
                    legacyMarkers.get(0),
                    "Delete legacy marker paths and recompile managed blocks, then rerun `bear pr-check --all`."
                );
            }

            List<String> orphanMarkers = options.strictOrphans()
                ? computeOrphanMarkersRepoWide(options.repoRoot(), index)
                : computeOrphanMarkersInManagedRoots(options.repoRoot(), selected);
            if (!orphanMarkers.isEmpty()) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "pr-check: IO_ERROR: ORPHAN_MARKER: " + orphanMarkers.get(0),
                    FailureCode.IO_ERROR,
                    orphanMarkers.get(0),
                    "Add missing block entries to `bear.blocks.yaml` or remove stale generated BEAR artifacts."
                );
            }
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "pr-check: IO_ERROR: ORPHAN_SCAN_FAILED: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                "bear.blocks.yaml",
                "Ensure repo paths are readable and rerun `bear pr-check --all`."
            );
        }

        List<BlockExecutionResult> blockResults = new ArrayList<>();
        for (BlockIndexEntry block : selected) {
            if (!block.enabled()) {
                blockResults.add(skipBlock(block, "DISABLED"));
                continue;
            }
            String mappingError = validateIndexIrNameMatch(options.repoRoot().resolve(block.ir()).normalize(), block.name());
            if (mappingError != null) {
                blockResults.add(new BlockExecutionResult(
                    block.name(),
                    block.ir(),
                    block.projectRoot(),
                    BlockStatus.FAIL,
                    ExitCode.VALIDATION,
                    "VALIDATION",
                    FailureCode.IR_VALIDATION,
                    "block.name",
                    mappingError,
                    "Set `block.name` to match index `name` and rerun `bear pr-check --all`.",
                    null,
                    null,
                    List.of()
                ));
                continue;
            }
            PrCheckResult prResult = executePrCheck(options.repoRoot(), block.ir(), options.baseRef());
            blockResults.add(toPrBlockResult(block, prResult));
        }

        RepoAggregationResult summary = aggregatePrResults(blockResults);
        List<String> lines = renderPrAllOutput(blockResults, summary);
        if (summary.exitCode() == ExitCode.OK) {
            printLines(out, lines);
            return ExitCode.OK;
        }
        printLines(err, lines);
        return fail(
            err,
            summary.exitCode(),
            FailureCode.REPO_MULTI_BLOCK_FAILED,
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    private static int emitCheckResult(CheckResult result, PrintStream out, PrintStream err) {
        printLines(out, result.stdoutLines());
        printLines(err, result.stderrLines());
        if (result.exitCode() == ExitCode.OK) {
            return ExitCode.OK;
        }
        return fail(
            err,
            result.exitCode(),
            result.failureCode(),
            result.failurePath(),
            result.failureRemediation()
        );
    }

    private static int emitFixResult(FixResult result, PrintStream out, PrintStream err) {
        printLines(out, result.stdoutLines());
        printLines(err, result.stderrLines());
        if (result.exitCode() == ExitCode.OK) {
            return ExitCode.OK;
        }
        return fail(
            err,
            result.exitCode(),
            result.failureCode(),
            result.failurePath(),
            result.failureRemediation()
        );
    }

    private static int emitPrCheckResult(PrCheckResult result, PrintStream out, PrintStream err) {
        printLines(out, result.stdoutLines());
        printLines(err, result.stderrLines());
        if (result.exitCode() == ExitCode.OK) {
            return ExitCode.OK;
        }
        return fail(
            err,
            result.exitCode(),
            result.failureCode(),
            result.failurePath(),
            result.failureRemediation()
        );
    }

    private static FixResult executeFix(Path irFile, Path projectRoot, String expectedBlockKey) {
        try {
            maybeFailInternalForTest("fix");
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            JvmTarget target = new JvmTarget();

            BearIr ir = parser.parse(irFile);
            validator.validate(ir);
            BearIr normalized = normalizer.normalize(ir);
            String blockKey = toBlockKey(normalized.block().name());
            if (expectedBlockKey != null && !expectedBlockKey.equals(blockKey)) {
                String line = "schema at block.name: INVALID_VALUE: block name must match index name: " + expectedBlockKey;
                return fixFailure(
                    ExitCode.VALIDATION,
                    List.of(line),
                    "VALIDATION",
                    FailureCode.IR_VALIDATION,
                    "block.name",
                    "Set `block.name` to match index `name` and rerun `bear fix --all`.",
                    line
                );
            }

            target.compile(normalized, projectRoot);
            return new FixResult(ExitCode.OK, List.of("fix: OK"), List.of(), null, null, null, null, null);
        } catch (BearIrValidationException e) {
            return fixFailure(
                ExitCode.VALIDATION,
                List.of(e.formatLine()),
                "VALIDATION",
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear fix <ir-file> --project <path>`.",
                e.formatLine()
            );
        } catch (IOException e) {
            return fixFailure(
                ExitCode.IO,
                List.of("io: IO_ERROR: " + e.getMessage()),
                "IO_ERROR",
                FailureCode.IO_ERROR,
                "project.root",
                "Ensure the IR/project paths are readable and writable, then rerun `bear fix`.",
                "io: IO_ERROR: " + e.getMessage()
            );
        } catch (Exception e) {
            return fixFailure(
                ExitCode.INTERNAL,
                List.of("internal: INTERNAL_ERROR:"),
                "INTERNAL_ERROR",
                FailureCode.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli.",
                "internal: INTERNAL_ERROR:"
            );
        }
    }

    private static CheckResult executeCheck(
        Path irFile,
        Path projectRoot,
        boolean runReachAndTests,
        String expectedBlockKey
    ) {
        Path baselineRoot = projectRoot.resolve("build").resolve("generated").resolve("bear");
        Path tempRoot = null;
        try {
            maybeFailInternalForTest("check");
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            JvmTarget target = new JvmTarget();

            BearIr ir = parser.parse(irFile);
            validator.validate(ir);
            BearIr normalized = normalizer.normalize(ir);
            String blockKey = toBlockKey(normalized.block().name());
            if (expectedBlockKey != null && !expectedBlockKey.equals(blockKey)) {
                String line = "schema at block.name: INVALID_VALUE: block name must match index name: " + expectedBlockKey;
                return checkFailure(
                    ExitCode.VALIDATION,
                    List.of(line),
                    "VALIDATION",
                    FailureCode.IR_VALIDATION,
                    "block.name",
                    "Set `block.name` to match index `name` and rerun `bear check --all`.",
                    line
                );
            }
            String packageSegment = toGeneratedPackageSegment(normalized.block().name());
            Set<String> ownedPrefixes = Set.of(
                "src/main/java/com/bear/generated/" + packageSegment.replace('.', '/') + "/",
                "src/test/java/com/bear/generated/" + packageSegment.replace('.', '/') + "/"
            );
            String markerRelPath = "surfaces/" + blockKey + ".surface.json";
            Path legacyMarkerPath = baselineRoot.resolve("bear.surface.json");
            if (Files.isRegularFile(legacyMarkerPath)) {
                return checkFailure(
                    ExitCode.IO,
                    List.of("check: IO_ERROR: LEGACY_SURFACE_MARKER: build/generated/bear/bear.surface.json"),
                    "IO_ERROR",
                    FailureCode.IO_ERROR,
                    "build/generated/bear/bear.surface.json",
                    "Delete legacy marker and rerun compile for managed blocks, then rerun `bear check`.",
                    "check: IO_ERROR: LEGACY_SURFACE_MARKER: build/generated/bear/bear.surface.json"
                );
            }

            if (!hasOwnedBaselineFiles(baselineRoot, ownedPrefixes, markerRelPath)) {
                String line = "drift: MISSING_BASELINE: build/generated/bear (run: bear compile "
                    + irFile + " --project " + projectRoot + ")";
                return checkFailure(
                    ExitCode.DRIFT,
                    List.of(line),
                    "DRIFT",
                    FailureCode.DRIFT_MISSING_BASELINE,
                    "build/generated/bear",
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`.",
                    "drift: MISSING_BASELINE: build/generated/bear"
                );
            }

            tempRoot = Files.createTempDirectory("bear-check-");
            target.compile(normalized, tempRoot);
            Path candidateRoot = tempRoot.resolve("build").resolve("generated").resolve("bear");
            Path baselineManifestPath = baselineRoot.resolve(markerRelPath);
            Path candidateManifestPath = candidateRoot.resolve(markerRelPath);

            applyCandidateManifestTestMode(candidateManifestPath);

            List<String> diagnostics = new ArrayList<>();
            BoundaryManifest baselineManifest = null;
            if (!Files.isRegularFile(baselineManifestPath)) {
                diagnostics.add("check: BASELINE_MANIFEST_MISSING: " + baselineManifestPath);
            } else {
                try {
                    baselineManifest = parseManifest(baselineManifestPath);
                } catch (ManifestParseException e) {
                    diagnostics.add("check: BASELINE_MANIFEST_INVALID: " + e.reasonCode());
                }
            }
            if (!Files.isRegularFile(candidateManifestPath)) {
                return checkFailure(
                    ExitCode.INTERNAL,
                    List.of("internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_MISSING"),
                    "INTERNAL_ERROR",
                    FailureCode.INTERNAL_ERROR,
                    "build/generated/bear/" + markerRelPath,
                    "Capture stderr and file an issue against bear-cli.",
                    "internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_MISSING"
                );
            }
            BoundaryManifest candidateManifest;
            try {
                candidateManifest = parseManifest(candidateManifestPath);
            } catch (ManifestParseException e) {
                return checkFailure(
                    ExitCode.INTERNAL,
                    List.of("internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_INVALID:" + e.reasonCode()),
                    "INTERNAL_ERROR",
                    FailureCode.INTERNAL_ERROR,
                    "build/generated/bear/" + markerRelPath,
                    "Capture stderr and file an issue against bear-cli.",
                    "internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_INVALID:" + e.reasonCode()
                );
            }

            List<BoundarySignal> boundarySignals = List.of();
            if (baselineManifest != null) {
                if (!baselineManifest.irHash().equals(candidateManifest.irHash())
                    || !baselineManifest.generatorVersion().equals(candidateManifest.generatorVersion())) {
                    diagnostics.add("check: BASELINE_STAMP_MISMATCH: irHash/generatorVersion differ; classification may be stale");
                }
                boundarySignals = computeBoundarySignals(baselineManifest, candidateManifest);
            }
            for (BoundarySignal signal : boundarySignals) {
                diagnostics.add("boundary: EXPANSION: " + signal.type().label + ": " + signal.key());
            }

            List<DriftItem> drift = computeDrift(
                baselineRoot,
                candidateRoot,
                path -> path.equals(markerRelPath) || startsWithAny(path, ownedPrefixes)
            );
            if (!drift.isEmpty()) {
                for (DriftItem item : drift) {
                    diagnostics.add("drift: " + item.type().label + ": " + item.path());
                }
                return checkFailure(
                    ExitCode.DRIFT,
                    diagnostics,
                    "DRIFT",
                    FailureCode.DRIFT_DETECTED,
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
                return new CheckResult(ExitCode.OK, List.of(), List.of(), null, null, null, null, null);
            }

            List<UndeclaredReachFinding> undeclaredReach = scanUndeclaredReach(projectRoot);
            if (!undeclaredReach.isEmpty()) {
                for (UndeclaredReachFinding finding : undeclaredReach) {
                    diagnostics.add("check: UNDECLARED_REACH: " + finding.path() + ": " + finding.surface());
                }
                return checkFailure(
                    ExitCode.UNDECLARED_REACH,
                    diagnostics,
                    "UNDECLARED_REACH",
                    FailureCode.UNDECLARED_REACH,
                    undeclaredReach.get(0).path(),
                    "Declare a port/op in IR, run bear compile, and route call through generated port interface.",
                    diagnostics.get(diagnostics.size() - 1)
                );
            }

            ProjectTestResult testResult = runProjectTests(projectRoot);
            if (testResult.status == ProjectTestStatus.LOCKED) {
                String lockLine = firstGradleLockLine(testResult.output);
                String ioLine = lockLine == null
                    ? "io: IO_ERROR: PROJECT_TEST_LOCK: Gradle wrapper lock detected"
                    : "io: IO_ERROR: PROJECT_TEST_LOCK: " + lockLine;
                diagnostics.add(ioLine);
                diagnostics.addAll(tailLines(testResult.output));
                return checkFailure(
                    ExitCode.IO,
                    diagnostics,
                    "IO_ERROR",
                    FailureCode.IO_ERROR,
                    "project.tests",
                    "Release Gradle wrapper lock or set isolated GRADLE_USER_HOME, then rerun `bear check <ir-file> --project <path>`.",
                    ioLine
                );
            }
            if (testResult.status == ProjectTestStatus.FAILED) {
                diagnostics.add("check: TEST_FAILED: project tests failed");
                diagnostics.addAll(tailLines(testResult.output));
                return checkFailure(
                    ExitCode.TEST_FAILURE,
                    diagnostics,
                    "TEST_FAILURE",
                    FailureCode.TEST_FAILURE,
                    "project.tests",
                    "Fix project tests and rerun `bear check <ir-file> --project <path>`.",
                    "check: TEST_FAILED: project tests failed"
                );
            }
            if (testResult.status == ProjectTestStatus.TIMEOUT) {
                String timeoutLine = "check: TEST_TIMEOUT: project tests exceeded " + testTimeoutSeconds() + "s";
                diagnostics.add(timeoutLine);
                diagnostics.addAll(tailLines(testResult.output));
                return checkFailure(
                    ExitCode.TEST_FAILURE,
                    diagnostics,
                    "TEST_FAILURE",
                    FailureCode.TEST_TIMEOUT,
                    "project.tests",
                    "Reduce test runtime or increase timeout, then rerun `bear check <ir-file> --project <path>`.",
                    timeoutLine
                );
            }

            return new CheckResult(ExitCode.OK, List.of("check: OK"), List.of(), null, null, null, null, null);
        } catch (BearIrValidationException e) {
            return checkFailure(
                ExitCode.VALIDATION,
                List.of(e.formatLine()),
                "VALIDATION",
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear check <ir-file> --project <path>`.",
                e.formatLine()
            );
        } catch (IOException e) {
            return checkFailure(
                ExitCode.IO,
                List.of("io: IO_ERROR: " + e.getMessage()),
                "IO_ERROR",
                FailureCode.IO_ERROR,
                "project.root",
                "Ensure project paths are accessible (including Gradle wrapper), then rerun `bear check`.",
                "io: IO_ERROR: " + e.getMessage()
            );
        } catch (Exception e) {
            return checkFailure(
                ExitCode.INTERNAL,
                List.of("internal: INTERNAL_ERROR:"),
                "INTERNAL_ERROR",
                FailureCode.INTERNAL_ERROR,
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

    private static PrCheckResult executePrCheck(Path projectRoot, String repoRelativePath, String baseRef) {
        Path tempRoot = null;
        try {
            maybeFailInternalForTest("pr-check");
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();

            Path headIrPath = projectRoot.resolve(repoRelativePath).normalize();
            if (!headIrPath.startsWith(projectRoot) || !Files.isRegularFile(headIrPath)) {
                return prFailure(
                    ExitCode.IO,
                    List.of("pr-check: IO_ERROR: READ_HEAD_FAILED: " + repoRelativePath),
                    "IO_ERROR",
                    FailureCode.IO_ERROR,
                    repoRelativePath,
                    "Ensure the IR file exists at HEAD and rerun `bear pr-check`.",
                    "pr-check: IO_ERROR: READ_HEAD_FAILED: " + repoRelativePath,
                    List.of(),
                    false,
                    false
                );
            }

            BearIr head = normalizer.normalize(parseAndValidateIr(parser, validator, headIrPath));

            GitResult isRepoResult = runGitForPrCheck(projectRoot, List.of("rev-parse", "--is-inside-work-tree"), "git.repo");
            if (isRepoResult.exitCode() != 0 || !"true".equals(isRepoResult.stdout().trim())) {
                return prFailure(
                    ExitCode.IO,
                    List.of("pr-check: IO_ERROR: NOT_A_GIT_REPO: " + projectRoot),
                    "IO_ERROR",
                    FailureCode.IO_GIT,
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
                    ExitCode.IO,
                    List.of("pr-check: IO_ERROR: MERGE_BASE_FAILED: " + baseRef),
                    "IO_ERROR",
                    FailureCode.IO_GIT,
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
                    ExitCode.IO,
                    List.of("pr-check: IO_ERROR: MERGE_BASE_EMPTY: unable to resolve merge base"),
                    "IO_ERROR",
                    FailureCode.IO_GIT,
                    "git.baseRef",
                    "Ensure base ref resolves to a merge base with HEAD, then rerun `bear pr-check`.",
                    "pr-check: IO_ERROR: MERGE_BASE_EMPTY: unable to resolve merge base",
                    List.of(),
                    false,
                    false
                );
            }

            List<String> stderrLines = new ArrayList<>();
            BearIr base = null;
            GitResult catFileResult = runGitForPrCheck(
                projectRoot,
                List.of("cat-file", "-e", mergeBase + ":" + repoRelativePath),
                repoRelativePath
            );
            if (catFileResult.exitCode() != 0) {
                GitResult existsResult = runGitForPrCheck(
                    projectRoot,
                    List.of("ls-tree", "--name-only", mergeBase, "--", repoRelativePath),
                    repoRelativePath
                );
                if (existsResult.exitCode() != 0) {
                    return prFailure(
                        ExitCode.IO,
                        List.of("pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath),
                        "IO_ERROR",
                        FailureCode.IO_GIT,
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
                        ExitCode.IO,
                        List.of("pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath),
                        "IO_ERROR",
                        FailureCode.IO_GIT,
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
                        ExitCode.IO,
                        List.of("pr-check: IO_ERROR: BASE_IR_READ_FAILED: " + repoRelativePath),
                        "IO_ERROR",
                        FailureCode.IO_GIT,
                        repoRelativePath,
                        "Ensure base IR snapshot is readable from git history, then rerun `bear pr-check`.",
                        "pr-check: IO_ERROR: BASE_IR_READ_FAILED: " + repoRelativePath,
                        List.of(),
                        false,
                        false
                    );
                }
                tempRoot = Files.createTempDirectory("bear-pr-check-");
                Path baseTempIr = tempRoot.resolve("base.bear.yaml");
                Files.writeString(baseTempIr, showResult.stdout(), StandardCharsets.UTF_8);
                base = normalizer.normalize(parseAndValidateIr(parser, validator, baseTempIr));
            }

            List<PrDelta> deltas = computePrDeltas(base, head);
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
                    ExitCode.BOUNDARY_EXPANSION,
                    stderrLines,
                    "BOUNDARY_EXPANSION",
                    FailureCode.BOUNDARY_EXPANSION,
                    repoRelativePath,
                    "Review boundary-expanding deltas and route through explicit boundary review.",
                    "pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED",
                    deltaLines,
                    true,
                    !deltaLines.isEmpty()
                );
            }

            return new PrCheckResult(
                ExitCode.OK,
                List.of("pr-check: OK: NO_BOUNDARY_EXPANSION"),
                stderrLines,
                null,
                null,
                null,
                null,
                null,
                deltaLines,
                false,
                !deltaLines.isEmpty()
            );
        } catch (PrCheckGitException e) {
            return prFailure(
                ExitCode.IO,
                List.of(e.legacyLine()),
                "IO_ERROR",
                FailureCode.IO_GIT,
                e.pathLocator(),
                "Resolve git invocation/base-reference issues and rerun `bear pr-check`.",
                e.legacyLine(),
                List.of(),
                false,
                false
            );
        } catch (BearIrValidationException e) {
            return prFailure(
                ExitCode.VALIDATION,
                List.of(e.formatLine()),
                "VALIDATION",
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear pr-check`.",
                e.formatLine(),
                List.of(),
                false,
                false
            );
        } catch (IOException e) {
            return prFailure(
                ExitCode.IO,
                List.of("pr-check: IO_ERROR: INTERNAL_IO: " + squash(e.getMessage())),
                "IO_ERROR",
                FailureCode.IO_ERROR,
                "internal",
                "Ensure local filesystem paths are accessible, then rerun `bear pr-check`.",
                "pr-check: IO_ERROR: INTERNAL_IO: " + squash(e.getMessage()),
                List.of(),
                false,
                false
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return prFailure(
                ExitCode.IO,
                List.of("pr-check: IO_ERROR: INTERRUPTED"),
                "IO_ERROR",
                FailureCode.IO_GIT,
                "git.repo",
                "Retry `bear pr-check`; if interruption persists, rerun in a stable shell/CI environment.",
                "pr-check: IO_ERROR: INTERRUPTED",
                List.of(),
                false,
                false
            );
        } catch (Exception e) {
            return prFailure(
                ExitCode.INTERNAL,
                List.of("internal: INTERNAL_ERROR:"),
                "INTERNAL_ERROR",
                FailureCode.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli.",
                "internal: INTERNAL_ERROR:",
                List.of(),
                false,
                false
            );
        } finally {
            if (tempRoot != null) {
                deleteRecursivelyBestEffort(tempRoot);
            }
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

    private static List<String> tailLines(String output) {
        List<String> lines = normalizeLf(output).lines().toList();
        int start = Math.max(0, lines.size() - 40);
        return new ArrayList<>(lines.subList(start, lines.size()));
    }

    private static void printLines(PrintStream stream, List<String> lines) {
        for (String line : lines) {
            stream.println(line);
        }
    }

    private static AllCheckOptions parseAllCheckOptions(String[] args, PrintStream err) {
        Path repoRoot = null;
        String blocksArg = null;
        String onlyArg = null;
        boolean failFast = false;
        boolean strictOrphans = false;
        for (int i = 2; i < args.length; i++) {
            String token = args[i];
            switch (token) {
                case "--project" -> {
                    if (i + 1 >= args.length) {
                        failWithLegacy(
                            err,
                            ExitCode.USAGE,
                            "usage: INVALID_ARGS: expected value after --project",
                            FailureCode.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Run `bear check --all --project <repoRoot>` with required arguments."
                        );
                        return null;
                    }
                    repoRoot = Path.of(args[++i]).toAbsolutePath().normalize();
                }
                case "--blocks" -> {
                    if (i + 1 >= args.length) {
                        failWithLegacy(
                            err,
                            ExitCode.USAGE,
                            "usage: INVALID_ARGS: expected value after --blocks",
                            FailureCode.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass a repo-relative path after `--blocks`."
                        );
                        return null;
                    }
                    blocksArg = args[++i];
                }
                case "--only" -> {
                    if (i + 1 >= args.length) {
                        failWithLegacy(
                            err,
                            ExitCode.USAGE,
                            "usage: INVALID_ARGS: expected value after --only",
                            FailureCode.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass comma-separated block names after `--only`."
                        );
                        return null;
                    }
                    onlyArg = args[++i];
                }
                case "--fail-fast" -> failFast = true;
                case "--strict-orphans" -> strictOrphans = true;
                default -> {
                    failWithLegacy(
                        err,
                        ExitCode.USAGE,
                        "usage: INVALID_ARGS: unexpected argument: " + token,
                        FailureCode.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear check --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]`."
                    );
                    return null;
                }
            }
        }
        if (repoRoot == null) {
            failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear check --all --project <repoRoot>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear check --all --project <repoRoot>` with required arguments."
            );
            return null;
        }
        Path blocksPath;
        try {
            blocksPath = resolveBlocksPath(repoRoot, blocksArg);
        } catch (IllegalArgumentException e) {
            failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: " + e.getMessage(),
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative path for `--blocks`."
            );
            return null;
        }
        Set<String> onlyNames;
        try {
            onlyNames = parseOnlyNames(onlyArg);
        } catch (IllegalArgumentException e) {
            failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: " + e.getMessage(),
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass comma-separated block names for `--only`."
            );
            return null;
        }
        return new AllCheckOptions(repoRoot, blocksPath, onlyNames, failFast, strictOrphans);
    }

    private static AllFixOptions parseAllFixOptions(String[] args, PrintStream err) {
        Path repoRoot = null;
        String blocksArg = null;
        String onlyArg = null;
        boolean failFast = false;
        boolean strictOrphans = false;
        for (int i = 2; i < args.length; i++) {
            String token = args[i];
            switch (token) {
                case "--project" -> {
                    if (i + 1 >= args.length) {
                        failWithLegacy(
                            err,
                            ExitCode.USAGE,
                            "usage: INVALID_ARGS: expected value after --project",
                            FailureCode.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Run `bear fix --all --project <repoRoot>` with required arguments."
                        );
                        return null;
                    }
                    repoRoot = Path.of(args[++i]).toAbsolutePath().normalize();
                }
                case "--blocks" -> {
                    if (i + 1 >= args.length) {
                        failWithLegacy(
                            err,
                            ExitCode.USAGE,
                            "usage: INVALID_ARGS: expected value after --blocks",
                            FailureCode.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass a repo-relative path after `--blocks`."
                        );
                        return null;
                    }
                    blocksArg = args[++i];
                }
                case "--only" -> {
                    if (i + 1 >= args.length) {
                        failWithLegacy(
                            err,
                            ExitCode.USAGE,
                            "usage: INVALID_ARGS: expected value after --only",
                            FailureCode.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass comma-separated block names after `--only`."
                        );
                        return null;
                    }
                    onlyArg = args[++i];
                }
                case "--fail-fast" -> failFast = true;
                case "--strict-orphans" -> strictOrphans = true;
                default -> {
                    failWithLegacy(
                        err,
                        ExitCode.USAGE,
                        "usage: INVALID_ARGS: unexpected argument: " + token,
                        FailureCode.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear fix --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]`."
                    );
                    return null;
                }
            }
        }
        if (repoRoot == null) {
            failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear fix --all --project <repoRoot>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear fix --all --project <repoRoot>` with required arguments."
            );
            return null;
        }
        Path blocksPath;
        try {
            blocksPath = resolveBlocksPath(repoRoot, blocksArg);
        } catch (IllegalArgumentException e) {
            failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: " + e.getMessage(),
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative path for `--blocks`."
            );
            return null;
        }
        Set<String> onlyNames;
        try {
            onlyNames = parseOnlyNames(onlyArg);
        } catch (IllegalArgumentException e) {
            failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: " + e.getMessage(),
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass comma-separated block names for `--only`."
            );
            return null;
        }
        return new AllFixOptions(repoRoot, blocksPath, onlyNames, failFast, strictOrphans);
    }

    private static AllPrCheckOptions parseAllPrCheckOptions(String[] args, PrintStream err) {
        Path repoRoot = null;
        String blocksArg = null;
        String onlyArg = null;
        String baseRef = null;
        boolean strictOrphans = false;
        for (int i = 2; i < args.length; i++) {
            String token = args[i];
            switch (token) {
                case "--project" -> {
                    if (i + 1 >= args.length) {
                        failWithLegacy(
                            err,
                            ExitCode.USAGE,
                            "usage: INVALID_ARGS: expected value after --project",
                            FailureCode.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Run `bear pr-check --all --project <repoRoot> --base <ref>` with required arguments."
                        );
                        return null;
                    }
                    repoRoot = Path.of(args[++i]).toAbsolutePath().normalize();
                }
                case "--blocks" -> {
                    if (i + 1 >= args.length) {
                        failWithLegacy(
                            err,
                            ExitCode.USAGE,
                            "usage: INVALID_ARGS: expected value after --blocks",
                            FailureCode.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass a repo-relative path after `--blocks`."
                        );
                        return null;
                    }
                    blocksArg = args[++i];
                }
                case "--only" -> {
                    if (i + 1 >= args.length) {
                        failWithLegacy(
                            err,
                            ExitCode.USAGE,
                            "usage: INVALID_ARGS: expected value after --only",
                            FailureCode.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass comma-separated block names after `--only`."
                        );
                        return null;
                    }
                    onlyArg = args[++i];
                }
                case "--base" -> {
                    if (i + 1 >= args.length) {
                        failWithLegacy(
                            err,
                            ExitCode.USAGE,
                            "usage: INVALID_ARGS: expected value after --base",
                            FailureCode.USAGE_INVALID_ARGS,
                            "cli.args",
                            "Pass a base ref after `--base`."
                        );
                        return null;
                    }
                    baseRef = args[++i];
                }
                case "--strict-orphans" -> strictOrphans = true;
                default -> {
                    failWithLegacy(
                        err,
                        ExitCode.USAGE,
                        "usage: INVALID_ARGS: unexpected argument: " + token,
                        FailureCode.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear pr-check --all --project <repoRoot> --base <ref> [--blocks <path>] [--only <csv>] [--strict-orphans]`."
                    );
                    return null;
                }
            }
        }
        if (repoRoot == null || baseRef == null || baseRef.isBlank()) {
            failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear pr-check --all --project <repoRoot> --base <ref>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear pr-check --all --project <repoRoot> --base <ref>` with required arguments."
            );
            return null;
        }
        Path blocksPath;
        try {
            blocksPath = resolveBlocksPath(repoRoot, blocksArg);
        } catch (IllegalArgumentException e) {
            failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: " + e.getMessage(),
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative path for `--blocks`."
            );
            return null;
        }
        Set<String> onlyNames;
        try {
            onlyNames = parseOnlyNames(onlyArg);
        } catch (IllegalArgumentException e) {
            failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: " + e.getMessage(),
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass comma-separated block names for `--only`."
            );
            return null;
        }
        return new AllPrCheckOptions(repoRoot, blocksPath, onlyNames, strictOrphans, baseRef);
    }

    private static Path resolveBlocksPath(Path repoRoot, String blocksArg) {
        if (blocksArg == null) {
            return repoRoot.resolve("bear.blocks.yaml").normalize();
        }
        Path relative = Path.of(blocksArg).normalize();
        if (relative.isAbsolute() || relative.toString().isBlank() || relative.startsWith("..")) {
            throw new IllegalArgumentException("blocks path must be repo-relative");
        }
        Path resolved = repoRoot.resolve(relative).normalize();
        if (!resolved.startsWith(repoRoot)) {
            throw new IllegalArgumentException("blocks path must be repo-relative");
        }
        return resolved;
    }

    private static Set<String> parseOnlyNames(String onlyArg) {
        if (onlyArg == null) {
            return Set.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String raw : onlyArg.split(",")) {
            String name = raw.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            throw new IllegalArgumentException("--only requires at least one block name");
        }
        return names;
    }

    private static List<BlockIndexEntry> selectBlocks(BlockIndex index, Set<String> onlyNames) {
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

    private static List<String> computeOrphanMarkersRepoWide(Path repoRoot, BlockIndex index) throws IOException {
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

    private static List<String> computeOrphanMarkersInManagedRoots(Path repoRoot, List<BlockIndexEntry> selected) throws IOException {
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

    private static List<String> computeLegacyMarkersRepoWide(Path repoRoot) throws IOException {
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

    private static List<String> computeLegacyMarkersInManagedRoots(Path repoRoot, List<BlockIndexEntry> selected) {
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

    private static BlockExecutionResult skipBlock(BlockIndexEntry block, String reason) {
        return new BlockExecutionResult(
            block.name(),
            block.ir(),
            block.projectRoot(),
            BlockStatus.SKIP,
            ExitCode.OK,
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

    private static BlockExecutionResult toCheckBlockResult(BlockIndexEntry block, CheckResult result) {
        if (result.exitCode() == ExitCode.OK) {
            return new BlockExecutionResult(
                block.name(),
                block.ir(),
                block.projectRoot(),
                BlockStatus.PASS,
                ExitCode.OK,
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

    private static BlockExecutionResult toFixBlockResult(BlockIndexEntry block, FixResult result) {
        if (result.exitCode() == ExitCode.OK) {
            return new BlockExecutionResult(
                block.name(),
                block.ir(),
                block.projectRoot(),
                BlockStatus.PASS,
                ExitCode.OK,
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

    private static BlockExecutionResult rootFailure(
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
            base.deltaLines()
        );
    }

    private static String validateIndexIrNameMatch(Path irFile, String expectedBlockKey) {
        try {
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            BearIr normalized = normalizer.normalize(parseAndValidateIr(parser, validator, irFile));
            String actualBlockKey = toBlockKey(normalized.block().name());
            if (!expectedBlockKey.equals(actualBlockKey)) {
                return "index name `" + expectedBlockKey + "` does not match IR block.name `" + normalized.block().name() + "`";
            }
            return null;
        } catch (Exception e) {
            return "unable to validate block name mapping: " + squash(e.getMessage());
        }
    }

    private static BlockExecutionResult toPrBlockResult(BlockIndexEntry block, PrCheckResult result) {
        if (result.exitCode() == ExitCode.OK) {
            String classification = result.hasDeltas() ? "ORDINARY" : "NO_CHANGES";
            return new BlockExecutionResult(
                block.name(),
                block.ir(),
                block.projectRoot(),
                BlockStatus.PASS,
                ExitCode.OK,
                null,
                null,
                null,
                null,
                null,
                null,
                classification,
                result.deltaLines()
            );
        }
        String classification = result.exitCode() == ExitCode.BOUNDARY_EXPANSION ? "BOUNDARY_EXPANDING" : null;
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
            result.deltaLines()
        );
    }

    private static RepoAggregationResult aggregateCheckResults(
        List<BlockExecutionResult> results,
        boolean failFastTriggered,
        int rootReachFailed,
        int rootTestFailed,
        int rootTestSkippedDueToReach
    ) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        int exitCode = ExitCode.OK;
        int rank = severityRankCheck(exitCode);
        for (BlockExecutionResult result : results) {
            if (result.status() == BlockStatus.PASS) {
                passed++;
            } else if (result.status() == BlockStatus.FAIL) {
                failed++;
                int candidateRank = severityRankCheck(result.exitCode());
                if (candidateRank < rank) {
                    rank = candidateRank;
                    exitCode = result.exitCode();
                }
            } else {
                skipped++;
            }
        }
        return new RepoAggregationResult(
            exitCode,
            results.size(),
            passed + failed,
            passed,
            failed,
            skipped,
            failFastTriggered,
            rootReachFailed,
            rootTestFailed,
            rootTestSkippedDueToReach
        );
    }

    private static RepoAggregationResult aggregatePrResults(List<BlockExecutionResult> results) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        int exitCode = ExitCode.OK;
        int rank = severityRankPr(exitCode);
        for (BlockExecutionResult result : results) {
            if (result.status() == BlockStatus.PASS) {
                passed++;
            } else if (result.status() == BlockStatus.FAIL) {
                failed++;
                int candidateRank = severityRankPr(result.exitCode());
                if (candidateRank < rank) {
                    rank = candidateRank;
                    exitCode = result.exitCode();
                }
            } else {
                skipped++;
            }
        }
        return new RepoAggregationResult(
            exitCode,
            results.size(),
            passed + failed,
            passed,
            failed,
            skipped,
            false,
            0,
            0,
            0
        );
    }

    private static RepoAggregationResult aggregateFixResults(List<BlockExecutionResult> results, boolean failFastTriggered) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        int exitCode = ExitCode.OK;
        int rank = severityRankFix(exitCode);
        for (BlockExecutionResult result : results) {
            if (result.status() == BlockStatus.PASS) {
                passed++;
            } else if (result.status() == BlockStatus.FAIL) {
                failed++;
                int candidateRank = severityRankFix(result.exitCode());
                if (candidateRank < rank) {
                    rank = candidateRank;
                    exitCode = result.exitCode();
                }
            } else {
                skipped++;
            }
        }
        return new RepoAggregationResult(
            exitCode,
            results.size(),
            passed + failed,
            passed,
            failed,
            skipped,
            failFastTriggered,
            0,
            0,
            0
        );
    }

    private static int severityRankCheck(int code) {
        return switch (code) {
            case 70 -> 1;
            case 74 -> 2;
            case 64 -> 3;
            case 2 -> 4;
            case 3 -> 5;
            case 6 -> 6;
            case 4 -> 7;
            case 0 -> 8;
            default -> 1;
        };
    }

    private static int severityRankPr(int code) {
        return switch (code) {
            case 70 -> 1;
            case 74 -> 2;
            case 64 -> 3;
            case 2 -> 4;
            case 5 -> 5;
            case 0 -> 6;
            default -> 1;
        };
    }

    private static int severityRankFix(int code) {
        return switch (code) {
            case 70 -> 1;
            case 74 -> 2;
            case 64 -> 3;
            case 2 -> 4;
            case 0 -> 5;
            default -> 1;
        };
    }

    private static List<String> renderCheckAllOutput(List<BlockExecutionResult> results, RepoAggregationResult summary) {
        List<String> lines = new ArrayList<>();
        for (BlockExecutionResult result : results) {
            lines.add("BLOCK: " + result.name());
            lines.add("IR: " + result.ir());
            lines.add("PROJECT: " + result.project());
            lines.add("STATUS: " + result.status());
            lines.add("EXIT_CODE: " + result.exitCode());
            if (result.status() == BlockStatus.FAIL) {
                lines.add("CATEGORY: " + result.category());
                lines.add("BLOCK_CODE: " + result.blockCode());
                lines.add("BLOCK_PATH: " + result.blockPath());
                lines.add("DETAIL: " + result.detail());
                lines.add("BLOCK_REMEDIATION: " + result.blockRemediation());
            } else if (result.status() == BlockStatus.SKIP) {
                lines.add("REASON: " + result.reason());
            }
            lines.add("");
        }
        lines.add("SUMMARY:");
        lines.add(summary.total() + " blocks total");
        lines.add(summary.checked() + " checked");
        lines.add(summary.passed() + " passed");
        lines.add(summary.failed() + " failed");
        lines.add(summary.skipped() + " skipped");
        lines.add("ROOT_REACH_FAILED: " + summary.rootReachFailed());
        lines.add("ROOT_TEST_FAILED: " + summary.rootTestFailed());
        lines.add("ROOT_TEST_SKIPPED_DUE_TO_REACH: " + summary.rootTestSkippedDueToReach());
        lines.add("FAIL_FAST_TRIGGERED: " + summary.failFastTriggered());
        lines.add("EXIT_CODE: " + summary.exitCode());
        return lines;
    }

    private static List<String> renderPrAllOutput(List<BlockExecutionResult> results, RepoAggregationResult summary) {
        List<String> lines = new ArrayList<>();
        int boundaryCount = 0;
        for (BlockExecutionResult result : results) {
            lines.add("BLOCK: " + result.name());
            lines.add("IR: " + result.ir());
            lines.add("PROJECT: " + result.project());
            lines.add("STATUS: " + result.status());
            lines.add("EXIT_CODE: " + result.exitCode());
            if (result.classification() != null) {
                lines.add("CLASSIFICATION: " + result.classification());
                if ("BOUNDARY_EXPANDING".equals(result.classification())) {
                    boundaryCount++;
                }
            }
            if (!result.deltaLines().isEmpty()) {
                lines.add("DELTA:");
                for (String deltaLine : result.deltaLines()) {
                    lines.add("  " + deltaLine);
                }
            } else {
                lines.add("DELTA: (no changes)");
            }
            if (result.status() == BlockStatus.FAIL && result.blockCode() != null) {
                lines.add("CATEGORY: " + result.category());
                lines.add("BLOCK_CODE: " + result.blockCode());
                lines.add("BLOCK_PATH: " + result.blockPath());
                lines.add("DETAIL: " + result.detail());
                lines.add("BLOCK_REMEDIATION: " + result.blockRemediation());
            } else if (result.status() == BlockStatus.SKIP) {
                lines.add("REASON: " + result.reason());
            }
            lines.add("");
        }
        lines.add("SUMMARY:");
        lines.add(summary.total() + " blocks total");
        lines.add(summary.checked() + " checked");
        lines.add(summary.passed() + " passed");
        lines.add(summary.failed() + " failed");
        lines.add(summary.skipped() + " skipped");
        lines.add("BOUNDARY_EXPANDING: " + boundaryCount);
        lines.add("EXIT_CODE: " + summary.exitCode());
        return lines;
    }

    private static List<String> renderFixAllOutput(List<BlockExecutionResult> results, RepoAggregationResult summary) {
        List<String> lines = new ArrayList<>();
        for (BlockExecutionResult result : results) {
            lines.add("BLOCK: " + result.name());
            lines.add("IR: " + result.ir());
            lines.add("PROJECT: " + result.project());
            lines.add("STATUS: " + result.status());
            lines.add("EXIT_CODE: " + result.exitCode());
            if (result.status() == BlockStatus.FAIL) {
                lines.add("CATEGORY: " + result.category());
                lines.add("BLOCK_CODE: " + result.blockCode());
                lines.add("BLOCK_PATH: " + result.blockPath());
                lines.add("DETAIL: " + result.detail());
                lines.add("BLOCK_REMEDIATION: " + result.blockRemediation());
            } else if (result.status() == BlockStatus.SKIP) {
                lines.add("REASON: " + result.reason());
            }
            lines.add("");
        }
        lines.add("SUMMARY:");
        lines.add(summary.total() + " blocks total");
        lines.add(summary.checked() + " checked");
        lines.add(summary.passed() + " passed");
        lines.add(summary.failed() + " failed");
        lines.add(summary.skipped() + " skipped");
        lines.add("FAIL_FAST_TRIGGERED: " + summary.failFastTriggered());
        lines.add("EXIT_CODE: " + summary.exitCode());
        return lines;
    }

    private static List<DriftItem> computeDrift(
        Path baselineRoot,
        Path candidateRoot,
        java.util.function.Predicate<String> includePath
    ) throws IOException {
        Map<String, byte[]> baseline = readRegularFiles(baselineRoot);
        Map<String, byte[]> candidate = readRegularFiles(candidateRoot);

        TreeSet<String> allPaths = new TreeSet<>();
        allPaths.addAll(baseline.keySet());
        allPaths.addAll(candidate.keySet());

        List<DriftItem> drift = new ArrayList<>();
        for (String path : allPaths) {
            if (!includePath.test(path)) {
                continue;
            }
            boolean inBaseline = baseline.containsKey(path);
            boolean inCandidate = candidate.containsKey(path);
            if (inBaseline && !inCandidate) {
                drift.add(new DriftItem(path, DriftType.ADDED));
                continue;
            }
            if (!inBaseline && inCandidate) {
                drift.add(new DriftItem(path, DriftType.REMOVED));
                continue;
            }
            if (!Arrays.equals(baseline.get(path), candidate.get(path))) {
                drift.add(new DriftItem(path, DriftType.CHANGED));
            }
        }

        drift.sort(Comparator
            .comparing(DriftItem::path)
            .thenComparing(item -> item.type().order));
        return drift;
    }

    private static List<BoundarySignal> computeBoundarySignals(BoundaryManifest baseline, BoundaryManifest candidate) {
        List<BoundarySignal> signals = new ArrayList<>();
        for (String capability : candidate.capabilities().keySet()) {
            if (!baseline.capabilities().containsKey(capability)) {
                signals.add(new BoundarySignal(BoundaryType.CAPABILITY_ADDED, capability));
            }
        }
        for (Map.Entry<String, String> dep : candidate.allowedDeps().entrySet()) {
            String ga = dep.getKey();
            if (!baseline.allowedDeps().containsKey(ga)) {
                signals.add(new BoundarySignal(BoundaryType.PURE_DEP_ADDED, ga + "@" + dep.getValue()));
                continue;
            }
            String oldVersion = baseline.allowedDeps().get(ga);
            if (!oldVersion.equals(dep.getValue())) {
                signals.add(new BoundarySignal(BoundaryType.PURE_DEP_VERSION_CHANGED, ga + "@" + oldVersion + "->" + dep.getValue()));
            }
        }
        for (Map.Entry<String, TreeSet<String>> entry : candidate.capabilities().entrySet()) {
            String capability = entry.getKey();
            if (!baseline.capabilities().containsKey(capability)) {
                continue;
            }
            TreeSet<String> baselineOps = baseline.capabilities().get(capability);
            for (String op : entry.getValue()) {
                if (!baselineOps.contains(op)) {
                    signals.add(new BoundarySignal(BoundaryType.CAPABILITY_OP_ADDED, capability + "." + op));
                }
            }
        }
        for (String invariant : baseline.invariants()) {
            if (!candidate.invariants().contains(invariant)) {
                signals.add(new BoundarySignal(BoundaryType.INVARIANT_RELAXED, invariant));
            }
        }
        signals.sort(Comparator
            .comparing((BoundarySignal signal) -> signal.type().order)
            .thenComparing(BoundarySignal::key));
        return signals;
    }

    private static BearIr parseAndValidateIr(BearIrParser parser, BearIrValidator validator, Path path) throws IOException {
        BearIr ir = parser.parse(path);
        validator.validate(ir);
        return ir;
    }

    private static List<PrDelta> computePrDeltas(BearIr baseIr, BearIr headIr) {
        PrSurface base = baseIr == null ? emptyPrSurface() : toPrSurface(baseIr);
        PrSurface head = toPrSurface(headIr);

        List<PrDelta> deltas = new ArrayList<>();

        for (String port : head.ports()) {
            if (!base.ports().contains(port)) {
                deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.PORTS, PrChange.ADDED, port));
            }
        }
        for (String port : base.ports()) {
            if (!head.ports().contains(port)) {
                deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.PORTS, PrChange.REMOVED, port));
            }
        }

        TreeSet<String> commonPorts = new TreeSet<>(head.ports());
        commonPorts.retainAll(base.ports());
        for (String port : commonPorts) {
            TreeSet<String> headOps = head.opsByPort().getOrDefault(port, new TreeSet<>());
            TreeSet<String> baseOps = base.opsByPort().getOrDefault(port, new TreeSet<>());
            for (String op : headOps) {
                if (!baseOps.contains(op)) {
                    deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.OPS, PrChange.ADDED, port + "." + op));
                }
            }
            for (String op : baseOps) {
                if (!headOps.contains(op)) {
                    deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.OPS, PrChange.REMOVED, port + "." + op));
                }
            }
        }

        addIdempotencyDeltas(deltas, base.idempotency(), head.idempotency());
        addAllowedDepDeltas(deltas, base.allowedDeps(), head.allowedDeps());
        addContractDeltas(deltas, base.inputs(), head.inputs(), true);
        addContractDeltas(deltas, base.outputs(), head.outputs(), false);

        for (String invariant : head.invariants()) {
            if (!base.invariants().contains(invariant)) {
                deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.INVARIANTS, PrChange.ADDED, invariant));
            }
        }
        for (String invariant : base.invariants()) {
            if (!head.invariants().contains(invariant)) {
                deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.INVARIANTS, PrChange.REMOVED, invariant));
            }
        }

        deltas.sort(Comparator
            .comparing((PrDelta delta) -> delta.clazz().order)
            .thenComparing(delta -> delta.category().order)
            .thenComparing(delta -> delta.change().order)
            .thenComparing(PrDelta::key));
        return deltas;
    }

    private static void addAllowedDepDeltas(List<PrDelta> deltas, Map<String, String> base, Map<String, String> head) {
        TreeSet<String> names = new TreeSet<>();
        names.addAll(base.keySet());
        names.addAll(head.keySet());
        for (String ga : names) {
            boolean inBase = base.containsKey(ga);
            boolean inHead = head.containsKey(ga);
            if (!inBase) {
                deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.ALLOWED_DEPS, PrChange.ADDED, ga + "@" + head.get(ga)));
                continue;
            }
            if (!inHead) {
                deltas.add(new PrDelta(PrClass.ORDINARY, PrCategory.ALLOWED_DEPS, PrChange.REMOVED, ga + "@" + base.get(ga)));
                continue;
            }
            if (!base.get(ga).equals(head.get(ga))) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.ALLOWED_DEPS,
                    PrChange.CHANGED,
                    ga + "@" + base.get(ga) + "->" + head.get(ga)
                ));
            }
        }
    }

    private static void addIdempotencyDeltas(
        List<PrDelta> deltas,
        BearIr.Idempotency base,
        BearIr.Idempotency head
    ) {
        if (base == null && head == null) {
            return;
        }
        if (base == null) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.ADDED, "idempotency"));
            return;
        }
        if (head == null) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.REMOVED, "idempotency"));
            return;
        }

        if (!base.key().equals(head.key())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "idempotency.key"));
        }
        if (!base.store().port().equals(head.store().port())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "idempotency.store.port"));
        }
        if (!base.store().getOp().equals(head.store().getOp())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "idempotency.store.getOp"));
        }
        if (!base.store().putOp().equals(head.store().putOp())) {
            deltas.add(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.IDEMPOTENCY, PrChange.CHANGED, "idempotency.store.putOp"));
        }
    }

    private static void addContractDeltas(
        List<PrDelta> deltas,
        Map<String, BearIr.FieldType> base,
        Map<String, BearIr.FieldType> head,
        boolean input
    ) {
        TreeSet<String> names = new TreeSet<>();
        names.addAll(base.keySet());
        names.addAll(head.keySet());
        for (String name : names) {
            boolean inBase = base.containsKey(name);
            boolean inHead = head.containsKey(name);
            String prefix = input ? "input." : "output.";

            if (!inBase) {
                PrClass clazz = input ? PrClass.ORDINARY : PrClass.BOUNDARY_EXPANDING;
                deltas.add(new PrDelta(
                    clazz,
                    PrCategory.CONTRACT,
                    PrChange.ADDED,
                    prefix + name + ":" + typeToken(head.get(name))
                ));
                continue;
            }
            if (!inHead) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.CONTRACT,
                    PrChange.REMOVED,
                    prefix + name + ":" + typeToken(base.get(name))
                ));
                continue;
            }
            if (base.get(name) != head.get(name)) {
                deltas.add(new PrDelta(
                    PrClass.BOUNDARY_EXPANDING,
                    PrCategory.CONTRACT,
                    PrChange.CHANGED,
                    prefix + name + ":" + typeToken(base.get(name)) + "->" + typeToken(head.get(name))
                ));
            }
        }
    }

    private static String typeToken(BearIr.FieldType type) {
        return type.name().toLowerCase();
    }

    private static PrSurface toPrSurface(BearIr ir) {
        TreeSet<String> ports = new TreeSet<>();
        Map<String, TreeSet<String>> opsByPort = new TreeMap<>();
        for (BearIr.EffectPort port : ir.block().effects().allow()) {
            ports.add(port.port());
            opsByPort.put(port.port(), new TreeSet<>(port.ops()));
        }
        Map<String, String> allowedDeps = new TreeMap<>();
        if (ir.block().impl() != null && ir.block().impl().allowedDeps() != null) {
            for (BearIr.AllowedDep dep : ir.block().impl().allowedDeps()) {
                allowedDeps.put(dep.maven(), dep.version());
            }
        }

        Map<String, BearIr.FieldType> inputs = new TreeMap<>();
        for (BearIr.Field input : ir.block().contract().inputs()) {
            inputs.put(input.name(), input.type());
        }
        Map<String, BearIr.FieldType> outputs = new TreeMap<>();
        for (BearIr.Field output : ir.block().contract().outputs()) {
            outputs.put(output.name(), output.type());
        }

        TreeSet<String> invariants = new TreeSet<>();
        if (ir.block().invariants() != null) {
            for (BearIr.Invariant invariant : ir.block().invariants()) {
                invariants.add(invariant.kind().name().toLowerCase() + ":" + invariant.field());
            }
        }
        return new PrSurface(ports, opsByPort, allowedDeps, inputs, outputs, ir.block().idempotency(), invariants);
    }

    private static PrSurface emptyPrSurface() {
        return new PrSurface(
            new TreeSet<>(),
            new TreeMap<>(),
            new TreeMap<>(),
            new TreeMap<>(),
            new TreeMap<>(),
            null,
            new TreeSet<>()
        );
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
        return new GitResult(process.exitValue(), normalizeLf(stdout), normalizeLf(stderr));
    }

    private static GitResult runGitForPrCheck(Path projectRoot, List<String> gitArgs, String pathLocator)
        throws PrCheckGitException, InterruptedException {
        try {
            return runGit(projectRoot, gitArgs);
        } catch (IOException e) {
            throw new PrCheckGitException("pr-check: IO_ERROR: INTERNAL_IO: " + squash(e.getMessage()), pathLocator);
        }
    }

    private static String squash(String text) {
        if (text == null) {
            return "no details";
        }
        String squashed = normalizeLf(text).replace('\n', ' ').trim();
        return squashed.isEmpty() ? "no details" : squashed;
    }

    private static BoundaryManifest parseManifest(Path path) throws IOException, ManifestParseException {
        String json = Files.readString(path, StandardCharsets.UTF_8).trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new ManifestParseException("MALFORMED_JSON");
        }
        String schemaVersion = extractRequiredString(json, "schemaVersion");
        String target = extractRequiredString(json, "target");
        String block = extractRequiredString(json, "block");
        String irHash = extractRequiredString(json, "irHash");
        String generatorVersion = extractRequiredString(json, "generatorVersion");

        String capabilitiesPayload = extractRequiredArrayPayload(json, "capabilities");
        String allowedDepsPayload = extractOptionalArrayPayload(json, "allowedDeps");
        String invariantsPayload = extractRequiredArrayPayload(json, "invariants");
        Map<String, TreeSet<String>> capabilities = parseCapabilities(capabilitiesPayload);
        Map<String, String> allowedDeps = parseAllowedDeps(allowedDepsPayload);
        TreeSet<String> invariants = parseInvariants(invariantsPayload);
        return new BoundaryManifest(schemaVersion, target, block, irHash, generatorVersion, capabilities, allowedDeps, invariants);
    }

    private static String extractRequiredString(String json, String key) throws ManifestParseException {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\":\"((?:\\\\.|[^\\\\\"])*)\"").matcher(json);
        if (!m.find()) {
            throw new ManifestParseException("MISSING_KEY_" + key);
        }
        return jsonUnescape(m.group(1));
    }

    private static String extractRequiredArrayPayload(String json, String key) throws ManifestParseException {
        int keyIdx = json.indexOf("\"" + key + "\":[");
        if (keyIdx < 0) {
            throw new ManifestParseException("MISSING_KEY_" + key);
        }
        int start = json.indexOf('[', keyIdx);
        if (start < 0) {
            throw new ManifestParseException("MALFORMED_ARRAY_" + key);
        }
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(start + 1, i);
                }
            }
        }
        throw new ManifestParseException("MALFORMED_ARRAY_" + key);
    }

    private static String extractOptionalArrayPayload(String json, String key) throws ManifestParseException {
        int keyIdx = json.indexOf("\"" + key + "\":[");
        if (keyIdx < 0) {
            return null;
        }
        int start = json.indexOf('[', keyIdx);
        if (start < 0) {
            throw new ManifestParseException("MALFORMED_ARRAY_" + key);
        }
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(start + 1, i);
                }
            }
        }
        throw new ManifestParseException("MALFORMED_ARRAY_" + key);
    }

    private static Map<String, TreeSet<String>> parseCapabilities(String payload) throws ManifestParseException {
        Map<String, TreeSet<String>> capabilities = new TreeMap<>();
        if (payload.isBlank()) {
            return capabilities;
        }

        Matcher m = Pattern.compile("\\{\"name\":\"((?:\\\\.|[^\\\\\"])*)\",\"ops\":\\[([^\\]]*)\\]\\}").matcher(payload);
        int count = 0;
        while (m.find()) {
            count++;
            String name = jsonUnescape(m.group(1));
            TreeSet<String> ops = new TreeSet<>();
            String opsPayload = m.group(2);
            if (!opsPayload.isBlank()) {
                Matcher opMatcher = Pattern.compile("\"((?:\\\\.|[^\\\\\"])*)\"").matcher(opsPayload);
                while (opMatcher.find()) {
                    ops.add(jsonUnescape(opMatcher.group(1)));
                }
            }
            capabilities.put(name, ops);
        }

        if (count == 0) {
            throw new ManifestParseException("INVALID_CAPABILITIES");
        }
        return capabilities;
    }

    private static TreeSet<String> parseInvariants(String payload) throws ManifestParseException {
        TreeSet<String> invariants = new TreeSet<>();
        if (payload.isBlank()) {
            return invariants;
        }

        Matcher m = Pattern.compile("\\{\"kind\":\"((?:\\\\.|[^\\\\\"])*)\",\"field\":\"((?:\\\\.|[^\\\\\"])*)\"\\}")
            .matcher(payload);
        int count = 0;
        while (m.find()) {
            count++;
            String kind = jsonUnescape(m.group(1));
            String field = jsonUnescape(m.group(2));
            if ("non_negative".equals(kind)) {
                invariants.add("non_negative:" + field);
            }
        }

        if (count == 0) {
            throw new ManifestParseException("INVALID_INVARIANTS");
        }
        return invariants;
    }

    private static Map<String, String> parseAllowedDeps(String payload) throws ManifestParseException {
        Map<String, String> allowedDeps = new TreeMap<>();
        if (payload == null || payload.isBlank()) {
            return allowedDeps;
        }

        Matcher m = Pattern.compile("\\{\\\"ga\\\":\\\"((?:\\\\.|[^\\\\\\\"])*)\\\",\\\"version\\\":\\\"((?:\\\\.|[^\\\\\\\"])*)\\\"\\}")
            .matcher(payload);
        int count = 0;
        while (m.find()) {
            count++;
            allowedDeps.put(jsonUnescape(m.group(1)), jsonUnescape(m.group(2)));
        }
        if (count == 0) {
            throw new ManifestParseException("INVALID_ALLOWED_DEPS");
        }
        return allowedDeps;
    }

    private static String jsonUnescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                if (next == 'n') {
                    out.append('\n');
                } else if (next == 'r') {
                    out.append('\r');
                } else if (next == 't') {
                    out.append('\t');
                } else {
                    out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
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
                ExitCode.IO,
                diagnostics,
                "CONTAINMENT",
                FailureCode.CONTAINMENT_UNSUPPORTED_TARGET,
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
                ExitCode.IO,
                diagnostics,
                "CONTAINMENT",
                FailureCode.CONTAINMENT_NOT_VERIFIED,
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
                ExitCode.IO,
                diagnostics,
                "CONTAINMENT",
                FailureCode.CONTAINMENT_NOT_VERIFIED,
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
                ExitCode.IO,
                diagnostics,
                "CONTAINMENT",
                FailureCode.CONTAINMENT_NOT_VERIFIED,
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
                ExitCode.IO,
                diagnostics,
                "CONTAINMENT",
                FailureCode.CONTAINMENT_NOT_VERIFIED,
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
        Map<String, byte[]> files = new TreeMap<>();
        if (!Files.isDirectory(root)) {
            return files;
        }

        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    String rel = root.relativize(path).toString().replace('\\', '/');
                    files.put(rel, Files.readAllBytes(path));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
        return files;
    }

    private static List<UndeclaredReachFinding> scanUndeclaredReach(Path projectRoot) throws IOException {
        List<UndeclaredReachFinding> findings = new ArrayList<>();
        if (!Files.isDirectory(projectRoot)) {
            return findings;
        }
        Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                String rel = projectRoot.relativize(file).toString().replace('\\', '/');
                if (!rel.endsWith(".java") || isUndeclaredReachExcluded(rel)) {
                    return FileVisitResult.CONTINUE;
                }
                String content = Files.readString(file, StandardCharsets.UTF_8);
                for (UndeclaredReachSurface surface : UNDECLARED_REACH_SURFACES) {
                    if (surface.matches(content)) {
                        findings.add(new UndeclaredReachFinding(rel, surface.label()));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        findings.sort(
            Comparator.comparing(UndeclaredReachFinding::path)
                .thenComparing(UndeclaredReachFinding::surface)
        );
        return findings;
    }

    private static boolean isUndeclaredReachExcluded(String relPath) {
        return relPath.startsWith("build/")
            || relPath.startsWith(".gradle/")
            || relPath.startsWith("src/test/")
            || relPath.startsWith("build/generated/bear/");
    }

    private static boolean hasOwnedBaselineFiles(Path baselineRoot, Set<String> ownedPrefixes, String markerRelPath) throws IOException {
        if (!Files.isDirectory(baselineRoot)) {
            return false;
        }
        Path marker = baselineRoot.resolve(markerRelPath);
        if (Files.isRegularFile(marker)) {
            return true;
        }
        try (var stream = Files.walk(baselineRoot)) {
            return stream.filter(Files::isRegularFile)
                .map(path -> baselineRoot.relativize(path).toString().replace('\\', '/'))
                .anyMatch(path -> startsWithAny(path, ownedPrefixes));
        }
    }

    private static boolean startsWithAny(String value, Set<String> prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String toBlockKey(String raw) {
        List<String> tokens = splitTokens(raw);
        if (tokens.isEmpty()) {
            return "block";
        }
        return String.join("-", tokens);
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

    private static List<String> splitTokens(String raw) {
        String adjusted = raw.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("[^A-Za-z0-9]+", " ").trim();
        if (adjusted.isEmpty()) {
            return List.of();
        }
        String[] parts = adjusted.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            tokens.add(part.toLowerCase());
        }
        return tokens;
    }

    private static ProjectTestResult runProjectTests(Path projectRoot) throws IOException, InterruptedException {
        Path wrapper = resolveWrapper(projectRoot);
        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("cmd");
            command.add("/c");
            command.add(wrapper.toString());
        } else {
            command.add(wrapper.toString());
        }
        command.add("--no-daemon");
        command.add("test");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);
        Map<String, String> environment = pb.environment();
        if (!environment.containsKey("GRADLE_USER_HOME")) {
            environment.put("GRADLE_USER_HOME", projectRoot.resolve(".bear-gradle-user-home").toString());
        }
        Process process = pb.start();

        String output;
        try (InputStream in = process.getInputStream()) {
            boolean finished = process.waitFor(testTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return new ProjectTestResult(ProjectTestStatus.TIMEOUT, output);
            }
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        if (process.exitValue() == 0) {
            return new ProjectTestResult(ProjectTestStatus.PASSED, output);
        }
        if (isGradleWrapperLockOutput(output)) {
            return new ProjectTestResult(ProjectTestStatus.LOCKED, output);
        }
        return new ProjectTestResult(ProjectTestStatus.FAILED, output);
    }

    private static boolean isGradleWrapperLockOutput(String output) {
        String lower = normalizeLf(output).toLowerCase();
        if (lower.contains(".zip.lck")) {
            return true;
        }
        if (lower.contains("gradlewrappermain") && lower.contains("access is denied")) {
            return true;
        }
        return lower.contains("project_test_gradle_lock_simulated");
    }

    private static String firstGradleLockLine(String output) {
        for (String line : normalizeLf(output).lines().toList()) {
            String lower = line.toLowerCase();
            if (lower.contains(".zip.lck") || lower.contains("access is denied")) {
                return line.trim();
            }
        }
        return null;
    }

    private static Path resolveWrapper(Path projectRoot) throws IOException {
        if (isWindows()) {
            Path wrapper = projectRoot.resolve("gradlew.bat");
            if (!Files.isRegularFile(wrapper)) {
                throw new IOException("PROJECT_TEST_WRAPPER_MISSING: expected " + wrapper);
            }
            return wrapper;
        }

        Path wrapper = projectRoot.resolve("gradlew");
        if (!Files.isRegularFile(wrapper)) {
            throw new IOException("PROJECT_TEST_WRAPPER_MISSING: expected " + wrapper);
        }
        if (!Files.isExecutable(wrapper)) {
            throw new IOException("PROJECT_TEST_WRAPPER_NOT_EXECUTABLE: expected executable " + wrapper + " (run: chmod +x gradlew)");
        }
        return wrapper;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static int testTimeoutSeconds() {
        String raw = System.getProperty("bear.check.testTimeoutSeconds");
        if (raw == null || raw.isBlank()) {
            return 300;
        }
        try {
            int parsed = Integer.parseInt(raw);
            return parsed > 0 ? parsed : 300;
        } catch (NumberFormatException ignored) {
            return 300;
        }
    }

    private static void printTail(PrintStream err, String output) {
        List<String> lines = normalizeLf(output).lines().toList();
        int start = Math.max(0, lines.size() - 40);
        for (int i = start; i < lines.size(); i++) {
            err.println(lines.get(i));
        }
    }

    private static String normalizeLf(String text) {
        return text.replace("\r\n", "\n");
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

    private static void printUsage(PrintStream out) {
        out.println("Usage: bear validate <file>");
        out.println("       bear compile <ir-file> --project <path>");
        out.println("       bear fix <ir-file> --project <path>");
        out.println("       bear fix --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]");
        out.println("       bear check <ir-file> --project <path>");
        out.println("       bear check --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]");
        out.println("       bear pr-check <ir-file> --project <path> --base <ref>");
        out.println("       bear pr-check --all --project <repoRoot> --base <ref> [--blocks <path>] [--only <csv>] [--strict-orphans]");
        out.println("       bear --help");
    }

    private static void maybeFailInternalForTest(String command) {
        String key = "bear.cli.test.failInternal." + command;
        if ("true".equals(System.getProperty(key))) {
            throw new IllegalStateException("INJECTED_INTERNAL_" + command);
        }
    }

    private static int failWithLegacy(
        PrintStream err,
        int exitCode,
        String legacyLine,
        String code,
        String pathLocator,
        String remediation
    ) {
        err.println(legacyLine);
        return fail(err, exitCode, code, pathLocator, remediation);
    }

    private static int fail(PrintStream err, int exitCode, String code, String pathLocator, String remediation) {
        String locator = normalizeLocator(pathLocator);
        err.println("CODE=" + code);
        err.println("PATH=" + locator);
        err.println("REMEDIATION=" + remediation);
        return exitCode;
    }

    private static String normalizeLocator(String raw) {
        if (raw == null) {
            return "internal";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "internal";
        }
        if (looksAbsolute(trimmed)) {
            return "internal";
        }
        return trimmed.replace('\\', '/');
    }

    private static boolean looksAbsolute(String value) {
        String normalized = value.replace('\\', '/');
        if (normalized.startsWith("/")) {
            return true;
        }
        if (normalized.startsWith("//")) {
            return true;
        }
        return normalized.matches("^[A-Za-z]:/.*");
    }

    private static final class ExitCode {
        private static final int OK = 0;
        private static final int VALIDATION = 2;
        private static final int DRIFT = 3;
        private static final int TEST_FAILURE = 4;
        private static final int BOUNDARY_EXPANSION = 5;
        private static final int UNDECLARED_REACH = 6;
        private static final int USAGE = 64;
        private static final int IO = 74;
        private static final int INTERNAL = 70;
    }

    private static final class FailureCode {
        private static final String USAGE_INVALID_ARGS = "USAGE_INVALID_ARGS";
        private static final String USAGE_UNKNOWN_COMMAND = "USAGE_UNKNOWN_COMMAND";
        private static final String IR_VALIDATION = "IR_VALIDATION";
        private static final String IO_ERROR = "IO_ERROR";
        private static final String IO_GIT = "IO_GIT";
        private static final String DRIFT_MISSING_BASELINE = "DRIFT_MISSING_BASELINE";
        private static final String DRIFT_DETECTED = "DRIFT_DETECTED";
        private static final String TEST_FAILURE = "TEST_FAILURE";
        private static final String TEST_TIMEOUT = "TEST_TIMEOUT";
        private static final String BOUNDARY_EXPANSION = "BOUNDARY_EXPANSION";
        private static final String UNDECLARED_REACH = "UNDECLARED_REACH";
        private static final String CONTAINMENT_NOT_VERIFIED = "CONTAINMENT_NOT_VERIFIED";
        private static final String CONTAINMENT_UNSUPPORTED_TARGET = "CONTAINMENT_UNSUPPORTED_TARGET";
        private static final String REPO_MULTI_BLOCK_FAILED = "REPO_MULTI_BLOCK_FAILED";
        private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    }

    private record UndeclaredReachFinding(String path, String surface) {
    }

    private record UndeclaredReachSurface(String label, Pattern pattern) {
        private boolean matches(String content) {
            return pattern.matcher(content).find();
        }
    }

    private static final List<UndeclaredReachSurface> UNDECLARED_REACH_SURFACES = List.of(
        new UndeclaredReachSurface("java.net.http.HttpClient", Pattern.compile("\\bjava\\.net\\.http\\.HttpClient\\b")),
        new UndeclaredReachSurface("java.net.URL#openConnection", Pattern.compile("\\bjava\\.net\\.URL\\b(?s).*\\bopenConnection\\s*\\(")),
        new UndeclaredReachSurface("okhttp3.OkHttpClient", Pattern.compile("\\bokhttp3\\.OkHttpClient\\b")),
        new UndeclaredReachSurface(
            "org.springframework.web.client.RestTemplate",
            Pattern.compile("\\borg\\.springframework\\.web\\.client\\.RestTemplate\\b")
        ),
        new UndeclaredReachSurface("java.net.HttpURLConnection", Pattern.compile("\\bjava\\.net\\.HttpURLConnection\\b"))
    );

    private record AllCheckOptions(
        Path repoRoot,
        Path blocksPath,
        Set<String> onlyNames,
        boolean failFast,
        boolean strictOrphans
    ) {
    }

    private record AllFixOptions(
        Path repoRoot,
        Path blocksPath,
        Set<String> onlyNames,
        boolean failFast,
        boolean strictOrphans
    ) {
    }

    private record AllPrCheckOptions(
        Path repoRoot,
        Path blocksPath,
        Set<String> onlyNames,
        boolean strictOrphans,
        String baseRef
    ) {
    }

    private enum DriftType {
        ADDED("ADDED", 0),
        REMOVED("REMOVED", 1),
        CHANGED("CHANGED", 2);

        private final String label;
        private final int order;

        DriftType(String label, int order) {
            this.label = label;
            this.order = order;
        }
    }

    private record DriftItem(String path, DriftType type) {
    }

    private enum BoundaryType {
        CAPABILITY_ADDED("CAPABILITY_ADDED", 0),
        PURE_DEP_ADDED("PURE_DEP_ADDED", 1),
        PURE_DEP_VERSION_CHANGED("PURE_DEP_VERSION_CHANGED", 2),
        CAPABILITY_OP_ADDED("CAPABILITY_OP_ADDED", 3),
        INVARIANT_RELAXED("INVARIANT_RELAXED", 4);

        private final String label;
        private final int order;

        BoundaryType(String label, int order) {
            this.label = label;
            this.order = order;
        }
    }

    private record BoundarySignal(BoundaryType type, String key) {
    }

    private enum PrClass {
        BOUNDARY_EXPANDING("BOUNDARY_EXPANDING", 0),
        ORDINARY("ORDINARY", 1);

        private final String label;
        private final int order;

        PrClass(String label, int order) {
            this.label = label;
            this.order = order;
        }
    }

    private enum PrCategory {
        PORTS("PORTS", 0),
        ALLOWED_DEPS("ALLOWED_DEPS", 1),
        OPS("OPS", 2),
        IDEMPOTENCY("IDEMPOTENCY", 3),
        CONTRACT("CONTRACT", 4),
        INVARIANTS("INVARIANTS", 5);

        private final String label;
        private final int order;

        PrCategory(String label, int order) {
            this.label = label;
            this.order = order;
        }
    }

    private enum PrChange {
        CHANGED("CHANGED", 0),
        ADDED("ADDED", 1),
        REMOVED("REMOVED", 2);

        private final String label;
        private final int order;

        PrChange(String label, int order) {
            this.label = label;
            this.order = order;
        }
    }

    private record PrDelta(PrClass clazz, PrCategory category, PrChange change, String key) {
    }

    private record PrSurface(
        TreeSet<String> ports,
        Map<String, TreeSet<String>> opsByPort,
        Map<String, String> allowedDeps,
        Map<String, BearIr.FieldType> inputs,
        Map<String, BearIr.FieldType> outputs,
        BearIr.Idempotency idempotency,
        TreeSet<String> invariants
    ) {
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

    private record BoundaryManifest(
        String schemaVersion,
        String target,
        String block,
        String irHash,
        String generatorVersion,
        Map<String, TreeSet<String>> capabilities,
        Map<String, String> allowedDeps,
        TreeSet<String> invariants
    ) {
    }

    private static final class ManifestParseException extends Exception {
        private final String reasonCode;

        private ManifestParseException(String reasonCode) {
            super(reasonCode);
            this.reasonCode = reasonCode;
        }

        private String reasonCode() {
            return reasonCode;
        }
    }

    private enum ProjectTestStatus {
        PASSED,
        FAILED,
        TIMEOUT,
        LOCKED
    }

    private record ProjectTestResult(ProjectTestStatus status, String output) {
    }
}


