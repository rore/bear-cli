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
    private static final String CHECK_BLOCKED_MARKER_RELATIVE = "build/bear/check.blocked.marker";
    private static final String CHECK_BLOCKED_REASON_LOCK = "LOCK";
    private static final String CHECK_BLOCKED_REASON_BOOTSTRAP = "BOOTSTRAP_IO";
    private static final Pattern DIRECT_IMPL_IMPORT_PATTERN = Pattern.compile(
        "\\bimport\\s+blocks(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.impl\\.[A-Za-z_][A-Za-z0-9_]*Impl\\s*;"
    );
    private static final Pattern DIRECT_IMPL_NEW_PATTERN = Pattern.compile(
        "\\bnew\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\s*\\("
    );
    private static final Pattern DIRECT_IMPL_TYPE_CAST_PATTERN = Pattern.compile(
        "\\(\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\s*\\)"
    );
    private static final Pattern DIRECT_IMPL_VAR_DECL_PATTERN = Pattern.compile(
        "(?m)\\b(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\b\\s+[A-Za-z_][A-Za-z0-9_]*\\s*(?:[=;,)])"
    );
    private static final Pattern DIRECT_IMPL_EXTENDS_IMPL_PATTERN = Pattern.compile(
        "\\bextends\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\b"
    );
    private static final Pattern DIRECT_IMPL_IMPLEMENTS_IMPL_PATTERN = Pattern.compile(
        "\\bimplements\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\b"
    );
    private static final Pattern SUPPRESSION_PATTERN = Pattern.compile("(?m)^\\s*//\\s*BEAR:PORT_USED\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");

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
            case "unblock" -> runUnblock(args, out, err);
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
            BlockIdentityResolution identity = BlockIdentityResolver.resolveSingleCommandIdentity(
                irFile,
                projectRoot,
                normalized.block().name()
            );
            target.compile(normalized, projectRoot, identity.blockKey());

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
        } catch (BlockIndexValidationException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                "index: VALIDATION_ERROR: " + e.getMessage(),
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear compile <ir-file> --project <path>`."
            );
        } catch (BlockIdentityResolutionException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                e.line(),
                FailureCode.IR_VALIDATION,
                e.path(),
                e.remediation()
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
        FixResult result = executeFix(irFile, projectRoot, null, null);
        return emitFixResult(result, out, err);
    }

    private static int runFixAll(String[] args, PrintStream out, PrintStream err) {
        return FixAllCommandService.runFixAll(args, out, err);
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
        CheckBlockedState blockedState = readCheckBlockedState(projectRoot);
        if (blockedState.blocked()) {
            String line = "check: IO_ERROR: CHECK_BLOCKED: " + blockedState.summary();
            return failWithLegacy(
                err,
                ExitCode.IO,
                line,
                FailureCode.IO_ERROR,
                CHECK_BLOCKED_MARKER_RELATIVE,
                "Run `bear unblock --project <path>` after fixing lock/bootstrap IO and rerun `bear check`."
            );
        }
        CheckResult result = executeCheck(irFile, projectRoot, true, null, null);
        return emitCheckResult(result, out, err);
    }

    private static int runUnblock(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
        }
        if (args.length != 3 || !"--project".equals(args[1])) {
            return failWithLegacy(
                err,
                ExitCode.USAGE,
                "usage: INVALID_ARGS: expected: bear unblock --project <path>",
                FailureCode.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear unblock --project <path>` with the expected arguments."
            );
        }
        Path projectRoot = Path.of(args[2]);
        try {
            clearCheckBlockedMarker(projectRoot);
            out.println("unblock: OK");
            return ExitCode.OK;
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "io: IO_ERROR: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                CHECK_BLOCKED_MARKER_RELATIVE,
                "Ensure the project path is writable, then rerun `bear unblock --project <path>`."
            );
        }
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
        if (Files.isRegularFile(headIrPath)) {
            try {
                BearIrParser parser = new BearIrParser();
                BearIrValidator validator = new BearIrValidator();
                BearIrNormalizer normalizer = new BearIrNormalizer();
                BearIr normalized = normalizer.normalize(parseAndValidateIr(parser, validator, headIrPath));
                BlockIdentityResolver.resolveSingleCommandIdentity(headIrPath, projectRoot, normalized.block().name());
            } catch (BearIrValidationException e) {
                return failWithLegacy(
                    err,
                    ExitCode.VALIDATION,
                    e.formatLine(),
                    FailureCode.IR_VALIDATION,
                    e.path(),
                    "Fix the IR issue at the reported path and rerun `bear pr-check <ir-file> --project <path> --base <ref>`."
                );
            } catch (BlockIndexValidationException e) {
                return failWithLegacy(
                    err,
                    ExitCode.VALIDATION,
                    "index: VALIDATION_ERROR: " + e.getMessage(),
                    FailureCode.IR_VALIDATION,
                    e.path(),
                    "Fix `bear.blocks.yaml` and rerun `bear pr-check`."
                );
            } catch (BlockIdentityResolutionException e) {
                return failWithLegacy(
                    err,
                    ExitCode.VALIDATION,
                    e.line(),
                    FailureCode.IR_VALIDATION,
                    e.path(),
                    e.remediation()
                );
            } catch (IOException e) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "io: IO_ERROR: " + squash(e.getMessage()),
                    FailureCode.IO_ERROR,
                    "input.ir",
                    "Ensure the IR file and block index are readable, then rerun `bear pr-check`."
                );
            }
        }
        PrCheckResult result = executePrCheck(projectRoot, repoRelativePath, baseRef);
        return emitPrCheckResult(result, out, err);
    }

    private static int runCheckAll(String[] args, PrintStream out, PrintStream err) {
        return CheckAllCommandService.runCheckAll(args, out, err);
    }

    private static int runPrCheckAll(String[] args, PrintStream out, PrintStream err) {
        return PrCheckAllCommandService.runPrCheckAll(args, out, err);
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

    static FixResult executeFix(Path irFile, Path projectRoot, String expectedBlockKey, String expectedBlockLocator) {
        try {
            maybeFailInternalForTest("fix");
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

            target.compile(normalized, projectRoot, identity.blockKey());
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
        } catch (BlockIndexValidationException e) {
            return fixFailure(
                ExitCode.VALIDATION,
                List.of("index: VALIDATION_ERROR: " + e.getMessage()),
                "VALIDATION",
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix `bear.blocks.yaml` and rerun `bear fix`.",
                "index: VALIDATION_ERROR: " + e.getMessage()
            );
        } catch (BlockIdentityResolutionException e) {
            return fixFailure(
                ExitCode.VALIDATION,
                List.of(e.line()),
                "VALIDATION",
                FailureCode.IR_VALIDATION,
                e.path(),
                e.remediation(),
                e.line()
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
        String expectedBlockKey,
        String expectedBlockLocator
    ) {
        return CheckCommandService.executeCheck(
            irFile,
            projectRoot,
            runReachAndTests,
            expectedBlockKey,
            expectedBlockLocator
        );
    }

    private static PrCheckResult executePrCheck(Path projectRoot, String repoRelativePath, String baseRef) {
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

    static BlockExecutionResult toCheckBlockResult(BlockIndexEntry block, CheckResult result) {
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

    static BlockExecutionResult toFixBlockResult(BlockIndexEntry block, FixResult result) {
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
            base.deltaLines()
        );
    }

    static String validateIndexIrNameMatch(Path irFile, String expectedBlockKey, String expectedBlockLocator) {
        try {
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();
            BearIr normalized = normalizer.normalize(parseAndValidateIr(parser, validator, irFile));
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

    private static BearIr parseAndValidateIr(BearIrParser parser, BearIrValidator validator, Path path) throws IOException {
        BearIr ir = parser.parse(path);
        validator.validate(ir);
        return ir;
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

    private static String squash(String text) {
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
        return DriftAnalyzer.readRegularFiles(root);
    }

    private static List<UndeclaredReachFinding> scanUndeclaredReach(Path projectRoot) throws IOException {
        return UndeclaredReachScanner.scanUndeclaredReach(projectRoot);
    }

    private static boolean isUndeclaredReachExcluded(String relPath) {
        return UndeclaredReachScanner.isUndeclaredReachExcluded(relPath);
    }

    private static List<BoundaryBypassFinding> scanBoundaryBypass(Path projectRoot, List<WiringManifest> manifests) throws IOException {
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
        Path marker = projectRoot.resolve(CHECK_BLOCKED_MARKER_RELATIVE);
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
        Path marker = projectRoot.resolve(CHECK_BLOCKED_MARKER_RELATIVE);
        Files.createDirectories(marker.getParent());
        String safeReason = (reason == null || reason.isBlank()) ? "UNKNOWN" : reason;
        String safeDetail = (detail == null || detail.isBlank()) ? "no details" : detail.trim();
        String content = "reason=" + safeReason + "\n" + "detail=" + safeDetail + "\n";
        Files.writeString(marker, content, StandardCharsets.UTF_8);
    }

    static void clearCheckBlockedMarker(Path projectRoot) throws IOException {
        Path marker = projectRoot.resolve(CHECK_BLOCKED_MARKER_RELATIVE);
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

    private static void printUsage(PrintStream out) {
        out.println("Usage: bear validate <file>");
        out.println("       bear compile <ir-file> --project <path>");
        out.println("       bear fix <ir-file> --project <path>");
        out.println("       bear fix --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]");
        out.println("       bear check <ir-file> --project <path>");
        out.println("       bear check --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]");
        out.println("       bear unblock --project <path>");
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

    static int failWithLegacy(
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

    static int fail(PrintStream err, int exitCode, String code, String pathLocator, String remediation) {
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
        private static final int BOUNDARY_BYPASS = 6;
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
        private static final String BOUNDARY_BYPASS = "BOUNDARY_BYPASS";
        private static final String CONTAINMENT_NOT_VERIFIED = "CONTAINMENT_NOT_VERIFIED";
        private static final String CONTAINMENT_UNSUPPORTED_TARGET = "CONTAINMENT_UNSUPPORTED_TARGET";
        private static final String REPO_MULTI_BLOCK_FAILED = "REPO_MULTI_BLOCK_FAILED";
        private static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    }

}


