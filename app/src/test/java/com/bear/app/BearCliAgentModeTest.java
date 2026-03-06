package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearCliAgentModeTest {
    @Test
    void checkAgentModeWritesJsonOnlyToStdoutOnDeterministicFailure(@TempDir Path tempDir) {
        CliRunResult run = runCli(new String[] {
            "check",
            "missing-file.bear.yaml",
            "--project",
            tempDir.toString(),
            "--agent"
        });

        assertEquals(CliCodes.EXIT_IO, run.exitCode());
        assertTrue(run.stdout().startsWith("{\"schemaVersion\":\"bear.nextAction.v1\""));
        String rerun = AgentCommandContextTestSupport.firstRerunCommand(run.stdout());
        AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
        AgentCommandContext expected = AgentCommandContext.forCheckSingle(
            Path.of("missing-file.bear.yaml"),
            tempDir,
            null,
            false,
            false,
            true
        );
        AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
        assertEquals("", run.stderr());
    }

    @Test
    void checkAgentModePreservesCollectAllInRerunCommand(@TempDir Path tempDir) {
        CliRunResult run = runCli(new String[] {
            "check",
            "missing-file.bear.yaml",
            "--project",
            tempDir.toString(),
            "--collect=all",
            "--agent"
        });

        assertEquals(CliCodes.EXIT_IO, run.exitCode());
        String rerun = AgentCommandContextTestSupport.firstRerunCommand(run.stdout());
        AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
        AgentCommandContext expected = AgentCommandContext.forCheckSingle(
            Path.of("missing-file.bear.yaml"),
            tempDir,
            null,
            false,
            true,
            true
        );
        AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
        assertEquals("", run.stderr());
    }

    @Test
    void checkAllAgentModeMissingIndexEmitsJsonWithDeterministicNextAction(@TempDir Path tempDir) {
        CliRunResult run = runCli(new String[] {
            "check",
            "--all",
            "--project",
            tempDir.toString(),
            "--agent"
        });

        assertEquals(CliCodes.EXIT_VALIDATION, run.exitCode());
        assertTrue(run.stdout().startsWith("{\"schemaVersion\":\"bear.nextAction.v1\""));
        assertTrue(run.stdout().contains("\"failureCode\":\"INDEX_REQUIRED_MISSING\""), run.stdout());
        assertTrue(run.stdout().contains("\"reasonKey\":\"INDEX_REQUIRED_MISSING\""), run.stdout());
        assertTrue(run.stdout().contains("\"title\":\"Satisfy index preflight before --all gates\""), run.stdout());

        String rerun = AgentCommandContextTestSupport.firstRerunCommand(run.stdout());
        AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
        AgentCommandContext expected = AgentCommandContext.forCheckAll(
            new AllCheckOptions(
                tempDir.toAbsolutePath().normalize(),
                tempDir.resolve("bear.blocks.yaml").toAbsolutePath().normalize(),
                Set.of(),
                false,
                false,
                false,
                false,
                true
            )
        );
        AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
        assertEquals("", run.stderr());
    }

    @Test
    void prCheckAllAgentModeMissingIndexEmitsJsonWithDeterministicNextAction(@TempDir Path tempDir) {
        CliRunResult run = runCli(new String[] {
            "pr-check",
            "--all",
            "--project",
            tempDir.toString(),
            "--base",
            "HEAD",
            "--agent"
        });

        assertEquals(CliCodes.EXIT_VALIDATION, run.exitCode());
        assertTrue(run.stdout().startsWith("{\"schemaVersion\":\"bear.nextAction.v1\""));
        assertTrue(run.stdout().contains("\"failureCode\":\"INDEX_REQUIRED_MISSING\""), run.stdout());
        assertTrue(run.stdout().contains("\"reasonKey\":\"INDEX_REQUIRED_MISSING\""), run.stdout());
        assertTrue(run.stdout().contains("\"title\":\"Satisfy index preflight before --all gates\""), run.stdout());

        String rerun = AgentCommandContextTestSupport.firstRerunCommand(run.stdout());
        AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
        AgentCommandContext expected = AgentCommandContext.forPrCheckAll(
            new AllPrCheckOptions(
                tempDir.toAbsolutePath().normalize(),
                tempDir.resolve("bear.blocks.yaml").toAbsolutePath().normalize(),
                Set.of(),
                false,
                "HEAD",
                false,
                true
            )
        );
        AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
        assertEquals("", run.stderr());
    }
    @Test
    void checkAgentModeEmitsProjectTestLockReasonKey(@TempDir Path tempDir) throws Exception {
        Path fixture = TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode());
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho java.io.FileNotFoundException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip.lck (Access is denied)\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"java.io.FileNotFoundException: /tmp/gradle-8.12.1-bin.zip.lck (Access is denied)\"\nexit 1\n"
        );

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            CliRunResult run = runCli(new String[] {
                "check",
                fixture.toString(),
                "--project",
                tempDir.toString(),
                "--collect=all",
                "--agent"
            });

            assertEquals(CliCodes.EXIT_IO, run.exitCode());
            assertTrue(run.stdout().contains("\"reasonKey\":\"PROJECT_TEST_LOCK\""), run.stdout());
            assertTrue(run.stdout().contains("\"title\":\"Clear blocked check marker and retry\""));
            assertTrue(run.stdout().contains("bear unblock"));
            String rerun = AgentCommandContextTestSupport.firstRerunCommand(run.stdout());
            AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
            AgentCommandContext expected = AgentCommandContext.forCheckSingle(
                fixture,
                tempDir,
                null,
                false,
                true,
                true
            );
            AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
            assertEquals("", run.stderr());
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void prCheckAgentModeWritesJsonOnlyToStdoutOutsideGitRepo(@TempDir Path tempDir) throws Exception {
        Path fixture = TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml");
        Path ir = tempDir.resolve("withdraw.bear.yaml");
        Files.copy(fixture, ir);

        CliRunResult run = runCli(new String[] {
            "pr-check",
            "withdraw.bear.yaml",
            "--project",
            tempDir.toString(),
            "--base",
            "HEAD",
            "--agent"
        });

        assertEquals(CliCodes.EXIT_IO, run.exitCode());
        assertTrue(run.stdout().startsWith("{\"schemaVersion\":\"bear.nextAction.v1\""));
        assertTrue(run.stdout().contains("\"reasonKey\":\"NOT_A_GIT_REPO\""));
        assertEquals("", run.stderr());
    }

    @Test
    void prCheckAgentModeEmitsMergeBaseFailedReasonKey(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("spec/withdraw.bear.yaml");
        Files.createDirectories(ir.getParent());
        Files.writeString(
            ir,
            Files.readString(TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml"), StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );
        gitCommitAll(repo, "base ir");

        CliRunResult run = runCli(new String[] {
            "pr-check",
            "spec/withdraw.bear.yaml",
            "--project",
            repo.toString(),
            "--base",
            "origin/does-not-exist",
            "--collect=all",
            "--agent"
        });

        assertEquals(CliCodes.EXIT_IO, run.exitCode());
        assertTrue(run.stdout().contains("\"reasonKey\":\"MERGE_BASE_FAILED\""), run.stdout());
        assertTrue(run.stdout().contains("\"title\":\"Capture base-resolution diagnostics and escalate\""));
        String rerun = AgentCommandContextTestSupport.firstRerunCommand(run.stdout());
        AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
        AgentCommandContext expected = AgentCommandContext.forPrCheckSingle(
            "spec/withdraw.bear.yaml",
            repo,
            "origin/does-not-exist",
            null,
            true,
            true
        );
        AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
        assertEquals("", run.stderr());
    }

    @Test
    void checkAgentModeEmitsProjectTestBootstrapReasonKey(@TempDir Path tempDir) throws Exception {
        Path fixture = TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode());
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho unable to unzip gradle-8.12.1-bin.zip\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"unable to unzip gradle-8.12.1-bin.zip\"\nexit 1\n"
        );

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            CliRunResult run = runCli(new String[] {
                "check",
                fixture.toString(),
                "--project",
                tempDir.toString(),
                "--collect=all",
                "--agent"
            });

            assertEquals(CliCodes.EXIT_IO, run.exitCode());
            assertTrue(run.stdout().contains("\"reasonKey\":\"PROJECT_TEST_BOOTSTRAP\""), run.stdout());
            String rerun = AgentCommandContextTestSupport.firstRerunCommand(run.stdout());
            AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
            AgentCommandContext expected = AgentCommandContext.forCheckSingle(
                fixture,
                tempDir,
                null,
                false,
                true,
                true
            );
            AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
            assertEquals("", run.stderr());
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void checkAgentModeEmitsContainmentMetadataMismatchReasonKey(@TempDir Path tempDir) throws Exception {
        Path fixture = TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode());
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho ^> Task :compileBearImpl__shared FAILED\r\necho error: illegal character: '\\u0000'\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"> Task :compileBearImpl__shared FAILED\"\necho \"error: illegal character: '\\\\u0000'\"\nexit 1\n"
        );

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            CliRunResult run = runCli(new String[] {
                "check",
                fixture.toString(),
                "--project",
                tempDir.toString(),
                "--collect=all",
                "--agent"
            });

            assertEquals(CliCodes.EXIT_TEST_FAILURE, run.exitCode());
            assertTrue(run.stdout().contains("\"failureCode\":\"CONTAINMENT_NOT_VERIFIED\""), run.stdout());
            assertTrue(run.stdout().contains("\"reasonKey\":\"CONTAINMENT_METADATA_MISMATCH\""), run.stdout());
            assertTrue(run.stdout().contains("\"title\":\"Apply bounded containment repair\""), run.stdout());
            assertTrue(run.stdout().contains("bear compile --all --project " + tempDir.toString().replace('\\', '/')), run.stdout());
            String rerun = AgentCommandContextTestSupport.firstRerunCommand(run.stdout());
            AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
            AgentCommandContext expected = AgentCommandContext.forCheckSingle(
                fixture,
                tempDir,
                null,
                false,
                true,
                true
            );
            AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
            assertEquals("", run.stderr());
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void checkAgentModeKeepsGenericCompileFailureWhenContainmentSignalsAreAbsent(@TempDir Path tempDir) throws Exception {
        Path fixture = TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode());
        writeWorkingWithdrawImpl(tempDir);

        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho ^> Task :compileJava FAILED\r\necho error: illegal character: '\\u0000'\r\nexit /b 1\r\n",
            "#!/usr/bin/env sh\necho \"> Task :compileJava FAILED\"\necho \"error: illegal character: '\\\\u0000'\"\nexit 1\n"
        );

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            CliRunResult run = runCli(new String[] {
                "check",
                fixture.toString(),
                "--project",
                tempDir.toString(),
                "--collect=all",
                "--agent"
            });

            assertEquals(CliCodes.EXIT_TEST_FAILURE, run.exitCode());
            assertTrue(run.stdout().contains("\"failureCode\":\"COMPILE_FAILURE\""), run.stdout());
            assertTrue(!run.stdout().contains("\"reasonKey\":\"CONTAINMENT_METADATA_MISMATCH\""), run.stdout());
            assertEquals("", run.stderr());
        } finally {
            restoreSystemProperty(key, previous);
        }
    }

    @Test
    void prCheckAgentModeEmitsReadHeadFailedReasonKey(@TempDir Path tempDir) {
        CliRunResult run = runCli(new String[] {
            "pr-check",
            "spec/missing.bear.yaml",
            "--project",
            tempDir.toString(),
            "--base",
            "HEAD",
            "--collect=all",
            "--agent"
        });

        assertEquals(CliCodes.EXIT_IO, run.exitCode());
        assertTrue(run.stdout().contains("\"reasonKey\":\"READ_HEAD_FAILED\""), run.stdout());
        String rerun = AgentCommandContextTestSupport.firstRerunCommand(run.stdout());
        AgentCommandContext reparsed = AgentCommandContextTestSupport.parseCommandContext(rerun);
        AgentCommandContext expected = AgentCommandContext.forPrCheckSingle(
            "spec/missing.bear.yaml",
            tempDir,
            "HEAD",
            null,
            true,
            true
        );
        AgentCommandContextTestSupport.assertEquivalent(expected, reparsed);
        assertEquals("", run.stderr());
    }

    @Test
    void documentedExactInfraReasonKeysAreReachableFromOriginSites(@TempDir Path tempDir) throws Exception {
        java.util.Set<String> observed = new java.util.HashSet<>();

        Path fixture = TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml");
        assertEquals(0, runCli(new String[] { "compile", fixture.toString(), "--project", tempDir.toString() }).exitCode());
        writeWorkingWithdrawImpl(tempDir);

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            writeProjectWrapper(
                tempDir,
                "@echo off\r\necho java.io.FileNotFoundException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip.lck (Access is denied)\r\nexit /b 1\r\n",
                "#!/usr/bin/env sh\necho \"java.io.FileNotFoundException: /tmp/gradle-8.12.1-bin.zip.lck (Access is denied)\"\nexit 1\n"
            );
            observed.addAll(extractReasonKeys(runCli(new String[] {
                "check", fixture.toString(), "--project", tempDir.toString(), "--collect=all", "--agent"
            }).stdout()));

            writeProjectWrapper(
                tempDir,
                "@echo off\r\necho unable to unzip gradle-8.12.1-bin.zip\r\nexit /b 1\r\n",
                "#!/usr/bin/env sh\necho \"unable to unzip gradle-8.12.1-bin.zip\"\nexit 1\n"
            );
            observed.addAll(extractReasonKeys(runCli(new String[] {
                "check", fixture.toString(), "--project", tempDir.toString(), "--collect=all", "--agent"
            }).stdout()));

            writeProjectWrapper(
                tempDir,
                "@echo off\r\necho ^> Task :compileBearImpl__shared FAILED\r\necho error: illegal character: '\\u0000'\r\nexit /b 1\r\n",
                "#!/usr/bin/env sh\necho \"> Task :compileBearImpl__shared FAILED\"\necho \"error: illegal character: '\\\\u0000'\"\nexit 1\n"
            );
            observed.addAll(extractReasonKeys(runCli(new String[] {
                "check", fixture.toString(), "--project", tempDir.toString(), "--collect=all", "--agent"
            }).stdout()));
        } finally {
            restoreSystemProperty(key, previous);
        }

        Path nonGitIr = tempDir.resolve("withdraw.bear.yaml");
        Files.writeString(nonGitIr, Files.readString(fixture, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        observed.addAll(extractReasonKeys(runCli(new String[] {
            "pr-check", "withdraw.bear.yaml", "--project", tempDir.toString(), "--base", "HEAD", "--agent"
        }).stdout()));

        Path repo = initGitRepo(tempDir.resolve("repo-keys"));
        Path ir = repo.resolve("spec/withdraw.bear.yaml");
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, Files.readString(fixture, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        gitCommitAll(repo, "base ir");
        observed.addAll(extractReasonKeys(runCli(new String[] {
            "pr-check", "spec/withdraw.bear.yaml", "--project", repo.toString(), "--base", "origin/does-not-exist", "--agent"
        }).stdout()));

        observed.addAll(extractReasonKeys(runCli(new String[] {
            "pr-check", "spec/missing.bear.yaml", "--project", tempDir.toString(), "--base", "HEAD", "--agent"
        }).stdout()));

        assertTrue(observed.containsAll(AgentTemplateRegistry.exactInfraQualifiers()),
            "Missing reason key coverage: expected=" + AgentTemplateRegistry.exactInfraQualifiers() + " observed=" + observed);
    }
    @Test
    void checkRejectsUnsupportedCollectValue() {
        CliRunResult run = runCli(new String[] {
            "check",
            "missing-file.bear.yaml",
            "--project",
            ".",
            "--collect=foo"
        });

        assertEquals(CliCodes.EXIT_USAGE, run.exitCode());
        assertTrue(run.stderr().contains("unsupported value for --collect"));
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
            + "\n"
            + "public final class WithdrawImpl implements WithdrawLogic {\n"
            + "  public Withdraw_ExecuteWithdrawResult executeExecuteWithdraw(Withdraw_ExecuteWithdrawRequest request, LedgerPort ledgerPort) {\n"
            + "    ledgerPort.getBalance(BearValue.empty());\n"
            + "    return new Withdraw_ExecuteWithdrawResult(java.math.BigDecimal.ZERO);\n"
            + "  }\n"
            + "}\n";
        Files.writeString(impl, source, StandardCharsets.UTF_8);
    }

    private static void writeProjectWrapper(Path projectRoot, String windowsContent, String unixContent) throws Exception {
        Path wrapper = projectRoot.resolve(isWindows() ? "gradlew.bat" : "gradlew");
        String content = isWindows() ? windowsContent : unixContent;
        Files.writeString(wrapper, content, StandardCharsets.UTF_8);
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
            wrapper.toFile().setExecutable(true, false);
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

    private static java.util.Set<String> extractReasonKeys(String json) {
        java.util.HashSet<String> out = new java.util.HashSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"reasonKey\\\":\\\"([^\\\"]+)\\\"").matcher(json == null ? "" : json);
        while (matcher.find()) {
            out.add(matcher.group(1));
        }
        return java.util.Set.copyOf(out);
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

    private record CliRunResult(int exitCode, String stdout, String stderr) {
    }
}

