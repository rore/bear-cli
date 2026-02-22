package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectTestRunnerTest {
    @Test
    void gradleLockDetectionMatchesKnownSignatures() {
        assertTrue(ProjectTestRunner.isGradleWrapperLockOutput("java.io.FileNotFoundException: gradle-8.12.1-bin.zip.lck (Access is denied)"));
        assertTrue(ProjectTestRunner.isGradleWrapperLockOutput("PROJECT_TEST_GRADLE_LOCK_SIMULATED"));
    }

    @Test
    void gradleBootstrapDetectionMatchesKnownSignatures() {
        assertTrue(ProjectTestRunner.isGradleWrapperBootstrapIoOutput("java.nio.file.NoSuchFileException: /tmp/gradle-8.12.1-bin.zip"));
        assertTrue(ProjectTestRunner.isGradleWrapperBootstrapIoOutput("PROJECT_TEST_GRADLE_BOOTSTRAP_SIMULATED"));
    }

    @Test
    void shortFailureHelpersAndDetailFormattingAreDeterministic() {
        String firstLine = ProjectTestRunner.firstRelevantProjectTestFailureLine("line1\nFAILURE: Build failed\nline3");
        assertEquals("FAILURE: Build failed", firstLine);
        assertEquals(
            "root-level project tests failed; line: FAILURE: Build failed; tail: tail info",
            ProjectTestRunner.projectTestDetail("root-level project tests failed", "FAILURE: Build failed", "tail info")
        );
        assertNull(ProjectTestRunner.firstGradleLockLine("no lock signature"));
    }

    @Test
    void invariantMarkerParsingIsStrictAndDeterministic() {
        String valid = "BEAR_INVARIANT_VIOLATION|block=withdraw|kind=non_negative|field=balance|observed=\\<null\\>|rule=non_negative";
        assertTrue(ProjectTestRunner.isInvariantViolationOutput("line\n" + valid + "\nline2"));
        assertEquals(valid, ProjectTestRunner.firstInvariantViolationLine("prefix: " + valid));

        String missingField = "BEAR_INVARIANT_VIOLATION|block=withdraw|kind=non_negative|observed=1|rule=non_negative";
        assertFalse(ProjectTestRunner.isInvariantViolationOutput(missingField));
        assertNull(ProjectTestRunner.firstInvariantViolationLine(missingField));

        String wrongOrder = "BEAR_INVARIANT_VIOLATION|kind=non_negative|block=withdraw|field=balance|observed=1|rule=non_negative";
        assertFalse(ProjectTestRunner.isInvariantViolationOutput(wrongOrder));

        String extraField = valid + "|x=1";
        assertFalse(ProjectTestRunner.isInvariantViolationOutput(extraField));
    }

    @Test
    void timeoutSecondsUsesPropertyWithSafeFallbacks() {
        String key = "bear.check.testTimeoutSeconds";
        String previous = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertEquals(300, ProjectTestRunner.testTimeoutSeconds());

            System.setProperty(key, "15");
            assertEquals(15, ProjectTestRunner.testTimeoutSeconds());

            System.setProperty(key, "0");
            assertEquals(300, ProjectTestRunner.testTimeoutSeconds());

            System.setProperty(key, "abc");
            assertEquals(300, ProjectTestRunner.testTimeoutSeconds());
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    void runProjectTestsRetriesIsolatedAfterSelfHealAndPasses(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("project");
        Files.createDirectories(projectRoot);
        Path partFile = projectRoot.resolve(
            ".bear-gradle-user-home/wrapper/dists/gradle-8.12.1-bin/test/gradle-8.12.1-bin.zip.part"
        );
        Files.createDirectories(partFile.getParent());
        Files.writeString(partFile, "partial");
        Files.setLastModifiedTime(partFile, FileTime.from(Instant.now().minus(Duration.ofMinutes(11))));

        String partPath = partFile.toString();
        writeProjectWrapper(
            projectRoot,
            "@echo off\r\nif exist \"" + partPath + "\" (\r\n"
                + "  echo java.nio.file.NoSuchFileException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip\r\n"
                + "  exit /b 1\r\n"
                + ")\r\n"
                + "echo TEST_OK\r\n"
                + "exit /b 0\r\n",
            "#!/usr/bin/env sh\nif [ -f \"" + partPath.replace("\\", "\\\\") + "\" ]; then\n"
                + "  echo \"java.nio.file.NoSuchFileException: /tmp/gradle-8.12.1-bin.zip\"\n"
                + "  exit 1\n"
                + "fi\n"
                + "echo TEST_OK\n"
                + "exit 0\n"
        );

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            ProjectTestResult result = ProjectTestRunner.runProjectTests(projectRoot);
            assertEquals(ProjectTestStatus.PASSED, result.status());
            if (isWindows()) {
                assertEquals("isolated,user-cache", result.attemptTrail());
                assertEquals("user-cache", result.cacheMode());
                assertTrue(result.fallbackToUserCache());
            } else {
                assertEquals("isolated,isolated-retry", result.attemptTrail());
                assertEquals("isolated", result.cacheMode());
                assertFalse(result.fallbackToUserCache());
            }
            assertFalse(Files.exists(partFile));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    void runProjectTestsFallsBackToUserCacheAfterIsolatedRetry(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("project");
        Files.createDirectories(projectRoot);
        String isolated = projectRoot.resolve(".bear-gradle-user-home").toString();

        writeProjectWrapper(
            projectRoot,
            "@echo off\r\nif /I \"%GRADLE_USER_HOME%\"==\"" + isolated + "\" (\r\n"
                + "  echo java.nio.file.NoSuchFileException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip\r\n"
                + "  exit /b 1\r\n"
                + ")\r\n"
                + "echo TEST_OK\r\n"
                + "exit /b 0\r\n",
            "#!/usr/bin/env sh\nif [ \"$GRADLE_USER_HOME\" = \"" + isolated.replace("\\", "\\\\") + "\" ]; then\n"
                + "  echo \"java.nio.file.NoSuchFileException: /tmp/gradle-8.12.1-bin.zip\"\n"
                + "  exit 1\n"
                + "fi\n"
                + "echo TEST_OK\n"
                + "exit 0\n"
        );

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "NONE");
            ProjectTestResult result = ProjectTestRunner.runProjectTests(projectRoot);
            assertEquals(ProjectTestStatus.PASSED, result.status());
            if (isWindows()) {
                assertEquals("isolated,user-cache", result.attemptTrail());
            } else {
                assertEquals("isolated,isolated-retry,user-cache", result.attemptTrail());
            }
            assertEquals("user-cache", result.cacheMode());
            assertTrue(result.fallbackToUserCache());
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    void gradleTempDeleteClassificationIsScopedToSelectedGradleHome(@TempDir Path tempDir) {
        Path gradleHome = tempDir.resolve("gradle-home");
        String selectedHome = gradleHome.toString();

        String validTmp = "Failed to delete file: " + gradleHome.resolve(".tmp/cp_settings123.tmp");
        String validGroovy = "Failed to delete file: " + gradleHome.resolve("caches/8.12.1/groovy-dsl/a/instrumented/cp_settings.tmp");
        String outsideHome = "Failed to delete file: " + tempDir.resolve("other/.tmp/cp_settings123.tmp");
        String wrongPattern = "Failed to delete file: " + gradleHome.resolve("caches/8.12.1/other/cp_settings.tmp");

        assertTrue(ProjectTestRunner.isGradleWrapperLockOutput(validTmp, selectedHome));
        assertTrue(ProjectTestRunner.isGradleWrapperLockOutput(validGroovy, selectedHome));
        assertFalse(ProjectTestRunner.isGradleWrapperLockOutput(outsideHome, selectedHome));
        assertFalse(ProjectTestRunner.isGradleWrapperLockOutput(wrongPattern, selectedHome));
    }

    @Test
    void safeSelfHealDeletesOnlyStaleKnownArtifacts(@TempDir Path tempDir) throws Exception {
        Path gradleHome = tempDir.resolve("gradle-home");
        Path stalePart = gradleHome.resolve("wrapper/dists/gradle-8.12.1-bin/x/gradle-8.12.1-bin.zip.part");
        Path freshPart = gradleHome.resolve("wrapper/dists/gradle-8.12.1-bin/x/fresh.zip.part");
        Path staleTmp = gradleHome.resolve(".tmp/cp_settings1.tmp");
        Path freshZip = gradleHome.resolve("wrapper/dists/gradle-8.12.1-bin/x/gradle-8.12.1-bin.zip");
        Path staleGroovyTmp = gradleHome.resolve("caches/8.12.1/groovy-dsl/a/instrumented/cp_settings2.tmp");

        createFile(stalePart);
        createFile(freshPart);
        createFile(staleTmp);
        createFile(freshZip);
        createFile(staleGroovyTmp);

        FileTime staleTime = FileTime.from(Instant.now().minus(Duration.ofMinutes(11)));
        Files.setLastModifiedTime(stalePart, staleTime);
        Files.setLastModifiedTime(staleTmp, staleTime);
        Files.setLastModifiedTime(staleGroovyTmp, staleTime);

        ProjectTestRunner.safeSelfHealGradleHome(gradleHome.toString());

        assertFalse(Files.exists(stalePart));
        assertFalse(Files.exists(staleTmp));
        assertFalse(Files.exists(staleGroovyTmp));
        assertTrue(Files.exists(freshPart));
        assertTrue(Files.exists(freshZip));
    }

    @Test
    void safeSelfHealSkipsFutureTimestampArtifacts(@TempDir Path tempDir) throws Exception {
        Path gradleHome = tempDir.resolve("gradle-home");
        Path futureTmp = gradleHome.resolve(".tmp/cp_settings_future.tmp");
        createFile(futureTmp);
        Files.setLastModifiedTime(futureTmp, FileTime.from(Instant.now().plus(Duration.ofMinutes(30))));

        ProjectTestRunner.safeSelfHealGradleHome(gradleHome.toString());

        assertTrue(Files.exists(futureTmp));
    }

    @Test
    void runProjectTestsWithExternalGradleHomeDoesNotFallback(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("project");
        Files.createDirectories(projectRoot);

        String key = "bear.cli.test.gradleUserHomeOverride";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, tempDir.resolve("external-gradle-home").toString());
            writeProjectWrapper(
                projectRoot,
                "@echo off\r\n"
                    + "echo java.nio.file.NoSuchFileException: C:\\\\tmp\\\\gradle-8.12.1-bin.zip\r\n"
                    + "exit /b 1\r\n",
                "#!/usr/bin/env sh\n"
                    + "echo \"java.nio.file.NoSuchFileException: /tmp/gradle-8.12.1-bin.zip\"\n"
                    + "exit 1\n"
            );

            ProjectTestResult result = ProjectTestRunner.runProjectTests(projectRoot);
            assertEquals(ProjectTestStatus.BOOTSTRAP_IO, result.status());
            assertEquals("external-env,external-env-retry", result.attemptTrail());
            assertTrue(result.firstBootstrapLine().contains("gradle-8.12.1-bin.zip"));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    private static void writeProjectWrapper(Path projectRoot, String windowsContent, String unixContent) throws Exception {
        Path wrapper = projectRoot.resolve(isWindows() ? "gradlew.bat" : "gradlew");
        Files.writeString(wrapper, isWindows() ? windowsContent : unixContent);
        if (!isWindows()) {
            try {
                Files.setPosixFilePermissions(
                    wrapper,
                    Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE
                    )
                );
            } catch (UnsupportedOperationException ignored) {
                // Filesystem does not support POSIX perms.
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static void createFile(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }
}
