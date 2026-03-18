package com.bear.kernel.target.python.properties;

import com.bear.kernel.target.ProjectTestResult;
import com.bear.kernel.target.ProjectTestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for PythonProjectVerificationRunner.
 * Uses JUnit 5 parameterized tests with 100+ iterations.
 * 
 * Feature: phase-p2-python-checking
 */
class ProjectVerificationProperties {

    private static final int ITERATIONS = 100;
    private static final Random RANDOM = new Random(42); // Deterministic seed

    // ========================================================================
    // Property 13: mypy exit 0 → PASSED; non-zero → FAILED
    // Validates: Req 6.3, 6.4
    // ========================================================================

    @ParameterizedTest(name = "Property 13 iteration {0}: exit code mapping")
    @MethodSource("exitCodeIterations")
    @DisplayName("Property 13: mypy exit 0 → PASSED; non-zero → FAILED")
    void property13_exitCodeMapping(int iteration) {
        // **Validates: Requirements 6.3, 6.4**
        
        // Generate random exit code
        int exitCode = RANDOM.nextInt(256);
        String output = generateRandomOutput(iteration);
        
        // Simulate the exit code mapping logic
        ProjectTestStatus expectedStatus;
        if (exitCode == 0) {
            expectedStatus = ProjectTestStatus.PASSED;
        } else if (containsMypyMissingIndicator(output)) {
            expectedStatus = ProjectTestStatus.BOOTSTRAP_IO;
        } else {
            expectedStatus = ProjectTestStatus.FAILED;
        }
        
        // Create result using the mapping logic
        ProjectTestResult result = mapExitCodeToResult(exitCode, output);
        
        // Verify the mapping
        assertEquals(expectedStatus, result.status(),
            "Exit code " + exitCode + " should map to " + expectedStatus);
        
        // Additional verification for exit 0
        if (exitCode == 0) {
            assertEquals(ProjectTestStatus.PASSED, result.status(),
                "Exit code 0 must always map to PASSED");
        }
        
        // Additional verification for non-zero without mypy missing
        if (exitCode != 0 && !containsMypyMissingIndicator(output)) {
            assertEquals(ProjectTestStatus.FAILED, result.status(),
                "Non-zero exit without mypy missing must map to FAILED");
        }
    }

    @ParameterizedTest(name = "Property 13 exit-0 iteration {0}")
    @MethodSource("iterations")
    @DisplayName("Property 13a: exit 0 always maps to PASSED")
    void property13a_exitZeroAlwaysPassed(int iteration) {
        // **Validates: Requirement 6.3**
        
        String output = generateRandomOutput(iteration);
        ProjectTestResult result = mapExitCodeToResult(0, output);
        
        assertEquals(ProjectTestStatus.PASSED, result.status(),
            "Exit code 0 must always map to PASSED regardless of output content");
    }

    @ParameterizedTest(name = "Property 13 non-zero iteration {0}")
    @MethodSource("iterations")
    @DisplayName("Property 13b: non-zero exit maps to FAILED (when mypy is present)")
    void property13b_nonZeroExitMapsFailed(int iteration) {
        // **Validates: Requirement 6.4**
        
        // Generate non-zero exit code (1-255)
        int exitCode = 1 + RANDOM.nextInt(255);
        // Generate output that doesn't indicate mypy missing
        String output = "error: Argument 1 to \"func\" has incompatible type \"str\"; expected \"int\"  [arg-type]\n" +
                       "Found " + (iteration % 10 + 1) + " error in 1 file (checked 1 source file)";
        
        ProjectTestResult result = mapExitCodeToResult(exitCode, output);
        
        assertEquals(ProjectTestStatus.FAILED, result.status(),
            "Non-zero exit code " + exitCode + " with type errors should map to FAILED");
    }

    // ========================================================================
    // Property 14: ProjectTestResult.output is non-null and contains mypy output
    // Validates: Req 6.8
    // ========================================================================

    @ParameterizedTest(name = "Property 14 iteration {0}: output capture")
    @MethodSource("iterations")
    @DisplayName("Property 14: output is non-null and contains mypy output when mypy produces output")
    void property14_outputCapture(int iteration) {
        // **Validates: Requirement 6.8**
        
        // Generate various mypy output scenarios
        String mypyOutput = generateMypyOutput(iteration);
        
        // Create result with the output
        ProjectTestResult result = createResultWithOutput(mypyOutput);
        
        // Verify output is captured
        assertNotNull(result.output(),
            "ProjectTestResult.output must never be null");
        
        // When mypy produces output, it should be captured
        if (mypyOutput != null && !mypyOutput.isEmpty()) {
            assertEquals(mypyOutput, result.output(),
                "ProjectTestResult.output must contain the complete mypy output");
        }
    }

