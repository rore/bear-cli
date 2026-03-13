package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllModeContractTest {
    @Test
    void allModeCommandsUseUnifiedMissingIndexEnvelope(@TempDir Path tempDir) {
        assertMissingIndex(new String[] { "compile", "--all", "--project", tempDir.toString() });
        assertMissingIndex(new String[] { "check", "--all", "--project", tempDir.toString() });
        assertMissingIndex(new String[] { "fix", "--all", "--project", tempDir.toString() });
        assertMissingIndex(new String[] { "pr-check", "--all", "--project", tempDir.toString(), "--base", "HEAD" });
    }

    @Test
    void checkAllEmitsProgressLinesAsOrderedSubsequence(@TempDir Path tempDir) throws Exception {
        Fixture fixture = createSingleBlockFixture(tempDir, false);
        CliRunResult run = runCli(new String[] { "check", "--all", "--project", fixture.repoRoot().toString() });
        assertEquals(0, run.exitCode());

        List<String> lines = stdoutLines(run);
        assertOrderedSubsequence(
            lines,
            List.of(
                "check-all: START project=.",
                "check-all: BLOCK_START name=withdraw ir=bear-ir/withdraw.bear.yaml",
                "check-all: ROOT_TEST_START project=service",
                "check-all: ROOT_TEST_DONE project=service exit=0",
                "check-all: DONE project=. exit=0"
            )
        );
    }

    @Test
    void checkAllHeartbeatUsesDeterministicFormat(@TempDir Path tempDir) throws Exception {
        Fixture fixture = createSingleBlockFixture(tempDir, true);

        String key = "bear.check.all.heartbeatSeconds";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "1");
            CliRunResult run = runCli(new String[] { "check", "--all", "--project", fixture.repoRoot().toString() });
            assertEquals(0, run.exitCode());
            List<String> lines = stdoutLines(run);
            String heartbeat = "check-all: HEARTBEAT seconds=1 phase=root_test project=service";
            assertTrue(lines.contains(heartbeat));
            assertOrderedSubsequence(
                lines,
                List.of(
                    "check-all: ROOT_TEST_START project=service",
                    heartbeat,
                    "check-all: ROOT_TEST_DONE project=service exit=0"
                )
            );
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void fixAllDoesNotModifyIrFiles(@TempDir Path tempDir) throws Exception {
        Fixture fixture = createSingleBlockFixture(tempDir, false);
        Path ir = fixture.repoRoot().resolve("bear-ir/withdraw.bear.yaml");
        String before = sha256(Files.readAllBytes(ir));

        CliRunResult run = runCli(new String[] { "fix", "--all", "--project", fixture.repoRoot().toString() });
        assertEquals(0, run.exitCode(), run.stderr());

        String after = sha256(Files.readAllBytes(ir));
        assertEquals(before, after);
    }

    private static void assertMissingIndex(String[] args) {
        CliRunResult run = runCli(args);
        assertEquals(2, run.exitCode());
        assertEquals("", normalizeLf(run.stdout()));
        assertEquals(
            String.join(
                "\n",
                "index: VALIDATION_ERROR: INDEX_REQUIRED_MISSING: bear.blocks.yaml: project=.",
                "CODE=INDEX_REQUIRED_MISSING",
                "PATH=bear.blocks.yaml",
                "REMEDIATION=Create bear.blocks.yaml or run non---all command",
                ""
            ),
            normalizeLf(run.stderr())
        );
    }


    @Test
    void checkAllUnknownTargetBlockUsesPinnedValidationPath(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("bear-ir"));
        Files.writeString(
            repoRoot.resolve("bear.blocks.yaml"),
            "" +
                "version: v1\n" +
                "blocks:\n" +
                "  - name: account\n" +
                "    ir: bear-ir/account.bear.yaml\n" +
                "    projectRoot: .\n",
            StandardCharsets.UTF_8
        );
        Files.writeString(repoRoot.resolve("bear-ir/account.bear.yaml"), accountWithUnknownTarget(), StandardCharsets.UTF_8);

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", repoRoot.toString() });
        assertEquals(2, run.exitCode());
        String stderr = normalizeLf(run.stderr());
        assertTrue(stderr.contains("CODE=IR_VALIDATION"));
        assertTrue(stderr.contains("PATH=bear-ir/account.bear.yaml"));
    }

    @Test
    void checkAllUnknownTargetOpUsesPinnedValidationPath(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("bear-ir"));
        Files.writeString(
            repoRoot.resolve("bear.blocks.yaml"),
            "" +
                "version: v1\n" +
                "blocks:\n" +
                "  - name: account\n" +
                "    ir: bear-ir/account.bear.yaml\n" +
                "    projectRoot: .\n" +
                "  - name: transaction-log\n" +
                "    ir: bear-ir/transaction-log.bear.yaml\n" +
                "    projectRoot: .\n",
            StandardCharsets.UTF_8
        );
        Files.writeString(repoRoot.resolve("bear-ir/account.bear.yaml"), accountWithUnknownTargetOp(), StandardCharsets.UTF_8);
        Files.writeString(repoRoot.resolve("bear-ir/transaction-log.bear.yaml"), txWithSingleOp(), StandardCharsets.UTF_8);

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", repoRoot.toString() });
        assertEquals(2, run.exitCode());
        String stderr = normalizeLf(run.stderr());
        assertTrue(stderr.contains("CODE=IR_VALIDATION"));
        assertTrue(stderr.contains("PATH=bear-ir/account.bear.yaml#block.effects.allow[0].targetOps[0]"));
    }

    @Test
    void checkAllCycleUsesPinnedValidationPath(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("bear-ir"));
        Files.writeString(
            repoRoot.resolve("bear.blocks.yaml"),
            "" +
                "version: v1\n" +
                "blocks:\n" +
                "  - name: account\n" +
                "    ir: bear-ir/account.bear.yaml\n" +
                "    projectRoot: .\n" +
                "  - name: transaction-log\n" +
                "    ir: bear-ir/transaction-log.bear.yaml\n" +
                "    projectRoot: .\n",
            StandardCharsets.UTF_8
        );
        Files.writeString(repoRoot.resolve("bear-ir/account.bear.yaml"), accountCycle(), StandardCharsets.UTF_8);
        Files.writeString(repoRoot.resolve("bear-ir/transaction-log.bear.yaml"), txCycle(), StandardCharsets.UTF_8);

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", repoRoot.toString() });
        assertEquals(2, run.exitCode());
        String stderr = normalizeLf(run.stderr());
        assertTrue(stderr.contains("CODE=IR_VALIDATION"));
        assertTrue(stderr.contains("PATH=bear.blocks.yaml"));
    }
    private static Fixture createSingleBlockFixture(Path repoRoot, boolean slowWrapper) throws Exception {
        Path irDir = repoRoot.resolve("bear-ir");
        Files.createDirectories(irDir);
        Path ir = irDir.resolve("withdraw.bear.yaml");
        Path fixture = TestRepoPaths.repoRoot().resolve("bear-ir/fixtures/withdraw.bear.yaml");
        Files.writeString(ir, Files.readString(fixture, StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        Files.writeString(
            repoRoot.resolve("bear.blocks.yaml"),
            ""
                + "version: v1\n"
                + "blocks:\n"
                + "  - name: withdraw\n"
                + "    ir: bear-ir/withdraw.bear.yaml\n"
                + "    projectRoot: service\n",
            StandardCharsets.UTF_8
        );

        Path serviceRoot = repoRoot.resolve("service");
        Files.createDirectories(serviceRoot);
        writeProjectWrapper(serviceRoot, slowWrapper);

        CliRunResult compile = runCli(new String[] { "compile", "--all", "--project", repoRoot.toString() });
        assertEquals(0, compile.exitCode(), compile.stderr());
        writeWorkingWithdrawImpl(serviceRoot);
        return new Fixture(repoRoot, serviceRoot);
    }

    private static void writeProjectWrapper(Path projectRoot, boolean slowWrapper) throws Exception {
        String windows = slowWrapper
            ? "@echo off\r\npowershell -NoProfile -Command \"Start-Sleep -Seconds 2\"\r\necho TEST_OK\r\nexit /b 0\r\n"
            : "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n";
        String unix = slowWrapper
            ? "#!/usr/bin/env sh\nsleep 2\necho TEST_OK\nexit 0\n"
            : "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n";
        Files.writeString(projectRoot.resolve("gradlew.bat"), windows, StandardCharsets.UTF_8);
        Files.writeString(projectRoot.resolve("gradlew"), unix, StandardCharsets.UTF_8);
        // Ensure JvmTargetDetector can detect this as a JVM project.
        if (!Files.exists(projectRoot.resolve("build.gradle"))) {
            Files.writeString(projectRoot.resolve("build.gradle"), "// test fixture\n", StandardCharsets.UTF_8);
        }
    }

    private static void writeWorkingWithdrawImpl(Path projectRoot) throws Exception {
        Path impl = projectRoot.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        String source = ""
            + "package blocks.withdraw.impl;\n"
            + "\n"
            + "import com.bear.generated.withdraw.BearValue;\n"
            + "import com.bear.generated.withdraw.LedgerPort;\n"
            + "import com.bear.generated.withdraw.WithdrawLogic;\n"
            + "import com.bear.generated.withdraw.Withdraw_ExecuteWithdrawRequest;\n"
            + "import com.bear.generated.withdraw.Withdraw_ExecuteWithdrawResult;\n"
            + "import java.math.BigDecimal;\n"
            + "\n"
            + "public final class WithdrawImpl implements WithdrawLogic {\n"
            + "    @Override\n"
            + "    public Withdraw_ExecuteWithdrawResult executeExecuteWithdraw(\n"
            + "        Withdraw_ExecuteWithdrawRequest request,\n"
            + "        LedgerPort ledgerPort\n"
            + "    ) {\n"
            + "        ledgerPort.getBalance(BearValue.empty());\n"
            + "        return new Withdraw_ExecuteWithdrawResult(BigDecimal.ZERO);\n"
            + "    }\n"
            + "}\n";
        Files.writeString(impl, source, StandardCharsets.UTF_8);
    }

    private static List<String> stdoutLines(CliRunResult run) {
        List<String> lines = new ArrayList<>();
        for (String line : normalizeLf(run.stdout()).split("\n")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static void assertOrderedSubsequence(List<String> lines, List<String> expected) {
        int cursor = 0;
        for (String line : lines) {
            if (cursor >= expected.size()) {
                break;
            }
            if (line.equals(expected.get(cursor))) {
                cursor++;
            }
        }
        assertEquals(expected.size(), cursor, "expected ordered subsequence missing: " + expected);
    }

    private static CliRunResult runCli(String[] args) {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(args, new PrintStream(stdoutBytes), new PrintStream(stderrBytes));
        return new CliRunResult(
            exitCode,
            stdoutBytes.toString(StandardCharsets.UTF_8),
            stderrBytes.toString(StandardCharsets.UTF_8)
        );
    }

    private static void restoreSystemProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    private static String normalizeLf(String text) {
        return text.replace("\r\n", "\n");
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }


    private static String accountWithUnknownTarget() {
        return """
            version: v1
            block:
              name: Account
              kind: logic
              operations:
                - name: Deposit
                  contract:
                    inputs: [{name: accountId, type: string}]
                    outputs: [{name: balance, type: int}]
                  uses:
                    allow:
                      - port: transactionLog
                        kind: block
                        targetOps: [AppendTransaction]
              effects:
                allow:
                  - port: transactionLog
                    kind: block
                    targetBlock: missing-block
                    targetOps: [AppendTransaction]
            """;
    }

    private static String accountWithUnknownTargetOp() {
        return """
            version: v1
            block:
              name: Account
              kind: logic
              operations:
                - name: Deposit
                  contract:
                    inputs: [{name: accountId, type: string}]
                    outputs: [{name: balance, type: int}]
                  uses:
                    allow:
                      - port: transactionLog
                        kind: block
                        targetOps: [MissingOp]
              effects:
                allow:
                  - port: transactionLog
                    kind: block
                    targetBlock: transaction-log
                    targetOps: [MissingOp]
            """;
    }

    private static String txWithSingleOp() {
        return """
            version: v1
            block:
              name: TransactionLog
              kind: logic
              operations:
                - name: AppendTransaction
                  contract:
                    inputs: [{name: accountId, type: string}]
                    outputs: [{name: seq, type: int}]
                  uses:
                    allow:
                      - port: txStore
                        kind: external
                        ops: [append]
              effects:
                allow:
                  - port: txStore
                    kind: external
                    ops: [append]
            """;
    }

    private static String accountCycle() {
        return """
            version: v1
            block:
              name: Account
              kind: logic
              operations:
                - name: Deposit
                  contract:
                    inputs: [{name: accountId, type: string}]
                    outputs: [{name: balance, type: int}]
                  uses:
                    allow:
                      - port: transactionLog
                        kind: block
                        targetOps: [AppendTransaction]
              effects:
                allow:
                  - port: transactionLog
                    kind: block
                    targetBlock: transaction-log
                    targetOps: [AppendTransaction]
            """;
    }

    private static String txCycle() {
        return """
            version: v1
            block:
              name: TransactionLog
              kind: logic
              operations:
                - name: AppendTransaction
                  contract:
                    inputs: [{name: accountId, type: string}]
                    outputs: [{name: seq, type: int}]
                  uses:
                    allow:
                      - port: accountBoundary
                        kind: block
                        targetOps: [Deposit]
              effects:
                allow:
                  - port: accountBoundary
                    kind: block
                    targetBlock: account
                    targetOps: [Deposit]
            """;
    }
    private record Fixture(Path repoRoot, Path serviceRoot) {
    }

    private record CliRunResult(int exitCode, String stdout, String stderr) {
    }
}




