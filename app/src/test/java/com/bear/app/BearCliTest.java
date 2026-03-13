package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearCliTest {
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
        String stderr = stderrBytes.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.startsWith("usage: UNKNOWN_COMMAND:"));
        assertFailureEnvelope(
            stderr,
            "USAGE_UNKNOWN_COMMAND",
            "cli.command",
            "Run `bear --help` and use a supported command."
        );
    }

    @Test
    void helpIncludesFixCommands() {
        CliRunResult run = runCli(new String[] { "--help" });
        assertEquals(0, run.exitCode);
        String stdout = normalizeLf(run.stdout);
        assertTrue(stdout.contains("bear compile --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]"));
        assertTrue(stdout.contains("bear fix <ir-file> --project <path>"));
        assertTrue(stdout.contains("bear fix --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]"));
        assertTrue(stdout.contains("bear unblock --project <path>"));
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
        String stderr = stderrBytes.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.startsWith("usage: INVALID_ARGS:"));
        assertFailureEnvelope(
            stderr,
            "USAGE_INVALID_ARGS",
            "cli.args",
            "Run `bear compile <ir-file> --project <path> [--index <path>]` with the expected arguments."
        );
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
        String stderr = stderrBytes.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.startsWith("io: IO_ERROR:"));
        assertFailureEnvelope(
            stderr,
            "IO_ERROR",
            "project.root",
            "Ensure the IR/project paths are readable and writable, then rerun `bear compile`."
        );
    }

    @Test
    void compileFixtureGeneratesArtifactsAndPreservesExistingImpl(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");

        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        Files.writeString(impl, "package blocks.withdraw.impl;\npublic final class WithdrawImpl {}\n");

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
        assertTrue(Files.exists(generatedRoot.resolve("Withdraw_ExecuteWithdraw.java")));
        assertTrue(Files.exists(generatedRoot.resolve("WithdrawLogic.java")));
        assertTrue(Files.exists(generatedRoot.resolve("Withdraw_ExecuteWithdrawRequest.java")));
        assertTrue(Files.exists(generatedRoot.resolve("Withdraw_ExecuteWithdrawResult.java")));
        assertTrue(Files.exists(generatedRoot.resolve("LedgerPort.java")));
        assertTrue(Files.exists(generatedRoot.resolve("IdempotencyPort.java")));

        assertEquals("package blocks.withdraw.impl;\npublic final class WithdrawImpl {}\n", Files.readString(impl));
    }

    @Test
    void fixRequiresExpectedArgs() {
        CliRunResult run = runCli(new String[] { "fix" });
        assertEquals(64, run.exitCode);
        assertTrue(run.stderr.startsWith("usage: INVALID_ARGS:"));
        assertFailureEnvelope(
            run.stderr,
            "USAGE_INVALID_ARGS",
            "cli.args",
            "Run `bear fix <ir-file> --project <path> [--index <path>]` with the expected arguments."
        );
    }

    @Test
    void fixMissingIrReturnsIoExitCode(@TempDir Path tempDir) {
        CliRunResult run = runCli(new String[] { "fix", "missing.yaml", "--project", tempDir.toString() });
        assertEquals(74, run.exitCode);
        assertTrue(run.stderr.startsWith("io: IO_ERROR:"));
        assertFailureEnvelope(
            run.stderr,
            "IO_ERROR",
            "project.root",
            "Ensure the IR/project paths are readable and writable, then rerun `bear fix`."
        );
    }

    @Test
    void fixInvalidIrReturnsValidationExitCode(@TempDir Path tempDir) throws Exception {
        Path invalid = tempDir.resolve("invalid.bear.yaml");
        Files.writeString(invalid, ""
            + "version: v1\n"
            + "block:\n"
            + "  name: A\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: i, type: string}]\n"
            + "    outputs: [{name: o, type: string}]\n"
            + "  effects:\n"
            + "    allow: []\n"
            + "  extra: true\n");

        CliRunResult run = runCli(new String[] { "fix", invalid.toString(), "--project", tempDir.toString() });
        assertEquals(2, run.exitCode);
        assertTrue(run.stderr.startsWith("schema at block: UNKNOWN_KEY:"));
        assertFailureEnvelope(
            run.stderr,
            "IR_VALIDATION",
            "block",
            "Fix the IR issue at the reported path and rerun `bear fix <ir-file> --project <path>`."
        );
    }

    @Test
    void fixRegeneratesArtifactsAndPreservesExistingImpl(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");

        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        String implContent = "package blocks.withdraw.impl;\npublic final class WithdrawImpl {\n  // keep\n}\n";
        Files.writeString(impl, implContent);

        Path generated = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw/Withdraw_ExecuteWithdraw.java");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, "stale\n");

        CliRunResult run = runCli(new String[] { "fix", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, run.exitCode);
        assertEquals("fix: OK\n", normalizeLf(run.stdout));
        assertEquals("", run.stderr);

        assertTrue(Files.readString(generated).contains("public final class Withdraw_ExecuteWithdraw"));
        assertEquals(implContent, Files.readString(impl));
    }

    @Test
    void fixDeterministicRerunIsByteIdentical(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");

        assertEquals(0, runCli(new String[] { "fix", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        Path generated = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw/Withdraw_ExecuteWithdraw.java");
        byte[] first = Files.readAllBytes(generated);

        assertEquals(0, runCli(new String[] { "fix", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        byte[] second = Files.readAllBytes(generated);
        assertEquals(bytesToHex(first), bytesToHex(second));
    }

    @Test
    void checkRequiresExpectedArgs() {
        CliRunResult run = runCli(new String[] { "check" });
        assertEquals(64, run.exitCode);
        assertTrue(run.stderr.startsWith("usage: INVALID_ARGS:"));
        assertFailureEnvelope(
            run.stderr,
            "USAGE_INVALID_ARGS",
            "cli.args",
            "Run `bear check <ir-file> --project <path> [--strict-hygiene] [--index <path>] [--collect=all] [--agent]` with the expected arguments."
        );
    }

    @Test
    void checkMissingIrReturnsIoExitCode(@TempDir Path tempDir) {
        CliRunResult run = runCli(new String[] { "check", "missing.yaml", "--project", tempDir.toString() });
        assertEquals(74, run.exitCode);
        assertTrue(run.stderr.startsWith("io: IO_ERROR:"));
        assertFailureEnvelope(
            run.stderr,
            "IO_ERROR",
            "project.root",
            "Ensure project paths are accessible (including Gradle wrapper), then rerun `bear check`."
        );
    }

    @Test
    void checkInvalidIrReturnsValidationExitCode(@TempDir Path tempDir) throws Exception {
        Path invalid = tempDir.resolve("invalid.bear.yaml");
        Files.writeString(invalid, ""
            + "version: v1\n"
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
        assertFailureEnvelope(
            run.stderr,
            "IR_VALIDATION",
            "block",
            "Fix the IR issue at the reported path and rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkMissingBaselineIsDriftError(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, run.exitCode);
        assertTrue(run.stderr.startsWith("drift: MISSING_BASELINE: build/generated/bear"));
        assertTrue(normalizeLf(run.stderr).contains("drift: MISSING_BASELINE: build/generated/bear/wiring/withdraw.wiring.json"));
        assertFailureEnvelope(
            run.stderr,
            "DRIFT_MISSING_BASELINE",
            "build/generated/bear",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkEmptyBaselineIsDriftError(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        Path emptyBaseline = tempDir.resolve("build/generated/bear");
        Files.createDirectories(emptyBaseline);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, run.exitCode);
        assertTrue(run.stderr.startsWith("drift: MISSING_BASELINE: build/generated/bear"));
        assertTrue(normalizeLf(run.stderr).contains("drift: MISSING_BASELINE: build/generated/bear/wiring/withdraw.wiring.json"));
        assertFailureEnvelope(
            run.stderr,
            "DRIFT_MISSING_BASELINE",
            "build/generated/bear",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkPassesWithNoDriftAndDoesNotMutateBaseline(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path baselineFile = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw/Withdraw_ExecuteWithdraw.java");
        byte[] before = Files.readAllBytes(baselineFile);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
        assertTrue(check.stdout.startsWith("check: OK"));
        assertEquals("", check.stderr);

        byte[] after = Files.readAllBytes(baselineFile);
        assertEquals(bytesToHex(before), bytesToHex(after));
    }

    @Test
    void checkPassesWhenProjectPathIsRelative() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        Path relativeProjectRoot = Path.of("build/tmp/check-relative-project-" + System.nanoTime());
        Path absoluteProjectRoot = relativeProjectRoot.toAbsolutePath().normalize();
        Files.createDirectories(absoluteProjectRoot);
        try {
            CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", relativeProjectRoot.toString() });
            assertEquals(0, compile.exitCode);
            writeWorkingWithdrawImpl(absoluteProjectRoot);
            writeProjectWrapper(
                absoluteProjectRoot,
                "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
                "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
            );

            CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", relativeProjectRoot.toString() });
            assertEquals(0, check.exitCode);
            assertTrue(check.stdout.startsWith("check: OK"));
            assertEquals("", check.stderr);
        } finally {
            deleteRecursively(absoluteProjectRoot);
        }
    }

    @Test
    void checkReportsAddedRemovedChangedInDeterministicOrderAndDoesNotMutate(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);

        Path generatedRoot = tempDir.resolve("build/generated/bear");
        Path removedFile = generatedRoot.resolve("src/main/java/com/bear/generated/withdraw/BearValue.java");
        Path changedFile = generatedRoot.resolve("src/main/java/com/bear/generated/withdraw/Withdraw_ExecuteWithdraw.java");
        Path addedFile = generatedRoot.resolve("zzz_extra.txt");

        String changedBefore = Files.readString(changedFile);
        Files.delete(removedFile);
        Files.writeString(changedFile, "\n// drift\n", StandardOpenOption.APPEND);
        Files.writeString(addedFile, "extra");

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, check.exitCode);

        List<String> lines = nonEnvelopeLines(check.stderr);
        assertEquals(List.of(
            "drift: REMOVED: src/main/java/com/bear/generated/withdraw/BearValue.java",
            "drift: CHANGED: src/main/java/com/bear/generated/withdraw/Withdraw_ExecuteWithdraw.java"
        ), lines);
        assertFailureEnvelope(
            check.stderr,
            "DRIFT_DETECTED",
            "build/generated/bear",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );

        assertFalse(Files.exists(removedFile));
        assertTrue(Files.exists(addedFile));
        assertEquals(changedBefore + "\n// drift\n", Files.readString(changedFile));
    }

    @Test
    void checkWiringDriftUsesCanonicalPathWithoutDuplicates(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path wiring = tempDir.resolve("build/generated/bear/wiring/withdraw.wiring.json");
        Files.writeString(wiring, Files.readString(wiring, StandardCharsets.UTF_8) + "\n", StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, check.exitCode);
        List<String> lines = nonEnvelopeLines(check.stderr);

        String canonical = "drift: CHANGED: build/generated/bear/wiring/withdraw.wiring.json";
        assertEquals(1L, lines.stream().filter(line -> line.equals(canonical)).count());
        assertFalse(lines.stream().anyMatch(line -> line.equals("drift: CHANGED: wiring/withdraw.wiring.json")));
        assertFailureEnvelope(
            check.stderr,
            "DRIFT_DETECTED",
            "build/generated/bear",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkLegacyRuntimePathPresenceFailsDrift(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
                "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path legacy = tempDir.resolve("build/generated/bear/runtime/src/main/java/com/bear/generated/runtime/BearInvariantViolationException.java");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "// legacy runtime should not exist\n");

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, check.exitCode);
        assertTrue(
            normalizeLf(check.stderr).contains(
                "runtime/src/main/java/com/bear/generated/runtime/BearInvariantViolationException.java"
            )
        );
    }

    @Test
    void checkMissingCanonicalRuntimeFailsDrift(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
                "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Files.delete(tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/runtime/BearInvariantViolationException.java"));
        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, check.exitCode);
        assertTrue(
            normalizeLf(check.stderr).contains(
                "src/main/java/com/bear/generated/runtime/BearInvariantViolationException.java"
            )
        );
    }

    @Test
    void checkCanonicalRuntimeOnlyPasses(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
                "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        assertFalse(Files.exists(tempDir.resolve("build/generated/bear/runtime/src/main/java/com/bear/generated/runtime/BearInvariantViolationException.java")));
        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
    }

    @Test
    void checkEmitsCapabilityAddedBoundarySignalAndDrift(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/surfaces/withdraw.surface.json");
        ManifestData baseline = readManifestData(manifest);
        baseline.capabilities.remove("idempotency");
        writeManifestData(manifest, baseline);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        String stderr = normalizeLf(run.stderr);
        assertEquals(3, run.exitCode);
        assertTrue(stderr.contains("boundary: EXPANSION: CAPABILITY_ADDED: idempotency"));
        assertTrue(stderr.contains("drift: CHANGED: surfaces/withdraw.surface.json"));
        assertFailureEnvelope(
            run.stderr,
            "DRIFT_DETECTED",
            "build/generated/bear",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkEmitsCapabilityOpAddedBoundarySignalAndDrift(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/surfaces/withdraw.surface.json");
        ManifestData baseline = readManifestData(manifest);
        baseline.capabilities.put("idempotency", new ArrayList<>(List.of("get")));
        writeManifestData(manifest, baseline);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        String stderr = normalizeLf(run.stderr);
        assertEquals(3, run.exitCode);
        assertTrue(stderr.contains("boundary: EXPANSION: CAPABILITY_OP_ADDED: idempotency.put"));
        assertTrue(stderr.contains("drift: CHANGED: surfaces/withdraw.surface.json"));
        assertFailureEnvelope(
            run.stderr,
            "DRIFT_DETECTED",
            "build/generated/bear",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkEmitsInvariantRelaxedBoundarySignalAndDrift(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/surfaces/withdraw.surface.json");
        ManifestData baseline = readManifestData(manifest);
        baseline.invariants.add("non_negative:shadow");
        writeManifestData(manifest, baseline);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        String stderr = normalizeLf(run.stderr);
        assertEquals(3, run.exitCode);
        assertTrue(stderr.contains("boundary: EXPANSION: INVARIANT_RELAXED: non_negative:shadow"));
        assertTrue(stderr.contains("drift: CHANGED: surfaces/withdraw.surface.json"));
        assertFailureEnvelope(
            run.stderr,
            "DRIFT_DETECTED",
            "build/generated/bear",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkBoundarySignalOrderingIsTypeThenKey(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/surfaces/withdraw.surface.json");
        ManifestData baseline = readManifestData(manifest);
        baseline.capabilities.remove("idempotency");
        baseline.capabilities.put("ledger", new ArrayList<>(List.of("getBalance")));
        baseline.invariants.add("non_negative:zzz");
        writeManifestData(manifest, baseline);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        List<String> boundaryLines = normalizeLf(run.stderr).lines()
            .filter(line -> line.startsWith("boundary: "))
            .toList();
        assertEquals(List.of(
            "boundary: EXPANSION: CAPABILITY_ADDED: idempotency",
            "boundary: EXPANSION: CAPABILITY_OP_ADDED: ledger.setBalance",
            "boundary: EXPANSION: INVARIANT_RELAXED: non_negative:zzz"
        ), boundaryLines);
        assertFailureEnvelope(
            run.stderr,
            "DRIFT_DETECTED",
            "build/generated/bear",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkWarnsWhenBaselineManifestMissing(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/surfaces/withdraw.surface.json");
        Files.delete(manifest);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, run.exitCode);
        assertTrue(normalizeLf(run.stderr).contains("check: BASELINE_MANIFEST_MISSING: " + manifest));
        assertFailureEnvelope(
            run.stderr,
            "DRIFT_DETECTED",
            "build/generated/bear",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkWarnsWhenBaselineManifestInvalid(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/surfaces/withdraw.surface.json");
        Files.writeString(manifest, "{invalid", StandardCharsets.UTF_8);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, run.exitCode);
        assertTrue(normalizeLf(run.stderr).contains("check: BASELINE_MANIFEST_INVALID: MALFORMED_JSON"));
        assertFailureEnvelope(
            run.stderr,
            "DRIFT_DETECTED",
            "build/generated/bear",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkWarnsOnBaselineStampMismatch(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/surfaces/withdraw.surface.json");
        ManifestData baseline = readManifestData(manifest);
        baseline.irHash = "0000000000000000000000000000000000000000000000000000000000000000";
        writeManifestData(manifest, baseline);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, run.exitCode);
        assertTrue(normalizeLf(run.stderr).contains(
            "check: BASELINE_STAMP_MISMATCH: irHash/generatorVersion differ; classification may be stale"));
        assertFailureEnvelope(
            run.stderr,
            "DRIFT_DETECTED",
            "build/generated/bear",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkFailsInternalWhenCandidateManifestMissingOrInvalid(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        String previous = System.getProperty("bear.check.test.candidateManifestMode");
        try {
            System.setProperty("bear.check.test.candidateManifestMode", "missing");
            CliRunResult missing = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
            assertEquals(70, missing.exitCode);
            assertTrue(normalizeLf(missing.stderr).startsWith("internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_MISSING"));
            assertFailureEnvelope(
                missing.stderr,
                "INTERNAL_ERROR",
                "build/generated/bear/surfaces/withdraw.surface.json",
                "Capture stderr and file an issue against bear-cli."
            );

            System.setProperty("bear.check.test.candidateManifestMode", "invalid");
            CliRunResult invalid = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
            assertEquals(70, invalid.exitCode);
            assertTrue(normalizeLf(invalid.stderr).startsWith("internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_INVALID:"));
            assertFailureEnvelope(
                invalid.stderr,
                "INTERNAL_ERROR",
                "build/generated/bear/surfaces/withdraw.surface.json",
                "Capture stderr and file an issue against bear-cli."
            );
        } finally {
            if (previous == null) {
                System.clearProperty("bear.check.test.candidateManifestMode");
            } else {
                System.setProperty("bear.check.test.candidateManifestMode", previous);
            }
        }
    }

    @Test
    void checkMissingProjectWrapperReturnsIoError(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);
        writeWorkingWithdrawImpl(tempDir);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        assertTrue(check.stderr.startsWith("io: IO_ERROR: PROJECT_TEST_WRAPPER_MISSING:"));
        assertFailureEnvelope(
            check.stderr,
            "IO_ERROR",
            "project.root",
            "Ensure project paths are accessible (including Gradle wrapper), then rerun `bear check`."
        );
    }

    @Test
    void checkAllowedDepsWithoutWrapperFailsUnsupportedTarget(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));

        CliRunResult compile = runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);
        writeWorkingWithdrawImpl(tempDir);

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: CONTAINMENT_REQUIRED: UNSUPPORTED_TARGET: missing Gradle wrapper"));
        assertFailureEnvelope(
            check.stderr,
            "CONTAINMENT_UNSUPPORTED_TARGET",
            "project.root",
            "Containment enforcement in P2 requires Java+Gradle with wrapper at project root; remove `impl.allowedDeps`/`bear-policy/_shared.policy.yaml` scope usage or use supported target, then rerun `bear check`."
        );
    }

    @Test
    void checkAllAllowedDepsWithoutWrapperMatchesSingleCheckUnsupportedTarget(@TempDir Path tempDir) throws Exception {
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Path ir = specDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));

        CliRunResult compile = runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);
        writeWorkingWithdrawImpl(tempDir);

        CliRunResult single = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(74, single.exitCode);
        assertTrue(normalizeLf(single.stderr).contains("check: CONTAINMENT_REQUIRED: UNSUPPORTED_TARGET: missing Gradle wrapper"));
        assertFailureEnvelope(
            single.stderr,
            "CONTAINMENT_UNSUPPORTED_TARGET",
            "project.root",
            "Containment enforcement in P2 requires Java+Gradle with wrapper at project root; remove `impl.allowedDeps`/`bear-policy/_shared.policy.yaml` scope usage or use supported target, then rerun `bear check`."
        );

        writeBlockIndex(tempDir, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: withdraw\n"
            + "    ir: spec/withdraw-allowedDeps.bear.yaml\n"
            + "    projectRoot: .\n");

        CliRunResult all = runCli(new String[] { "check", "--all", "--project", tempDir.toString() });
        assertEquals(74, all.exitCode);
        String stderr = normalizeLf(all.stderr);
        assertTrue(stderr.contains("BLOCK: withdraw"));
        assertTrue(stderr.contains("BLOCK_CODE: CONTAINMENT_UNSUPPORTED_TARGET"));
        assertTrue(stderr.contains("BLOCK_PATH: project.root"));
        assertTrue(stderr.contains("DETAIL: check: CONTAINMENT_REQUIRED: UNSUPPORTED_TARGET: missing Gradle wrapper"));
        assertFailureEnvelope(
            all.stderr,
            "REPO_MULTI_BLOCK_FAILED",
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    @Test
    void checkAllowedDepsMissingMarkerFailsDeterministically(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));

        CliRunResult compile = runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: CONTAINMENT_REQUIRED: MARKER_MISSING: build/bear/containment/applied.marker"));
        assertFailureEnvelope(
            check.stderr,
            "CONTAINMENT_NOT_VERIFIED",
            "build/bear/containment/applied.marker",
            "Run Gradle build once so BEAR containment marker tasks write markers, then rerun `bear check`."
        );
    }

    @Test
    void checkAllowedDepsDoesNotRunMarkerVerificationWhenProjectTestsFail(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));
        assertEquals(0, runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho FAILURE: Build failed with an exception.\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"FAILURE: Build failed with an exception.\"\nexit 1\n"
        );

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(4, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: TEST_FAILED: project tests failed"));
        assertFalse(stderr.contains("CONTAINMENT_REQUIRED: MARKER_MISSING"));
        assertFailureEnvelope(
            check.stderr,
            "TEST_FAILURE",
            "project.tests",
            "Fix project tests and rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkAllowedDepsStaleMarkerFailsDeterministically(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));

        CliRunResult compile = runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        Path marker = tempDir.resolve("build/bear/containment/applied.marker");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "hash=deadbeef\nblocks=withdraw\n", StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: CONTAINMENT_REQUIRED: MARKER_STALE: build/bear/containment/applied.marker"));
        assertFailureEnvelope(
            check.stderr,
            "CONTAINMENT_NOT_VERIFIED",
            "build/bear/containment/applied.marker",
            "Run Gradle build once after BEAR compile so containment markers refresh, then rerun `bear check`."
        );
    }

    @Test
    void checkAllowedDepsMissingContainmentRequiredIsDrift(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));
        assertEquals(0, runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        Files.deleteIfExists(tempDir.resolve("build/generated/bear/config/containment-required.json"));

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(3, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("drift: MISSING_BASELINE: build/generated/bear/config/containment-required.json"));
        assertFailureEnvelope(
            check.stderr,
            "DRIFT_MISSING_BASELINE",
            "build/generated/bear/config/containment-required.json",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkAllowedDepsPreflightFailureSkipsProjectTestExecution(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));
        assertEquals(0, runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path sentinel = tempDir.resolve("build/test-invoked.marker");
        writeProjectWrapper(
            tempDir,
            "@echo off\r\n"
                + "echo invoked>\"" + sentinel + "\"\r\n"
                + "echo TEST_OK\r\n"
                + "exit /b 0\r\n",
            "#!/usr/bin/env sh\n"
                + "echo invoked > \"" + sentinel.toString().replace("\\", "\\\\") + "\"\n"
                + "echo TEST_OK\n"
                + "exit 0\n"
        );
        Files.deleteIfExists(tempDir.resolve("build/generated/bear/config/containment-required.json"));

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(3, check.exitCode);
        assertFalse(Files.exists(sentinel));
        assertTrue(normalizeLf(check.stderr).contains("drift: MISSING_BASELINE: build/generated/bear/config/containment-required.json"));
    }

    @Test
    void checkAllowedDepsMalformedContainmentRequiredIsDrift(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));
        assertEquals(0, runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        Files.writeString(
            tempDir.resolve("build/generated/bear/config/containment-required.json"),
            "{\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(3, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("drift: CHANGED: build/generated/bear/config/containment-required.json"));
        assertFailureEnvelope(
            check.stderr,
            "DRIFT_DETECTED",
            "build/generated/bear/config/containment-required.json",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkAllowedDepsAggregateBlocksMismatchFailsDeterministically(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));
        assertEquals(0, runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path required = tempDir.resolve("build/generated/bear/config/containment-required.json");
        String hash = bytesToHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(required)));
        Path marker = tempDir.resolve("build/bear/containment/applied.marker");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "hash=" + hash + "\nblocks=other\n", StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: CONTAINMENT_REQUIRED: MARKER_STALE: build/bear/containment/applied.marker"));
        assertFailureEnvelope(
            check.stderr,
            "CONTAINMENT_NOT_VERIFIED",
            "build/bear/containment/applied.marker",
            "Run Gradle build once after BEAR compile so containment markers refresh, then rerun `bear check`."
        );
    }

    @Test
    void checkAllowedDepsMissingPerBlockMarkerFailsDeterministically(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));
        assertEquals(0, runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path required = tempDir.resolve("build/generated/bear/config/containment-required.json");
        String hash = bytesToHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(required)));
        Path marker = tempDir.resolve("build/bear/containment/applied.marker");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "hash=" + hash + "\nblocks=withdraw\n", StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: CONTAINMENT_REQUIRED: BLOCK_MARKER_MISSING: build/bear/containment/withdraw.applied.marker"));
        assertFailureEnvelope(
            check.stderr,
            "CONTAINMENT_NOT_VERIFIED",
            "build/bear/containment/withdraw.applied.marker",
            "Run Gradle build once so BEAR containment marker tasks write markers, then rerun `bear check`."
        );
    }

    @Test
    void checkAllowedDepsStalePerBlockMarkerFailsDeterministically(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));
        assertEquals(0, runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path required = tempDir.resolve("build/generated/bear/config/containment-required.json");
        String hash = bytesToHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(required)));
        Path marker = tempDir.resolve("build/bear/containment/applied.marker");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "hash=" + hash + "\nblocks=withdraw\n", StandardCharsets.UTF_8);
        Path perBlockMarker = tempDir.resolve("build/bear/containment/withdraw.applied.marker");
        Files.writeString(perBlockMarker, "block=other\nhash=" + hash + "\n", StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: CONTAINMENT_REQUIRED: BLOCK_MARKER_STALE: build/bear/containment/withdraw.applied.marker"));
        assertFailureEnvelope(
            check.stderr,
            "CONTAINMENT_NOT_VERIFIED",
            "build/bear/containment/withdraw.applied.marker",
            "Run Gradle build once after BEAR compile so containment markers refresh, then rerun `bear check`."
        );
    }

    @Test
    void checkAllowedDepsPerBlockMarkerUsesFirstSortedRequiredBlock(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));
        assertEquals(0, runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path required = tempDir.resolve("build/generated/bear/config/containment-required.json");
        Files.writeString(
            required,
            ""
                + "{\"schemaVersion\":\"v1\",\"target\":\"java-gradle\",\"blocks\":["
                + "{\"blockKey\":\"withdraw\",\"implDir\":\"src/main/java/blocks/withdraw/impl\",\"allowedDeps\":[{\"ga\":\"com.fasterxml.jackson.core:jackson-databind\",\"version\":\"2.17.2\"}]},"
                + "{\"blockKey\":\"alpha\",\"implDir\":\"src/main/java/blocks/alpha/impl\",\"allowedDeps\":[{\"ga\":\"com.fasterxml.jackson.core:jackson-databind\",\"version\":\"2.17.2\"}]}"
                + "]}\n",
            StandardCharsets.UTF_8
        );
        String hash = bytesToHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(required)));
        Path marker = tempDir.resolve("build/bear/containment/applied.marker");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "hash=" + hash + "\nblocks=alpha,withdraw\n", StandardCharsets.UTF_8);
        Path withdrawBlockMarker = tempDir.resolve("build/bear/containment/withdraw.applied.marker");
        Files.writeString(withdrawBlockMarker, "block=withdraw\nhash=" + hash + "\n", StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: CONTAINMENT_REQUIRED: BLOCK_MARKER_MISSING: build/bear/containment/alpha.applied.marker"));
        assertFailureEnvelope(
            check.stderr,
            "CONTAINMENT_NOT_VERIFIED",
            "build/bear/containment/alpha.applied.marker",
            "Run Gradle build once so BEAR containment marker tasks write markers, then rerun `bear check`."
        );
    }

    @Test
    void checkAllowedDepsWithFreshMarkerPasses(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));

        CliRunResult compile = runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path required = tempDir.resolve("build/generated/bear/config/containment-required.json");
        String hash = bytesToHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(required)));
        Path marker = tempDir.resolve("build/bear/containment/applied.marker");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "hash=" + hash + "\nblocks=withdraw\n", StandardCharsets.UTF_8);
        Path perBlockMarker = tempDir.resolve("build/bear/containment/withdraw.applied.marker");
        Files.writeString(perBlockMarker, "block=withdraw\nhash=" + hash + "\n", StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
        assertTrue(check.stdout.startsWith("check: OK"));
    }

    @Test
    void checkContainmentInScopeFromSharedPolicyOnlyRequiresMarkers(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        Path policy = tempDir.resolve("bear-policy/_shared.policy.yaml");
        Files.createDirectories(policy.getParent());
        Files.writeString(
            policy,
            ""
                + "version: v1\n"
                + "scope: shared\n"
                + "impl:\n"
                + "  allowedDeps: []\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: CONTAINMENT_REQUIRED: MARKER_MISSING: build/bear/containment/applied.marker"));
    }

    @Test
    void checkContainmentInScopeFromSharedSourcesOnlyRequiresMarkers(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        Path sharedSource = tempDir.resolve("src/main/java/blocks/_shared/state/SharedOnly.java");
        Files.createDirectories(sharedSource.getParent());
        Files.writeString(sharedSource, "package blocks._shared.state;\npublic final class SharedOnly {}\n", StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: CONTAINMENT_REQUIRED: MARKER_MISSING: build/bear/containment/applied.marker"));
    }

    @Test
    void checkDoesNotActivateContainmentForEmptySharedDirWithoutPolicy(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        Files.createDirectories(tempDir.resolve("src/main/java/blocks/_shared"));

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
    }

    @Test
    void checkWithoutContainmentScopeDoesNotPreflightContainmentEntrypoint(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        Files.deleteIfExists(tempDir.resolve("build/generated/bear/gradle/bear-containment.gradle"));

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
        assertTrue(normalizeLf(check.stdout).contains("check: OK"));
    }

    @Test
    void checkSharedPolicyInvalidFailsValidation(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        Path policy = tempDir.resolve("bear-policy/_shared.policy.yaml");
        Files.createDirectories(policy.getParent());
        Files.writeString(
            policy,
            ""
                + "version: v1\n"
                + "scope: not_shared\n"
                + "impl:\n"
                + "  allowedDeps: []\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(2, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: POLICY_INVALID: INVALID_SCOPE"));
        assertFailureEnvelope(
            check.stderr,
            "POLICY_INVALID",
            "bear-policy/_shared.policy.yaml",
            "Fix `bear-policy/_shared.policy.yaml` (version/scope/schema and pinned allowedDeps) and rerun `bear check`."
        );
    }

    @Test
    void checkSharedDepsViolationFromProjectTestsUsesSharedPolicyRemediation(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));
        assertEquals(0, runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeFreshContainmentMarkers(tempDir);
        String marker = "BEAR_SHARED_DEPS_VIOLATION|unit=_shared|task=compileBearImpl__shared";
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho " + marker.replace("|", "^|") + "\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"" + marker + "\"\nexit 1\n"
        );

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: CONTAINMENT_REQUIRED: SHARED_DEPS_VIOLATION:"));
        assertFailureEnvelope(
            check.stderr,
            "CONTAINMENT_NOT_VERIFIED",
            "bear-policy/_shared.policy.yaml",
            "Add dependency to `bear-policy/_shared.policy.yaml` with exact pinned version, or remove external dependency usage from `src/main/java/blocks/_shared/**`, then rerun `bear check`."
        );
    }

    @Test
    void checkEmitsContainmentSkipInfoWhenSelectionHasNoAllowedDepsAndRequirementIsNonEmpty(@TempDir Path tempDir) throws Exception {
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Path alphaIr = specDir.resolve("alpha.bear.yaml");
        Path betaIr = specDir.resolve("beta.bear.yaml");
        Files.writeString(alphaIr, fixtureIrForBlockName("alpha"), StandardCharsets.UTF_8);
        Files.writeString(betaIr, fixtureIrWithAllowedDepForBlock("beta", "com.fasterxml.jackson.core:jackson-databind", "2.17.2"), StandardCharsets.UTF_8);

        Path projectRoot = tempDir.resolve("service");
        Files.createDirectories(projectRoot);
        assertEquals(0, runCli(new String[] { "compile", betaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        writeWorkingBlockImpl(projectRoot, "alpha");
        writeWorkingBlockImpl(projectRoot, "beta");
        writeProjectWrapper(
            projectRoot,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", alphaIr.toString(), "--project", projectRoot.toString() });
        assertEquals(0, check.exitCode);
        String stdout = normalizeLf(check.stdout);
        String info = "check: INFO: CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION: projectRoot="
            + projectRoot.toString().replace('\\', '/')
            + ": reason=no_selected_blocks_with_impl_allowedDeps";
        assertTrue(stdout.contains(info));
        assertTrue(stdout.indexOf(info) < stdout.indexOf("check: OK"));
        assertEquals("", check.stderr);
    }

    @Test
    void checkAllEnforcesContainmentWhenSelectedBlocksIncludeAllowedDeps(@TempDir Path tempDir) throws Exception {
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Path betaIr = specDir.resolve("beta.bear.yaml");
        Files.writeString(
            betaIr,
            fixtureIrWithAllowedDepForBlock("beta", "com.fasterxml.jackson.core:jackson-databind", "2.17.2"),
            StandardCharsets.UTF_8
        );

        Path projectRoot = tempDir.resolve("services/shared");
        Files.createDirectories(projectRoot);
        assertEquals(0, runCli(new String[] { "compile", betaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        writeWorkingBlockImpl(projectRoot, "beta");
        writeProjectWrapper(
            projectRoot,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        writeBlockIndex(tempDir, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: beta\n"
            + "    ir: spec/beta.bear.yaml\n"
            + "    projectRoot: services/shared\n");

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", tempDir.toString() });
        assertEquals(74, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK: beta"));
        assertTrue(stderr.contains("DETAIL: check: CONTAINMENT_REQUIRED: MARKER_MISSING: build/bear/containment/applied.marker"));
        assertTrue(stderr.contains("BLOCK_CODE: CONTAINMENT_NOT_VERIFIED"));
        assertFalse(stderr.contains("CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION"));
        assertFailureEnvelope(
            run.stderr,
            "REPO_MULTI_BLOCK_FAILED",
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    @Test
    void checkAllRunsCompileAndTestInvocationPerContainmentEnabledRoot(@TempDir Path tempDir) throws Exception {
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Path alphaIr = specDir.resolve("alpha.bear.yaml");
        Path betaIr = specDir.resolve("beta.bear.yaml");
        Files.writeString(alphaIr, fixtureIrForBlockName("alpha"), StandardCharsets.UTF_8);
        Files.writeString(
            betaIr,
            fixtureIrWithAllowedDepForBlock("beta", "com.fasterxml.jackson.core:jackson-databind", "2.17.2"),
            StandardCharsets.UTF_8
        );

        Path projectRoot = tempDir.resolve("services/shared");
        Files.createDirectories(projectRoot);
        assertEquals(0, runCli(new String[] { "compile", betaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        writeWorkingBlockImpl(projectRoot, "alpha");
        writeWorkingBlockImpl(projectRoot, "beta");
        writeFreshContainmentMarkers(projectRoot);

        Path invocationCount = projectRoot.resolve("build/test-run-count.txt");
        writeProjectWrapper(
            projectRoot,
            "@echo off\r\n"
                + "set \"countFile=" + invocationCount + "\"\r\n"
                + "set \"n=0\"\r\n"
                + "if exist \"%countFile%\" set /p n=<\"%countFile%\"\r\n"
                + "set /a n=n+1\r\n"
                + ">\"%countFile%\" echo %n%\r\n"
                + "echo TEST_OK\r\n"
                + "exit /b 0\r\n",
            "#!/usr/bin/env sh\n"
                + "count_file=\"" + invocationCount.toString().replace("\\", "\\\\") + "\"\n"
                + "n=0\n"
                + "if [ -f \"$count_file\" ]; then n=$(cat \"$count_file\"); fi\n"
                + "n=$((n+1))\n"
                + "echo \"$n\" > \"$count_file\"\n"
                + "echo TEST_OK\n"
                + "exit 0\n"
        );

        writeBlockIndex(tempDir, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/alpha.bear.yaml\n"
            + "    projectRoot: services/shared\n"
            + "  - name: beta\n"
            + "    ir: spec/beta.bear.yaml\n"
            + "    projectRoot: services/shared\n");

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", tempDir.toString() });
        assertEquals(0, run.exitCode);
        assertEquals("2", Files.readString(invocationCount, StandardCharsets.UTF_8).trim());
    }

    @Test
    void checkAllContainmentPreflightRunsOncePerRootBeforeSingleTestInvocation(@TempDir Path tempDir) throws Exception {
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Path alphaIr = specDir.resolve("alpha.bear.yaml");
        Path betaIr = specDir.resolve("beta.bear.yaml");
        Files.writeString(alphaIr, fixtureIrForBlockName("alpha"), StandardCharsets.UTF_8);
        Files.writeString(
            betaIr,
            fixtureIrWithAllowedDepForBlock("beta", "com.fasterxml.jackson.core:jackson-databind", "2.17.2"),
            StandardCharsets.UTF_8
        );

        Path projectRoot = tempDir.resolve("services/shared");
        Files.createDirectories(projectRoot);
        assertEquals(0, runCli(new String[] { "compile", betaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        writeWorkingBlockImpl(projectRoot, "alpha");
        writeWorkingBlockImpl(projectRoot, "beta");
        writeFreshContainmentMarkers(projectRoot);

        Path invocationCount = projectRoot.resolve("build/test-run-count.txt");
        writeProjectWrapper(
            projectRoot,
            "@echo off\r\n"
                + "set \"countFile=" + invocationCount + "\"\r\n"
                + "set \"n=0\"\r\n"
                + "if exist \"%countFile%\" set /p n=<\"%countFile%\"\r\n"
                + "set /a n=n+1\r\n"
                + ">\"%countFile%\" echo %n%\r\n"
                + "echo TEST_OK\r\n"
                + "exit /b 0\r\n",
            "#!/usr/bin/env sh\n"
                + "count_file=\"" + invocationCount.toString().replace("\\", "\\\\") + "\"\n"
                + "n=0\n"
                + "if [ -f \"$count_file\" ]; then n=$(cat \"$count_file\"); fi\n"
                + "n=$((n+1))\n"
                + "echo \"$n\" > \"$count_file\"\n"
                + "echo TEST_OK\n"
                + "exit 0\n"
        );

        Files.deleteIfExists(projectRoot.resolve("build/generated/bear/config/containment-required.json"));

        writeBlockIndex(tempDir, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/alpha.bear.yaml\n"
            + "    projectRoot: services/shared\n"
            + "  - name: beta\n"
            + "    ir: spec/beta.bear.yaml\n"
            + "    projectRoot: services/shared\n");

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", tempDir.toString(), "--fail-fast"
        });
        assertEquals(3, run.exitCode);
        assertFalse(Files.exists(invocationCount));
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("drift: MISSING_BASELINE: build/generated/bear/config/containment-required.json"));
        assertFalse(stderr.contains("REASON: FAIL_FAST_ABORT"));
        assertEquals(2, countOccurrences(stderr, "STATUS: FAIL"));
    }

    @Test
    void checkAllEnforcesContainmentWhenSharedPolicyExistsWithoutAllowedDeps(@TempDir Path tempDir) throws Exception {
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Path alphaIr = specDir.resolve("alpha.bear.yaml");
        Files.writeString(alphaIr, fixtureIrForBlockName("alpha"), StandardCharsets.UTF_8);

        Path projectRoot = tempDir.resolve("services/shared");
        Files.createDirectories(projectRoot);
        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        writeWorkingBlockImpl(projectRoot, "alpha");
        writeProjectWrapper(
            projectRoot,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        writeSharedPolicy(
            projectRoot,
            ""
                + "version: v1\n"
                + "scope: shared\n"
                + "impl:\n"
                + "  allowedDeps: []\n"
        );

        writeBlockIndex(tempDir, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/alpha.bear.yaml\n"
            + "    projectRoot: services/shared\n");

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", tempDir.toString() });
        assertEquals(74, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK: alpha"));
        assertTrue(stderr.contains("DETAIL: check: CONTAINMENT_REQUIRED: MARKER_MISSING: build/bear/containment/applied.marker"));
    }

    @Test
    void checkAllEnforcesContainmentWhenSharedSourcesExistWithoutAllowedDeps(@TempDir Path tempDir) throws Exception {
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Path alphaIr = specDir.resolve("alpha.bear.yaml");
        Files.writeString(alphaIr, fixtureIrForBlockName("alpha"), StandardCharsets.UTF_8);

        Path projectRoot = tempDir.resolve("services/shared");
        Files.createDirectories(projectRoot);
        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        writeWorkingBlockImpl(projectRoot, "alpha");
        writeProjectWrapper(
            projectRoot,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        Path sharedSource = projectRoot.resolve("src/main/java/blocks/_shared/state/SharedOnly.java");
        Files.createDirectories(sharedSource.getParent());
        Files.writeString(sharedSource, "package blocks._shared.state;\npublic final class SharedOnly {}\n", StandardCharsets.UTF_8);

        writeBlockIndex(tempDir, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/alpha.bear.yaml\n"
            + "    projectRoot: services/shared\n");

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", tempDir.toString() });
        assertEquals(74, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK: alpha"));
        assertTrue(stderr.contains("DETAIL: check: CONTAINMENT_REQUIRED: MARKER_MISSING: build/bear/containment/applied.marker"));
    }

    @Test
    void checkAllMapsSharedDepsViolationToContainmentLane(@TempDir Path tempDir) throws Exception {
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Path alphaIr = specDir.resolve("alpha.bear.yaml");
        Files.writeString(alphaIr, fixtureIrForBlockName("alpha"), StandardCharsets.UTF_8);

        Path projectRoot = tempDir.resolve("services/shared");
        Files.createDirectories(projectRoot);
        writeSharedPolicy(
            projectRoot,
            ""
                + "version: v1\n"
                + "scope: shared\n"
                + "impl:\n"
                + "  allowedDeps: []\n"
        );
        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        writeWorkingBlockImpl(projectRoot, "alpha");
        writeFreshContainmentMarkers(projectRoot);
        String marker = "BEAR_SHARED_DEPS_VIOLATION|unit=_shared|task=compileBearImpl__shared";
        writeProjectWrapper(
            projectRoot,
            "@echo off\r\necho " + marker.replace("|", "^|") + "\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"" + marker + "\"\nexit 1\n"
        );

        writeBlockIndex(tempDir, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/alpha.bear.yaml\n"
            + "    projectRoot: services/shared\n");

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", tempDir.toString() });
        assertEquals(74, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK: alpha"));
        assertTrue(stderr.contains("BLOCK_CODE: CONTAINMENT_NOT_VERIFIED"));
        assertTrue(stderr.contains("DETAIL: check: CONTAINMENT_REQUIRED: SHARED_DEPS_VIOLATION:"));
    }


    @Test
    void checkAllMapsContainmentCompileFailureToContainmentLane(@TempDir Path tempDir) throws Exception {
        Path irDir = tempDir.resolve("bear-ir");
        Files.createDirectories(irDir);
        Path alphaIr = irDir.resolve("alpha.bear.yaml");
        Files.writeString(alphaIr, fixtureIrWithAllowedDepForBlock("alpha", "com.fasterxml.jackson.core:jackson-databind", "2.17.2"), StandardCharsets.UTF_8);

        Path projectRoot = tempDir.resolve("services/shared");
        Files.createDirectories(projectRoot);
        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        writeWorkingBlockImpl(projectRoot, "alpha");
        writeProjectWrapper(
            projectRoot,
            "@echo off\r\necho ^> Task :compileBearImpl__shared FAILED\r\necho package blocks.account.impl does not exist\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"> Task :compileBearImpl__shared FAILED\"\necho \"package blocks.account.impl does not exist\"\nexit 1\n"
        );

        writeBlockIndex(tempDir, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: bear-ir/alpha.bear.yaml\n"
            + "    projectRoot: services/shared\n");

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", tempDir.toString() });
        assertEquals(4, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK: alpha"));
        assertTrue(stderr.contains("BLOCK_CODE: CONTAINMENT_NOT_VERIFIED"));
        assertTrue(stderr.contains("DETAIL: check: CONTAINMENT_REQUIRED: CONTAINMENT_METADATA_MISMATCH:"));
    }

    @Test
    void checkAllMapsContainmentGenericFailureToContainmentLane(@TempDir Path tempDir) throws Exception {
        Path irDir = tempDir.resolve("bear-ir");
        Files.createDirectories(irDir);
        Path alphaIr = irDir.resolve("alpha.bear.yaml");
        Files.writeString(alphaIr, fixtureIrWithAllowedDepForBlock("alpha", "com.fasterxml.jackson.core:jackson-databind", "2.17.2"), StandardCharsets.UTF_8);

        Path projectRoot = tempDir.resolve("services/shared");
        Files.createDirectories(projectRoot);
        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        writeWorkingBlockImpl(projectRoot, "alpha");
        writeProjectWrapper(
            projectRoot,
            "@echo off\r\necho ^> Task :compileBearImpl__shared\r\necho build/generated/bear/gradle/bear-containment.gradle\r\necho package blocks.account.impl does not exist\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"> Task :compileBearImpl__shared\"\necho \"build/generated/bear/gradle/bear-containment.gradle\"\necho \"package blocks.account.impl does not exist\"\nexit 1\n"
        );

        writeBlockIndex(tempDir, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: bear-ir/alpha.bear.yaml\n"
            + "    projectRoot: services/shared\n");

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", tempDir.toString() });
        assertEquals(4, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK: alpha"));
        assertTrue(stderr.contains("BLOCK_CODE: CONTAINMENT_NOT_VERIFIED"));
        assertTrue(stderr.contains("DETAIL: check: CONTAINMENT_REQUIRED: CONTAINMENT_METADATA_MISMATCH: root-level project tests failed for projectRoot services/shared"));
    }
    @Test
    void checkOmitsContainmentSkipInfoWhenContainmentRequiredMissing(@TempDir Path tempDir) throws Exception {
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Path alphaIr = specDir.resolve("alpha.bear.yaml");
        Path betaIr = specDir.resolve("beta.bear.yaml");
        Files.writeString(alphaIr, fixtureIrForBlockName("alpha"), StandardCharsets.UTF_8);
        Files.writeString(betaIr, fixtureIrWithAllowedDepForBlock("beta", "com.fasterxml.jackson.core:jackson-databind", "2.17.2"), StandardCharsets.UTF_8);

        Path projectRoot = tempDir.resolve("service");
        Files.createDirectories(projectRoot);
        assertEquals(0, runCli(new String[] { "compile", betaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        Files.deleteIfExists(projectRoot.resolve("build/generated/bear/config/containment-required.json"));
        writeWorkingBlockImpl(projectRoot, "alpha");
        writeProjectWrapper(
            projectRoot,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", alphaIr.toString(), "--project", projectRoot.toString() });
        assertEquals(0, check.exitCode);
        String stdout = normalizeLf(check.stdout);
        assertFalse(stdout.contains("CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION"));
        assertEquals("check: OK\n", stdout);
        assertEquals("", check.stderr);
    }

    @Test
    void checkOmitsContainmentSkipInfoWhenContainmentRequiredMalformed(@TempDir Path tempDir) throws Exception {
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Path alphaIr = specDir.resolve("alpha.bear.yaml");
        Path betaIr = specDir.resolve("beta.bear.yaml");
        Files.writeString(alphaIr, fixtureIrForBlockName("alpha"), StandardCharsets.UTF_8);
        Files.writeString(betaIr, fixtureIrWithAllowedDepForBlock("beta", "com.fasterxml.jackson.core:jackson-databind", "2.17.2"), StandardCharsets.UTF_8);

        Path projectRoot = tempDir.resolve("service");
        Files.createDirectories(projectRoot);
        assertEquals(0, runCli(new String[] { "compile", betaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", projectRoot.toString() }).exitCode);
        Files.writeString(
            projectRoot.resolve("build/generated/bear/config/containment-required.json"),
            "{\n",
            StandardCharsets.UTF_8
        );
        writeWorkingBlockImpl(projectRoot, "alpha");
        writeProjectWrapper(
            projectRoot,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", alphaIr.toString(), "--project", projectRoot.toString() });
        assertEquals(0, check.exitCode);
        String stdout = normalizeLf(check.stdout);
        assertFalse(stdout.contains("CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION"));
        assertEquals("check: OK\n", stdout);
        assertEquals("", check.stderr);
    }

    @Test
    void checkOmitsContainmentSkipInfoWhenContainmentRequiredIsEmpty(@TempDir Path tempDir) throws Exception {
        Path alphaIr = tempDir.resolve("alpha.bear.yaml");
        Files.writeString(alphaIr, fixtureIrForBlockName("alpha"), StandardCharsets.UTF_8);
        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingBlockImpl(tempDir, "alpha");
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", alphaIr.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
        String stdout = normalizeLf(check.stdout);
        assertFalse(stdout.contains("CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION"));
        assertEquals("check: OK\n", stdout);
        assertEquals("", check.stderr);
    }

    @Test
    void checkWithoutAllowedDepsKeepsImplOnNormalCompilePath(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path usesImpl = tempDir.resolve("src/test/java/com/example/UsesWithdrawImpl.java");
        Files.createDirectories(usesImpl.getParent());
        Files.writeString(usesImpl, ""
            + "package com.example;\n"
            + "\n"
            + "import blocks.withdraw.impl.WithdrawImpl;\n"
            + "\n"
            + "public final class UsesWithdrawImpl {\n"
            + "    private final WithdrawImpl impl = new WithdrawImpl();\n"
            + "    public WithdrawImpl impl() { return impl; }\n"
            + "}\n",
            StandardCharsets.UTF_8
        );

        writeProjectWrapper(
            tempDir,
            "@echo off\r\n"
                + "setlocal\r\n"
                + "findstr /C:\"exclude('blocks/**/impl/**')\" \"build\\generated\\bear\\gradle\\bear-containment.gradle\" >nul && (\r\n"
                + "  echo GLOBAL_EXCLUDE_PRESENT\r\n"
                + "  exit /b 1\r\n"
                + ")\r\n"
                + "if not exist src\\test\\java\\com\\example\\UsesWithdrawImpl.java exit /b 1\r\n"
                + "if not exist src\\main\\java\\blocks\\withdraw\\impl\\WithdrawImpl.java exit /b 1\r\n"
                + "if not exist build\\project-test-classes mkdir build\\project-test-classes\r\n"
                + "javac -d build\\project-test-classes build\\generated\\bear\\src\\main\\java\\com\\bear\\generated\\withdraw\\*.java build\\generated\\bear\\src\\main\\java\\com\\bear\\generated\\runtime\\*.java src\\main\\java\\blocks\\withdraw\\impl\\WithdrawImpl.java src\\test\\java\\com\\example\\UsesWithdrawImpl.java\r\n"
                + "if errorlevel 1 exit /b 1\r\n"
                + "echo TEST_OK\r\n"
                + "exit /b 0\r\n",
            "#!/usr/bin/env sh\n"
                + "set -e\n"
                + "if grep -F \"exclude('blocks/**/impl/**')\" \"build/generated/bear/gradle/bear-containment.gradle\" >/dev/null; then\n"
                + "  echo GLOBAL_EXCLUDE_PRESENT\n"
                + "  exit 1\n"
                + "fi\n"
                + "test -f src/test/java/com/example/UsesWithdrawImpl.java\n"
                + "test -f src/main/java/blocks/withdraw/impl/WithdrawImpl.java\n"
                + "mkdir -p build/project-test-classes\n"
                + "javac -d build/project-test-classes build/generated/bear/src/main/java/com/bear/generated/withdraw/*.java build/generated/bear/src/main/java/com/bear/generated/runtime/*.java src/main/java/blocks/withdraw/impl/WithdrawImpl.java src/test/java/com/example/UsesWithdrawImpl.java\n"
                + "echo TEST_OK\n"
                + "exit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
        assertTrue(check.stdout.startsWith("check: OK"));
    }

    @Test
    void checkRunsProjectTestsAndPasses(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
        assertTrue(check.stdout.startsWith("check: OK"));
    }

    @Test
    void checkSetsGradleUserHomeByDefault(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path marker = tempDir.resolve("gradle-user-home.txt");
        String markerPath = marker.toString();
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho %GRADLE_USER_HOME%>\"" + markerPath + "\"\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho \"$GRADLE_USER_HOME\" > \"" + markerPath.replace("\\", "\\\\") + "\"\nexit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
        String actual = Files.readString(marker, StandardCharsets.UTF_8).trim();
        String expected = System.getenv("GRADLE_USER_HOME");
        if (expected == null || expected.isBlank()) {
            expected = tempDir.resolve(".bear-gradle-user-home").toString();
        }
        assertEquals(expected, actual);
    }

    @Test
    void checkProjectTestGradleLockReturnsIoError(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho java.io.FileNotFoundException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip.lck (Access is denied)\r\necho PROJECT_TEST_GRADLE_LOCK_SIMULATED\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"java.io.FileNotFoundException: /tmp/gradle-8.12.1-bin.zip.lck (Access is denied)\"\necho \"PROJECT_TEST_GRADLE_LOCK_SIMULATED\"\nexit 1\n"
        );

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
            assertEquals(74, check.exitCode);
            String stderr = normalizeLf(check.stderr);
            assertTrue(stderr.startsWith("io: IO_ERROR: PROJECT_TEST_LOCK:"));
            assertTrue(stderr.contains("; attempts="));
            assertTrue(stderr.contains("; CACHE_MODE=user-cache"));
            assertTrue(stderr.contains("; FALLBACK=to_user_cache"));
            assertFailureEnvelope(
                check.stderr,
                "IO_ERROR",
                "project.tests",
                "Use BEAR-selected GRADLE_USER_HOME mode, run `bear unblock --project <path>`, then rerun `bear check <ir-file> --project <path>`."
            );
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void checkProjectTestGradleBootstrapFailureReturnsIoError(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho java.nio.file.NoSuchFileException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip\r\necho PROJECT_TEST_GRADLE_BOOTSTRAP_SIMULATED\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"java.nio.file.NoSuchFileException: /tmp/gradle-8.12.1-bin.zip\"\necho \"PROJECT_TEST_GRADLE_BOOTSTRAP_SIMULATED\"\nexit 1\n"
        );

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
            assertEquals(74, check.exitCode);
            String stderr = normalizeLf(check.stderr);
            assertTrue(stderr.startsWith("io: IO_ERROR: PROJECT_TEST_BOOTSTRAP:"));
            assertTrue(stderr.contains("; attempts="));
            assertTrue(stderr.contains("; CACHE_MODE=user-cache"));
            assertTrue(stderr.contains("; FALLBACK=to_user_cache"));
            assertFailureEnvelope(
                check.stderr,
                "IO_ERROR",
                "project.tests",
                "Use BEAR-selected GRADLE_USER_HOME mode, run `bear unblock --project <path>`, then rerun `bear check <ir-file> --project <path>`."
            );
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void checkProjectTestFailureReturnsExit4AndTail(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\nfor /L %%i in (1,1,50) do echo line%%i\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\nfor i in $(seq 1 50); do echo line$i; done\nexit 1\n"
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(4, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.startsWith("check: TEST_FAILED: project tests failed\n"));
        assertTrue(stderr.contains("\nline11\n"));
        assertTrue(stderr.contains("\nline50\n"));
        assertFalse(stderr.contains("\nline10\n"));
        assertFailureEnvelope(
            check.stderr,
            "TEST_FAILURE",
            "project.tests",
            "Fix project tests and rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkProjectTestTimeoutReturnsExit4(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho start\r\npowershell -Command \"Start-Sleep -Seconds 3\"\r\necho end\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho start\nsleep 3\necho end\nexit 0\n"
        );

        String timeoutPrev = System.getProperty("bear.check.testTimeoutSeconds");
        String forceTimeoutPrev = System.getProperty("bear.check.test.forceTimeout");
        String forceTimeoutOutcomePrev = System.getProperty("bear.check.test.forceTimeoutOutcome");
        Path timeoutMarker = tempDir.resolve(".bear-test-force-timeout");
        try {
            System.setProperty("bear.check.testTimeoutSeconds", "1");
            System.setProperty("bear.check.test.forceTimeout", "true");
            System.setProperty("bear.check.test.forceTimeoutOutcome", "true");
            Files.writeString(timeoutMarker, "true", StandardCharsets.UTF_8);
            CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
            String stderr = normalizeLf(check.stderr);
            String combined = "STDERR:\n" + stderr + "\nSTDOUT:\n" + normalizeLf(check.stdout);
            assertTrue(check.exitCode != 0, combined);
            assertTrue(stderr.contains("TEST_TIMEOUT"), combined);
            assertFailureEnvelope(
                check.stderr,
                "TEST_TIMEOUT",
                "project.tests",
                "Reduce test runtime or increase timeout, then rerun `bear check <ir-file> --project <path>`."
            );
        } finally {
            restoreSystemProperty("bear.check.testTimeoutSeconds", timeoutPrev);
            restoreSystemProperty("bear.check.test.forceTimeout", forceTimeoutPrev);
            restoreSystemProperty("bear.check.test.forceTimeoutOutcome", forceTimeoutOutcomePrev);
            Files.deleteIfExists(timeoutMarker);
        }
    }

    @Test
    void checkDriftShortCircuitsAndDoesNotRunProjectTests(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);

        Path marker = tempDir.resolve("wrapper-ran.txt");
        String markerPath = marker.toString();
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho ran>\"" + markerPath + "\"\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho ran > \"" + markerPath.replace("\\", "\\\\") + "\"\nexit 0\n"
        );

        Path driftFile = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw/Withdraw.java");
        Files.writeString(driftFile, "drift");

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, check.exitCode);
        assertFalse(Files.exists(marker));
        assertFailureEnvelope(
            check.stderr,
            "DRIFT_DETECTED",
            "build/generated/bear",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkUndeclaredReachReturnsExit6AndSkipsProjectTests(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path marker = tempDir.resolve("wrapper-ran.txt");
        String markerPath = marker.toString();
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho ran>\"" + markerPath + "\"\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho ran > \"" + markerPath.replace("\\", "\\\\") + "\"\nexit 0\n"
        );

        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.writeString(impl, "\n// violation\njava.net.http.HttpClient\n", StandardOpenOption.APPEND);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(6, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: UNDECLARED_REACH: src/main/java/blocks/withdraw/impl/WithdrawImpl.java: java.net.http.HttpClient"));
        assertFalse(Files.exists(marker));
        assertFailureEnvelope(
            check.stderr,
            "UNDECLARED_REACH",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            "Declare a port/op in IR, run bear compile, and route call through generated port interface."
        );
    }

    @Test
    void checkReflectionDispatchForbiddenReturnsDedicatedCodeAndSkipsProjectTests(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path marker = tempDir.resolve("wrapper-ran.txt");
        String markerPath = marker.toString();
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho ran>\"" + markerPath + "\"\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho ran > \"" + markerPath.replace("\\", "\\\\") + "\"\nexit 0\n"
        );

        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.writeString(impl, "\n// reflection-dispatch hygiene violation\nObject any = null;\nany.invoke();\n", StandardOpenOption.APPEND);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(6, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: UNDECLARED_REACH: src/main/java/blocks/withdraw/impl/WithdrawImpl.java: REACH_HYGIENE: KIND=REFLECTION_DISPATCH token=.invoke("));
        assertFalse(Files.exists(marker));
        assertFailureEnvelope(
            check.stderr,
            "REFLECTION_DISPATCH_FORBIDDEN",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            "Remove reflection/method-handle dynamic dispatch from governed roots and route through declared generated boundaries."
        );
    }

    @Test
    void checkReflectionDispatchForbiddenDetectsNoTargetTokenAliasCase(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path bypass = tempDir.resolve("src/main/java/blocks/withdraw/impl/ReflectionAliasBypass.java");
        Files.createDirectories(bypass.getParent());
        Files.writeString(
            bypass,
            "package blocks.withdraw.impl;\n"
                + "public final class ReflectionAliasBypass {\n"
                + "  Object run(Object candidate) {\n"
                + "    return candidate.invoke();\n"
                + "  }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(6, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains(
            "check: UNDECLARED_REACH: src/main/java/blocks/withdraw/impl/ReflectionAliasBypass.java: REACH_HYGIENE: KIND=REFLECTION_DISPATCH token=.invoke("
        ));
    }
    @Test
    void checkUndeclaredReachDetectsCoveredHttpSurfaces(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path srcRoot = tempDir.resolve("src/main/java/example");
        Files.createDirectories(srcRoot);
        Files.writeString(srcRoot.resolve("A.java"), "package example;\nimport java.net.http.HttpClient;\nclass A { HttpClient client; }\n");
        Files.writeString(srcRoot.resolve("B.java"), "package example;\nimport java.net.URL;\nclass B { void x(URL url) throws Exception { url.openConnection(); } }\n");
        Files.writeString(srcRoot.resolve("C.java"), "package example;\nimport okhttp3.OkHttpClient;\nclass C { OkHttpClient client; }\n");
        Files.writeString(srcRoot.resolve("D.java"), "package example;\nimport org.springframework.web.client.RestTemplate;\nclass D { RestTemplate template; }\n");
        Files.writeString(srcRoot.resolve("E.java"), "package example;\nimport java.net.HttpURLConnection;\nclass E { HttpURLConnection connection; }\n");

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString(), "--collect=all" });
        assertEquals(6, check.exitCode);
        List<String> lines = nonEnvelopeLines(check.stderr).stream()
            .filter(line -> line.startsWith("check: UNDECLARED_REACH: "))
            .toList();
        assertEquals(List.of(
            "check: UNDECLARED_REACH: src/main/java/example/A.java: java.net.http.HttpClient",
            "check: UNDECLARED_REACH: src/main/java/example/B.java: java.net.URL#openConnection",
            "check: UNDECLARED_REACH: src/main/java/example/C.java: okhttp3.OkHttpClient",
            "check: UNDECLARED_REACH: src/main/java/example/D.java: org.springframework.web.client.RestTemplate",
            "check: UNDECLARED_REACH: src/main/java/example/E.java: java.net.HttpURLConnection"
        ), lines);
    }

    @Test
    void checkUndeclaredReachSkipsExcludedPaths(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Files.createDirectories(tempDir.resolve("src/test/java/example"));
        Files.writeString(tempDir.resolve("src/test/java/example/TestOnly.java"), "class T { String s = \"java.net.http.HttpClient\"; }");
        Files.createDirectories(tempDir.resolve("build/tmp/example"));
        Files.writeString(tempDir.resolve("build/tmp/example/BuildOnly.java"), "class B { String s = \"okhttp3.OkHttpClient\"; }");
        Files.createDirectories(tempDir.resolve(".gradle/tmp/example"));
        Files.writeString(tempDir.resolve(".gradle/tmp/example/GradleOnly.java"), "class G { String s = \"java.net.HttpURLConnection\"; }");

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
        assertEquals("", check.stderr);
        assertTrue(check.stdout.startsWith("check: OK"));
    }

    @Test
    void checkUndeclaredReachOrderingIsDeterministic(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path a = tempDir.resolve("src/main/java/example/A.java");
        Path b = tempDir.resolve("src/main/java/example/B.java");
        Files.createDirectories(a.getParent());
        Files.writeString(a, "package example;\nimport okhttp3.OkHttpClient;\nimport org.springframework.web.client.RestTemplate;\nclass A { OkHttpClient c; RestTemplate t; }\n");
        Files.writeString(b, "package example;\nimport java.net.http.HttpClient;\nclass B { HttpClient c; }\n");

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString(), "--collect=all" });
        assertEquals(6, check.exitCode);
        List<String> lines = nonEnvelopeLines(check.stderr).stream()
            .filter(line -> line.startsWith("check: UNDECLARED_REACH: "))
            .toList();
        assertEquals(List.of(
            "check: UNDECLARED_REACH: src/main/java/example/A.java: okhttp3.OkHttpClient",
            "check: UNDECLARED_REACH: src/main/java/example/A.java: org.springframework.web.client.RestTemplate",
            "check: UNDECLARED_REACH: src/main/java/example/B.java: java.net.http.HttpClient"
        ), lines);
    }

    @Test
    void checkDefaultModeIgnoresHygieneSeedPaths(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        Files.createDirectories(tempDir.resolve(".g"));
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
    }

    @Test
    void checkStrictHygieneFailsOnUnexpectedSeedPath(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        Files.createDirectories(tempDir.resolve(".g"));
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString(), "--strict-hygiene" });
        assertEquals(6, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: HYGIENE_UNEXPECTED_PATHS: .g"));
        assertFailureEnvelope(
            check.stderr,
            "HYGIENE_UNEXPECTED_PATHS",
            ".g",
            "Remove unexpected tool directories or allowlist them in `bear-policy/hygiene-allowlist.txt`, then rerun `bear check`."
        );
    }

    @Test
    void checkStrictHygieneRespectsAllowlist(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        Files.createDirectories(tempDir.resolve(".g"));
        Path allowlist = tempDir.resolve("bear-policy/hygiene-allowlist.txt");
        Files.createDirectories(allowlist.getParent());
        Files.writeString(allowlist, ".g\n", StandardCharsets.UTF_8);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString(), "--strict-hygiene" });
        assertEquals(0, check.exitCode);
    }

    @Test
    void checkStrictHygieneNeverFlagsBearGradleUserHome(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        Files.createDirectories(tempDir.resolve(".bear-gradle-user-home"));
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString(), "--strict-hygiene" });
        assertEquals(0, check.exitCode);
    }

    @Test
    void checkInvalidPolicyAllowlistReturnsPolicyInvalid(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        Path policy = tempDir.resolve("bear-policy/reflection-allowlist.txt");
        Files.createDirectories(policy.getParent());
        Files.writeString(policy, "z/path.java\na/path.java\n", StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(2, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("policy: VALIDATION_ERROR: entries must be sorted lexicographically"));
        assertFailureEnvelope(
            check.stderr,
            "POLICY_INVALID",
            "bear-policy/reflection-allowlist.txt",
            "Fix the policy contract file and rerun `bear check`."
        );
    }

    @Test
    void checkDriftTakesPrecedenceOverUndeclaredReach(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Files.writeString(
            tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw/Withdraw_ExecuteWithdraw.java"),
            "drift",
            StandardOpenOption.APPEND
        );
        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.writeString(impl, "\njava.net.http.HttpClient\n", StandardOpenOption.APPEND);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, check.exitCode);
        assertFalse(normalizeLf(check.stderr).contains("check: UNDECLARED_REACH:"));
        assertFailureEnvelope(
            check.stderr,
            "DRIFT_DETECTED",
            "build/generated/bear",
            "Run `bear compile <ir-file> --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkBoundaryBypassDirectImplUsageFails(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path bypass = tempDir.resolve("src/main/java/com/example/Bypass.java");
        Files.createDirectories(bypass.getParent());
        Files.writeString(bypass, ""
            + "package com.example;\n"
            + "import blocks.withdraw.impl.WithdrawImpl;\n"
            + "public final class Bypass {\n"
            + "  WithdrawImpl impl = new WithdrawImpl();\n"
            + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: BOUNDARY_BYPASS: RULE=DIRECT_IMPL_USAGE: src/main/java/com/example/Bypass.java:"));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/com/example/Bypass.java",
            "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses."
        );
    }
    @Test
    void checkCollectAllBoundaryBypassFindingsAreDeterministicallyOrdered(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path aBypass = tempDir.resolve("src/main/java/com/example/ABypass.java");
        Files.createDirectories(aBypass.getParent());
        Files.writeString(aBypass, ""
            + "package com.example;\n"
            + "import blocks.withdraw.impl.WithdrawImpl;\n"
            + "public final class ABypass {\n"
            + "  WithdrawImpl impl = new WithdrawImpl();\n"
            + "}\n",
            StandardCharsets.UTF_8
        );
        Path zBypass = tempDir.resolve("src/main/java/com/example/ZBypass.java");
        Files.writeString(zBypass, ""
            + "package com.example;\n"
            + "import blocks.withdraw.impl.WithdrawImpl;\n"
            + "public final class ZBypass {\n"
            + "  WithdrawImpl impl = new WithdrawImpl();\n"
            + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString(), "--collect=all" });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        List<String> lines = stderr.lines()
            .filter(line -> line.startsWith("check: BOUNDARY_BYPASS:"))
            .toList();
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("src/main/java/com/example/ABypass.java"));
        assertTrue(lines.get(1).contains("src/main/java/com/example/ZBypass.java"));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/com/example/ABypass.java",
            "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses."
        );
    }

    @Test
    void checkBoundaryBypassReflectionClassloadingFails(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path bypass = tempDir.resolve("src/main/java/com/example/Bypass.java");
        Files.createDirectories(bypass.getParent());
        Files.writeString(bypass, ""
            + "package com.example;\n"
            + "public final class Bypass {\n"
            + "  Object load(String implClass) throws Exception {\n"
            + "    return Class.forName(implClass);\n"
            + "  }\n"
            + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: BOUNDARY_BYPASS: RULE=DIRECT_IMPL_USAGE: src/main/java/com/example/Bypass.java: KIND=REFLECTION_CLASSLOADING: Class.forName(...)"));
    }

    @Test
    void checkBoundaryBypassReflectionClassloadingAllowlistPasses(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path bypass = tempDir.resolve("src/main/java/com/example/Bypass.java");
        Files.createDirectories(bypass.getParent());
        Files.writeString(bypass, ""
            + "package com.example;\n"
            + "public final class Bypass {\n"
            + "  Object load(String implClass) throws Exception {\n"
            + "    return Class.forName(implClass);\n"
            + "  }\n"
            + "}\n",
            StandardCharsets.UTF_8
        );
        Path allowlist = tempDir.resolve("bear-policy/reflection-allowlist.txt");
        Files.createDirectories(allowlist.getParent());
        Files.writeString(allowlist, "src/main/java/com/example/Bypass.java\n", StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
    }

    @Test
    void checkBoundaryBypassGovernedServiceBindingFails(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path descriptor = tempDir.resolve("src/main/resources/META-INF/services/com.bear.generated.withdraw.WithdrawLogic");
        Files.createDirectories(descriptor.getParent());
        Files.writeString(
            descriptor,
            "# comment\n"
                + "blocks.withdraw.impl.WithdrawImpl extra\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains(
            "check: BOUNDARY_BYPASS: RULE=DIRECT_IMPL_USAGE: src/main/resources/META-INF/services/com.bear.generated.withdraw.WithdrawLogic: KIND=IMPL_SERVICE_BINDING: com.bear.generated.withdraw.WithdrawLogic -> blocks.withdraw.impl.WithdrawImpl"
        ));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/resources/META-INF/services/com.bear.generated.withdraw.WithdrawLogic",
            "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses."
        );
    }

    @Test
    void checkBoundaryBypassGovernedModuleBindingFails(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path moduleInfo = tempDir.resolve("src/main/java/module-info.java");
        Files.createDirectories(moduleInfo.getParent());
        Files.writeString(
            moduleInfo,
            "module demo {\n"
                + "  provides com.bear.generated.withdraw.WithdrawLogic\n"
                + "      with blocks.withdraw.impl.WithdrawImpl,\n"
                + "           com.example.Other;\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains(
            "check: BOUNDARY_BYPASS: RULE=DIRECT_IMPL_USAGE: src/main/java/module-info.java: KIND=IMPL_MODULE_BINDING: com.bear.generated.withdraw.WithdrawLogic -> blocks.withdraw.impl.WithdrawImpl"
        ));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/module-info.java",
            "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses."
        );
    }

    @Test
    void checkAllowsWrapperOfDefaultWiringPath(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path wiring = tempDir.resolve("src/main/java/com/example/Wiring.java");
        Files.createDirectories(wiring.getParent());
        Files.writeString(
            wiring,
            "package com.example;\n"
                + "import com.bear.generated.withdraw.IdempotencyPort;\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "import com.bear.generated.withdraw.Withdraw;\n"
                + "public final class Wiring {\n"
                + "  Withdraw wire(IdempotencyPort idempotencyPort, LedgerPort ledgerPort) {\n"
                + "    return Withdraw.of(idempotencyPort, ledgerPort);\n"
                + "  }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
        assertTrue(check.stdout.startsWith("check: OK"));
    }

    @Test
    void checkBoundaryBypassImplContainmentFailsForExternalResolvedCall(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path external = tempDir.resolve("src/main/java/com/example/domain/WalletDomain.java");
        Files.createDirectories(external.getParent());
        Files.writeString(
            external,
            "package com.example.domain;\n"
                + "public final class WalletDomain {\n"
                + "  public static Object apply() { return null; }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.writeString(
            impl,
            "package blocks.withdraw.impl;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request, Object ledgerPort) {\n"
                + "    ledgerPort.toString();\n"
                + "    return com.example.domain.WalletDomain.apply();\n"
                + "  }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: BOUNDARY_BYPASS: RULE=IMPL_CONTAINMENT_BYPASS: src/main/java/blocks/withdraw/impl/WithdrawImpl.java: KIND=IMPL_EXTERNAL_CALL: com.example.domain.WalletDomain"));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses."
        );
    }

    @Test
    void checkBoundaryBypassImplPurityViolationFailsForStaticMutableState(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.writeString(
            impl,
            "package blocks.withdraw.impl;\n"
                + "import com.bear.generated.withdraw.BearValue;\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "import com.bear.generated.withdraw.WithdrawLogic;\n"
                + "import com.bear.generated.withdraw.WithdrawRequest;\n"
                + "import com.bear.generated.withdraw.WithdrawResult;\n"
                + "public final class WithdrawImpl implements WithdrawLogic {\n"
                + "  static int calls = 0;\n"
                + "  public WithdrawResult execute(WithdrawRequest request, LedgerPort ledgerPort) {\n"
                + "    ledgerPort.getBalance(BearValue.empty());\n"
                + "    return new WithdrawResult(java.math.BigDecimal.ZERO);\n"
                + "  }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: BOUNDARY_BYPASS: RULE=IMPL_PURITY_VIOLATION: src/main/java/blocks/withdraw/impl/WithdrawImpl.java:"));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            "Keep impl lane pure: remove mutable static state and synchronized usage from `blocks/**/impl/**`; route cross-call state through generated ports and adapter/state lanes."
        );
    }

    @Test
    void checkBoundaryBypassImplStateDependencyFails(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Files.createDirectories(tempDir.resolve("src/main/java/blocks/_shared/state"));
        Files.writeString(
            tempDir.resolve("src/main/java/blocks/_shared/state/Store.java"),
            "package blocks._shared.state;\npublic final class Store { public static Object fetch() { return null; } }\n",
            StandardCharsets.UTF_8
        );

        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.writeString(
            impl,
            "package blocks.withdraw.impl;\n"
                + "import blocks._shared.state.Store;\n"
                + "import com.bear.generated.withdraw.BearValue;\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "import com.bear.generated.withdraw.WithdrawLogic;\n"
                + "import com.bear.generated.withdraw.WithdrawRequest;\n"
                + "import com.bear.generated.withdraw.WithdrawResult;\n"
                + "public final class WithdrawImpl implements WithdrawLogic {\n"
                + "  public WithdrawResult execute(WithdrawRequest request, LedgerPort ledgerPort) {\n"
                + "    Store.fetch();\n"
                + "    ledgerPort.getBalance(BearValue.empty());\n"
                + "    return new WithdrawResult(java.math.BigDecimal.ZERO);\n"
                + "  }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: BOUNDARY_BYPASS: RULE=IMPL_STATE_DEPENDENCY_BYPASS: src/main/java/blocks/withdraw/impl/WithdrawImpl.java:"));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            "Remove `blocks._shared.state.*` dependencies from impl lane and access state through generated port adapters."
        );
    }

    @Test
    void checkBoundaryBypassScopedImportPolicyFailsForSharedPure(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path pure = tempDir.resolve("src/main/java/blocks/_shared/pure/NetHelper.java");
        Files.createDirectories(pure.getParent());
        Files.writeString(
            pure,
            "package blocks._shared.pure;\n"
                + "import java.net.URI;\n"
                + "public final class NetHelper { static final String X = URI.create(\"https://example.com\").toString(); }\n",
            StandardCharsets.UTF_8
        );
        writeFreshContainmentMarkers(tempDir);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: BOUNDARY_BYPASS: RULE=SCOPED_IMPORT_POLICY_BYPASS: src/main/java/blocks/_shared/pure/NetHelper.java:"));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/blocks/_shared/pure/NetHelper.java",
            "Remove forbidden package usage from guarded lane (`impl` or `_shared.pure`) and move IO/network/filesystem/concurrency integration into adapter/state lanes."
        );
    }

    @Test
    void checkBoundaryBypassSharedPureNewInitializerFailsWhenNotAllowlisted(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path pure = tempDir.resolve("src/main/java/blocks/_shared/pure/Factory.java");
        Files.createDirectories(pure.getParent());
        Files.writeString(
            pure,
            "package blocks._shared.pure;\n"
                + "public final class Factory {\n"
                + "  static final Object HOLDER = new Object();\n"
                + "}\n",
            StandardCharsets.UTF_8
        );
        writeFreshContainmentMarkers(tempDir);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: BOUNDARY_BYPASS: RULE=SHARED_PURITY_VIOLATION: src/main/java/blocks/_shared/pure/Factory.java:"));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/blocks/_shared/pure/Factory.java",
            "Keep `_shared.pure` deterministic: remove mutable static state/synchronized usage, move stateful code to `blocks/**/adapter/**` or `blocks/_shared/state/**`, and use allowlisted immutable constants only."
        );
    }

    @Test
    void checkBoundaryBypassIgnoresImplTextInCommentsAndStrings(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path sample = tempDir.resolve("src/main/java/com/example/Sample.java");
        Files.createDirectories(sample.getParent());
        Files.writeString(sample, ""
            + "package com.example;\n"
            + "public final class Sample {\n"
            + "  // import blocks.withdraw.impl.WithdrawImpl;\n"
            + "  String s = \"new WithdrawImpl()\";\n"
            + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
        assertEquals("", check.stderr);
        assertTrue(check.stdout.startsWith("check: OK"));
    }

    @Test
    void checkBoundaryBypassNullPortWiringTopLevelFails(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path wiring = tempDir.resolve("src/main/java/com/example/Wiring.java");
        Files.createDirectories(wiring.getParent());
        Files.writeString(wiring, ""
            + "package com.example;\n"
            + "public final class Wiring {\n"
            + "  void wire() {\n"
            + "    new com.bear.generated.withdraw.Withdraw(null, ledgerPort, logic);\n"
            + "  }\n"
            + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: BOUNDARY_BYPASS: RULE=NULL_PORT_WIRING: src/main/java/com/example/Wiring.java:"));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/com/example/Wiring.java",
            "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses."
        );
    }

    @Test
    void checkBoundaryBypassNullPortNestedExpressionDoesNotFail(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path wiring = tempDir.resolve("src/main/java/com/example/Wiring.java");
        Files.createDirectories(wiring.getParent());
        Files.writeString(wiring, ""
            + "package com.example;\n"
            + "public final class Wiring {\n"
            + "  void wire() {\n"
            + "    new com.bear.generated.withdraw.Withdraw((flag == null ? idempotencyPort : idempotencyPort), ledgerPort, logic);\n"
            + "  }\n"
            + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
        assertEquals("", check.stderr);
        assertTrue(check.stdout.startsWith("check: OK"));
    }

    @Test
    void checkBoundaryBypassGeneratedPortImplOutsideGovernedRootsFails(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path adapter = tempDir.resolve("src/main/java/com/example/AppPortAdapter.java");
        Files.createDirectories(adapter.getParent());
        Files.writeString(
            adapter,
            "package com.example;\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "public final class AppPortAdapter implements LedgerPort {\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: BOUNDARY_BYPASS: RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: src/main/java/com/example/AppPortAdapter.java: KIND=PORT_IMPL_OUTSIDE_GOVERNED_ROOT: com.bear.generated.withdraw.LedgerPort -> com.example.AppPortAdapter"));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/com/example/AppPortAdapter.java",
            "Move the port implementation under the owning block governed roots (block root or blocks/_shared) or refactor so app layer calls wrappers without implementing generated ports."
        );
    }

    @Test
    void checkBoundaryBypassGeneratedPortImplInsideBlockRootPasses(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path adapter = tempDir.resolve("src/main/java/blocks/withdraw/adapters/LocalPortAdapter.java");
        Files.createDirectories(adapter.getParent());
        Files.writeString(
            adapter,
            "package blocks.withdraw.adapters;\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "public final class LocalPortAdapter implements LedgerPort {\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
    }

    @Test
    void checkBoundaryBypassGeneratedPortImplInsideSharedRootPasses(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path adapter = tempDir.resolve("src/main/java/blocks/_shared/state/SharedPortAdapter.java");
        Files.createDirectories(adapter.getParent());
        Files.writeString(
            adapter,
            "package blocks._shared.state;\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "public final class SharedPortAdapter implements LedgerPort {\n"
                + "}\n",
            StandardCharsets.UTF_8
        );
        writeFreshContainmentMarkers(tempDir);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, check.exitCode);
    }

    @Test
    void checkBoundaryBypassFailsWhenMarkerIsUsedOutsideShared(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path adapter = tempDir.resolve("src/main/java/blocks/withdraw/adapters/LocalPortAdapter.java");
        Files.createDirectories(adapter.getParent());
        Files.writeString(
            adapter,
            "package blocks.withdraw.adapters;\n"
                + "// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "public final class LocalPortAdapter implements LedgerPort {\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains(
            "check: BOUNDARY_BYPASS: RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: src/main/java/blocks/withdraw/adapters/LocalPortAdapter.java: KIND=MARKER_MISUSED_OUTSIDE_SHARED: blocks.withdraw.adapters.LocalPortAdapter"
        ));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/blocks/withdraw/adapters/LocalPortAdapter.java",
            "Split generated-port adapters so each class implements one generated block package, or move the adapter under blocks/_shared and add `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL` within 5 non-empty lines above the class declaration."
        );
    }

    @Test
    void checkBoundaryBypassEffectsMissingRequiredPortUsageFails(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.writeString(impl, ""
            + "package blocks.withdraw.impl;\n"
            + "public final class WithdrawImpl {\n"
            + "  Object execute(Object request, Object ledgerPort) {\n"
            + "    return null;\n"
            + "  }\n"
            + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(7, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: BOUNDARY_BYPASS: RULE=EFFECTS_BYPASS: src/main/java/blocks/withdraw/impl/WithdrawImpl.java: missing required effect port usage: ledgerPort"));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses."
        );
    }

    @Test
    void checkAllBoundaryBypassFailsOnSharedMultiBlockPortImplementerWithoutMarker(@TempDir Path tempDir) throws Exception {
        Path repo = tempDir.resolve("repo");
        Path specDir = repo.resolve("spec");
        Files.createDirectories(specDir);
        Path alphaIr = specDir.resolve("alpha.bear.yaml");
        Path betaIr = specDir.resolve("beta.bear.yaml");
        Files.writeString(alphaIr, fixtureIrForBlockName("alpha"), StandardCharsets.UTF_8);
        Files.writeString(betaIr, fixtureIrForBlockName("beta"), StandardCharsets.UTF_8);

        Path serviceRoot = repo.resolve("service");
        Files.createDirectories(serviceRoot);
        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", serviceRoot.toString() }).exitCode);
        assertEquals(0, runCli(new String[] { "compile", betaIr.toString(), "--project", serviceRoot.toString() }).exitCode);
        writeWorkingBlockImpl(serviceRoot, "alpha");
        writeWorkingBlockImpl(serviceRoot, "beta");
        writeProjectWrapper(
            serviceRoot,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path adapter = serviceRoot.resolve("src/main/java/blocks/_shared/MegaAdapter.java");
        Files.createDirectories(adapter.getParent());
        Files.writeString(
            adapter,
            "package blocks._shared;\n"
                + "public final class MegaAdapter implements com.bear.generated.alpha.LedgerPort, com.bear.generated.beta.LedgerPort {\n"
                + "}\n",
            StandardCharsets.UTF_8
        );
        writeFreshContainmentMarkers(serviceRoot);

        writeBlockIndex(repo, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/alpha.bear.yaml\n"
            + "    projectRoot: service\n"
            + "  - name: beta\n"
            + "    ir: spec/beta.bear.yaml\n"
            + "    projectRoot: service\n");

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", repo.toString() });
        assertEquals(7, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains(
            "check: BOUNDARY_BYPASS: RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: src/main/java/blocks/_shared/MegaAdapter.java: KIND=MULTI_BLOCK_PORT_IMPL_FORBIDDEN: blocks._shared.MegaAdapter -> com.bear.generated.alpha,com.bear.generated.beta"
        ));
        assertFailureEnvelope(
            run.stderr,
            "REPO_MULTI_BLOCK_FAILED",
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    @Test
    void checkBoundaryBypassEffectsHelperPassThroughAndSuppression(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.writeString(impl, ""
            + "package blocks.withdraw.impl;\n"
            + "public final class WithdrawImpl {\n"
            + "  Object execute(Object request, Object ledgerPort) {\n"
            + "    return helper(ledgerPort);\n"
            + "  }\n"
            + "  Object helper(Object value) { return null; }\n"
            + "}\n",
            StandardCharsets.UTF_8
        );
        CliRunResult passByHelper = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, passByHelper.exitCode);

        Files.writeString(impl, ""
            + "package blocks.withdraw.impl;\n"
            + "public final class WithdrawImpl {\n"
            + "  Object execute(Object request, Object ledgerPort) {\n"
            + "    // BEAR:PORT_USED ledgerPort\n"
            + "    return null;\n"
            + "  }\n"
            + "}\n",
            StandardCharsets.UTF_8
        );
        CliRunResult passBySuppression = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, passBySuppression.exitCode);

        Files.writeString(impl, ""
            + "package blocks.withdraw.impl;\n"
            + "public final class WithdrawImpl {\n"
            + "  Object execute(Object request, Object ledgerPort) {\n"
            + "    Object idempotencyPort = null;\n"
            + "    // BEAR:PORT_USED idempotencyPort\n"
            + "    return idempotencyPort;\n"
            + "  }\n"
            + "}\n",
            StandardCharsets.UTF_8
        );
        CliRunResult semanticSuppression = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString(), "--collect=all" });
        assertEquals(7, semanticSuppression.exitCode);
        assertTrue(
            normalizeLf(semanticSuppression.stderr).contains(
                "check: BOUNDARY_BYPASS: RULE=EFFECTS_BYPASS: src/main/java/blocks/withdraw/impl/WithdrawImpl.java: semantic port suppression forbidden: idempotencyPort"
            )
        );

        Files.writeString(impl, ""
            + "package blocks.withdraw.impl;\n"
            + "public final class WithdrawImpl {\n"
            + "  Object execute(Object request, Object ledgerPort) {\n"
            + "    // BEAR:PORT_USED\n"
            + "    return null;\n"
            + "  }\n"
            + "}\n",
            StandardCharsets.UTF_8
        );
        CliRunResult malformedSuppression = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString(), "--collect=all" });
        assertEquals(7, malformedSuppression.exitCode);
        assertTrue(
            normalizeLf(malformedSuppression.stderr).contains(
                "check: BOUNDARY_BYPASS: RULE=EFFECTS_BYPASS: src/main/java/blocks/withdraw/impl/WithdrawImpl.java: missing required effect port usage: ledgerPort"
            )
        );
    }

    @Test
    void checkBlockedMarkerIsAdvisoryAndSuccessfulCheckClears(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho java.io.FileNotFoundException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip.lck (Access is denied)\r\necho PROJECT_TEST_GRADLE_LOCK_SIMULATED\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"java.io.FileNotFoundException: /tmp/gradle-8.12.1-bin.zip.lck (Access is denied)\"\necho \"PROJECT_TEST_GRADLE_LOCK_SIMULATED\"\nexit 1\n"
        );
        CliRunResult locked = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(74, locked.exitCode);
        Path marker = tempDir.resolve("build/bear/check.blocked.marker");
        assertTrue(Files.isRegularFile(marker));
        assertTrue(Files.readString(marker).contains("reason=LOCK"));

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        CliRunResult pass = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, pass.exitCode);
        assertFalse(Files.exists(marker));
    }

    @Test
    void unblockIsIdempotentWhenMarkerIsMissing(@TempDir Path tempDir) {
        CliRunResult unblock = runCli(new String[] { "unblock", "--project", tempDir.toString() });
        assertEquals(0, unblock.exitCode);
        assertEquals("unblock: OK\n", normalizeLf(unblock.stdout));
        assertEquals("", unblock.stderr);
    }

    @Test
    void unblockRetriesAndSucceedsAfterTransientDeleteFailure(@TempDir Path tempDir) throws Exception {
        Path marker = tempDir.resolve("build/bear/check.blocked.marker");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "reason=LOCK\ndetail=x\n", StandardCharsets.UTF_8);

        String key = "bear.cli.test.unblock.failDeletes";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "1");
            CliRunResult unblock = runCli(new String[] { "unblock", "--project", tempDir.toString() });
            assertEquals(0, unblock.exitCode);
            assertEquals("unblock: OK\n", normalizeLf(unblock.stdout));
            assertFalse(Files.exists(marker));
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void unblockReturnsDeterministicUnblockLockedEnvelopeWhenDeleteFails(@TempDir Path tempDir) throws Exception {
        Path marker = tempDir.resolve("build/bear/check.blocked.marker");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "reason=LOCK\ndetail=x\n", StandardCharsets.UTF_8);

        String key = "bear.cli.test.unblock.failDeletes";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "always");
            CliRunResult unblock = runCli(new String[] { "unblock", "--project", tempDir.toString() });
            assertEquals(74, unblock.exitCode);
            String stderr = normalizeLf(unblock.stderr);
            assertTrue(stderr.startsWith("io: IO_ERROR: UNBLOCK_LOCKED:"));
            assertTrue(stderr.contains("; ATTRS="));
            assertFailureEnvelope(
                unblock.stderr,
                "UNBLOCK_LOCKED",
                "build/bear/check.blocked.marker",
                "Close processes locking the marker and rerun `bear unblock --project <path>`."
            );
            assertTrue(Files.exists(marker));
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void checkAllBlockedMarkerIsAdvisoryAndRunCanProceed(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path alphaRoot = fixture.projectRoots().get(0);
        Path marker = alphaRoot.resolve("build/bear/check.blocked.marker");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "reason=BOOTSTRAP_IO\ndetail=java.nio.file.NoSuchFileException: gradle-8.12.1-bin.zip\n", StandardCharsets.UTF_8);

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString()
        });
        assertEquals(0, run.exitCode);
        assertTrue(normalizeLf(run.stdout).contains("BLOCK: alpha"));
        assertFalse(Files.exists(marker));
    }

    @Test
    void checkAllPassesInCanonicalOrder(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        CliRunResult run = runCli(new String[] { "check", "--all", "--project", fixture.repoRoot().toString() });

        assertEquals(0, run.exitCode);
        String stdout = normalizeLf(run.stdout);
        assertTrue(stdout.contains("BLOCK: alpha"));
        assertTrue(stdout.contains("BLOCK: beta"));
        assertTrue(stdout.contains("BLOCK: gamma"));
        assertTrue(stdout.indexOf("BLOCK: alpha") < stdout.indexOf("BLOCK: beta"));
        assertTrue(stdout.indexOf("BLOCK: beta") < stdout.indexOf("BLOCK: gamma"));
        assertTrue(stdout.contains("SUMMARY:"));
        assertTrue(stdout.contains("EXIT_CODE: 0"));
        assertEquals("", run.stderr);
    }

    @Test
    void checkAllAttachesContainmentSkipInfoToFirstPassingBlockOnlyAndIsDeterministic(@TempDir Path tempDir) throws Exception {
        Path specDir = tempDir.resolve("spec");
        Files.createDirectories(specDir);
        Path alphaIr = specDir.resolve("alpha.bear.yaml");
        Path gammaIr = specDir.resolve("gamma.bear.yaml");
        Files.writeString(alphaIr, fixtureIrForBlockName("alpha"), StandardCharsets.UTF_8);
        Files.writeString(gammaIr, fixtureIrForBlockName("gamma"), StandardCharsets.UTF_8);

        Path sharedRoot = tempDir.resolve("services/shared");
        Files.createDirectories(sharedRoot);
        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", sharedRoot.toString() }).exitCode);
        assertEquals(0, runCli(new String[] { "compile", gammaIr.toString(), "--project", sharedRoot.toString() }).exitCode);
        Path required = sharedRoot.resolve("build/generated/bear/config/containment-required.json");
        Files.createDirectories(required.getParent());
        Files.writeString(required, ""
            + "{\"schemaVersion\":\"v1\",\"target\":\"java-gradle\",\"blocks\":[{\"blockKey\":\"beta\",\"implDir\":\"src/main/java/blocks/beta/impl\",\"allowedDeps\":[{\"ga\":\"com.fasterxml.jackson.core:jackson-databind\",\"version\":\"2.17.2\"}]}]}\n",
            StandardCharsets.UTF_8
        );
        writeWorkingBlockImpl(sharedRoot, "alpha");
        writeWorkingBlockImpl(sharedRoot, "gamma");
        writeProjectWrapper(
            sharedRoot,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        writeBlockIndex(tempDir, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/alpha.bear.yaml\n"
            + "    projectRoot: services/shared\n"
            + "  - name: gamma\n"
            + "    ir: spec/gamma.bear.yaml\n"
            + "    projectRoot: services/shared\n");

        CliRunResult first = runCli(new String[] {
            "check", "--all", "--project", tempDir.toString()
        });
        CliRunResult second = runCli(new String[] {
            "check", "--all", "--project", tempDir.toString()
        });
        assertEquals(0, first.exitCode);
        assertEquals(0, second.exitCode);
        assertEquals("", first.stderr);
        assertEquals("", second.stderr);

        String firstStdout = normalizeLf(first.stdout);
        String secondStdout = normalizeLf(second.stdout);
        assertEquals(firstStdout, secondStdout);
        String infoLine = "DETAIL: check: INFO: CONTAINMENT_SURFACES_SKIPPED_FOR_SELECTION: projectRoot=services/shared: reason=no_selected_blocks_with_impl_allowedDeps";
        assertEquals(1, countOccurrences(firstStdout, infoLine));
        assertTrue(firstStdout.indexOf("BLOCK: alpha") < firstStdout.indexOf(infoLine));
        assertTrue(firstStdout.indexOf(infoLine) < firstStdout.indexOf("BLOCK: gamma"));
    }

    @Test
    void checkAllBoundaryBypassImplContainmentFails(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path alphaRoot = fixture.projectRoots().get(0);
        Path external = alphaRoot.resolve("src/main/java/com/example/domain/WalletDomain.java");
        Files.createDirectories(external.getParent());
        Files.writeString(
            external,
            "package com.example.domain;\n"
                + "public final class WalletDomain {\n"
                + "  public static Object apply() { return null; }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );
        Path alphaImpl = alphaRoot.resolve("src/main/java/blocks/alpha/impl/AlphaImpl.java");
        Files.writeString(
            alphaImpl,
            "package blocks.alpha.impl;\n"
                + "public final class AlphaImpl {\n"
                + "  Object execute(Object request, Object ledgerPort) {\n"
                + "    ledgerPort.toString();\n"
                + "    return com.example.domain.WalletDomain.apply();\n"
                + "  }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", fixture.repoRoot().toString() });
        assertEquals(7, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("RULE=IMPL_CONTAINMENT_BYPASS"));
        assertTrue(stderr.contains("KIND=IMPL_EXTERNAL_CALL: com.example.domain.WalletDomain"));
        assertFailureEnvelope(
            run.stderr,
            "REPO_MULTI_BLOCK_FAILED",
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    @Test
    void checkAllManifestInvalidWhenBlockRootSourceDirMissing(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path alphaWiring = fixture.projectRoots().get(0).resolve("build/generated/bear/wiring/alpha.wiring.json");
        String content = Files.readString(alphaWiring, StandardCharsets.UTF_8);
        content = content.replace("\"blockRootSourceDir\":\"src/main/java/blocks/alpha\",", "");
        Files.writeString(alphaWiring, content, StandardCharsets.UTF_8);

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", fixture.repoRoot().toString() });
        assertEquals(2, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK_CODE: MANIFEST_INVALID"));
        assertTrue(stderr.contains("DETAIL: check: MANIFEST_INVALID: MISSING_KEY_blockRootSourceDir"));
        assertFailureEnvelope(
            run.stderr,
            "REPO_MULTI_BLOCK_FAILED",
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    @Test
    void checkAllDefaultContinueAllEvaluatesRemainingBlocks(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        deleteGeneratedBaseline(fixture.projectRoots().get(1));

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", fixture.repoRoot().toString() });

        assertEquals(3, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK: alpha"));
        assertTrue(stderr.contains("BLOCK: beta"));
        assertTrue(stderr.contains("BLOCK: gamma"));
        assertTrue(stderr.contains("BLOCK: gamma\nIR: spec/gamma.bear.yaml\nPROJECT: services/gamma\nSTATUS: PASS"));
        assertTrue(stderr.contains("DETAIL: drift: MISSING_BASELINE: build/generated/bear/wiring/beta.wiring.json"));
        assertTrue(stderr.contains("CODE=REPO_MULTI_BLOCK_FAILED"));
        assertTrue(stderr.contains("PATH=bear.blocks.yaml"));
    }

    @Test
    void checkAllBlockDetailIncludesCanonicalWiringPathForWiringDrift(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path betaWiring = fixture.projectRoots().get(1).resolve("build/generated/bear/wiring/beta.wiring.json");
        Files.writeString(betaWiring, Files.readString(betaWiring, StandardCharsets.UTF_8) + "\n", StandardCharsets.UTF_8);

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", fixture.repoRoot().toString() });
        assertEquals(3, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK: beta"));
        assertTrue(stderr.contains("DETAIL: drift: CHANGED: build/generated/bear/wiring/beta.wiring.json"));
        assertFailureEnvelope(
            run.stderr,
            "REPO_MULTI_BLOCK_FAILED",
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    @Test
    void wiringDriftDetailSummaryCapsAtTwentyWithDeterministicOverflowSuffix() {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            tokens.add("CHANGED|build/generated/bear/wiring/block-" + String.format("%02d", i) + ".wiring.json");
        }

        String summary = CheckCommandService.summarizeWiringDriftDetail(tokens);
        assertTrue(summary.contains("drift: CHANGED: build/generated/bear/wiring/block-00.wiring.json"));
        assertTrue(summary.contains("drift: CHANGED: build/generated/bear/wiring/block-19.wiring.json"));
        assertFalse(summary.contains("drift: CHANGED: build/generated/bear/wiring/block-20.wiring.json"));
        assertTrue(summary.endsWith("(+5 more)"));
    }

    @Test
    void checkAllFailFastMarksRemainingAsSkip(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        deleteGeneratedBaseline(fixture.projectRoots().get(0));

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString(), "--fail-fast"
        });

        assertEquals(3, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK: beta\nIR: spec/beta.bear.yaml\nPROJECT: services/beta\nSTATUS: SKIP\nEXIT_CODE: 0\nREASON: FAIL_FAST_ABORT"));
        assertTrue(stderr.contains("BLOCK: gamma\nIR: spec/gamma.bear.yaml\nPROJECT: services/gamma\nSTATUS: SKIP\nEXIT_CODE: 0\nREASON: FAIL_FAST_ABORT"));
        assertTrue(stderr.contains("FAIL_FAST_TRIGGERED: true"));
    }

    @Test
    void checkAllUnknownOnlyNameIsUsageError(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString(), "--only", "not-a-block"
        });
        assertEquals(64, run.exitCode);
        assertTrue(run.stderr.startsWith("usage: INVALID_ARGS: unknown block in --only"));
        assertFailureEnvelope(
            run.stderr,
            "USAGE_INVALID_ARGS",
            "cli.args",
            "Use only block names declared in `bear.blocks.yaml`."
        );
    }

    @Test
    void checkAllDisabledBlockRenderedAsSkip(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        writeBlockIndex(fixture.repoRoot(), ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/alpha.bear.yaml\n"
            + "    projectRoot: services/alpha\n"
            + "  - name: beta\n"
            + "    ir: spec/beta.bear.yaml\n"
            + "    projectRoot: services/beta\n"
            + "    enabled: false\n");

        CliRunResult run = runCli(new String[] { "check", "--all", "--project", fixture.repoRoot().toString() });
        assertEquals(0, run.exitCode);
        String stdout = normalizeLf(run.stdout);
        assertTrue(stdout.contains("BLOCK: beta\nIR: spec/beta.bear.yaml\nPROJECT: services/beta\nSTATUS: SKIP\nEXIT_CODE: 0\nREASON: DISABLED"));
    }

    @Test
    void checkAllStrictOrphansFailsOnRepoMarker(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path orphan = fixture.repoRoot().resolve("orphan-root/build/generated/bear/surfaces/orphan.surface.json");
        Files.createDirectories(orphan.getParent());
        Files.writeString(orphan, "{}\n");

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString(), "--strict-orphans"
        });
        assertEquals(74, run.exitCode);
        assertTrue(run.stderr.startsWith("check: IO_ERROR: ORPHAN_MARKER: orphan-root/build/generated/bear/surfaces/orphan.surface.json"));
        assertFailureEnvelope(
            run.stderr,
            "IO_ERROR",
            "orphan-root/build/generated/bear/surfaces/orphan.surface.json",
            "Add missing block entries to `bear.blocks.yaml` or remove stale generated BEAR artifacts."
        );
    }

    @Test
    void checkAllDefaultManagedRootGuardFailsOnUnexpectedManagedMarker(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path orphan = fixture.repoRoot().resolve("services/alpha/build/generated/bear/surfaces/orphan.surface.json");
        Files.createDirectories(orphan.getParent());
        Files.writeString(orphan, "{}\n");

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString()
        });
        assertEquals(74, run.exitCode);
        assertTrue(run.stderr.startsWith("check: IO_ERROR: ORPHAN_MARKER: services/alpha/build/generated/bear/surfaces/orphan.surface.json"));
    }

    @Test
    void checkAllOnlyWithStrictStillUsesRepoWideOrphanScan(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path orphan = fixture.repoRoot().resolve("z/build/generated/bear/surfaces/orphan.surface.json");
        Files.createDirectories(orphan.getParent());
        Files.writeString(orphan, "{}\n");

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString(), "--only", "alpha", "--strict-orphans"
        });
        assertEquals(74, run.exitCode);
        assertTrue(run.stderr.startsWith("check: IO_ERROR: ORPHAN_MARKER: z/build/generated/bear/surfaces/orphan.surface.json"));
    }

    @Test
    void checkAllStrictHygieneFailsOnRepoSeedPath(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Files.createDirectories(fixture.repoRoot().resolve(".g"));

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString(), "--strict-hygiene"
        });
        assertEquals(6, run.exitCode);
        assertTrue(run.stderr.startsWith("check: HYGIENE_UNEXPECTED_PATHS: .g"));
        assertFailureEnvelope(
            run.stderr,
            "HYGIENE_UNEXPECTED_PATHS",
            ".g",
            "Remove unexpected tool directories or allowlist them in `bear-policy/hygiene-allowlist.txt`, then rerun `bear check --all`."
        );
    }

    @Test
    void checkAllStrictHygieneAllowlistPasses(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Files.createDirectories(fixture.repoRoot().resolve(".g"));
        Path allowlist = fixture.repoRoot().resolve("bear-policy/hygiene-allowlist.txt");
        Files.createDirectories(allowlist.getParent());
        Files.writeString(allowlist, ".g\n", StandardCharsets.UTF_8);

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString(), "--strict-hygiene"
        });
        assertEquals(0, run.exitCode);
    }

    @Test
    void checkAllReflectionDispatchForbiddenFailsBeforeRootTests(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path alphaRoot = fixture.projectRoots().get(0);
        Path alphaImpl = alphaRoot.resolve("src/main/java/blocks/alpha/impl/AlphaImpl.java");
        Files.writeString(alphaImpl, "\nObject any = null;\nany.invoke();\n", StandardOpenOption.APPEND);

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString()
        });
        assertEquals(6, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK_CODE: REFLECTION_DISPATCH_FORBIDDEN"));
        assertTrue(stderr.contains(
            "DETAIL: check: UNDECLARED_REACH: src/main/java/blocks/alpha/impl/AlphaImpl.java: REACH_HYGIENE: KIND=REFLECTION_DISPATCH token=.invoke("
        ));
        assertFailureEnvelope(
            run.stderr,
            "REPO_MULTI_BLOCK_FAILED",
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );

        String stdout = normalizeLf(run.stdout);
        assertFalse(stdout.contains("check-all: ROOT_TEST_START project=services/alpha"));
    }
    @Test
    void checkAllClassifiesGradleLockAsIoError(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path alphaRoot = fixture.projectRoots().get(0);
        writeProjectWrapper(
            alphaRoot,
            "@echo off\r\necho java.io.FileNotFoundException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip.lck (Access is denied)\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"java.io.FileNotFoundException: /tmp/gradle-8.12.1-bin.zip.lck (Access is denied)\"\nexit 1\n"
        );

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            CliRunResult run = runCli(new String[] {
                "check", "--all", "--project", fixture.repoRoot().toString()
            });
            assertEquals(74, run.exitCode);
            assertTrue(normalizeLf(run.stderr).contains("CATEGORY: IO_ERROR"));
            assertTrue(normalizeLf(run.stderr).contains("DETAIL: root-level project test runner lock in projectRoot services/alpha; line:"));
            assertTrue(normalizeLf(run.stderr).contains("; attempts="));
            assertTrue(normalizeLf(run.stderr).contains("; CACHE_MODE=user-cache"));
            assertTrue(normalizeLf(run.stderr).contains("; FALLBACK=to_user_cache"));
            assertFailureEnvelope(
                run.stderr,
                "REPO_MULTI_BLOCK_FAILED",
                "bear.blocks.yaml",
                "Review per-block results above and fix failing blocks, then rerun the command."
            );
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void checkAllClassifiesGradleBootstrapAsIoError(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path alphaRoot = fixture.projectRoots().get(0);
        writeProjectWrapper(
            alphaRoot,
            "@echo off\r\necho java.nio.file.NoSuchFileException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip\r\necho PROJECT_TEST_GRADLE_BOOTSTRAP_SIMULATED\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"java.nio.file.NoSuchFileException: /tmp/gradle-8.12.1-bin.zip\"\necho \"PROJECT_TEST_GRADLE_BOOTSTRAP_SIMULATED\"\nexit 1\n"
        );

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            CliRunResult run = runCli(new String[] {
                "check", "--all", "--project", fixture.repoRoot().toString()
            });
            assertEquals(74, run.exitCode);
            String stderr = normalizeLf(run.stderr);
            assertTrue(stderr.contains("CATEGORY: IO_ERROR"));
            assertTrue(stderr.contains("DETAIL: root-level project test bootstrap IO failure in projectRoot services/alpha; line:"));
            assertTrue(stderr.contains("; attempts="));
            assertTrue(stderr.contains("; CACHE_MODE=user-cache"));
            assertTrue(stderr.contains("; FALLBACK=to_user_cache"));
            assertFailureEnvelope(
                run.stderr,
                "REPO_MULTI_BLOCK_FAILED",
                "bear.blocks.yaml",
                "Review per-block results above and fix failing blocks, then rerun the command."
            );
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void checkAllClassifiesInvariantViolationAsExit4(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path alphaRoot = fixture.projectRoots().get(0);
        String marker = "BEAR_INVARIANT_VIOLATION|block=alpha|kind=non_negative|field=balance|observed=-1|rule=non_negative";
        writeProjectWrapper(
            alphaRoot,
            "@echo off\r\n"
                + "echo " + marker.replace("|", "^|") + "\r\n"
                + "exit /b 1\r\n",
            "#!/usr/bin/env sh\n"
                + "echo \"" + marker + "\"\n"
                + "exit 1\n"
        );

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString()
        });
        assertEquals(4, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("CATEGORY: TEST_FAILURE"));
        assertTrue(stderr.contains("CODE: INVARIANT_VIOLATION"));
        assertTrue(stderr.contains(marker));
    }

    @Test
    void checkPreservesRootCauseWhenBlockedMarkerWriteFails(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path markerParentAsFile = tempDir.resolve("build/bear");
        Files.createDirectories(markerParentAsFile.getParent());
        Files.writeString(markerParentAsFile, "not-a-directory", StandardCharsets.UTF_8);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho java.io.FileNotFoundException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip.lck (Access is denied)\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"java.io.FileNotFoundException: /tmp/gradle-8.12.1-bin.zip.lck (Access is denied)\"\nexit 1\n"
        );

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(74, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.startsWith("io: IO_ERROR: PROJECT_TEST_LOCK:"));
        assertTrue(stderr.contains("; markerWrite=failed:"));
        assertFailureEnvelope(
            run.stderr,
            "IO_ERROR",
            "project.tests",
            "Use BEAR-selected GRADLE_USER_HOME mode, run `bear unblock --project <path>`, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkAllIncludesRootProjectFailureLineAndTailInDetail(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path alphaRoot = fixture.projectRoots().get(0);
        writeProjectWrapper(
            alphaRoot,
            "@echo off\r\necho FAILURE: Build failed with an exception.\r\necho line48\r\necho line49\r\necho line50\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"FAILURE: Build failed with an exception.\"\necho \"line48\"\necho \"line49\"\necho \"line50\"\nexit 1\n"
        );

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString()
        });
        assertEquals(4, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("CATEGORY: TEST_FAILURE"));
        assertTrue(stderr.contains("DETAIL: root-level project tests failed for projectRoot services/alpha; line: FAILURE: Build failed with an exception.; tail:"));
        assertTrue(stderr.contains("line48"));
        assertTrue(stderr.contains("line49"));
        assertTrue(stderr.contains("line50"));
        assertFailureEnvelope(
            run.stderr,
            "REPO_MULTI_BLOCK_FAILED",
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    @Test
    void compileAllPassesInCanonicalOrder(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        CliRunResult run = runCli(new String[] { "compile", "--all", "--project", fixture.repoRoot().toString() });

        assertEquals(0, run.exitCode);
        String stdout = normalizeLf(run.stdout);
        assertTrue(stdout.contains("BLOCK: alpha"));
        assertTrue(stdout.contains("BLOCK: beta"));
        assertTrue(stdout.contains("BLOCK: gamma"));
        assertTrue(stdout.indexOf("BLOCK: alpha") < stdout.indexOf("BLOCK: beta"));
        assertTrue(stdout.indexOf("BLOCK: beta") < stdout.indexOf("BLOCK: gamma"));
        assertTrue(stdout.contains("SUMMARY:"));
        assertTrue(stdout.contains("EXIT_CODE: 0"));
        assertEquals("", run.stderr);
    }

    @Test
    void compileAllUnknownOnlyNameIsUsageError(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        CliRunResult run = runCli(new String[] {
            "compile", "--all", "--project", fixture.repoRoot().toString(), "--only", "not-a-block"
        });
        assertEquals(64, run.exitCode);
        assertTrue(run.stderr.startsWith("usage: INVALID_ARGS: unknown block in --only"));
        assertFailureEnvelope(
            run.stderr,
            "USAGE_INVALID_ARGS",
            "cli.args",
            "Use only block names declared in `bear.blocks.yaml`."
        );
    }

    @Test
    void compileAllOnlyFiltersSelectedBlocks(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        CliRunResult run = runCli(new String[] {
            "compile", "--all", "--project", fixture.repoRoot().toString(), "--only", "alpha,beta"
        });

        assertEquals(0, run.exitCode);
        String stdout = normalizeLf(run.stdout);
        assertTrue(stdout.contains("BLOCK: alpha"));
        assertTrue(stdout.contains("BLOCK: beta"));
        assertTrue(!stdout.contains("BLOCK: gamma"));
    }

    @Test
    void compileAllFailFastMarksRemainingAsSkip(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path alphaIr = fixture.repoRoot().resolve("spec/alpha.bear.yaml");
        Files.writeString(alphaIr, "version: v1\nblock:\n  name: alpha\n");

        CliRunResult run = runCli(new String[] {
            "compile", "--all", "--project", fixture.repoRoot().toString(), "--fail-fast"
        });

        assertEquals(2, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK: beta\nIR: spec/beta.bear.yaml\nPROJECT: services/beta\nSTATUS: SKIP\nEXIT_CODE: 0\nREASON: FAIL_FAST_ABORT"));
        assertTrue(stderr.contains("BLOCK: gamma\nIR: spec/gamma.bear.yaml\nPROJECT: services/gamma\nSTATUS: SKIP\nEXIT_CODE: 0\nREASON: FAIL_FAST_ABORT"));
        assertTrue(stderr.contains("FAIL_FAST_TRIGGERED: true"));
    }

    @Test
    void compileAllStrictOrphansFailsOnRepoMarker(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path orphan = fixture.repoRoot().resolve("orphan-root/build/generated/bear/surfaces/orphan.surface.json");
        Files.createDirectories(orphan.getParent());
        Files.writeString(orphan, "{}\n");

        CliRunResult run = runCli(new String[] {
            "compile", "--all", "--project", fixture.repoRoot().toString(), "--strict-orphans"
        });
        assertEquals(74, run.exitCode);
        assertTrue(run.stderr.startsWith("compile: IO_ERROR: ORPHAN_MARKER: orphan-root/build/generated/bear/surfaces/orphan.surface.json"));
        assertFailureEnvelope(
            run.stderr,
            "IO_ERROR",
            "orphan-root/build/generated/bear/surfaces/orphan.surface.json",
            "Add missing block entries to `bear.blocks.yaml` or remove stale generated BEAR artifacts."
        );
    }

    @Test
    void fixAllPassesInCanonicalOrder(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        CliRunResult run = runCli(new String[] { "fix", "--all", "--project", fixture.repoRoot().toString() });

        assertEquals(0, run.exitCode);
        String stdout = normalizeLf(run.stdout);
        assertTrue(stdout.contains("BLOCK: alpha"));
        assertTrue(stdout.contains("BLOCK: beta"));
        assertTrue(stdout.contains("BLOCK: gamma"));
        assertTrue(stdout.indexOf("BLOCK: alpha") < stdout.indexOf("BLOCK: beta"));
        assertTrue(stdout.indexOf("BLOCK: beta") < stdout.indexOf("BLOCK: gamma"));
        assertTrue(stdout.contains("SUMMARY:"));
        assertTrue(stdout.contains("EXIT_CODE: 0"));
        assertEquals("", run.stderr);
    }

    @Test
    void fixAllUnknownOnlyNameIsUsageError(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        CliRunResult run = runCli(new String[] {
            "fix", "--all", "--project", fixture.repoRoot().toString(), "--only", "not-a-block"
        });
        assertEquals(64, run.exitCode);
        assertTrue(run.stderr.startsWith("usage: INVALID_ARGS: unknown block in --only"));
        assertFailureEnvelope(
            run.stderr,
            "USAGE_INVALID_ARGS",
            "cli.args",
            "Use only block names declared in `bear.blocks.yaml`."
        );
    }

    @Test
    void fixAllDisabledBlockRenderedAsSkip(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        writeBlockIndex(fixture.repoRoot(), ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/alpha.bear.yaml\n"
            + "    projectRoot: services/alpha\n"
            + "  - name: beta\n"
            + "    ir: spec/beta.bear.yaml\n"
            + "    projectRoot: services/beta\n"
            + "    enabled: false\n");

        CliRunResult run = runCli(new String[] { "fix", "--all", "--project", fixture.repoRoot().toString() });
        assertEquals(0, run.exitCode);
        String stdout = normalizeLf(run.stdout);
        assertTrue(stdout.contains("BLOCK: beta\nIR: spec/beta.bear.yaml\nPROJECT: services/beta\nSTATUS: SKIP\nEXIT_CODE: 0\nREASON: DISABLED"));
    }

    @Test
    void fixAllFailFastMarksRemainingAsSkip(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path alphaIr = fixture.repoRoot().resolve("spec/alpha.bear.yaml");
        Files.writeString(alphaIr, "version: v1\nblock:\n  name: alpha\n");

        CliRunResult run = runCli(new String[] {
            "fix", "--all", "--project", fixture.repoRoot().toString(), "--fail-fast"
        });

        assertEquals(2, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK: beta\nIR: spec/beta.bear.yaml\nPROJECT: services/beta\nSTATUS: SKIP\nEXIT_CODE: 0\nREASON: FAIL_FAST_ABORT"));
        assertTrue(stderr.contains("BLOCK: gamma\nIR: spec/gamma.bear.yaml\nPROJECT: services/gamma\nSTATUS: SKIP\nEXIT_CODE: 0\nREASON: FAIL_FAST_ABORT"));
        assertTrue(stderr.contains("FAIL_FAST_TRIGGERED: true"));
    }

    @Test
    void fixAllStrictOrphansFailsOnRepoMarker(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path orphan = fixture.repoRoot().resolve("orphan-root/build/generated/bear/surfaces/orphan.surface.json");
        Files.createDirectories(orphan.getParent());
        Files.writeString(orphan, "{}\n");

        CliRunResult run = runCli(new String[] {
            "fix", "--all", "--project", fixture.repoRoot().toString(), "--strict-orphans"
        });
        assertEquals(74, run.exitCode);
        assertTrue(run.stderr.startsWith("fix: IO_ERROR: ORPHAN_MARKER: orphan-root/build/generated/bear/surfaces/orphan.surface.json"));
        assertFailureEnvelope(
            run.stderr,
            "IO_ERROR",
            "orphan-root/build/generated/bear/surfaces/orphan.surface.json",
            "Add missing block entries to `bear.blocks.yaml` or remove stale generated BEAR artifacts."
        );
    }

    @Test
    void fixAllAggregatedFailureUsesRepoFooter(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path alphaIr = fixture.repoRoot().resolve("spec/alpha.bear.yaml");
        Files.writeString(alphaIr, "version: v1\nblock:\n  name: alpha\n");

        CliRunResult run = runCli(new String[] { "fix", "--all", "--project", fixture.repoRoot().toString() });
        assertEquals(2, run.exitCode);
        assertFailureEnvelope(
            run.stderr,
            "REPO_MULTI_BLOCK_FAILED",
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
    }

    @Test
    void prCheckAllProducesMixedClassifications(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path servicesA = repo.resolve("services/a");
        Path servicesB = repo.resolve("services/b");
        Files.createDirectories(servicesA);
        Files.createDirectories(servicesB);
        writeProjectWrapper(
            servicesA,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        writeProjectWrapper(
            servicesB,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        Path irA = repo.resolve("spec/alpha.bear.yaml");
        Path irB = repo.resolve("spec/beta.bear.yaml");
        Files.createDirectories(irA.getParent());
        String baseAlpha = fixtureIrForBlockName("alpha");
        String baseBeta = fixtureIrForBlockName("beta");
        Files.writeString(irA, baseAlpha, StandardCharsets.UTF_8);
        Files.writeString(irB, baseBeta, StandardCharsets.UTF_8);
        writeBlockIndex(repo, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/alpha.bear.yaml\n"
            + "    projectRoot: services/a\n"
            + "  - name: beta\n"
            + "    ir: spec/beta.bear.yaml\n"
            + "    projectRoot: services/b\n");
        gitCommitAll(repo, "base");

        String ordinary = baseAlpha.replace(
            "          - name: txId\n"
                + "            type: string\n",
            "          - name: txId\n"
                + "            type: string\n"
                + "          - name: note\n"
                + "            type: string\n"
        );
        String boundary = baseBeta.replace(
            "      - port: idempotency\n        ops:\n          - get\n          - put\n",
            "      - port: audit\n        ops:\n          - write\n      - port: idempotency\n        ops:\n          - get\n          - put\n"
        );
        Files.writeString(irA, ordinary, StandardCharsets.UTF_8);
        Files.writeString(irB, boundary, StandardCharsets.UTF_8);
        gitCommitAll(repo, "head");

        CliRunResult run = runCli(new String[] {
            "pr-check", "--all", "--project", repo.toString(), "--base", "HEAD~1"
        });
        assertEquals(5, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("BLOCK: alpha"));
        assertTrue(stderr.contains("CLASSIFICATION: ORDINARY"));
        assertTrue(stderr.contains("BLOCK: beta"));
        assertTrue(stderr.contains("CLASSIFICATION: BOUNDARY_EXPANDING"));
        assertTrue(stderr.contains("CODE=REPO_MULTI_BLOCK_FAILED"));
    }

    @Test
    void prCheckRequiresExpectedArgs() {
        CliRunResult run = runCli(new String[] { "pr-check" });
        assertEquals(64, run.exitCode);
        assertTrue(run.stderr.startsWith("usage: INVALID_ARGS: expected: bear pr-check"));
        assertFailureEnvelope(
            run.stderr,
            "USAGE_INVALID_ARGS",
            "cli.args",
            "Run `bear pr-check <ir-file> --project <path> --base <ref> [--index <path>] [--collect=all] [--agent]` with the expected arguments."
        );
    }

    @Test
    void prCheckRejectsAbsoluteIrPath(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("bear-ir/withdraw.bear.yaml");
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, Files.readString(TestRepoPaths.repoRoot().resolve("bear-ir/fixtures/withdraw.bear.yaml")));
        gitCommitAll(repo, "add ir");

        CliRunResult run = runCli(new String[] {
            "pr-check", ir.toString(), "--project", repo.toString(), "--base", "HEAD"
        });
        assertEquals(64, run.exitCode);
        assertTrue(run.stderr.startsWith("usage: INVALID_ARGS: ir-file must be repo-relative"));
        assertFailureEnvelope(
            run.stderr,
            "USAGE_INVALID_ARGS",
            "cli.args",
            "Pass a repo-relative `ir-file` path for `bear pr-check`."
        );
    }

    @Test
    void prCheckMissingBaseRefReturnsIo(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        writeFixtureIr(repo.resolve("bear-ir/withdraw.bear.yaml"));
        gitCommitAll(repo, "add ir");

        CliRunResult run = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "missing-ref"
        });
        assertEquals(74, run.exitCode);
        assertTrue(normalizeLf(run.stderr).startsWith("pr-check: IO_ERROR: MERGE_BASE_FAILED:"));
        assertFailureEnvelope(
            run.stderr,
            "IO_GIT",
            "git.baseRef",
            "Ensure base ref exists and is fetchable, then rerun `bear pr-check`."
        );
    }

    @Test
    void prCheckMissingHeadIrReturnsIo(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Files.writeString(repo.resolve("README.md"), "x\n", StandardCharsets.UTF_8);
        gitCommitAll(repo, "base");

        CliRunResult run = runCli(new String[] {
            "pr-check", "spec/missing.bear.yaml", "--project", repo.toString(), "--base", "HEAD"
        });
        assertEquals(74, run.exitCode);
        assertTrue(normalizeLf(run.stderr).startsWith("pr-check: IO_ERROR: READ_HEAD_FAILED: spec/missing.bear.yaml\n"));
        assertFailureEnvelope(
            run.stderr,
            "IO_ERROR",
            "spec/missing.bear.yaml",
            "Ensure the IR file exists at HEAD and rerun `bear pr-check`."
        );
    }

    @Test
    void prCheckNoDeltaReturnsOk(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        writeFixtureIr(repo.resolve("bear-ir/withdraw.bear.yaml"));
        gitCommitAll(repo, "add ir");

        CliRunResult run = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD"
        });
        assertEquals(0, run.exitCode);
        assertEquals("pr-check: OK: NO_BOUNDARY_EXPANSION\n", normalizeLf(run.stdout));
        assertEquals("", run.stderr);
    }

    @Test
    void prCheckFailsWhenGeneratedPortImplLivesOutsideGovernedRoots(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        writeFixtureIr(repo.resolve("bear-ir/withdraw.bear.yaml"));
        Files.createDirectories(repo.resolve("src/main/java/com/acme"));
        Files.writeString(
            repo.resolve("src/main/java/com/acme/AppPortAdapter.java"),
            "package com.acme;\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "public final class AppPortAdapter implements LedgerPort {\n"
                + "}\n",
            StandardCharsets.UTF_8
        );
        gitCommitAll(repo, "add ir and external port adapter");

        CliRunResult run = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD"
        });
        assertEquals(7, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("pr-check: BOUNDARY_BYPASS: RULE=PORT_IMPL_OUTSIDE_GOVERNED_ROOT"));
        assertFalse(stderr.contains("bear-pr-check-"));
        assertFailureEnvelope(
            run.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/com/acme/AppPortAdapter.java",
            "Move the port implementation under the owning block governed roots (block root or blocks/_shared) or refactor so app layer calls wrappers without implementing generated ports."
        );
    }

    @Test
    void prCheckAllowsGeneratedPortImplInsideSharedGovernedRoot(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        writeFixtureIr(repo.resolve("bear-ir/withdraw.bear.yaml"));
        Files.createDirectories(repo.resolve("src/main/java/blocks/_shared"));
        Files.writeString(
            repo.resolve("src/main/java/blocks/_shared/AppPortAdapter.java"),
            "package blocks._shared;\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "public final class AppPortAdapter implements LedgerPort {\n"
                + "}\n",
            StandardCharsets.UTF_8
        );
        gitCommitAll(repo, "add ir and shared adapter");

        CliRunResult run = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD"
        });
        assertEquals(0, run.exitCode);
        assertEquals("pr-check: OK: NO_BOUNDARY_EXPANSION\n", normalizeLf(run.stdout));
    }

    @Test
    void prCheckPrintsGovernanceSignalWhenMultiBlockPortImplIsAllowedByMarker(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        writeFixtureIr(repo.resolve("bear-ir/withdraw.bear.yaml"));
        Files.createDirectories(repo.resolve("src/main/java/blocks/_shared"));
        Files.writeString(
            repo.resolve("src/main/java/blocks/_shared/MegaAdapter.java"),
            "package blocks._shared;\n"
                + "import com.bear.generated.deposit.DepositPort;\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL\n"
                + "public final class MegaAdapter implements LedgerPort, DepositPort {\n"
                + "}\n",
            StandardCharsets.UTF_8
        );
        gitCommitAll(repo, "add ir and allowed multi-block adapter");

        CliRunResult run = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD"
        });
        assertEquals(0, run.exitCode);
        String stdout = normalizeLf(run.stdout);
        assertTrue(stdout.contains(
            "pr-check: GOVERNANCE: MULTI_BLOCK_PORT_IMPL_ALLOWED: src/main/java/blocks/_shared/MegaAdapter.java: blocks._shared.MegaAdapter -> com.bear.generated.deposit,com.bear.generated.withdraw"
        ));
        assertTrue(stdout.endsWith("pr-check: OK: NO_BOUNDARY_EXPANSION\n"));
        assertFalse(normalizeLf(run.stderr).contains("MULTI_BLOCK_PORT_IMPL_ALLOWED"));
    }

    @Test
    void prCheckFailsWhenMarkerIsUsedOutsideSharedForGeneratedPortImplementer(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        writeFixtureIr(repo.resolve("bear-ir/withdraw.bear.yaml"));
        Files.createDirectories(repo.resolve("src/main/java/blocks/withdraw/adapters"));
        Files.writeString(
            repo.resolve("src/main/java/blocks/withdraw/adapters/LocalPortAdapter.java"),
            "package blocks.withdraw.adapters;\n"
                + "// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "public final class LocalPortAdapter implements LedgerPort {\n"
                + "}\n",
            StandardCharsets.UTF_8
        );
        gitCommitAll(repo, "add ir and marker misuse");

        CliRunResult run = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD"
        });
        assertEquals(7, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("pr-check: BOUNDARY_BYPASS: RULE=MULTI_BLOCK_PORT_IMPL_FORBIDDEN"));
        assertTrue(stderr.contains("KIND=MARKER_MISUSED_OUTSIDE_SHARED: blocks.withdraw.adapters.LocalPortAdapter"));
        assertFalse(stderr.contains("bear-pr-check-"));
        assertFailureEnvelope(
            run.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/blocks/withdraw/adapters/LocalPortAdapter.java",
            "Split generated-port adapters so each class implements one generated block package, or move the adapter under blocks/_shared and add `// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL` within 5 non-empty lines above the class declaration."
        );
    }

    @Test
    void prCheckUsesFixedTempLayoutWithWiringOnlyOutputs(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        writeFixtureIr(repo.resolve("bear-ir/withdraw.bear.yaml"));
        gitCommitAll(repo, "add ir");

        String key = "bear.prcheck.test.keepTemp";
        String previous = System.getProperty(key);
        Path stagedRoot = null;
        try {
            System.setProperty(key, "true");
            CliRunResult run = runCli(new String[] {
                "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD"
            });
            assertEquals(0, run.exitCode);

            stagedRoot = PrCheckCommandService.consumeLastTempRootForTest();
            assertTrue(stagedRoot != null && Files.isDirectory(stagedRoot));
            assertTrue(Files.isRegularFile(stagedRoot.resolve("work/base/base.bear.yaml")));
            assertTrue(Files.isRegularFile(stagedRoot.resolve("generated/base/wiring/withdraw.wiring.json")));
            assertTrue(Files.isRegularFile(stagedRoot.resolve("generated/head/wiring/withdraw.wiring.json")));

            long javaCount;
            try (var stream = Files.walk(stagedRoot)) {
                javaCount = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .count();
            }
            assertEquals(0L, javaCount);
        } finally {
            restoreSystemProperty(key, previous);
            if (stagedRoot != null) {
                deleteRecursively(stagedRoot);
            }
            PrCheckCommandService.consumeLastTempRootForTest();
        }
    }

    @Test
    void prCheckAllPrintsAggregatedGovernanceSignalsBeforeSummaryOnSuccess(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        writeFixtureIr(repo.resolve("bear-ir/withdraw.bear.yaml"));
        writeBlockIndex(repo, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: withdraw\n"
            + "    ir: bear-ir/withdraw.bear.yaml\n"
            + "    projectRoot: .\n");
        Files.createDirectories(repo.resolve("src/main/java/blocks/_shared"));
        Files.writeString(
            repo.resolve("src/main/java/blocks/_shared/MegaAdapter.java"),
            "package blocks._shared;\n"
                + "import com.bear.generated.deposit.DepositPort;\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL\n"
                + "public final class MegaAdapter implements LedgerPort, DepositPort {\n"
                + "}\n",
            StandardCharsets.UTF_8
        );
        gitCommitAll(repo, "add ir index and allowed multi-block adapter");

        CliRunResult run = runCli(new String[] {
            "pr-check", "--all", "--project", repo.toString(), "--base", "HEAD"
        });
        assertEquals(0, run.exitCode);
        String stdout = normalizeLf(run.stdout);
        int governanceIdx = stdout.indexOf("GOVERNANCE SIGNALS:");
        int summaryIdx = stdout.indexOf("SUMMARY:");
        assertTrue(governanceIdx >= 0);
        assertTrue(summaryIdx > governanceIdx);
        assertTrue(stdout.contains("MULTI_BLOCK_PORT_IMPL_ALLOWED"));
    }

    @Test
    void prCheckTreatsMissingBaseIrAsBoundaryExpansion(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Files.writeString(repo.resolve("README.md"), "base\n", StandardCharsets.UTF_8);
        gitCommitAll(repo, "base");

        writeFixtureIr(repo.resolve("bear-ir/withdraw.bear.yaml"));
        gitCommitAll(repo, "add ir");

        CliRunResult run = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        String stderr = normalizeLf(run.stderr);
        assertEquals(5, run.exitCode);
        assertTrue(stderr.contains("pr-check: INFO: BASE_IR_MISSING_AT_MERGE_BASE: bear-ir/withdraw.bear.yaml: treated_as_empty_base"));
        assertTrue(stderr.contains("pr-delta: BOUNDARY_EXPANDING: PORTS: ADDED: idempotency"));
        assertTrue(stderr.contains("pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED"));
        assertFailureEnvelope(
            run.stderr,
            "BOUNDARY_EXPANSION",
            "bear-ir/withdraw.bear.yaml",
            "Review boundary-expanding deltas and route through explicit boundary review."
        );
    }

    @Test
    void prCheckEnvelopeAnomalyBecomesInternalError(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Files.writeString(repo.resolve("README.md"), "base\n", StandardCharsets.UTF_8);
        gitCommitAll(repo, "base");
        writeFixtureIr(repo.resolve("bear-ir/withdraw.bear.yaml"));
        gitCommitAll(repo, "add ir");

        String key = "bear.cli.test.expectedBoundaryExpansionExit.pr-check";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "9");
            CliRunResult run = runCli(new String[] {
                "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
            });
            String stderr = normalizeLf(run.stderr);
            assertEquals(70, run.exitCode);
            assertTrue(stderr.contains("PR_CHECK_EXIT_ENVELOPE_ANOMALY"));
            assertTrue(stderr.contains("observedExit=5"));
            assertTrue(stderr.contains("expectedExit=9"));
            assertFailureEnvelope(
                run.stderr,
                "INTERNAL_ERROR",
                "pr-check.envelope",
                "Capture stderr and file an issue against bear-cli (pr-check exit-envelope anomaly)."
            );
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void prCheckAllEnvelopeAnomalySurfacesAsInternalFailure(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Files.writeString(repo.resolve("README.md"), "base\n", StandardCharsets.UTF_8);
        gitCommitAll(repo, "base");
        writeFixtureIr(repo.resolve("bear-ir/withdraw.bear.yaml"));
        writeBlockIndex(repo, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: withdraw\n"
            + "    ir: bear-ir/withdraw.bear.yaml\n"
            + "    projectRoot: .\n");
        gitCommitAll(repo, "add ir + index");

        String key = "bear.cli.test.expectedBoundaryExpansionExit.pr-check";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "9");
            CliRunResult run = runCli(new String[] {
                "pr-check", "--all", "--project", repo.toString(), "--base", "HEAD~1"
            });
            String stderr = normalizeLf(run.stderr);
            assertEquals(70, run.exitCode);
            assertTrue(stderr.contains("PR_CHECK_EXIT_ENVELOPE_ANOMALY"));
            assertTrue(stderr.contains("CATEGORY: INTERNAL_ERROR"));
            assertFailureEnvelope(
                run.stderr,
                "REPO_MULTI_BLOCK_FAILED",
                "bear.blocks.yaml",
                "Review per-block results above and fix failing blocks, then rerun the command."
            );
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void prCheckOrdinaryOpsDeltaReturnsZero(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("bear-ir/withdraw.bear.yaml");
        String base = fixtureIrContent();
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, base, StandardCharsets.UTF_8);
        gitCommitAll(repo, "base ir");

        String head = base.replace(
            "          - name: txId\n"
                + "            type: string\n",
            "          - name: txId\n"
                + "            type: string\n"
                + "          - name: note\n"
                + "            type: string\n"
        );
        Files.writeString(ir, head, StandardCharsets.UTF_8);
        gitCommitAll(repo, "ordinary op change");

        CliRunResult run = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        String stderr = normalizeLf(run.stderr);
        assertEquals(0, run.exitCode);
        assertTrue(stderr.contains("pr-delta: ORDINARY: CONTRACT: ADDED: op.ExecuteWithdraw:input.note:string"));
        assertFalse(stderr.contains("BOUNDARY_EXPANDING"));
        assertEquals("pr-check: OK: NO_BOUNDARY_EXPANSION\n", normalizeLf(run.stdout));
    }

    @Test
    void prCheckOrderingIsDeterministic(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("bear-ir/withdraw.bear.yaml");
        String base = fixtureIrContent();
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, base, StandardCharsets.UTF_8);
        gitCommitAll(repo, "base ir");

        String head = base
            .replace(
                "          - name: txId\n"
                    + "            type: string\n",
                "          - name: txId\n"
                    + "            type: string\n"
                    + "          - name: note\n"
                    + "            type: string\n"
            )
            .replace(
                "          - port: ledger\n"
                    + "            ops:\n"
                    + "              - getBalance\n"
                    + "              - setBalance\n",
                "          - port: ledger\n"
                    + "            ops:\n"
                    + "              - getBalance\n"
                    + "              - setBalance\n"
                    + "              - reverse\n"
            )
            .replace(
                "      - port: ledger\n"
                    + "        ops:\n"
                    + "          - getBalance\n"
                    + "          - setBalance\n",
                "      - port: ledger\n"
                    + "        ops:\n"
                    + "          - getBalance\n"
                    + "          - setBalance\n"
                    + "          - reverse\n"
            )
            .replace(
                "      - port: idempotency\n        ops:\n          - get\n          - put\n",
                "      - port: audit\n        ops:\n          - write\n      - port: idempotency\n        ops:\n          - get\n          - put\n"
            )
            .replace("        key: txId\n", "        key: accountId\n");
        Files.writeString(ir, head, StandardCharsets.UTF_8);
        gitCommitAll(repo, "mixed changes");

        CliRunResult run = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        List<String> lines = normalizeLf(run.stderr).lines()
            .filter(line -> line.startsWith("pr-delta: "))
            .toList();
        assertEquals(5, run.exitCode);
        assertEquals(List.of(
            "pr-delta: BOUNDARY_EXPANDING: PORTS: ADDED: audit",
            "pr-delta: BOUNDARY_EXPANDING: OPS: ADDED: ledger.reverse",
            "pr-delta: BOUNDARY_EXPANDING: OPS: ADDED: op.ExecuteWithdraw:uses.ledger.reverse",
            "pr-delta: BOUNDARY_EXPANDING: IDEMPOTENCY: CHANGED: op.ExecuteWithdraw:idempotency.key",
            "pr-delta: ORDINARY: CONTRACT: ADDED: op.ExecuteWithdraw:input.note:string"
        ), lines);
        assertFailureEnvelope(
            run.stderr,
            "BOUNDARY_EXPANSION",
            "bear-ir/withdraw.bear.yaml",
            "Review boundary-expanding deltas and route through explicit boundary review."
        );
    }

    @Test
    void prCheckIdempotencyAddEmitsOnlyTopLevelDelta(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("bear-ir/withdraw.bear.yaml");
        String fixture = fixtureIrContent();
        String base = fixture.replace(
            "      idempotency:\n"
                + "        mode: use\n"
                + "        key: txId\n",
            ""
        ).replace(
            "  idempotency:\n"
                + "    store:\n"
                + "      port: idempotency\n"
                + "      getOp: get\n"
                + "      putOp: put\n",
            ""
        );
        assertFalse(base.equals(fixture));
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, base, StandardCharsets.UTF_8);
        gitCommitAll(repo, "base without idempotency");

        Files.writeString(ir, fixture, StandardCharsets.UTF_8);
        gitCommitAll(repo, "add idempotency");

        CliRunResult run = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        String stderr = normalizeLf(run.stderr);
        assertEquals(5, run.exitCode);
        assertTrue(stderr.contains("pr-delta: BOUNDARY_EXPANDING: IDEMPOTENCY: ADDED: block.idempotency"));
        assertTrue(stderr.contains("pr-delta: BOUNDARY_EXPANDING: IDEMPOTENCY: ADDED: op.ExecuteWithdraw:idempotency"));
        assertFalse(stderr.contains("block.idempotency.store.port"));
        assertFalse(stderr.contains("block.idempotency.store.getOp"));
        assertFalse(stderr.contains("block.idempotency.store.putOp"));
        assertFailureEnvelope(
            run.stderr,
            "BOUNDARY_EXPANSION",
            "bear-ir/withdraw.bear.yaml",
            "Review boundary-expanding deltas and route through explicit boundary review."
        );
    }

    @Test
    void compileInjectedInternalFailureIsEnveloped(@TempDir Path tempDir) {
        String key = "bear.cli.test.failInternal.compile";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "true");
            CliRunResult run = runCli(new String[] { "compile", "bear-ir/fixtures/withdraw.bear.yaml", "--project", tempDir.toString() });
            assertEquals(70, run.exitCode);
            assertTrue(run.stderr.startsWith("internal: INTERNAL_ERROR:"));
            assertFailureEnvelope(
                run.stderr,
                "INTERNAL_ERROR",
                "internal",
                "Capture stderr and file an issue against bear-cli."
            );
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void checkInjectedInternalFailureIsEnveloped(@TempDir Path tempDir) {
        String key = "bear.cli.test.failInternal.check";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "true");
            CliRunResult run = runCli(new String[] { "check", "bear-ir/fixtures/withdraw.bear.yaml", "--project", tempDir.toString() });
            assertEquals(70, run.exitCode);
            assertTrue(run.stderr.startsWith("internal: INTERNAL_ERROR:"));
            assertFailureEnvelope(
                run.stderr,
                "INTERNAL_ERROR",
                "internal",
                "Capture stderr and file an issue against bear-cli."
            );
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void fixInjectedInternalFailureIsEnveloped(@TempDir Path tempDir) {
        String key = "bear.cli.test.failInternal.fix";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "true");
            CliRunResult run = runCli(new String[] { "fix", "bear-ir/fixtures/withdraw.bear.yaml", "--project", tempDir.toString() });
            assertEquals(70, run.exitCode);
            assertTrue(run.stderr.startsWith("internal: INTERNAL_ERROR:"));
            assertFailureEnvelope(
                run.stderr,
                "INTERNAL_ERROR",
                "internal",
                "Capture stderr and file an issue against bear-cli."
            );
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void prCheckInjectedInternalFailureIsEnveloped(@TempDir Path tempDir) throws Exception {
        Path project = tempDir.resolve("project");
        Path ir = project.resolve("bear-ir/withdraw.bear.yaml");
        writeFixtureIr(ir);

        String key = "bear.cli.test.failInternal.pr-check";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "true");
            CliRunResult run = runCli(new String[] {
                "pr-check", "bear-ir/withdraw.bear.yaml", "--project", project.toString(), "--base", "HEAD"
            });
            assertEquals(70, run.exitCode);
            assertTrue(run.stderr.startsWith("internal: INTERNAL_ERROR:"));
            assertFailureEnvelope(
                run.stderr,
                "INTERNAL_ERROR",
                "internal",
                "Capture stderr and file an issue against bear-cli."
            );
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    private static MultiBlockFixture createMultiBlockFixture(Path repoRoot) throws Exception {
        Path specDir = repoRoot.resolve("spec");
        Files.createDirectories(specDir);
        Path alphaIr = specDir.resolve("alpha.bear.yaml");
        Path betaIr = specDir.resolve("beta.bear.yaml");
        Path gammaIr = specDir.resolve("gamma.bear.yaml");
        Files.writeString(alphaIr, fixtureIrForBlockName("alpha"), StandardCharsets.UTF_8);
        Files.writeString(betaIr, fixtureIrForBlockName("beta"), StandardCharsets.UTF_8);
        Files.writeString(gammaIr, fixtureIrForBlockName("gamma"), StandardCharsets.UTF_8);

        Path alphaProject = repoRoot.resolve("services/alpha");
        Path betaProject = repoRoot.resolve("services/beta");
        Path gammaProject = repoRoot.resolve("services/gamma");
        Files.createDirectories(alphaProject);
        Files.createDirectories(betaProject);
        Files.createDirectories(gammaProject);

        assertEquals(0, runCli(new String[] { "compile", alphaIr.toString(), "--project", alphaProject.toString() }).exitCode);
        assertEquals(0, runCli(new String[] { "compile", betaIr.toString(), "--project", betaProject.toString() }).exitCode);
        assertEquals(0, runCli(new String[] { "compile", gammaIr.toString(), "--project", gammaProject.toString() }).exitCode);
        writeWorkingBlockImpl(alphaProject, "alpha");
        writeWorkingBlockImpl(betaProject, "beta");
        writeWorkingBlockImpl(gammaProject, "gamma");

        writeProjectWrapper(
            alphaProject,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        writeProjectWrapper(
            betaProject,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );
        writeProjectWrapper(
            gammaProject,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        writeBlockIndex(repoRoot, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/alpha.bear.yaml\n"
            + "    projectRoot: services/alpha\n"
            + "  - name: beta\n"
            + "    ir: spec/beta.bear.yaml\n"
            + "    projectRoot: services/beta\n"
            + "  - name: gamma\n"
            + "    ir: spec/gamma.bear.yaml\n"
            + "    projectRoot: services/gamma\n");

        return new MultiBlockFixture(
            repoRoot,
            List.of(alphaProject, betaProject, gammaProject)
        );
    }

    private static void writeBlockIndex(Path repoRoot, String content) throws Exception {
        Files.writeString(repoRoot.resolve("bear.blocks.yaml"), content, StandardCharsets.UTF_8);
    }

    private static void deleteGeneratedBaseline(Path projectRoot) throws Exception {
        Path generated = projectRoot.resolve("build/generated/bear");
        if (!Files.exists(generated)) {
            return;
        }
        try (var stream = Files.walk(generated)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static List<String> nonEnvelopeLines(String stderr) {
        List<String> lines = normalizeLf(stderr).lines().filter(line -> !line.isBlank()).toList();
        if (lines.size() < 3) {
            return lines;
        }
        if (lines.get(lines.size() - 3).startsWith("CODE=")
            && lines.get(lines.size() - 2).startsWith("PATH=")
            && lines.get(lines.size() - 1).startsWith("REMEDIATION=")) {
            return lines.subList(0, lines.size() - 3);
        }
        return lines;
    }

    private static void assertFailureEnvelope(String stderr, String code, String path, String remediation) {
        List<String> lines = normalizeLf(stderr).lines().filter(line -> !line.isBlank()).toList();
        assertTrue(lines.size() >= 3);

        long codeCount = lines.stream().filter(line -> line.startsWith("CODE=")).count();
        long pathCount = lines.stream().filter(line -> line.startsWith("PATH=")).count();
        long remediationCount = lines.stream().filter(line -> line.startsWith("REMEDIATION=")).count();
        assertEquals(1L, codeCount);
        assertEquals(1L, pathCount);
        assertEquals(1L, remediationCount);

        assertEquals("CODE=" + code, lines.get(lines.size() - 3));
        assertEquals("PATH=" + path, lines.get(lines.size() - 2));
        assertEquals("REMEDIATION=" + remediation, lines.get(lines.size() - 1));

        String pathValue = path.replace('\\', '/');
        assertFalse(pathValue.startsWith("/"));
        assertFalse(pathValue.startsWith("//"));
        assertFalse(pathValue.matches("^[A-Za-z]:/.*"));
    }

    private static void restoreSystemProperty(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    private static ManifestData readManifestData(Path manifestPath) throws Exception {
        String json = Files.readString(manifestPath, StandardCharsets.UTF_8);
        ManifestData data = new ManifestData();
        data.schemaVersion = extractString(json, "schemaVersion");
        data.target = extractString(json, "target");
        data.block = extractString(json, "block");
        data.irHash = extractString(json, "irHash");
        data.generatorVersion = extractString(json, "generatorVersion");

        String capabilitiesPayload = extractArrayPayload(json, "capabilities");
        Matcher capMatcher = Pattern.compile("\\{\"name\":\"([^\"]+)\",\"ops\":\\[([^\\]]*)\\]\\}").matcher(capabilitiesPayload);
        while (capMatcher.find()) {
            String name = capMatcher.group(1);
            String opsPayload = capMatcher.group(2);
            ArrayList<String> ops = new ArrayList<>();
            Matcher opMatcher = Pattern.compile("\"([^\"]+)\"").matcher(opsPayload);
            while (opMatcher.find()) {
                ops.add(opMatcher.group(1));
            }
            data.capabilities.put(name, ops);
        }

        String invariantsPayload = extractArrayPayload(json, "invariants");
        Matcher invMatcher = Pattern.compile("\\{\"kind\":\"([^\"]+)\",\"field\":\"([^\"]+)\"\\}").matcher(invariantsPayload);
        while (invMatcher.find()) {
            data.invariants.add(invMatcher.group(1) + ":" + invMatcher.group(2));
        }
        return data;
    }

    private static void writeManifestData(Path manifestPath, ManifestData data) throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("{");
        out.append("\"schemaVersion\":\"").append(data.schemaVersion).append("\",");
        out.append("\"target\":\"").append(data.target).append("\",");
        out.append("\"block\":\"").append(data.block).append("\",");
        out.append("\"irHash\":\"").append(data.irHash).append("\",");
        out.append("\"generatorVersion\":\"").append(data.generatorVersion).append("\",");
        out.append("\"capabilities\":[");
        boolean firstCap = true;
        for (Map.Entry<String, ArrayList<String>> entry : data.capabilities.entrySet()) {
            if (!firstCap) {
                out.append(",");
            }
            firstCap = false;
            out.append("{\"name\":\"").append(entry.getKey()).append("\",\"ops\":[");
            for (int i = 0; i < entry.getValue().size(); i++) {
                if (i > 0) {
                    out.append(",");
                }
                out.append("\"").append(entry.getValue().get(i)).append("\"");
            }
            out.append("]}");
        }
        out.append("],");
        out.append("\"invariants\":[");
        for (int i = 0; i < data.invariants.size(); i++) {
            if (i > 0) {
                out.append(",");
            }
            String[] parts = data.invariants.get(i).split(":", 2);
            out.append("{\"kind\":\"").append(parts[0]).append("\",\"field\":\"").append(parts[1]).append("\"}");
        }
        out.append("]}");
        out.append("\n");
        Files.writeString(manifestPath, out.toString(), StandardCharsets.UTF_8);
    }

    private static String extractString(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\"([^\"]*)\"").matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("missing key " + key);
        }
        return m.group(1);
    }

    private static String extractArrayPayload(String json, String key) {
        int keyIdx = json.indexOf("\"" + key + "\":[");
        if (keyIdx < 0) {
            throw new IllegalStateException("missing key " + key);
        }
        int start = json.indexOf('[', keyIdx);
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
        throw new IllegalStateException("unterminated array " + key);
    }

    private static void writeProjectWrapper(Path projectRoot, String windowsContent, String unixContent) throws Exception {
        Path wrapper = projectRoot.resolve(isWindows() ? "gradlew.bat" : "gradlew");
        String content = isWindows() ? windowsContent : unixContent;
        Files.writeString(wrapper, content);
        if (!isWindows()) {
            try {
                Files.setPosixFilePermissions(wrapper, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
                ));
            } catch (UnsupportedOperationException ignored) {
                // Ignore on filesystems without POSIX permissions.
            }
            // Fallback for environments where POSIX permission APIs are unavailable or partial.
            wrapper.toFile().setExecutable(true, false);
        }
        // Ensure JvmTargetDetector can detect this as a JVM project.
        if (!Files.exists(projectRoot.resolve("build.gradle"))) {
            Files.writeString(projectRoot.resolve("build.gradle"), "// test fixture\n");
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static Path initGitRepo(Path repoRoot) throws Exception {
        Files.createDirectories(repoRoot);
        git(repoRoot, "init");
        git(repoRoot, "config", "user.email", "bear@example.com");
        git(repoRoot, "config", "user.name", "Bear Test");
        return repoRoot;
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

    private static String fixtureIrContent() throws Exception {
        return normalizeLf(Files.readString(TestRepoPaths.repoRoot().resolve("bear-ir/fixtures/withdraw.bear.yaml"), StandardCharsets.UTF_8));
    }

    @Test
    void prCheckAllowedDepDeltaClassification(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("bear-ir/withdraw.bear.yaml");
        String base = fixtureIrContent();
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, base, StandardCharsets.UTF_8);
        gitCommitAll(repo, "base ir");

        String head = fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2");
        Files.writeString(ir, head, StandardCharsets.UTF_8);
        gitCommitAll(repo, "add allowed dep");

        CliRunResult added = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        assertEquals(5, added.exitCode);
        assertTrue(normalizeLf(added.stderr).contains(
            "pr-delta: BOUNDARY_EXPANDING: ALLOWED_DEPS: ADDED: com.fasterxml.jackson.core:jackson-databind@2.17.2"
        ));

        String changed = fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.18.0");
        Files.writeString(ir, changed, StandardCharsets.UTF_8);
        gitCommitAll(repo, "change allowed dep version");

        CliRunResult versionChanged = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        assertEquals(5, versionChanged.exitCode);
        assertTrue(normalizeLf(versionChanged.stderr).contains(
            "pr-delta: BOUNDARY_EXPANDING: ALLOWED_DEPS: CHANGED: com.fasterxml.jackson.core:jackson-databind@2.17.2->2.18.0"
        ));

        Files.writeString(ir, base, StandardCharsets.UTF_8);
        gitCommitAll(repo, "remove allowed dep");

        CliRunResult removed = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        assertEquals(0, removed.exitCode);
        assertTrue(normalizeLf(removed.stderr).contains(
            "pr-delta: ORDINARY: ALLOWED_DEPS: REMOVED: com.fasterxml.jackson.core:jackson-databind@2.18.0"
        ));
    }

    @Test
    void prCheckSharedPolicyDeltaClassification(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("bear-ir/withdraw.bear.yaml");
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, fixtureIrContent(), StandardCharsets.UTF_8);
        gitCommitAll(repo, "base ir");

        writeSharedPolicy(
            repo,
            """
                version: v1
                scope: shared
                impl:
                  allowedDeps:
                    - maven: com.fasterxml.jackson.core:jackson-databind
                      version: 2.17.2
                """
        );
        gitCommitAll(repo, "add shared policy dep");

        CliRunResult added = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        assertEquals(5, added.exitCode);
        assertTrue(normalizeLf(added.stderr).contains(
            "pr-delta: BOUNDARY_EXPANDING: ALLOWED_DEPS: ADDED: .:_shared:com.fasterxml.jackson.core:jackson-databind@2.17.2"
        ));

        writeSharedPolicy(
            repo,
            """
                version: v1
                scope: shared
                impl:
                  allowedDeps:
                    - maven: com.fasterxml.jackson.core:jackson-databind
                      version: 2.18.0
                """
        );
        gitCommitAll(repo, "change shared policy dep version");

        CliRunResult changed = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        assertEquals(5, changed.exitCode);
        assertTrue(normalizeLf(changed.stderr).contains(
            "pr-delta: BOUNDARY_EXPANDING: ALLOWED_DEPS: CHANGED: .:_shared:com.fasterxml.jackson.core:jackson-databind@2.17.2->2.18.0"
        ));

        Files.delete(repo.resolve("bear-policy/_shared.policy.yaml"));
        gitCommitAll(repo, "remove shared policy");

        CliRunResult removed = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        assertEquals(0, removed.exitCode);
        assertTrue(normalizeLf(removed.stderr).contains(
            "pr-delta: ORDINARY: ALLOWED_DEPS: REMOVED: .:_shared:com.fasterxml.jackson.core:jackson-databind@2.18.0"
        ));
    }

    @Test
    void prCheckSharedPolicyInvalidReturnsPolicyInvalid(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("bear-ir/withdraw.bear.yaml");
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, fixtureIrContent(), StandardCharsets.UTF_8);
        writeSharedPolicy(
            repo,
            """
                version: v1
                scope: invalid
                impl:
                  allowedDeps: []
                """
        );
        gitCommitAll(repo, "add malformed shared policy");

        CliRunResult run = runCli(new String[] {
            "pr-check", "bear-ir/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD"
        });
        assertEquals(2, run.exitCode);
        assertTrue(normalizeLf(run.stderr).contains("pr-check: POLICY_INVALID: INVALID_SCOPE"));
        assertFailureEnvelope(
            run.stderr,
            "POLICY_INVALID",
            "bear-policy/_shared.policy.yaml",
            "Fix `bear-policy/_shared.policy.yaml` (version/scope/schema and pinned allowedDeps) and rerun `bear pr-check`."
        );
    }

    @Test
    void prCheckAllPrintsRepoDeltaForSharedPolicyBeforeSummary(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("bear-ir/withdraw.bear.yaml");
        writeFixtureIr(ir);
        writeBlockIndex(repo, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: withdraw\n"
            + "    ir: bear-ir/withdraw.bear.yaml\n"
            + "    projectRoot: .\n");
        gitCommitAll(repo, "base repo");

        writeSharedPolicy(
            repo,
            """
                version: v1
                scope: shared
                impl:
                  allowedDeps:
                    - maven: com.fasterxml.jackson.core:jackson-databind
                      version: 2.17.2
                """
        );
        gitCommitAll(repo, "add shared policy dep");

        CliRunResult run = runCli(new String[] {
            "pr-check", "--all", "--project", repo.toString(), "--base", "HEAD~1"
        });
        assertEquals(5, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("REPO DELTA:"));
        assertTrue(stderr.contains("pr-delta: BOUNDARY_EXPANDING: ALLOWED_DEPS: ADDED: .:_shared:com.fasterxml.jackson.core:jackson-databind@2.17.2"));
        assertTrue(stderr.indexOf("REPO DELTA:") < stderr.indexOf("SUMMARY:"));
        assertEquals(1, countOccurrences(stderr, ":_shared:com.fasterxml.jackson.core:jackson-databind@2.17.2"));
    }
    @Test
    void prCheckAllRepoDeltaLinesAreLexicallySorted(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("bear-ir/withdraw.bear.yaml");
        writeFixtureIr(ir);
        writeBlockIndex(repo, ""
            + "version: v1\n"
            + "blocks:\n"
            + "  - name: withdraw\n"
            + "    ir: bear-ir/withdraw.bear.yaml\n"
            + "    projectRoot: .\n");
        gitCommitAll(repo, "base repo");

        writeSharedPolicy(
            repo,
            """
                version: v1
                scope: shared
                impl:
                  allowedDeps:
                    - maven: com.zeta:dep
                      version: 2.0.0
                    - maven: com.alpha:dep
                      version: 1.0.0
                """
        );
        gitCommitAll(repo, "add shared policy deps");

        CliRunResult run = runCli(new String[] {
            "pr-check", "--all", "--project", repo.toString(), "--base", "HEAD~1"
        });
        assertEquals(5, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        CliTestAsserts.assertContainsInOrder(stderr, List.of(
            "REPO DELTA:",
            "pr-delta: BOUNDARY_EXPANDING: ALLOWED_DEPS: ADDED: .:_shared:com.alpha:dep@1.0.0",
            "pr-delta: BOUNDARY_EXPANDING: ALLOWED_DEPS: ADDED: .:_shared:com.zeta:dep@2.0.0",
            "SUMMARY:"
        ));
    }

    @Test
    void checkFailsValidationOnWiringSemanticPortOverlap(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path wiring = tempDir.resolve("build/generated/bear/wiring/withdraw.wiring.json");
        String content = Files.readString(wiring, StandardCharsets.UTF_8);
        content = content.replace(
            "\"logicRequiredPorts\":[\"ledgerPort\"]",
            "\"logicRequiredPorts\":[\"idempotencyPort\",\"ledgerPort\"]"
        );
        Files.writeString(wiring, content, StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(2, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: MANIFEST_INVALID: wrapperOwnedSemanticPorts overlaps logicRequiredPorts: idempotencyPort"));
        assertFailureEnvelope(
            check.stderr,
            "MANIFEST_INVALID",
            "build/generated/bear/wiring/withdraw.wiring.json",
            "Regenerate wiring so semantic ports are wrapper-owned only, then rerun `bear check`."
        );
    }

    @Test
    void checkFailsManifestInvalidWhenGovernedBindingFieldsMissing(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path wiring = tempDir.resolve("build/generated/bear/wiring/withdraw.wiring.json");
        String content = Files.readString(wiring, StandardCharsets.UTF_8);
        content = content.replace("\"logicInterfaceFqcn\":\"com.bear.generated.withdraw.WithdrawLogic\",", "");
        Files.writeString(wiring, content, StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(2, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: MANIFEST_INVALID: MISSING_KEY_logicInterfaceFqcn"));
        assertFailureEnvelope(
            check.stderr,
            "MANIFEST_INVALID",
            "build/generated/bear/wiring/withdraw.wiring.json",
            "Regenerate wiring manifests with governed binding fields and rerun `bear check`."
        );
    }

    @Test
    void checkFailsManifestInvalidWhenBlockRootSourceDirMissing(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path wiring = tempDir.resolve("build/generated/bear/wiring/withdraw.wiring.json");
        String content = Files.readString(wiring, StandardCharsets.UTF_8);
        content = content.replace("\"blockRootSourceDir\":\"src/main/java/blocks/withdraw\",", "");
        Files.writeString(wiring, content, StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(2, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: MANIFEST_INVALID: MISSING_KEY_blockRootSourceDir"));
        assertFailureEnvelope(
            check.stderr,
            "MANIFEST_INVALID",
            "build/generated/bear/wiring/withdraw.wiring.json",
            "Regenerate wiring manifests with governed binding fields and rerun `bear check`."
        );
    }

    @Test
    void checkFailsManifestInvalidWhenGovernedSourceRootsMissing(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path wiring = tempDir.resolve("build/generated/bear/wiring/withdraw.wiring.json");
        String content = Files.readString(wiring, StandardCharsets.UTF_8);
        content = content.replace("\"governedSourceRoots\":[\"src/main/java/blocks/withdraw\",\"src/main/java/blocks/_shared\"],", "");
        Files.writeString(wiring, content, StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(2, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: MANIFEST_INVALID: MISSING_KEY_governedSourceRoots"));
        assertFailureEnvelope(
            check.stderr,
            "MANIFEST_INVALID",
            "build/generated/bear/wiring/withdraw.wiring.json",
            "Regenerate wiring manifests with governed binding fields and rerun `bear check`."
        );
    }

    @Test
    void checkFailsManifestInvalidWhenWiringSchemaNotV3(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        Path wiring = tempDir.resolve("build/generated/bear/wiring/withdraw.wiring.json");
        String content = Files.readString(wiring, StandardCharsets.UTF_8);
        content = content.replace("\"schemaVersion\":\"v3\"", "\"schemaVersion\":\"v1\"");
        Files.writeString(wiring, content, StandardCharsets.UTF_8);

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(2, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: MANIFEST_INVALID: UNSUPPORTED_WIRING_SCHEMA_VERSION"));
        assertFailureEnvelope(
            check.stderr,
            "MANIFEST_INVALID",
            "build/generated/bear/wiring/withdraw.wiring.json",
            "Regenerate wiring manifests with governed binding fields and rerun `bear check`."
        );
    }

    @Test
    void checkFailsValidationWhenSemanticChecksTargetIsUnsupported(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        String key = "bear.check.test.candidateManifestMode";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "target:python");
            CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
            assertEquals(2, check.exitCode);
            assertTrue(normalizeLf(check.stderr).contains("check: MANIFEST_INVALID: unsupported semantic enforcement target: python"));
            assertFailureEnvelope(
                check.stderr,
                "MANIFEST_INVALID",
                "build/generated/bear/surfaces/withdraw.surface.json",
                "Use a target that enforces declared semantics or remove semantic declarations."
            );
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void checkClassifiesInvariantMarkerAsInvariantViolation(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        writeWorkingWithdrawImpl(tempDir);

        String marker = "BEAR_INVARIANT_VIOLATION|block=withdraw|kind=non_negative|field=balance|observed=-1|rule=non_negative";
        writeProjectWrapper(
            tempDir,
            "@echo off\r\n"
                + "echo " + marker.replace("|", "^|") + "\r\n"
                + "exit /b 1\r\n",
            "#!/usr/bin/env sh\n"
                + "echo \"" + marker + "\"\n"
                + "exit 1\n"
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(4, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: TEST_FAILED: " + marker));
        assertFailureEnvelope(
            check.stderr,
            "INVARIANT_VIOLATION",
            "project.tests",
            "Fix invariant violation and rerun `bear check <ir-file> --project <path>`."
        );
    }

    private static String fixtureIrForBlockName(String blockName) throws Exception {
        return fixtureIrContent().replaceFirst("(?im)^\\s*name:\\s*withdraw\\s*$", "  name: " + blockName);
    }

    private static String fixtureIrWithAllowedDep(String maven, String version) throws Exception {
        return fixtureIrContent()
            + "  impl:\n"
            + "    allowedDeps:\n"
            + "      - maven: " + maven + "\n"
            + "        version: " + version + "\n";
    }

    private static String fixtureIrWithAllowedDepForBlock(String blockName, String maven, String version) throws Exception {
        return fixtureIrForBlockName(blockName)
            + "  impl:\n"
            + "    allowedDeps:\n"
            + "      - maven: " + maven + "\n"
            + "        version: " + version + "\n";
    }

    private static void writeSharedPolicy(Path repoRoot, String content) throws Exception {
        Path policy = repoRoot.resolve("bear-policy/_shared.policy.yaml");
        Files.createDirectories(policy.getParent());
        Files.writeString(policy, content, StandardCharsets.UTF_8);
    }

    private static void writeFixtureIr(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, fixtureIrContent(), StandardCharsets.UTF_8);
    }

    private static void writeWorkingWithdrawImpl(Path projectRoot) throws Exception {
        writeWorkingBlockImpl(projectRoot, "withdraw");
    }

    private static void writeFreshContainmentMarkers(Path projectRoot) throws Exception {
        Path required = projectRoot.resolve("build/generated/bear/config/containment-required.json");
        String requiredJson = Files.readString(required, StandardCharsets.UTF_8);
        String hash = bytesToHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(required)));
        List<String> blockKeys = parseContainmentRequiredBlockKeys(requiredJson);

        Path markerDir = projectRoot.resolve("build/bear/containment");
        Files.createDirectories(markerDir);
        Files.writeString(
            markerDir.resolve("applied.marker"),
            "hash=" + hash + "\nblocks=" + String.join(",", blockKeys) + "\n",
            StandardCharsets.UTF_8
        );
        for (String blockKey : blockKeys) {
            Files.writeString(
                markerDir.resolve(blockKey + ".applied.marker"),
                "block=" + blockKey + "\nhash=" + hash + "\n",
                StandardCharsets.UTF_8
            );
        }
    }

    private static List<String> parseContainmentRequiredBlockKeys(String json) {
        TreeMap<String, Boolean> keys = new TreeMap<>();
        Matcher matcher = Pattern.compile("\\\"blockKey\\\":\\\"([^\\\"]+)\\\"").matcher(json);
        while (matcher.find()) {
            keys.put(matcher.group(1), Boolean.TRUE);
        }
        return List.copyOf(keys.keySet());
    }

    private static void writeWorkingBlockImpl(Path projectRoot, String blockName) throws Exception {
        String className = Character.toUpperCase(blockName.charAt(0)) + blockName.substring(1);
        String operationName = "ExecuteWithdraw";
        String requestType = className + "_" + operationName + "Request";
        String resultType = className + "_" + operationName + "Result";
        Path impl = projectRoot.resolve("src/main/java/blocks/" + blockName + "/impl/" + className + "Impl.java");
        Files.createDirectories(impl.getParent());
        String generatedPackage = "com.bear.generated." + blockName;
        String source = ""
            + "package blocks." + blockName + ".impl;\n"
            + "\n"
            + "import " + generatedPackage + ".BearValue;\n"
            + "import " + generatedPackage + ".LedgerPort;\n"
            + "import " + generatedPackage + "." + className + "Logic;\n"
            + "import " + generatedPackage + "." + requestType + ";\n"
            + "import " + generatedPackage + "." + resultType + ";\n"
            + "\n"
            + "public final class " + className + "Impl implements " + className + "Logic {\n"
            + "  public " + resultType + " execute" + operationName + "(" + requestType + " request, LedgerPort ledgerPort) {\n"
            + "    ledgerPort.getBalance(BearValue.empty());\n"
            + "    return new " + resultType + "(java.math.BigDecimal.ZERO);\n"
            + "  }\n"
            + "}\n";
        Files.writeString(impl, source, StandardCharsets.UTF_8);
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // Best-effort cleanup for temp test workspace.
                }
            }
        } catch (Exception ignored) {
            // Best-effort cleanup for temp test workspace.
        }
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

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while (true) {
            index = text.indexOf(needle, index);
            if (index < 0) {
                return count;
            }
            count++;
            index += needle.length();
        }
    }

    private static final class ManifestData {
        private String schemaVersion;
        private String target;
        private String block;
        private String irHash;
        private String generatorVersion;
        private final TreeMap<String, ArrayList<String>> capabilities = new TreeMap<>();
        private final ArrayList<String> invariants = new ArrayList<>();
    }

    private record CliRunResult(int exitCode, String stdout, String stderr) {
    }

    private record MultiBlockFixture(Path repoRoot, List<Path> projectRoots) {
    }
}