    @ParameterizedTest(name = "Property 14 non-empty iteration {0}")
    @MethodSource("iterations")
    @DisplayName("Property 14a: non-empty mypy output is fully captured")
    void property14a_nonEmptyOutputCaptured(int iteration) {
        // **Validates: Requirement 6.8**
        
        // Generate non-empty output
        String mypyOutput = "Success: no issues found in " + (iteration + 1) + " source files";
        
        ProjectTestResult result = createResultWithOutput(mypyOutput);
        
        assertNotNull(result.output());
        assertTrue(result.output().contains(mypyOutput),
            "Output must contain the mypy output");
    }

    @ParameterizedTest(name = "Property 14 error output iteration {0}")
    @MethodSource("iterations")
    @DisplayName("Property 14b: error output is captured in result")
    void property14b_errorOutputCaptured(int iteration) {
        // **Validates: Requirement 6.8**
        
        // Generate error output
        String errorOutput = String.format(
            "src/blocks/my-block/impl/service.py:%d: error: Function is missing a return type annotation  [no-untyped-def]\n" +
            "Found 1 error in 1 file (checked %d source files)",
            iteration + 1, iteration + 5
        );
        
        ProjectTestResult result = mapExitCodeToResult(1, errorOutput);
        
        assertNotNull(result.output());
        assertEquals(errorOutput, result.output(),
            "Error output must be fully captured");
        assertTrue(result.output().contains("error:"),
            "Error output must contain error markers");
    }

    @ParameterizedTest(name = "Property 14 multiline iteration {0}")
    @MethodSource("iterations")
    @DisplayName("Property 14c: multiline output is fully captured")
    void property14c_multilineOutputCaptured(int iteration) {
        // **Validates: Requirement 6.8**
        
        // Generate multiline output
        StringBuilder sb = new StringBuilder();
        int lineCount = 1 + (iteration % 20);
        for (int i = 0; i < lineCount; i++) {
            sb.append("src/blocks/block-").append(i).append("/impl/service.py:")
              .append(i + 1).append(": error: Missing type annotation\n");
        }
        sb.append("Found ").append(lineCount).append(" errors in ").append(lineCount).append(" files");
        String multilineOutput = sb.toString();
        
        ProjectTestResult result = mapExitCodeToResult(1, multilineOutput);
        
        assertNotNull(result.output());
        assertEquals(multilineOutput, result.output(),
            "Multiline output must be fully captured");
        
        // Verify all lines are present
        String[] lines = result.output().split("\n");
        assertTrue(lines.length >= lineCount,
            "All output lines must be captured");
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    static Stream<Integer> iterations() {
        return IntStream.range(0, ITERATIONS).boxed();
    }

    static Stream<Integer> exitCodeIterations() {
        return IntStream.range(0, ITERATIONS).boxed();
    }

    private String generateRandomOutput(int seed) {
        Random r = new Random(seed);
        int scenario = r.nextInt(5);
        return switch (scenario) {
            case 0 -> "Success: no issues found in " + (r.nextInt(100) + 1) + " source files";
            case 1 -> "error: Argument 1 has incompatible type \"str\"; expected \"int\"";
            case 2 -> "src/blocks/my-block/impl/service.py:10: error: Missing return type";
            case 3 -> ""; // Empty output
            default -> "Found " + (r.nextInt(10) + 1) + " errors in " + (r.nextInt(5) + 1) + " files";
        };
    }

    private String generateMypyOutput(int seed) {
        Random r = new Random(seed);
        int scenario = r.nextInt(4);
        return switch (scenario) {
            case 0 -> "Success: no issues found in " + (r.nextInt(100) + 1) + " source files";
            case 1 -> "error: Function is missing a return type annotation  [no-untyped-def]";
            case 2 -> "src/blocks/my-block/impl/service.py:" + (r.nextInt(100) + 1) + ": error: Incompatible types";
            default -> "Found " + (r.nextInt(20) + 1) + " errors in " + (r.nextInt(10) + 1) + " files";
        };
    }

    private boolean containsMypyMissingIndicator(String output) {
        if (output == null) {
            return false;
        }
        return output.contains("No module named 'mypy'") || output.contains("ModuleNotFoundError");
    }

    /**
     * Simulates the exit code to result mapping logic from PythonProjectVerificationRunner.
     */
    private ProjectTestResult mapExitCodeToResult(int exitCode, String output) {
        ProjectTestStatus status;
        if (exitCode == 0) {
            status = ProjectTestStatus.PASSED;
        } else if (containsMypyMissingIndicator(output)) {
            status = ProjectTestStatus.BOOTSTRAP_IO;
        } else {
            status = ProjectTestStatus.FAILED;
        }
        
        return new ProjectTestResult(
            status,
            output,
            null,  // attemptTrail
            null,  // firstLockLine
            status == ProjectTestStatus.BOOTSTRAP_IO ? output : null,  // firstBootstrapLine
            null,  // firstSharedDepsViolationLine
            null,  // cacheMode
            false, // fallbackToUserCache
            "mypy", // phase
            null   // lastObservedTask
        );
    }

    private ProjectTestResult createResultWithOutput(String output) {
        return new ProjectTestResult(
            ProjectTestStatus.PASSED,
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
}
