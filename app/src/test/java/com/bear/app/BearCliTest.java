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
        String stderr = stderrBytes.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.startsWith("usage: INVALID_ARGS:"));
        assertFailureEnvelope(
            stderr,
            "USAGE_INVALID_ARGS",
            "cli.args",
            "Run `bear validate <file>` with exactly one IR file path."
        );
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
        assertTrue(stdout.contains("bear fix <ir-file> --project <path>"));
        assertTrue(stdout.contains("bear fix --all --project <repoRoot> [--blocks <path>] [--only <csv>] [--fail-fast] [--strict-orphans]"));
        assertTrue(stdout.contains("bear unblock --project <path>"));
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
        String stderr = stderrBytes.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.startsWith("io: IO_ERROR:"));
        assertFailureEnvelope(
            stderr,
            "IO_ERROR",
            "input.ir",
            "Ensure the IR file exists and is readable, then rerun `bear validate <file>`."
        );
    }

    @Test
    void validateSchemaErrorPrintsDeterministicPrefix(@TempDir Path tempDir) throws Exception {
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

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exitCode = BearCli.run(
            new String[] { "validate", invalid.toString() },
            new PrintStream(stdoutBytes),
            new PrintStream(stderrBytes)
        );

        assertEquals(2, exitCode);
        String stderr = stderrBytes.toString(StandardCharsets.UTF_8);
        assertTrue(stderr.startsWith("schema at block: UNKNOWN_KEY:"));
        assertFailureEnvelope(
            stderr,
            "IR_VALIDATION",
            "block",
            "Fix the IR issue at the reported path and rerun `bear validate <file>`."
        );
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
            "Run `bear compile <ir-file> --project <path>` with the expected arguments."
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
    void fixRequiresExpectedArgs() {
        CliRunResult run = runCli(new String[] { "fix" });
        assertEquals(64, run.exitCode);
        assertTrue(run.stderr.startsWith("usage: INVALID_ARGS:"));
        assertFailureEnvelope(
            run.stderr,
            "USAGE_INVALID_ARGS",
            "cli.args",
            "Run `bear fix <ir-file> --project <path>` with the expected arguments."
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        Path impl = tempDir.resolve("src/main/java/com/bear/generated/withdraw/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        String implContent = "package com.bear.generated.withdraw;\npublic final class WithdrawImpl {\n  // keep\n}\n";
        Files.writeString(impl, implContent);

        Path generated = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw/Withdraw.java");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, "stale\n");

        CliRunResult run = runCli(new String[] { "fix", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, run.exitCode);
        assertEquals("fix: OK\n", normalizeLf(run.stdout));
        assertEquals("", run.stderr);

        assertTrue(Files.readString(generated).contains("public final class Withdraw"));
        assertEquals(implContent, Files.readString(impl));
    }

    @Test
    void fixDeterministicRerunIsByteIdentical(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        assertEquals(0, runCli(new String[] { "fix", fixture.toString(), "--project", tempDir.toString() }).exitCode);
        Path generated = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw/Withdraw.java");
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
            "Run `bear check <ir-file> --project <path>` with the expected arguments."
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, run.exitCode);
        assertTrue(run.stderr.startsWith("drift: MISSING_BASELINE: build/generated/bear"));
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        Path emptyBaseline = tempDir.resolve("build/generated/bear");
        Files.createDirectories(emptyBaseline);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, run.exitCode);
        assertTrue(run.stderr.startsWith("drift: MISSING_BASELINE: build/generated/bear"));
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);

        Path baselineFile = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw/Withdraw.java");
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

        List<String> lines = nonEnvelopeLines(check.stderr);
        assertEquals(List.of(
            "drift: REMOVED: src/main/java/com/bear/generated/withdraw/BearValue.java",
            "drift: CHANGED: src/main/java/com/bear/generated/withdraw/Withdraw.java"
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
    void checkEmitsCapabilityAddedBoundarySignalAndDrift(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);

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

        CliRunResult check = runCli(new String[] { "check", ir.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        assertTrue(normalizeLf(check.stderr).contains("check: CONTAINMENT_REQUIRED: UNSUPPORTED_TARGET: missing Gradle wrapper"));
        assertFailureEnvelope(
            check.stderr,
            "CONTAINMENT_UNSUPPORTED_TARGET",
            "project.root",
            "Allowed dependency containment in P2 requires Java+Gradle with wrapper at project root; remove `impl.allowedDeps` or use supported target, then rerun `bear check`."
        );
    }

    @Test
    void checkAllowedDepsMissingMarkerFailsDeterministically(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));

        CliRunResult compile = runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);

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
            "Run Gradle build once so BEAR containment compile tasks write markers, then rerun `bear check`."
        );
    }

    @Test
    void checkAllowedDepsStaleMarkerFailsDeterministically(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));

        CliRunResult compile = runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);

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
    void checkAllowedDepsWithFreshMarkerPasses(@TempDir Path tempDir) throws Exception {
        Path ir = tempDir.resolve("withdraw-allowedDeps.bear.yaml");
        Files.writeString(ir, fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2"));

        CliRunResult compile = runCli(new String[] { "compile", ir.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);

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
        assertEquals(0, check.exitCode);
        assertTrue(check.stdout.startsWith("check: OK"));
    }

    @Test
    void checkWithoutAllowedDepsKeepsImplOnNormalCompilePath(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

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
                + "javac -d build\\project-test-classes build\\generated\\bear\\src\\main\\java\\com\\bear\\generated\\withdraw\\*.java src\\main\\java\\blocks\\withdraw\\impl\\WithdrawImpl.java src\\test\\java\\com\\example\\UsesWithdrawImpl.java\r\n"
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
                + "javac -d build/project-test-classes build/generated/bear/src/main/java/com/bear/generated/withdraw/*.java src/main/java/blocks/withdraw/impl/WithdrawImpl.java src/test/java/com/example/UsesWithdrawImpl.java\n"
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);

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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho java.io.FileNotFoundException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip.lck (Access is denied)\r\necho PROJECT_TEST_GRADLE_LOCK_SIMULATED\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"java.io.FileNotFoundException: /tmp/gradle-8.12.1-bin.zip.lck (Access is denied)\"\necho \"PROJECT_TEST_GRADLE_LOCK_SIMULATED\"\nexit 1\n"
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        assertTrue(normalizeLf(check.stderr).startsWith("io: IO_ERROR: PROJECT_TEST_LOCK:"));
        assertFailureEnvelope(
            check.stderr,
            "IO_ERROR",
            "project.tests",
            "Release Gradle wrapper lock or set isolated GRADLE_USER_HOME, then rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkProjectTestGradleBootstrapFailureReturnsIoError(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho java.nio.file.NoSuchFileException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip\r\necho PROJECT_TEST_GRADLE_BOOTSTRAP_SIMULATED\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"java.nio.file.NoSuchFileException: /tmp/gradle-8.12.1-bin.zip\"\necho \"PROJECT_TEST_GRADLE_BOOTSTRAP_SIMULATED\"\nexit 1\n"
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(74, check.exitCode);
        assertTrue(normalizeLf(check.stderr).startsWith("io: IO_ERROR: PROJECT_TEST_BOOTSTRAP:"));
        assertFailureEnvelope(
            check.stderr,
            "IO_ERROR",
            "project.tests",
            "Fix Gradle wrapper bootstrap/cache (distribution zip/unzip) and rerun `bear check <ir-file> --project <path>`."
        );
    }

    @Test
    void checkProjectTestFailureReturnsExit4AndTail(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);

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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        CliRunResult compile = runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, compile.exitCode);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho start\r\npowershell -Command \"Start-Sleep -Seconds 3\"\r\necho end\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho start\nsleep 3\necho end\nexit 0\n"
        );

        String prev = System.getProperty("bear.check.testTimeoutSeconds");
        try {
            System.setProperty("bear.check.testTimeoutSeconds", "1");
            CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
            assertEquals(4, check.exitCode);
            assertTrue(normalizeLf(check.stderr).startsWith("check: TEST_TIMEOUT: project tests exceeded 1s"));
            assertFailureEnvelope(
                check.stderr,
                "TEST_TIMEOUT",
                "project.tests",
                "Reduce test runtime or increase timeout, then rerun `bear check <ir-file> --project <path>`."
            );
        } finally {
            if (prev == null) {
                System.clearProperty("bear.check.testTimeoutSeconds");
            } else {
                System.setProperty("bear.check.testTimeoutSeconds", prev);
            }
        }
    }

    @Test
    void checkDriftShortCircuitsAndDoesNotRunProjectTests(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

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
    void checkUndeclaredReachDetectsCoveredHttpSurfaces(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path srcRoot = tempDir.resolve("src/main/java/example");
        Files.createDirectories(srcRoot);
        Files.writeString(srcRoot.resolve("A.java"), "class A { String s = \"java.net.http.HttpClient\"; }");
        Files.writeString(srcRoot.resolve("B.java"), "class B { String s = \"java.net.URL\"; String t = \"openConnection(\"; }");
        Files.writeString(srcRoot.resolve("C.java"), "class C { String s = \"okhttp3.OkHttpClient\"; }");
        Files.writeString(srcRoot.resolve("D.java"), "class D { String s = \"org.springframework.web.client.RestTemplate\"; }");
        Files.writeString(srcRoot.resolve("E.java"), "class E { String s = \"java.net.HttpURLConnection\"; }");

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path a = tempDir.resolve("src/main/java/example/A.java");
        Path b = tempDir.resolve("src/main/java/example/B.java");
        Files.createDirectories(a.getParent());
        Files.writeString(a, "class A { String s = \"org.springframework.web.client.RestTemplate\"; String t = \"okhttp3.OkHttpClient\"; }");
        Files.writeString(b, "class B { String s = \"java.net.http.HttpClient\"; }");

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
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
    void checkDriftTakesPrecedenceOverUndeclaredReach(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Files.writeString(
            tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw/Withdraw.java"),
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
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
        assertEquals(6, check.exitCode);
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
    void checkBoundaryBypassIgnoresImplTextInCommentsAndStrings(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
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
        assertEquals(6, check.exitCode);
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
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);
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
    void checkBoundaryBypassEffectsMissingRequiredPortUsageFails(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
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
            + "  Object execute(Object request, Object idempotencyPort, Object ledgerPort) {\n"
            + "    return null;\n"
            + "  }\n"
            + "}\n",
            StandardCharsets.UTF_8
        );

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(6, check.exitCode);
        String stderr = normalizeLf(check.stderr);
        assertTrue(stderr.contains("check: BOUNDARY_BYPASS: RULE=EFFECTS_BYPASS: src/main/java/blocks/withdraw/impl/WithdrawImpl.java: missing required effect port usage: idempotencyPort"));
        assertFailureEnvelope(
            check.stderr,
            "BOUNDARY_BYPASS",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            "Wire via generated entrypoints and declared effect ports; remove impl seam bypasses."
        );
    }

    @Test
    void checkBoundaryBypassEffectsHelperPassThroughAndSuppression(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
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
            + "  Object execute(Object request, Object idempotencyPort, Object ledgerPort) {\n"
            + "    return helper(idempotencyPort, ledgerPort);\n"
            + "  }\n"
            + "  Object helper(Object left, Object right) { return null; }\n"
            + "}\n",
            StandardCharsets.UTF_8
        );
        CliRunResult passByHelper = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, passByHelper.exitCode);

        Files.writeString(impl, ""
            + "package blocks.withdraw.impl;\n"
            + "public final class WithdrawImpl {\n"
            + "  Object execute(Object request, Object idempotencyPort, Object ledgerPort) {\n"
            + "    // BEAR:PORT_USED idempotencyPort\n"
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
            + "  Object execute(Object request, Object idempotencyPort, Object ledgerPort) {\n"
            + "    // BEAR:PORT_USED\n"
            + "    return null;\n"
            + "  }\n"
            + "}\n",
            StandardCharsets.UTF_8
        );
        CliRunResult malformedSuppression = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(6, malformedSuppression.exitCode);
        assertTrue(
            normalizeLf(malformedSuppression.stderr).contains(
                "check: BOUNDARY_BYPASS: RULE=EFFECTS_BYPASS: src/main/java/blocks/withdraw/impl/WithdrawImpl.java: missing required effect port usage: idempotencyPort"
            )
        );
    }

    @Test
    void checkBlockedMarkerRequiresUnblockAndSuccessfulCheckClears(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

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
        CliRunResult blocked = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(74, blocked.exitCode);
        assertTrue(normalizeLf(blocked.stderr).startsWith("check: IO_ERROR: CHECK_BLOCKED:"));

        CliRunResult unblock = runCli(new String[] { "unblock", "--project", tempDir.toString() });
        assertEquals(0, unblock.exitCode);
        assertEquals("unblock: OK\n", normalizeLf(unblock.stdout));
        assertFalse(Files.exists(marker));

        CliRunResult pass = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(0, pass.exitCode);
        assertFalse(Files.exists(marker));
    }

    @Test
    void checkAllHonorsBlockedMarkerPreflight(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path alphaRoot = fixture.projectRoots().get(0);
        Path marker = alphaRoot.resolve("build/bear/check.blocked.marker");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "reason=BOOTSTRAP_IO\ndetail=java.nio.file.NoSuchFileException: gradle-8.12.1-bin.zip\n", StandardCharsets.UTF_8);

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString()
        });
        assertEquals(74, run.exitCode);
        assertTrue(normalizeLf(run.stderr).startsWith("check: IO_ERROR: CHECK_BLOCKED: services/alpha:"));
        assertFailureEnvelope(
            run.stderr,
            "IO_ERROR",
            "services/alpha/build/bear/check.blocked.marker",
            "Run `bear unblock --project <path>` after fixing lock/bootstrap IO and rerun `bear check --all`."
        );
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
        assertTrue(stderr.contains("CODE=REPO_MULTI_BLOCK_FAILED"));
        assertTrue(stderr.contains("PATH=bear.blocks.yaml"));
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
            + "version: v0\n"
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
    void checkAllClassifiesGradleLockAsIoError(@TempDir Path tempDir) throws Exception {
        MultiBlockFixture fixture = createMultiBlockFixture(tempDir);
        Path alphaRoot = fixture.projectRoots().get(0);
        writeProjectWrapper(
            alphaRoot,
            "@echo off\r\necho java.io.FileNotFoundException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip.lck (Access is denied)\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"java.io.FileNotFoundException: /tmp/gradle-8.12.1-bin.zip.lck (Access is denied)\"\nexit 1\n"
        );

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString()
        });
        assertEquals(74, run.exitCode);
        assertTrue(normalizeLf(run.stderr).contains("CATEGORY: IO_ERROR"));
        assertTrue(normalizeLf(run.stderr).contains("DETAIL: root-level project test runner lock in projectRoot services/alpha; line:"));
        assertFailureEnvelope(
            run.stderr,
            "REPO_MULTI_BLOCK_FAILED",
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
        );
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

        CliRunResult run = runCli(new String[] {
            "check", "--all", "--project", fixture.repoRoot().toString()
        });
        assertEquals(74, run.exitCode);
        String stderr = normalizeLf(run.stderr);
        assertTrue(stderr.contains("CATEGORY: IO_ERROR"));
        assertTrue(stderr.contains("DETAIL: root-level project test bootstrap IO failure in projectRoot services/alpha; line:"));
        assertFailureEnvelope(
            run.stderr,
            "REPO_MULTI_BLOCK_FAILED",
            "bear.blocks.yaml",
            "Review per-block results above and fix failing blocks, then rerun the command."
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
            + "version: v0\n"
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
            + "version: v0\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/alpha.bear.yaml\n"
            + "    projectRoot: services/a\n"
            + "  - name: beta\n"
            + "    ir: spec/beta.bear.yaml\n"
            + "    projectRoot: services/b\n");
        gitCommitAll(repo, "base");

        String ordinary = baseAlpha.replace("          - setBalance\n", "          - setBalance\n          - reverse\n");
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
            "Run `bear pr-check <ir-file> --project <path> --base <ref>` with the expected arguments."
        );
    }

    @Test
    void prCheckRejectsAbsoluteIrPath(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("spec/withdraw.bear.yaml");
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, Files.readString(TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml")));
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
        writeFixtureIr(repo.resolve("spec/withdraw.bear.yaml"));
        gitCommitAll(repo, "add ir");

        CliRunResult run = runCli(new String[] {
            "pr-check", "spec/withdraw.bear.yaml", "--project", repo.toString(), "--base", "missing-ref"
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
        writeFixtureIr(repo.resolve("spec/withdraw.bear.yaml"));
        gitCommitAll(repo, "add ir");

        CliRunResult run = runCli(new String[] {
            "pr-check", "spec/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD"
        });
        assertEquals(0, run.exitCode);
        assertEquals("pr-check: OK: NO_BOUNDARY_EXPANSION\n", normalizeLf(run.stdout));
        assertEquals("", run.stderr);
    }

    @Test
    void prCheckTreatsMissingBaseIrAsBoundaryExpansion(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Files.writeString(repo.resolve("README.md"), "base\n", StandardCharsets.UTF_8);
        gitCommitAll(repo, "base");

        writeFixtureIr(repo.resolve("spec/withdraw.bear.yaml"));
        gitCommitAll(repo, "add ir");

        CliRunResult run = runCli(new String[] {
            "pr-check", "spec/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        String stderr = normalizeLf(run.stderr);
        assertEquals(5, run.exitCode);
        assertTrue(stderr.contains("pr-check: INFO: BASE_IR_MISSING_AT_MERGE_BASE: spec/withdraw.bear.yaml: treated_as_empty_base"));
        assertTrue(stderr.contains("pr-delta: BOUNDARY_EXPANDING: PORTS: ADDED: idempotency"));
        assertTrue(stderr.contains("pr-check: FAIL: BOUNDARY_EXPANSION_DETECTED"));
        assertFailureEnvelope(
            run.stderr,
            "BOUNDARY_EXPANSION",
            "spec/withdraw.bear.yaml",
            "Review boundary-expanding deltas and route through explicit boundary review."
        );
    }

    @Test
    void prCheckOrdinaryOpsDeltaReturnsZero(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("spec/withdraw.bear.yaml");
        String base = fixtureIrContent();
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, base, StandardCharsets.UTF_8);
        gitCommitAll(repo, "base ir");

        String head = base.replace("          - setBalance\n", "          - setBalance\n          - reverse\n");
        Files.writeString(ir, head, StandardCharsets.UTF_8);
        gitCommitAll(repo, "ordinary op change");

        CliRunResult run = runCli(new String[] {
            "pr-check", "spec/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        String stderr = normalizeLf(run.stderr);
        assertEquals(0, run.exitCode);
        assertTrue(stderr.contains("pr-delta: ORDINARY: OPS: ADDED: ledger.reverse"));
        assertFalse(stderr.contains("BOUNDARY_EXPANDING"));
        assertEquals("pr-check: OK: NO_BOUNDARY_EXPANSION\n", normalizeLf(run.stdout));
    }

    @Test
    void prCheckOrderingIsDeterministic(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("spec/withdraw.bear.yaml");
        String base = fixtureIrContent();
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, base, StandardCharsets.UTF_8);
        gitCommitAll(repo, "base ir");

        String head = base
            .replace("      - name: txId\n        type: string\n", "      - name: txId\n        type: string\n      - name: note\n        type: string\n")
            .replace("          - setBalance\n", "          - setBalance\n          - reverse\n")
            .replace(
                "      - port: idempotency\n        ops:\n          - get\n          - put\n",
                "      - port: audit\n        ops:\n          - write\n      - port: idempotency\n        ops:\n          - get\n          - put\n"
            )
            .replace("    key: txId\n", "    key: accountId\n")
            .replace("  invariants:\n    - kind: non_negative\n      field: balance\n", "");
        Files.writeString(ir, head, StandardCharsets.UTF_8);
        gitCommitAll(repo, "mixed changes");

        CliRunResult run = runCli(new String[] {
            "pr-check", "spec/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        List<String> lines = normalizeLf(run.stderr).lines()
            .filter(line -> line.startsWith("pr-delta: "))
            .toList();
        assertEquals(5, run.exitCode);
        assertEquals(List.of(
            "pr-delta: BOUNDARY_EXPANDING: PORTS: ADDED: audit",
            "pr-delta: BOUNDARY_EXPANDING: IDEMPOTENCY: CHANGED: idempotency.key",
            "pr-delta: BOUNDARY_EXPANDING: INVARIANTS: REMOVED: non_negative:balance",
            "pr-delta: ORDINARY: OPS: ADDED: ledger.reverse",
            "pr-delta: ORDINARY: CONTRACT: ADDED: input.note:string"
        ), lines);
        assertFailureEnvelope(
            run.stderr,
            "BOUNDARY_EXPANSION",
            "spec/withdraw.bear.yaml",
            "Review boundary-expanding deltas and route through explicit boundary review."
        );
    }

    @Test
    void prCheckIdempotencyAddEmitsOnlyTopLevelDelta(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("spec/withdraw.bear.yaml");
        String fixture = fixtureIrContent();
        String base = fixture.replace(
            "  idempotency:\n    key: txId\n    store:\n      port: idempotency\n      getOp: get\n      putOp: put\n",
            ""
        );
        assertFalse(base.equals(fixture));
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, base, StandardCharsets.UTF_8);
        gitCommitAll(repo, "base without idempotency");

        Files.writeString(ir, fixture, StandardCharsets.UTF_8);
        gitCommitAll(repo, "add idempotency");

        CliRunResult run = runCli(new String[] {
            "pr-check", "spec/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        String stderr = normalizeLf(run.stderr);
        assertEquals(5, run.exitCode);
        assertTrue(stderr.contains("pr-delta: BOUNDARY_EXPANDING: IDEMPOTENCY: ADDED: idempotency"));
        assertFalse(stderr.contains("idempotency.store.port"));
        assertFalse(stderr.contains("idempotency.store.getOp"));
        assertFalse(stderr.contains("idempotency.store.putOp"));
        assertFalse(stderr.contains("idempotency.key"));
        assertFailureEnvelope(
            run.stderr,
            "BOUNDARY_EXPANSION",
            "spec/withdraw.bear.yaml",
            "Review boundary-expanding deltas and route through explicit boundary review."
        );
    }

    @Test
    void validateInjectedInternalFailureIsEnveloped() {
        String key = "bear.cli.test.failInternal.validate";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "true");
            CliRunResult run = runCli(new String[] { "validate", "spec/fixtures/withdraw.bear.yaml" });
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
    void compileInjectedInternalFailureIsEnveloped(@TempDir Path tempDir) {
        String key = "bear.cli.test.failInternal.compile";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "true");
            CliRunResult run = runCli(new String[] { "compile", "spec/fixtures/withdraw.bear.yaml", "--project", tempDir.toString() });
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
            CliRunResult run = runCli(new String[] { "check", "spec/fixtures/withdraw.bear.yaml", "--project", tempDir.toString() });
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
            CliRunResult run = runCli(new String[] { "fix", "spec/fixtures/withdraw.bear.yaml", "--project", tempDir.toString() });
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
        Path ir = project.resolve("spec/withdraw.bear.yaml");
        writeFixtureIr(ir);

        String key = "bear.cli.test.failInternal.pr-check";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "true");
            CliRunResult run = runCli(new String[] {
                "pr-check", "spec/withdraw.bear.yaml", "--project", project.toString(), "--base", "HEAD"
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
            + "version: v0\n"
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
        return normalizeLf(Files.readString(TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml"), StandardCharsets.UTF_8));
    }

    @Test
    void prCheckAllowedDepDeltaClassification(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("spec/withdraw.bear.yaml");
        String base = fixtureIrContent();
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, base, StandardCharsets.UTF_8);
        gitCommitAll(repo, "base ir");

        String head = fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.17.2");
        Files.writeString(ir, head, StandardCharsets.UTF_8);
        gitCommitAll(repo, "add allowed dep");

        CliRunResult added = runCli(new String[] {
            "pr-check", "spec/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        assertEquals(5, added.exitCode);
        assertTrue(normalizeLf(added.stderr).contains(
            "pr-delta: BOUNDARY_EXPANDING: ALLOWED_DEPS: ADDED: com.fasterxml.jackson.core:jackson-databind@2.17.2"
        ));

        String changed = fixtureIrWithAllowedDep("com.fasterxml.jackson.core:jackson-databind", "2.18.0");
        Files.writeString(ir, changed, StandardCharsets.UTF_8);
        gitCommitAll(repo, "change allowed dep version");

        CliRunResult versionChanged = runCli(new String[] {
            "pr-check", "spec/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        assertEquals(5, versionChanged.exitCode);
        assertTrue(normalizeLf(versionChanged.stderr).contains(
            "pr-delta: BOUNDARY_EXPANDING: ALLOWED_DEPS: CHANGED: com.fasterxml.jackson.core:jackson-databind@2.17.2->2.18.0"
        ));

        Files.writeString(ir, base, StandardCharsets.UTF_8);
        gitCommitAll(repo, "remove allowed dep");

        CliRunResult removed = runCli(new String[] {
            "pr-check", "spec/withdraw.bear.yaml", "--project", repo.toString(), "--base", "HEAD~1"
        });
        assertEquals(0, removed.exitCode);
        assertTrue(normalizeLf(removed.stderr).contains(
            "pr-delta: ORDINARY: ALLOWED_DEPS: REMOVED: com.fasterxml.jackson.core:jackson-databind@2.18.0"
        ));
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

    private static void writeFixtureIr(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, fixtureIrContent(), StandardCharsets.UTF_8);
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



