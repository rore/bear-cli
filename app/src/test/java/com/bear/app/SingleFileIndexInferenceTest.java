package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleFileIndexInferenceTest {
    @Test
    void compileSingleFileBlockPortsInfersProjectIndex(@TempDir Path tempDir) throws Exception {
        BlockPortFixture fixture = writeBlockPortFixture(tempDir.resolve("repo"), true);

        CompileResult result = BearCli.executeCompile(fixture.accountIr(), fixture.repoRoot(), null, null, null);

        assertEquals(CliCodes.EXIT_OK, result.exitCode());
        assertEquals("compiled: OK", result.stdoutLines().get(0));
    }

    @Test
    void fixSingleFileBlockPortsInfersProjectIndex(@TempDir Path tempDir) throws Exception {
        BlockPortFixture fixture = writeBlockPortFixture(tempDir.resolve("repo"), true);

        FixResult result = BearCli.executeFix(fixture.accountIr(), fixture.repoRoot(), null, null, null);

        assertEquals(CliCodes.EXIT_OK, result.exitCode());
        assertEquals("fix: OK", result.stdoutLines().get(0));
    }

    @Test
    void checkSingleFileBlockPortsInfersProjectIndex(@TempDir Path tempDir) throws Exception {
        BlockPortFixture fixture = writeBlockPortFixture(tempDir.resolve("repo"), true);
        CompileResult compile = BearCli.executeCompile(fixture.accountIr(), fixture.repoRoot(), null, null, null);
        assertEquals(CliCodes.EXIT_OK, compile.exitCode());

        CheckResult check = CheckCommandService.executeCheck(
            fixture.accountIr(),
            fixture.repoRoot(),
            false,
            false,
            null,
            null,
            Boolean.FALSE,
            false,
            null
        );

        assertEquals(CliCodes.EXIT_OK, check.exitCode());
    }

    @Test
    void compileSingleFileBlockPortsFailsWhenInferredIndexMissing(@TempDir Path tempDir) throws Exception {
        BlockPortFixture fixture = writeBlockPortFixture(tempDir.resolve("repo"), false);

        CompileResult result = BearCli.executeCompile(fixture.accountIr(), fixture.repoRoot(), null, null, null);

        assertEquals(CliCodes.EXIT_VALIDATION, result.exitCode());
        assertEquals("IR_VALIDATION", result.failureCode());
        assertEquals("bear.blocks.yaml", result.failurePath());
        assertTrue(result.stderrLines().get(0).startsWith(
            "index: VALIDATION_ERROR: BLOCK_PORT_INDEX_REQUIRED: missing inferred index at --project/bear.blocks.yaml"
        ));
        assertTrue(result.failureRemediation().contains("Create `bear.blocks.yaml` under `--project`"));
    }

    @Test
    void prCheckSingleFileBlockPortsInfersProjectIndex(@TempDir Path tempDir) throws Exception {
        BlockPortFixture fixture = writeBlockPortFixture(tempDir.resolve("repo"), true);
        initGitRepo(fixture.repoRoot());
        CompileResult compile = BearCli.executeCompile(fixture.accountIr(), fixture.repoRoot(), null, null, null);
        assertEquals(CliCodes.EXIT_OK, compile.exitCode());
        gitCommitAll(fixture.repoRoot(), "base");

        PrCheckResult result = PrCheckCommandService.executePrCheck(
            fixture.repoRoot(),
            fixture.repoRoot().relativize(fixture.accountIr()).toString().replace('\\', '/'),
            "HEAD",
            null
        );

        assertEquals(CliCodes.EXIT_OK, result.exitCode());
        assertTrue(result.stdoutLines().contains("pr-check: OK: NO_BOUNDARY_EXPANSION"));
    }

    private static BlockPortFixture writeBlockPortFixture(Path repoRoot, boolean includeIndex) throws Exception {
        Path irDir = repoRoot.resolve("bear-ir");
        Files.createDirectories(irDir);

        Path accountIr = irDir.resolve("account.bear.yaml");
        Path transactionLogIr = irDir.resolve("transaction-log.bear.yaml");

        Files.writeString(accountIr, accountIrContent(), StandardCharsets.UTF_8);
        Files.writeString(transactionLogIr, transactionLogIrContent(), StandardCharsets.UTF_8);

        if (includeIndex) {
            Files.writeString(
                repoRoot.resolve("bear.blocks.yaml"),
                """
                version: v1
                blocks:
                  - name: account
                    ir: bear-ir/account.bear.yaml
                    projectRoot: .
                  - name: transaction-log
                    ir: bear-ir/transaction-log.bear.yaml
                    projectRoot: .
                """,
                StandardCharsets.UTF_8
            );
        }

        return new BlockPortFixture(repoRoot, accountIr, transactionLogIr);
    }

    private static String accountIrContent() {
        return """
            version: v1
            block:
              name: Account
              kind: logic
              operations:
                - name: Deposit
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                    outputs:
                      - name: txSeq
                        type: int
                  uses:
                    allow:
                      - port: transactionLog
                        kind: block
                        targetOps: [AppendTransaction]
                  idempotency:
                    mode: none
              effects:
                allow:
                  - port: transactionLog
                    kind: block
                    targetBlock: transaction-log
                    targetOps: [AppendTransaction]
            """;
    }

    private static String transactionLogIrContent() {
        return """
            version: v1
            block:
              name: TransactionLog
              kind: logic
              operations:
                - name: AppendTransaction
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                    outputs:
                      - name: seq
                        type: int
                  uses:
                    allow:
                      - port: txStore
                        kind: external
                        ops: [append]
                  idempotency:
                    mode: none
              effects:
                allow:
                  - port: txStore
                    kind: external
                    ops: [append]
            """;
    }

    private static void initGitRepo(Path repoRoot) throws Exception {
        Files.createDirectories(repoRoot);
        git(repoRoot, "init");
        git(repoRoot, "config", "user.email", "bear@example.com");
        git(repoRoot, "config", "user.name", "Bear Test");
    }

    private static void gitCommitAll(Path repoRoot, String message) throws Exception {
        git(repoRoot, "add", "-A");
        git(repoRoot, "commit", "-m", message);
    }

    private static void git(Path repoRoot, String... args) throws Exception {
        ArrayList<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repoRoot.toString());
        command.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (var in = process.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exit = process.waitFor();
        assertEquals(0, exit, "git command failed: " + String.join(" ", command) + "\n" + output);
    }

    private record BlockPortFixture(Path repoRoot, Path accountIr, Path transactionLogIr) {
    }
}



