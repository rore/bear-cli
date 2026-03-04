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

        Path irFile = null;
        Path projectRoot = null;
        Path indexPath = null;
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
                        "Run `bear compile <ir-file> --project <path> [--index <path>]` with the expected arguments."
                    );
                }
                projectRoot = Path.of(args[++i]);
                continue;
            }
            if ("--index".equals(token)) {
                if (i + 1 >= args.length) {
                    return BearCli.failWithLegacy(
                        err,
                        CliCodes.EXIT_USAGE,
                        "usage: INVALID_ARGS: expected value after --index",
                        CliCodes.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear compile <ir-file> --project <path> [--index <path>]` with the expected arguments."
                    );
                }
                indexPath = Path.of(args[++i]);
                continue;
            }
            if (token.startsWith("--") || irFile != null) {
                return BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_USAGE,
                    "usage: INVALID_ARGS: unexpected argument: " + token,
                    CliCodes.USAGE_INVALID_ARGS,
                    "cli.args",
                    "Run `bear compile <ir-file> --project <path> [--index <path>]` with the expected arguments."
                );
            }
            irFile = Path.of(token);
        }

        if (irFile == null || projectRoot == null) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: expected: bear compile <ir-file> --project <path> [--index <path>]",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear compile <ir-file> --project <path> [--index <path>]` with the expected arguments."
            );
        }

        CompileResult result = BearCli.executeCompile(irFile, projectRoot, null, null, indexPath);
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

        Path irFile = null;
        Path projectRoot = null;
        Path indexPath = null;
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
                        "Run `bear fix <ir-file> --project <path> [--index <path>]` with the expected arguments."
                    );
                }
                projectRoot = Path.of(args[++i]);
                continue;
            }
            if ("--index".equals(token)) {
                if (i + 1 >= args.length) {
                    return BearCli.failWithLegacy(
                        err,
                        CliCodes.EXIT_USAGE,
                        "usage: INVALID_ARGS: expected value after --index",
                        CliCodes.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear fix <ir-file> --project <path> [--index <path>]` with the expected arguments."
                    );
                }
                indexPath = Path.of(args[++i]);
                continue;
            }
            if (token.startsWith("--") || irFile != null) {
                return BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_USAGE,
                    "usage: INVALID_ARGS: unexpected argument: " + token,
                    CliCodes.USAGE_INVALID_ARGS,
                    "cli.args",
                    "Run `bear fix <ir-file> --project <path> [--index <path>]` with the expected arguments."
                );
            }
            irFile = Path.of(token);
        }

        if (irFile == null || projectRoot == null) {
            return BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: expected: bear fix <ir-file> --project <path> [--index <path>]",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear fix <ir-file> --project <path> [--index <path>]` with the expected arguments."
            );
        }

        FixResult result = BearCli.executeFix(irFile, projectRoot, null, null, indexPath);
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

        ParsedCheckInvocation invocation = parseCheckInvocation(args, err);
        if (invocation == null) {
            return CliCodes.EXIT_USAGE;
        }

        CheckResult result = BearCli.executeCheck(
            invocation.irFile(),
            invocation.projectRoot(),
            true,
            invocation.strictHygiene(),
            null,
            null,
            invocation.indexPath(),
            invocation.collectAll()
        );
        if (invocation.agent()) {
            out.println(AgentDiagnostics.toJson(AgentDiagnostics.payloadForCheck(result, invocation.commandContext())));
            return result.exitCode();
        }
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

        ParsedPrCheckInvocation invocation = parsePrCheckInvocation(args, err);
        if (invocation == null) {
            return CliCodes.EXIT_USAGE;
        }

        PrCheckResult result = BearCli.executePrCheck(
            invocation.projectRoot(),
            invocation.repoRelativePath(),
            invocation.baseRef(),
            invocation.indexPath(),
            invocation.collectAll()
        );
        result = BearCli.enforcePrCheckExitEnvelope(result, invocation.repoRelativePath());
        if (invocation.agent()) {
            out.println(AgentDiagnostics.toJson(AgentDiagnostics.payloadForPrCheck(result, invocation.commandContext())));
            return result.exitCode();
        }
        return BearCli.emitPrCheckResult(result, out, err);
    }
    static AgentCommandContext parseAgentCommandContext(String[] rawArgs, PrintStream err) {
        if (rawArgs == null || rawArgs.length == 0) {
            return null;
        }
        String[] args = rawArgs;
        if ("bear".equals(args[0])) {
            if (args.length == 1) {
                return null;
            }
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length < 2) {
            return null;
        }
        String command = args[0];
        if ("check".equals(command)) {
            if ("--all".equals(args[1])) {
                AllCheckOptions options = AllModeOptionParser.parseAllCheckOptions(args, err);
                return options == null ? null : AgentCommandContext.forCheckAll(options);
            }
            ParsedCheckInvocation invocation = parseCheckInvocation(args, err);
            return invocation == null ? null : invocation.commandContext();
        }
        if ("pr-check".equals(command)) {
            if ("--all".equals(args[1])) {
                AllPrCheckOptions options = AllModeOptionParser.parseAllPrCheckOptions(args, err);
                return options == null ? null : AgentCommandContext.forPrCheckAll(options);
            }
            ParsedPrCheckInvocation invocation = parsePrCheckInvocation(args, err);
            return invocation == null ? null : invocation.commandContext();
        }
        return null;
    }

    private static ParsedCheckInvocation parseCheckInvocation(String[] args, PrintStream err) {
        Path irFile = null;
        Path projectRoot = null;
        Path indexPath = null;
        boolean strictHygiene = false;
        boolean collectAll = false;
        boolean agent = false;
        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if ("--project".equals(token)) {
                if (i + 1 >= args.length) {
                    BearCli.failWithLegacy(
                        err,
                        CliCodes.EXIT_USAGE,
                        "usage: INVALID_ARGS: expected value after --project",
                        CliCodes.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear check <ir-file> --project <path> [--strict-hygiene] [--index <path>] [--collect=all] [--agent]` with the expected arguments."
                    );
                    return null;
                }
                projectRoot = Path.of(args[++i]);
                continue;
            }
            if ("--strict-hygiene".equals(token)) {
                strictHygiene = true;
                continue;
            }
            if ("--agent".equals(token)) {
                agent = true;
                continue;
            }
            if ("--collect=all".equals(token)) {
                collectAll = true;
                continue;
            }
            if (token.startsWith("--collect=")) {
                BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_USAGE,
                    "usage: INVALID_ARGS: unsupported value for --collect",
                    CliCodes.USAGE_INVALID_ARGS,
                    "cli.args",
                    "Use `--collect=all` or omit the flag."
                );
                return null;
            }
            if ("--index".equals(token)) {
                if (i + 1 >= args.length) {
                    BearCli.failWithLegacy(
                        err,
                        CliCodes.EXIT_USAGE,
                        "usage: INVALID_ARGS: expected value after --index",
                        CliCodes.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear check <ir-file> --project <path> [--strict-hygiene] [--index <path>] [--collect=all] [--agent]` with the expected arguments."
                    );
                    return null;
                }
                indexPath = Path.of(args[++i]);
                continue;
            }
            if (token.startsWith("--") || irFile != null) {
                BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_USAGE,
                    "usage: INVALID_ARGS: unexpected argument: " + token,
                    CliCodes.USAGE_INVALID_ARGS,
                    "cli.args",
                    "Run `bear check <ir-file> --project <path> [--strict-hygiene] [--index <path>] [--collect=all] [--agent]` with the expected arguments."
                );
                return null;
            }
            irFile = Path.of(token);
        }

        if (irFile == null || projectRoot == null) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: expected: bear check <ir-file> --project <path> [--strict-hygiene] [--index <path>] [--collect=all] [--agent]",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear check <ir-file> --project <path> [--strict-hygiene] [--index <path>] [--collect=all] [--agent]` with the expected arguments."
            );
            return null;
        }

        AgentCommandContext context = AgentCommandContext.forCheckSingle(
            irFile,
            projectRoot,
            indexPath,
            strictHygiene,
            collectAll,
            agent
        );
        return new ParsedCheckInvocation(irFile, projectRoot, indexPath, strictHygiene, collectAll, agent, context);
    }

    private static ParsedPrCheckInvocation parsePrCheckInvocation(String[] args, PrintStream err) {
        String irArg = null;
        Path projectRoot = null;
        String baseRef = null;
        Path indexPath = null;
        boolean collectAll = false;
        boolean agent = false;

        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if ("--project".equals(token)) {
                if (i + 1 >= args.length) {
                    BearCli.failWithLegacy(
                        err,
                        CliCodes.EXIT_USAGE,
                        "usage: INVALID_ARGS: expected value after --project",
                        CliCodes.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear pr-check <ir-file> --project <path> --base <ref> [--index <path>] [--collect=all] [--agent]` with the expected arguments."
                    );
                    return null;
                }
                projectRoot = Path.of(args[++i]).toAbsolutePath().normalize();
                continue;
            }
            if ("--base".equals(token)) {
                if (i + 1 >= args.length) {
                    BearCli.failWithLegacy(
                        err,
                        CliCodes.EXIT_USAGE,
                        "usage: INVALID_ARGS: expected value after --base",
                        CliCodes.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear pr-check <ir-file> --project <path> --base <ref> [--index <path>] [--collect=all] [--agent]` with the expected arguments."
                    );
                    return null;
                }
                baseRef = args[++i];
                continue;
            }
            if ("--index".equals(token)) {
                if (i + 1 >= args.length) {
                    BearCli.failWithLegacy(
                        err,
                        CliCodes.EXIT_USAGE,
                        "usage: INVALID_ARGS: expected value after --index",
                        CliCodes.USAGE_INVALID_ARGS,
                        "cli.args",
                        "Run `bear pr-check <ir-file> --project <path> --base <ref> [--index <path>] [--collect=all] [--agent]` with the expected arguments."
                    );
                    return null;
                }
                indexPath = Path.of(args[++i]);
                continue;
            }
            if ("--agent".equals(token)) {
                agent = true;
                continue;
            }
            if ("--collect=all".equals(token)) {
                collectAll = true;
                continue;
            }
            if (token.startsWith("--collect=")) {
                BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_USAGE,
                    "usage: INVALID_ARGS: unsupported value for --collect",
                    CliCodes.USAGE_INVALID_ARGS,
                    "cli.args",
                    "Use `--collect=all` or omit the flag."
                );
                return null;
            }
            if (token.startsWith("--") || irArg != null) {
                BearCli.failWithLegacy(
                    err,
                    CliCodes.EXIT_USAGE,
                    "usage: INVALID_ARGS: unexpected argument: " + token,
                    CliCodes.USAGE_INVALID_ARGS,
                    "cli.args",
                    "Run `bear pr-check <ir-file> --project <path> --base <ref> [--index <path>] [--collect=all] [--agent]` with the expected arguments."
                );
                return null;
            }
            irArg = token;
        }

        if (irArg == null || projectRoot == null || baseRef == null || baseRef.isBlank()) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: expected: bear pr-check <ir-file> --project <path> --base <ref> [--index <path>] [--collect=all] [--agent]",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Run `bear pr-check <ir-file> --project <path> --base <ref> [--index <path>] [--collect=all] [--agent]` with the expected arguments."
            );
            return null;
        }

        Path irPath = Path.of(irArg);
        if (irPath.isAbsolute()) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: ir-file must be repo-relative",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative `ir-file` path for `bear pr-check`."
            );
            return null;
        }
        Path normalizedRelative = irPath.normalize();
        if (normalizedRelative.startsWith("..") || normalizedRelative.toString().isBlank()) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: ir-file must be repo-relative",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative `ir-file` path for `bear pr-check`."
            );
            return null;
        }

        String repoRelativePath = normalizedRelative.toString().replace('\\', '/');
        Path headIrPath = projectRoot.resolve(repoRelativePath).normalize();
        if (!headIrPath.startsWith(projectRoot)) {
            BearCli.failWithLegacy(
                err,
                CliCodes.EXIT_USAGE,
                "usage: INVALID_ARGS: ir-file must be repo-relative",
                CliCodes.USAGE_INVALID_ARGS,
                "cli.args",
                "Pass a repo-relative `ir-file` path for `bear pr-check`."
            );
            return null;
        }

        AgentCommandContext context = AgentCommandContext.forPrCheckSingle(
            repoRelativePath,
            projectRoot,
            baseRef,
            indexPath,
            collectAll,
            agent
        );
        return new ParsedPrCheckInvocation(repoRelativePath, projectRoot, baseRef, indexPath, collectAll, agent, context);
    }

    private record ParsedCheckInvocation(
        Path irFile,
        Path projectRoot,
        Path indexPath,
        boolean strictHygiene,
        boolean collectAll,
        boolean agent,
        AgentCommandContext commandContext
    ) {
    }

    private record ParsedPrCheckInvocation(
        String repoRelativePath,
        Path projectRoot,
        String baseRef,
        Path indexPath,
        boolean collectAll,
        boolean agent,
        AgentCommandContext commandContext
    ) {
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
