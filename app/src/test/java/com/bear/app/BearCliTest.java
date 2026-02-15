package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
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

    @Test
    void checkEmitsCapabilityAddedBoundarySignalAndDrift(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/bear.surface.json");
        ManifestData baseline = readManifestData(manifest);
        baseline.capabilities.remove("idempotency");
        writeManifestData(manifest, baseline);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        String stderr = normalizeLf(run.stderr);
        assertEquals(3, run.exitCode);
        assertTrue(stderr.contains("boundary: EXPANSION: CAPABILITY_ADDED: idempotency"));
        assertTrue(stderr.contains("drift: CHANGED: bear.surface.json"));
    }

    @Test
    void checkEmitsCapabilityOpAddedBoundarySignalAndDrift(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/bear.surface.json");
        ManifestData baseline = readManifestData(manifest);
        baseline.capabilities.put("idempotency", new ArrayList<>(List.of("get")));
        writeManifestData(manifest, baseline);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        String stderr = normalizeLf(run.stderr);
        assertEquals(3, run.exitCode);
        assertTrue(stderr.contains("boundary: EXPANSION: CAPABILITY_OP_ADDED: idempotency.put"));
        assertTrue(stderr.contains("drift: CHANGED: bear.surface.json"));
    }

    @Test
    void checkEmitsInvariantRelaxedBoundarySignalAndDrift(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/bear.surface.json");
        ManifestData baseline = readManifestData(manifest);
        baseline.invariants.add("non_negative:shadow");
        writeManifestData(manifest, baseline);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        String stderr = normalizeLf(run.stderr);
        assertEquals(3, run.exitCode);
        assertTrue(stderr.contains("boundary: EXPANSION: INVARIANT_RELAXED: non_negative:shadow"));
        assertTrue(stderr.contains("drift: CHANGED: bear.surface.json"));
    }

    @Test
    void checkBoundarySignalOrderingIsTypeThenKey(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/bear.surface.json");
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
    }

    @Test
    void checkWarnsWhenBaselineManifestMissing(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/bear.surface.json");
        Files.delete(manifest);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, run.exitCode);
        assertTrue(normalizeLf(run.stderr).contains("check: BASELINE_MANIFEST_MISSING: " + manifest));
    }

    @Test
    void checkWarnsWhenBaselineManifestInvalid(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/bear.surface.json");
        Files.writeString(manifest, "{invalid", StandardCharsets.UTF_8);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, run.exitCode);
        assertTrue(normalizeLf(run.stderr).contains("check: BASELINE_MANIFEST_INVALID: MALFORMED_JSON"));
    }

    @Test
    void checkWarnsOnBaselineStampMismatch(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode);

        Path manifest = tempDir.resolve("build/generated/bear/bear.surface.json");
        ManifestData baseline = readManifestData(manifest);
        baseline.irHash = "0000000000000000000000000000000000000000000000000000000000000000";
        writeManifestData(manifest, baseline);

        CliRunResult run = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, run.exitCode);
        assertTrue(normalizeLf(run.stderr).contains(
            "check: BASELINE_STAMP_MISMATCH: irHash/generatorVersion differ; classification may be stale"));
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

            System.setProperty("bear.check.test.candidateManifestMode", "invalid");
            CliRunResult invalid = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
            assertEquals(70, invalid.exitCode);
            assertTrue(normalizeLf(invalid.stderr).startsWith("internal: INTERNAL_ERROR: CANDIDATE_MANIFEST_INVALID:"));
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

        Path driftFile = tempDir.resolve("build/generated/bear/drift-added.txt");
        Files.writeString(driftFile, "drift");

        CliRunResult check = runCli(new String[] { "check", fixture.toString(), "--project", tempDir.toString() });
        assertEquals(3, check.exitCode);
        assertFalse(Files.exists(marker));
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
}
