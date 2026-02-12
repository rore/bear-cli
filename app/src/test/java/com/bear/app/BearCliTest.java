package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearCliTest {
    @Test
    void validateFixturePrintsGoldenCanonicalYaml() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        Path golden = repoRoot.resolve("spec/golden/withdraw.canonical.yaml");

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "validate", fixture.toString() },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderrBytes.toString());

        String expected = normalizeLf(Files.readString(golden));
        String actual = normalizeLf(stdoutBytes.toString());
        assertEquals(expected, actual);
    }

    @Test
    void validateRequiresOneFileArg() {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "validate" },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(64, exitCode);
        assertTrue(stderrBytes.toString(StandardCharsets.UTF_8).startsWith("usage: INVALID_ARGS:"));
    }

    @Test
    void unknownCommandIsUsageError() {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "wat" },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(64, exitCode);
        assertTrue(stderrBytes.toString(StandardCharsets.UTF_8).startsWith("usage: UNKNOWN_COMMAND:"));
    }

    @Test
    void validateMissingFileReturnsIoExitCode() {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "validate", "does-not-exist.yaml" },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(74, exitCode);
        assertTrue(stderrBytes.toString(StandardCharsets.UTF_8).startsWith("io: IO_ERROR:"));
    }

    @Test
    void validateSchemaErrorPrintsDeterministicPrefix(@TempDir Path tempDir) throws Exception {
        Path invalid = tempDir.resolve("invalid.bear.yaml");
        Files.writeString(invalid, ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "  extra: true\n");

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "validate", invalid.toString() },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(2, exitCode);
        assertTrue(stderrBytes.toString(StandardCharsets.UTF_8).startsWith("schema at block: UNKNOWN_KEY:"));
    }

    @Test
    void compileRequiresExpectedArgs() {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "compile" },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(64, exitCode);
        assertTrue(stderrBytes.toString(StandardCharsets.UTF_8).startsWith("usage: INVALID_ARGS:"));
    }

    @Test
    void compileMissingIrReturnsIoExitCode(@TempDir Path tempDir) {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "compile", "missing.yaml", "--project", tempDir.toString() },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(74, exitCode);
        assertTrue(stderrBytes.toString(StandardCharsets.UTF_8).startsWith("io: IO_ERROR:"));
    }

    @Test
    void compileFixtureGeneratesArtifactsAndPreservesExistingImpl(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        Path impl = tempDir.resolve("src/main/java/com/bear/generated/withdraw/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        Files.writeString(impl, "package com.bear.generated.withdraw;\npublic final class WithdrawImpl {}\n");

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "compile", fixture.toString(), "--project", tempDir.toString() },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(0, exitCode);
        assertEquals("", stderrBytes.toString());

        Path generatedRoot = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw");
        assertTrue(Files.exists(generatedRoot.resolve("Withdraw.java")));
        assertTrue(Files.exists(generatedRoot.resolve("WithdrawLogic.java")));
        assertTrue(Files.exists(generatedRoot.resolve("WithdrawRequest.java")));
        assertTrue(Files.exists(generatedRoot.resolve("WithdrawResult.java")));
        assertTrue(Files.exists(generatedRoot.resolve("LedgerPort.java")));
        assertTrue(Files.exists(generatedRoot.resolve("IdempotencyPort.java")));

        assertEquals("package com.bear.generated.withdraw;\npublic final class WithdrawImpl {}\n", Files.readString(impl));
    }

    @Test
    void checkRequiresExpectedArgs() {
        CliRunResult run = runCli(new String[] { "check" });
        assertEquals(64, run.exitCode);
        assertTrue(run.stderr.startsWith("usage: INVALID_ARGS:"));
    }

    @Test
    void checkMissingIrReturnsIoExitCode(@TempDir Path tempDir) {
        CliRunResult run = runCli(new String[] { "check", "missing.yaml", "--project", tempDir.toString() });
        assertEquals(74, run.exitCode);
        assertTrue(run.stderr.startsWith("io: IO_ERROR:"));
    }

    @Test
    void checkInvalidIrReturnsValidationExitCode(@TempDir Path tempDir) throws Exception {
        Path invalid = tempDir.resolve("invalid.bear.yaml");
        Files.writeString(invalid, ""
            + "version: v0\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "  extra: true\n");

        CliRunResult run = runCli(new String[] { "check", invalid.toString(), "--project", tempDir.toString() });
        assertEquals(2, run.exitCode);
        assertTrue(run.stderr.startsWith("schema at block: UNKNOWN_KEY:"));
    }

    @Test
    void checkMissingBaselineIsDriftError(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, run.exitCode);
        assertTrue(run.stderr.startsWith("drift: MISSING_BASELINE: build/generated/bear"));
    }

    @Test
    void checkEmptyBaselineIsDriftError(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        Path emptyBaseline = tempDir.resolve("build/generated/bear");
        Files.createDirectories(emptyBaseline);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, run.exitCode);
        assertTrue(run.stderr.startsWith("drift: MISSING_BASELINE: build/generated/bear"));
    }

    @Test
    void checkPassesWithNoDriftAndDoesNotMutateBaseline(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);

        Path baselineFile = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw/Withdraw.java");
        byte[] before = Files.readAllBytes(baselineFile);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
        assertTrue(check.stdout.startsWith("check: OK"));
        assertEquals("", check.stderr);

        byte[] after = Files.readAllBytes(baselineFile);
        assertEquals(bytesToHex(before), bytesToHex(after));
    }

    @Test
    void checkReportsAddedRemovedChangedInDeterministicOrderAndDoesNotMutate(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);

        Path generatedRoot = tempDir.resolve("build/generated/bear");
        Path removedFile = generatedRoot.resolve("src/main/java/com/bear/generated/withdraw/BearValue.java");
        Path changedFile = generatedRoot.resolve("src/main/java/com/bear/generated/withdraw/Withdraw.java");
        Path addedFile = generatedRoot.resolve("zzz_extra.txt");

        String changedBefore = Files.readString(changedFile);
        Files.delete(removedFile);
        Files.writeString(changedFile, "\n// drift\n", StandardOpenOption.APPEND);
        Files.writeString(addedFile, "extra");

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, check.exitCode);

        List<String> lines = normalizeLf(check.stderr).lines().filter(line -> !line.isBlank()).toList();
        assertEquals(List.of(
            "drift: REMOVED: src/main/java/com/bear/generated/withdraw/BearValue.java",
            "drift: CHANGED: src/main/java/com/bear/generated/withdraw/Withdraw.java",
            "drift: ADDED: zzz_extra.txt"
        ), lines);

        assertFalse(Files.exists(removedFile));
        assertTrue(Files.exists(addedFile));
        assertEquals(changedBefore + "\n// drift\n", Files.readString(changedFile));
    }

    private static CliRunResult runCli(String[] args) {
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            args,
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );
        return new CliRunResult(
            exitCode,
            stdoutBytes.toString(StandardCharsets.UTF_8),
            stderrBytes.toString(StandardCharsets.UTF_8)
        );
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }

    private static String normalizeLf(String text) {
        return text.replace("\r\n", "\n");
    }

    private record CliRunResult(int exitCode, String stdout, String stderr) {
    }
}
