package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearCliValidateCommandTest {
    @Test
    void validateFixturePrintsGoldenCanonicalYaml() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("bear-ir/fixtures/withdraw.bear.yaml");
        Path golden = repoRoot.resolve("testdata/golden/withdraw.canonical.yaml");

        CliRunResult run = runCli(new String[] { "validate", fixture.toString() });
        assertEquals(0, run.exitCode);
        assertEquals("", run.stderr);
        assertEquals(normalizeLf(Files.readString(golden)), normalizeLf(run.stdout));
    }

    @Test
    void validateRequiresOneFileArg() {
        CliRunResult run = runCli(new String[] { "validate" });
        assertEquals(64, run.exitCode);
        assertTrue(run.stderr.startsWith("usage: INVALID_ARGS:"));
        assertFailureEnvelope(
            run.stderr,
            "USAGE_INVALID_ARGS",
            "cli.args",
            "Run `bear validate <file>` with exactly one IR file path."
        );
    }

    @Test
    void validateMissingFileReturnsIoExitCode() {
        CliRunResult run = runCli(new String[] { "validate", "does-not-exist.yaml" });
        assertEquals(74, run.exitCode);
        assertTrue(run.stderr.startsWith("io: IO_ERROR:"));
        assertFailureEnvelope(
            run.stderr,
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

        CliRunResult run = runCli(new String[] { "validate", invalid.toString() });
        assertEquals(2, run.exitCode);
        assertTrue(run.stderr.startsWith("schema at block: UNKNOWN_KEY:"));
        assertFailureEnvelope(
            run.stderr,
            "IR_VALIDATION",
            "block",
            "Fix the IR issue at the reported path and rerun `bear validate <file>`."
        );
    }

    @Test
    void validateInjectedInternalFailureIsEnveloped() {
        String key = "bear.cli.test.failInternal.validate";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "true");
            CliRunResult run = runCli(new String[] { "validate", "bear-ir/fixtures/withdraw.bear.yaml" });
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

    private static String normalizeLf(String text) {
        return text.replace("\r\n", "\n");
    }

    private record CliRunResult(int exitCode, String stdout, String stderr) {
    }
}
