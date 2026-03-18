package com.bear.kernel.target.python;

import com.bear.kernel.target.ProjectTestResult;
import com.bear.kernel.target.ProjectTestStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Runs mypy --strict on governed Python source roots as the project verification step.
 * Tool preference: uv first (preferred, faster), then poetry fallback.
 */
public final class PythonProjectVerificationRunner {
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final String MYPY_MISSING_MODULE = "No module named 'mypy'";
    private static final String MODULE_NOT_FOUND_ERROR = "ModuleNotFoundError";

    private PythonProjectVerificationRunner() {
    }

    /**
     * Runs mypy --strict on src/blocks/ using uv or poetry.
     *
     * @param projectRoot the project root directory
     * @return ProjectTestResult with status and output
     * @throws IOException if process creation fails
     * @throws InterruptedException if the process is interrupted
     */
    public static ProjectTestResult run(Path projectRoot) throws IOException, InterruptedException {
        String tool = findTool();
        if (tool == null) {
            return toolMissing("Neither uv nor poetry found on PATH");
        }

        List<String> command = List.of(tool, "run", "mypy", "src/blocks/", "--strict");
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);

        long startTime = System.currentTimeMillis();
        Process process = pb.start();

        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        Thread outputReader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                in.transferTo(outputBuffer);
            } catch (IOException ignored) {
                // Best-effort capture only
            }
        }, "bear-python-verification-output-reader");
        outputReader.setDaemon(true);
        outputReader.start();

        boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        long durationMs = System.currentTimeMillis() - startTime;

        if (!finished) {
            destroyProcess(process);
            process.waitFor(5, TimeUnit.SECONDS);
            joinOutputReader(outputReader);
            String output = outputBuffer.toString(StandardCharsets.UTF_8);
            return timeout(output, durationMs);
        }

        joinOutputReader(outputReader);
        String output = outputBuffer.toString(StandardCharsets.UTF_8);
        int exitCode = process.exitValue();

        if (exitCode == 0) {
            return passed(output, durationMs);
        }

        // Check for mypy not installed
        if (isMypyMissing(output)) {
            return toolMissing(output);
        }

        return failed(output, durationMs);
    }

    /**
     * Finds the first available tool (uv or poetry) on the system PATH.
     *
     * @return "uv" or "poetry" if found, null otherwise
     */
    static String findTool() {
        if (isToolAvailable("uv")) {
            return "uv";
        }
        if (isToolAvailable("poetry")) {
            return "poetry";
        }
        return null;
    }

    private static boolean isToolAvailable(String tool) {
        try {
            List<String> command = isWindows()
                ? List.of("where", tool)
                : List.of("which", tool);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase(Locale.ROOT).contains("windows");
    }

    private static boolean isMypyMissing(String output) {
        if (output == null) {
            return false;
        }
        return output.contains(MYPY_MISSING_MODULE) || output.contains(MODULE_NOT_FOUND_ERROR);
    }

    private static void destroyProcess(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.toHandle().descendants().forEach(child -> {
                if (child.isAlive()) {
                    child.destroyForcibly();
                }
            });
        } catch (Exception ignored) {
            // Best effort
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private static void joinOutputReader(Thread outputReader) throws InterruptedException {
        if (outputReader != null) {
            outputReader.join(2000L);
        }
    }

    private static ProjectTestResult passed(String output, long durationMs) {
        return new ProjectTestResult(
            ProjectTestStatus.PASSED,
            output,
            null,  // attemptTrail
            null,  // firstLockLine
            null,  // firstBootstrapLine
            null,  // firstSharedDepsViolationLine
            null,  // cacheMode
            false, // fallbackToUserCache
            "mypy", // phase
            null   // lastObservedTask
        );
    }

    private static ProjectTestResult failed(String output, long durationMs) {
        return new ProjectTestResult(
            ProjectTestStatus.FAILED,
            output,
            null,
            null,
            null,
            null,
            null,
            false,
            "mypy",
            null
        );
    }

    private static ProjectTestResult timeout(String output, long durationMs) {
        return new ProjectTestResult(
            ProjectTestStatus.TIMEOUT,
            output,
            null,
            null,
            null,
            null,
            null,
            false,
            "mypy",
            null
        );
    }

    private static ProjectTestResult toolMissing(String output) {
        return new ProjectTestResult(
            ProjectTestStatus.BOOTSTRAP_IO,
            output,
            null,
            null,
            output,  // firstBootstrapLine - use output as the bootstrap message
            null,
            null,
            false,
            "mypy",
            null
        );
    }
}
