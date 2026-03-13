package com.bear.app;

import com.bear.kernel.target.*;
import com.bear.kernel.target.jvm.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetSeamParityTest {
    @Test
    void explicitJvmCheckMatchesRegistryRoutedCheck(@TempDir Path tempDir) throws Exception {
        Path fixture = TestRepoPaths.repoRoot().resolve("bear-ir/fixtures/withdraw.bear.yaml");

        CompileResult compileResult = BearCli.executeCompile(fixture, tempDir, null, null);
        assertEquals(0, compileResult.exitCode());
        writeWorkingWithdrawImpl(tempDir);
        writeProjectWrapper(
            tempDir,
            "@echo off\r\necho TEST_OK\r\nexit /b 0\r\n",
            "#!/usr/bin/env sh\necho TEST_OK\nexit 0\n"
        );

        CheckResult explicit = CheckCommandService.executeCheck(
            fixture,
            tempDir,
            true,
            false,
            null,
            null,
            null,
            true,
            null,
            false,
new JvmTarget()
        );
        CheckResult routed = CheckCommandService.executeCheck(
            fixture,
            tempDir,
            true,
            false,
            null,
            null,
            null,
            true,
            null,
            false
        );

        assertEquals(explicit, routed);
    }

    @Test
    void explicitJvmPrCheckMatchesRegistryRoutedPrCheck(@TempDir Path tempDir) throws Exception {
        Path repo = initGitRepo(tempDir.resolve("repo"));
        Path ir = repo.resolve("bear-ir/withdraw.bear.yaml");
        Files.createDirectories(ir.getParent());
        Files.writeString(ir, fixtureIrContent(), StandardCharsets.UTF_8);
        gitCommitAll(repo, "base ir");

        PrCheckResult explicit = PrCheckCommandService.executePrCheck(
            repo,
            "bear-ir/withdraw.bear.yaml",
            "HEAD",
            true,
            ".",
            null,
            false,
new JvmTarget()
        );
        PrCheckResult routed = PrCheckCommandService.executePrCheck(
            repo,
            "bear-ir/withdraw.bear.yaml",
            "HEAD",
            null,
            false
        );

        assertEquals(explicit.exitCode(), routed.exitCode());
        assertEquals(explicit.stdoutLines(), routed.stdoutLines());
        assertEquals(explicit.stderrLines(), routed.stderrLines());
        assertEquals(explicit.failureCode(), routed.failureCode());
        assertEquals(explicit.failurePath(), routed.failurePath());
        assertEquals(explicit.failureRemediation(), routed.failureRemediation());
        assertEquals(explicit.detail(), routed.detail());
        assertEquals(explicit.deltaLines(), routed.deltaLines());
        assertEquals(explicit.hasBoundary(), routed.hasBoundary());
        assertEquals(explicit.hasDeltas(), routed.hasDeltas());
        assertEquals(explicit.governanceLines(), routed.governanceLines());
    }

    private static String fixtureIrContent() throws Exception {
        return Files.readString(TestRepoPaths.repoRoot().resolve("bear-ir/fixtures/withdraw.bear.yaml"), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
    }

    private static void writeWorkingWithdrawImpl(Path projectRoot) throws Exception {
        Path impl = projectRoot.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        Files.writeString(impl, """
            package blocks.withdraw.impl;

            import com.bear.generated.withdraw.BearValue;
            import com.bear.generated.withdraw.LedgerPort;
            import com.bear.generated.withdraw.WithdrawLogic;
            import com.bear.generated.withdraw.Withdraw_ExecuteWithdrawRequest;
            import com.bear.generated.withdraw.Withdraw_ExecuteWithdrawResult;

            public final class WithdrawImpl implements WithdrawLogic {
              public Withdraw_ExecuteWithdrawResult executeExecuteWithdraw(
                  Withdraw_ExecuteWithdrawRequest request,
                  LedgerPort ledgerPort
              ) {
                ledgerPort.getBalance(BearValue.empty());
                return new Withdraw_ExecuteWithdrawResult(java.math.BigDecimal.ZERO);
              }
            }
            """, StandardCharsets.UTF_8);
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
        // Ensure JvmTargetDetector can detect this as a JVM project.
        if (!Files.exists(projectRoot.resolve("build.gradle"))) {
            Files.writeString(projectRoot.resolve("build.gradle"), "// test fixture\n", StandardCharsets.UTF_8);
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
}
