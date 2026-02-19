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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BearCli {
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

    private static int runCheck(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
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

            if (!Files.isDirectory(baselineRoot) || !hasRegularFiles(baselineRoot)) {
                err.println("drift: MISSING_BASELINE: build/generated/bear (run: bear compile "
                    + irFile + " --project " + projectRoot + ")");
                return fail(
                    err,
                    ExitCode.DRIFT,
                    FailureCode.DRIFT_MISSING_BASELINE,
                    "build/generated/bear",
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
                );
            }

            tempRoot = Files.createTempDirectory("bear-check-");
            target.compile(normalized, tempRoot);
            Path candidateRoot = tempRoot.resolve("build").resolve("generated").resolve("bear");
            Path baselineManifestPath = baselineRoot.resolve("bear.surface.json");
            Path candidateManifestPath = candidateRoot.resolve("bear.surface.json");

            applyCandidateManifestTestMode(candidateManifestPath);

            List<String> manifestWarnings = new ArrayList<>();
            BoundaryManifest baselineManifest = null;
            if (!Files.isRegularFile(baselineManifestPath)) {
                manifestWarnings.add("check: BASELINE_MANIFEST_MISSING: " + baselineManifestPath);
            } else {
                try {
                    baselineManifest = parseManifest(baselineManifestPath);
                } catch (ManifestParseException e) {
                    manifestWarnings.add("check: BASELINE_MANIFEST_INVALID: " + e.reasonCode());
                }
            }
            if (!Files.isRegularFile(candidateManifestPath)) {
                return failWithLegacy(
                    err,
                    ExitCode.INTERNAL,
                    "internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_MISSING",
                    FailureCode.INTERNAL_ERROR,
                    "build/generated/bear/bear.surface.json",
                    "Capture stderr and file an issue against bear-cli."
                );
            }
            BoundaryManifest candidateManifest;
            try {
                candidateManifest = parseManifest(candidateManifestPath);
            } catch (ManifestParseException e) {
                return failWithLegacy(
                    err,
                    ExitCode.INTERNAL,
                    "internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_INVALID:" + e.reasonCode(),
                    FailureCode.INTERNAL_ERROR,
                    "build/generated/bear/bear.surface.json",
                    "Capture stderr and file an issue against bear-cli."
                );
            }

            List<BoundarySignal> boundarySignals = List.of();
            if (baselineManifest != null) {
                if (!baselineManifest.irHash().equals(candidateManifest.irHash())
                    || !baselineManifest.generatorVersion().equals(candidateManifest.generatorVersion())) {
                    manifestWarnings.add("check: BASELINE_STAMP_MISMATCH: irHash/generatorVersion differ; classification may be stale");
                }
                boundarySignals = computeBoundarySignals(baselineManifest, candidateManifest);
            }

            List<DriftItem> drift = computeDrift(baselineRoot, candidateRoot);
            for (String warning : manifestWarnings) {
                err.println(warning);
            }
            for (BoundarySignal signal : boundarySignals) {
                err.println("boundary: EXPANSION: " + signal.type().label + ": " + signal.key());
            }
            if (!drift.isEmpty()) {
                for (DriftItem item : drift) {
                    err.println("drift: " + item.type().label + ": " + item.path());
                }
                return fail(
                    err,
                    ExitCode.DRIFT,
                    FailureCode.DRIFT_DETECTED,
                    "build/generated/bear",
                    "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
                );
            }

            List<UndeclaredReachFinding> undeclaredReach = scanUndeclaredReach(projectRoot);
            if (!undeclaredReach.isEmpty()) {
                for (UndeclaredReachFinding finding : undeclaredReach) {
                    err.println("check: UNDECLARED_REACH: " + finding.path() + ": " + finding.surface());
                }
                return fail(
                    err,
                    ExitCode.UNDECLARED_REACH,
                    FailureCode.UNDECLARED_REACH,
                    undeclaredReach.get(0).path(),
                    "Declare a port/op in IR, run bear compile, and route call through generated port interface."
                );
            }

            ProjectTestResult testResult = runProjectTests(projectRoot);
            if (testResult.status == ProjectTestStatus.FAILED) {
                err.println("check: TEST_FAILED: project tests failed");
                printTail(err, testResult.output);
                return fail(
                    err,
                    ExitCode.TEST_FAILURE,
                    FailureCode.TEST_FAILURE,
                    "project.tests",
                    "Fix project tests and rerun `bear check <ir-file> --project <path>`."
                );
            }
            if (testResult.status == ProjectTestStatus.TIMEOUT) {
                err.println("check: TEST_TIMEOUT: project tests exceeded " + testTimeoutSeconds() + "s");
                printTail(err, testResult.output);
                return fail(
                    err,
                    ExitCode.TEST_FAILURE,
                    FailureCode.TEST_TIMEOUT,
                    "project.tests",
                    "Reduce test runtime or increase timeout, then rerun `bear check <ir-file> --project <path>`."
                );
            }

            out.println("check: OK");
            return ExitCode.OK;
        } catch (BearIrValidationException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                e.formatLine(),
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear check <ir-file> --project <path>`."
            );
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "io: IO_ERROR: " + e.getMessage(),
                FailureCode.IO_ERROR,
                "project.root",
                "Ensure project paths are accessible (including Gradle wrapper), then rerun `bear check`."
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
        } finally {
            if (tempRoot != null) {
                deleteRecursivelyBestEffort(tempRoot);
            }
        }
    }

    private static int runPrCheck(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            printUsage(out);
            return ExitCode.OK;
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
        Path headIrPath = projectRoot.resolve(normalizedRelative).normalize();
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
        String repoRelativePath = normalizedRelative.toString().replace('\\', '/');
        if (!Files.isRegularFile(headIrPath)) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "pr-check: IO_ERROR: READ_HEAD_FAILED: " + repoRelativePath,
                FailureCode.IO_ERROR,
                repoRelativePath,
                "Ensure the IR file exists at HEAD and rerun `bear pr-check`."
            );
        }

        Path tempRoot = null;
        try {
            maybeFailInternalForTest("pr-check");
            BearIrParser parser = new BearIrParser();
            BearIrValidator validator = new BearIrValidator();
            BearIrNormalizer normalizer = new BearIrNormalizer();

            BearIr head = normalizer.normalize(parseAndValidateIr(parser, validator, headIrPath));

            GitResult isRepoResult = runGitForPrCheck(projectRoot, List.of("rev-parse", "--is-inside-work-tree"), "git.repo");
            if (isRepoResult.exitCode() != 0 || !"true".equals(isRepoResult.stdout().trim())) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "pr-check: IO_ERROR: NOT_A_GIT_REPO: " + projectRoot,
                    FailureCode.IO_GIT,
                    "git.repo",
                    "Run `bear pr-check` from a git working tree with a valid project path."
                );
            }

            GitResult mergeBaseResult = runGitForPrCheck(projectRoot, List.of("merge-base", "HEAD", baseRef), "git.baseRef");
            if (mergeBaseResult.exitCode() != 0) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "pr-check: IO_ERROR: MERGE_BASE_FAILED: " + baseRef,
                    FailureCode.IO_GIT,
                    "git.baseRef",
                    "Ensure base ref exists and is fetchable, then rerun `bear pr-check`."
                );
            }
            String mergeBase = mergeBaseResult.stdout().trim();
            if (mergeBase.isBlank()) {
                return failWithLegacy(
                    err,
                    ExitCode.IO,
                    "pr-check: IO_ERROR: MERGE_BASE_EMPTY: unable to resolve merge base",
                    FailureCode.IO_GIT,
                    "git.baseRef",
                    "Ensure base ref resolves to a merge base with HEAD, then rerun `bear pr-check`."
                );
            }

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
                    return failWithLegacy(
                        err,
                        ExitCode.IO,
                        "pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath,
                        FailureCode.IO_GIT,
                        repoRelativePath,
                        "Ensure base ref and IR path are readable in git history, then rerun `bear pr-check`."
                    );
                }
                if (existsResult.stdout().trim().isEmpty()) {
                    err.println("pr-check: INFO: BASE_IR_MISSING_AT_MERGE_BASE: " + repoRelativePath + ": treated_as_empty_base");
                } else {
                    return failWithLegacy(
                        err,
                        ExitCode.IO,
                        "pr-check: IO_ERROR: BASE_IR_LOOKUP_FAILED: " + repoRelativePath,
                        FailureCode.IO_GIT,
                        repoRelativePath,
                        "Ensure base ref and IR path are readable in git history, then rerun `bear pr-check`."
                    );
                }
            } else {
                GitResult showResult = runGitForPrCheck(
                    projectRoot,
                    List.of("show", mergeBase + ":" + repoRelativePath),
                    repoRelativePath
                );
                if (showResult.exitCode() != 0) {
                    return failWithLegacy(
                        err,
                        ExitCode.IO,
                        "pr-check: IO_ERROR: BASE_IR_READ_FAILED: " + repoRelativePath,
                        FailureCode.IO_GIT,
                        repoRelativePath,
                        "Ensure base IR snapshot is readable from git history, then rerun `bear pr-check`."
                    );
                }
                tempRoot = Files.createTempDirectory("bear-pr-check-");
                Path baseTempIr = tempRoot.resolve("base.bear.yaml");
                Files.writeString(baseTempIr, showResult.stdout(), StandardCharsets.UTF_8);
                base = normalizer.normalize(parseAndValidateIr(parser, validator, baseTempIr));
            }

            List<PrDelta> deltas = computePrDeltas(base, head);
            for (PrDelta delta : deltas) {
                err.println("pr-delta: " + delta.clazz().label + ": " + delta.category().label + ": " + delta.change().label + ": " + delta.key());
            }

            boolean hasBoundary = deltas.stream().anyMatch(delta -> delta.clazz() == PrClass.BOUNDARY_EXPANDING);
            if (hasBoundary) {
                err.println("pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED");
                return fail(
                    err,
                    ExitCode.BOUNDARY_EXPANSION,
                    FailureCode.BOUNDARY_EXPANSION,
                    repoRelativePath,
                    "Review boundary-expanding deltas and route through explicit boundary review."
                );
            }

            out.println("pr-check: OK: NO_BOUNDARY_EXPANSION");
            return ExitCode.OK;
        } catch (PrCheckGitException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                e.legacyLine(),
                FailureCode.IO_GIT,
                e.pathLocator(),
                "Resolve git invocation/base-reference issues and rerun `bear pr-check`."
            );
        } catch (BearIrValidationException e) {
            return failWithLegacy(
                err,
                ExitCode.VALIDATION,
                e.formatLine(),
                FailureCode.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear pr-check`."
            );
        } catch (IOException e) {
            return failWithLegacy(
                err,
                ExitCode.IO,
                "pr-check: IO_ERROR: INTERNAL_IO: " + squash(e.getMessage()),
                FailureCode.IO_ERROR,
                "internal",
                "Ensure local filesystem paths are accessible, then rerun `bear pr-check`."
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failWithLegacy(
                err,
                ExitCode.IO,
                "pr-check: IO_ERROR: INTERRUPTED",
                FailureCode.IO_GIT,
                "git.repo",
                "Retry `bear pr-check`; if interruption persists, rerun in a stable shell/CI environment."
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
        } finally {
            if (tempRoot != null) {
                deleteRecursivelyBestEffort(tempRoot);
            }
        }
    }

    private static List<DriftItem> computeDrift(Path baselineRoot, Path candidateRoot) throws IOException {
        Map<String, byte[]> baseline = readRegularFiles(baselineRoot);
        Map<String, byte[]> candidate = readRegularFiles(candidateRoot);

        TreeSet<String> allPaths = new TreeSet<>();
        allPaths.addAll(baseline.keySet());
        allPaths.addAll(candidate.keySet());

        List<DriftItem> drift = new ArrayList<>();
        for (String path : allPaths) {
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
        return new PrSurface(ports, opsByPort, inputs, outputs, ir.block().idempotency(), invariants);
    }

    private static PrSurface emptyPrSurface() {
        return new PrSurface(
            new TreeSet<>(),
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
        String invariantsPayload = extractRequiredArrayPayload(json, "invariants");
        Map<String, TreeSet<String>> capabilities = parseCapabilities(capabilitiesPayload);
        TreeSet<String> invariants = parseInvariants(invariantsPayload);
        return new BoundaryManifest(schemaVersion, target, block, irHash, generatorVersion, capabilities, invariants);
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
        return new ProjectTestResult(ProjectTestStatus.FAILED, output);
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
        out.println("       bear check <ir-file> --project <path>");
        out.println("       bear pr-check <ir-file> --project <path> --base <ref>");
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
        CAPABILITY_OP_ADDED("CAPABILITY_OP_ADDED", 1),
        INVARIANT_RELAXED("INVARIANT_RELAXED", 2);

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
        OPS("OPS", 1),
        IDEMPOTENCY("IDEMPOTENCY", 2),
        CONTRACT("CONTRACT", 3),
        INVARIANTS("INVARIANTS", 4);

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
        TIMEOUT
    }

    private record ProjectTestResult(ProjectTestStatus status, String output) {
    }
}
