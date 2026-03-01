package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrValidationException;
import com.bear.kernel.ir.BearIrYamlEmitter;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class BearCliCommandHandlers {
    private static final IrPipeline IR_PIPELINE = new DefaultIrPipeline();
    private static final int UNBLOCK_DELETE_ATTEMPTS = 3;
    private static final long UNBLOCK_RETRY_BACKOFF_MILLIS = 200L;

    private BearCliCommandHandlers() {
    }

    static int runValidate(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            BearCli.printUsage(out);
            return CliCodes.EXIT_OK;
        }

        if (args.length != 2) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: expected: bear validate <file>",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear validate <file>` with exactly one IR file path."
            );
        }

        Path file = Path.of(args[1]);
        try {
            BearCli.maybeFailInternalForTest("validate");
            BearIrYamlEmitter emitter = new BearIrYamlEmitter();
            BearIr normalized = IR_PIPELINE.parseValidateNormalize(file);

            out.print(emitter.toCanonicalYaml(normalized));
            return CliCodes.EXIT_OK;
        } catch (BearIrValidationException e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_VALIDATION,
                e.formatLine(),
                CliCodes.IR_VALIDATION,
                e.path(),
                "Fix the IR issue at the reported path and rerun `bear validate <file>`."
            );
        } catch (IOException e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_IO,
                "io: IO_ERROR: " + file,
                CliCodes.IO_ERROR,
                "input.ir",
                "Ensure the IR file exists and is readable, then rerun `bear validate <file>`."
            );
        } catch (Exception e) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_INTERNAL,
                "internal: INTERNAL_ERROR:",
                CliCodes.INTERNAL_ERROR,
                "internal",
                "Capture stderr and file an issue against bear-cli."
            );
        }
    }

    static int runCompile(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            BearCli.printUsage(out);
            return CliCodes.EXIT_OK;
        }

        if (args.length >= 2 && "--all".equals(args[1])) {
            return runCompileAll(args, out, err);
        }

        if (args.length != 4 || !"--project".equals(args[2])) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: expected: bear compile <ir-file> --project <path>",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear compile <ir-file> --project <path>` with the expected arguments."
            );
        }

        Path irFile = Path.of(args[1]);
        Path projectRoot = Path.of(args[3]);

        CompileResult result = BearCli.executeCompile(irFile, projectRoot, null, null);
        return BearCli.emitCompileResult(result, out, err);
    }

    static int runFix(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            BearCli.printUsage(out);
            return CliCodes.EXIT_OK;
        }

        if (args.length >= 2 && "--all".equals(args[1])) {
            return runFixAll(args, out, err);
        }

        if (args.length != 4 || !"--project".equals(args[2])) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: expected: bear fix <ir-file> --project <path>",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear fix <ir-file> --project <path>` with the expected arguments."
            );
        }

        Path irFile = Path.of(args[1]);
        Path projectRoot = Path.of(args[3]);
        FixResult result = BearCli.executeFix(irFile, projectRoot, null, null);
        return BearCli.emitFixResult(result, out, err);
    }

    static int runCheck(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            BearCli.printUsage(out);
            return CliCodes.EXIT_OK;
        }

        if (args.length >= 2 && "--all".equals(args[1])) {
            return runCheckAll(args, out, err);
        }

        Path irFile = null;
        Path projectRoot = null;
        boolean strictHygiene = false;
        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if ("--project".equals(token)) {
                if (i + 1 >= args.length) {
                    return BearCli.failWithLegacy(
                        err,
                        CliCodes.EXIT_USAGE,
                        "usage: INVALID_ARGS: expected value after --project",
                        CliCodes.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear check <ir-file> --project <path> [--strict-hygiene]` with the expected arguments."
                    );
                }
                projectRoot = Path.of(args[++i]);
                continue;
            }
            if ("--strict-hygiene".equals(token)) {
                strictHygiene = true;
                continue;
            }
            if (token.startsWith("--") || irFile != null) {
                return BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_USAGE,
                    "usage: INVALID_ARGS: unexpected argument: " + token,
                    CliCodes.USAGE_INVALID_ARGS,
                    "cli.args",
                    "Run `bear check <ir-file> --project <path> [--strict-hygiene]` with the expected arguments."
                );
            }
            irFile = Path.of(token);
        }

        if (irFile == null || projectRoot == null) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: expected: bear check <ir-file> --project <path> [--strict-hygiene]",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear check <ir-file> --project <path> [--strict-hygiene]` with the expected arguments."
            );
        }

        CheckResult result = BearCli.executeCheck(irFile, projectRoot, true, strictHygiene, null, null);
        return BearCli.emitCheckResult(result, out, err);
    }

    static int runUnblock(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            BearCli.printUsage(out);
            return CliCodes.EXIT_OK;
        }
        if (args.length != 3 || !"--project".equals(args[1])) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: expected: bear unblock --project <path>",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear unblock --project <path>` with the expected arguments."
            );
        }
        Path projectRoot = Path.of(args[2]);
        Path marker = projectRoot.resolve(CheckBlockedMarker.RELATIVE_PATH);
        if (!Files.isRegularFile(marker)) {
            out.println("unblock: OK");
            return CliCodes.EXIT_OK;
        }

        IOException lastDeleteError = null;
        for (int attempt = 1; attempt <= UNBLOCK_DELETE_ATTEMPTS; attempt++) {
            try {
                BearCli.maybeInjectUnblockDeleteFailureForTest(attempt);
                BearCli.clearCheckBlockedMarker(projectRoot);
                out.println("unblock: OK");
                return CliCodes.EXIT_OK;
            } catch (IOException e) {
                lastDeleteError = e;
                if (attempt < UNBLOCK_DELETE_ATTEMPTS) {
                    try {
                        Thread.sleep(UNBLOCK_RETRY_BACKOFF_MILLIS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        lastDeleteError = new IOException("unblock interrupted during retry backoff", interrupted);
                        break;
                    }
                }
            }
        }

        String attrs = BearCli.markerAttributes(marker);
        String line = "io: IO_ERROR: UNBLOCK_LOCKED: failed to delete check blocked marker: "
            + BearCli.squash(lastDeleteError == null ? "unknown IO error" : lastDeleteError.getMessage())
            + "; ATTRS="
            + attrs;
        return BearCli.failWithLegacy(
            err,
            CliCodes.EXIT_IO,
            line,
            CliCodes.UNBLOCK_LOCKED,
            CheckBlockedMarker.RELATIVE_PATH,
            "Close processes locking the marker and rerun `bear unblock --project <path>`."
        );
    }

    static int runPrCheck(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 2 && ("--help".equals(args[1]) || "-h".equals(args[1]))) {
            BearCli.printUsage(out);
            return CliCodes.EXIT_OK;
        }
        if (args.length >= 2 && "--all".equals(args[1])) {
            return runPrCheckAll(args, out, err);
        }
        if (args.length != 6 || !"--project".equals(args[2]) || !"--base".equals(args[4])) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: expected: bear pr-check <ir-file> --project <path> --base <ref>",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear pr-check <ir-file> --project <path> --base <ref>` with the expected arguments."
            );
        }

        String irArg = args[1];
        Path irPath = Path.of(irArg);
        if (irPath.isAbsolute()) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: ir-file must be repo-relative",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative `ir-file` path for `bear pr-check`."
            );
        }
        Path normalizedRelative = irPath.normalize();
        if (normalizedRelative.startsWith("..") || normalizedRelative.toString().isBlank()) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: ir-file must be repo-relative",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative `ir-file` path for `bear pr-check`."
            );
        }

        Path projectRoot = Path.of(args[3]).toAbsolutePath().normalize();
        String baseRef = args[5];
        String repoRelativePath = normalizedRelative.toString().replace('\\', '/');
        Path headIrPath = projectRoot.resolve(repoRelativePath).normalize();
        if (!headIrPath.startsWith(projectRoot)) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: ir-file must be repo-relative",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative `ir-file` path for `bear pr-check`."
            );
        }
        if (Files.isRegularFile(headIrPath)) {
            try {
                BearIr normalized = IR_PIPELINE.parseValidateNormalize(headIrPath);
                BlockIdentityResolver.resolveSingleCommandIdentity(headIrPath, projectRoot, normalized.block().name());
            } catch (BearIrValidationException e) {
                return BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_VALIDATION,
                    e.formatLine(),
                    CliCodes.IR_VALIDATION,
                    e.path(),
                    "Fix the IR issue at the reported path and rerun `bear pr-check <ir-file> --project <path> --base <ref>`."
                );
            } catch (BlockIndexValidationException e) {
                return BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_VALIDATION,
                    "index: VALIDATION_ERROR: " + e.getMessage(),
                    CliCodes.IR_VALIDATION,
                    e.path(),
                    "Fix `bear.blocks.yaml` and rerun `bear pr-check`."
                );
            } catch (BlockIdentityResolutionException e) {
                return BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_VALIDATION,
                    e.line(),
                    CliCodes.IR_VALIDATION,
                    e.path(),
                    e.remediation()
                );
            } catch (IOException e) {
                return BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_IO,
                    "io: IO_ERROR: " + BearCli.squash(e.getMessage()),
                    CliCodes.IO_ERROR,
                    "input.ir",
                    "Ensure the IR file and block index are readable, then rerun `bear pr-check`."
                );
            }
        }
        PrCheckResult result = BearCli.executePrCheck(projectRoot, repoRelativePath, baseRef);
        return BearCli.emitPrCheckResult(BearCli.enforcePrCheckExitEnvelope(result, repoRelativePath), out, err);
    }

    private static int runFixAll(String[] args, PrintStream out, PrintStream err) {
        return FixAllCommandService.runFixAll(args, out, err);
    }

    private static int runCompileAll(String[] args, PrintStream out, PrintStream err) {
        return CompileAllCommandService.runCompileAll(args, out, err);
    }

    private static int runCheckAll(String[] args, PrintStream out, PrintStream err) {
        return CheckAllCommandService.runCheckAll(args, out, err);
    }

    private static int runPrCheckAll(String[] args, PrintStream out, PrintStream err) {
        return PrCheckAllCommandService.runPrCheckAll(args, out, err);
    }
}
