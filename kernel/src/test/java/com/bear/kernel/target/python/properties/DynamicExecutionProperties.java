package com.bear.kernel.target.python.properties;

import com.bear.kernel.target.UndeclaredReachFinding;
import com.bear.kernel.target.WiringManifest;
import com.bear.kernel.target.python.PythonDynamicExecutionScanner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for PythonDynamicExecutionScanner.
 * Feature: phase-p2-python-checking, Property 11
 * 
 * Uses plain JUnit 5 parameterized tests with 100+ iterations.
 */
class DynamicExecutionProperties {

    private static final List<String> ESCAPE_HATCHES = List.of("eval", "exec", "compile");

    // ========== Property 11: Dynamic execution escape hatch detection ==========
    // **Validates: Requirements 4.1, 4.2, 4.3**
    // Any governed file with direct eval()/exec()/compile() call → at least one finding with matching surface

    static Stream<Integer> property11Iterations() {
        return IntStream.range(0, 110).boxed();
    }

    @ParameterizedTest(name = "Property 11 - iteration {0}: escape hatch call detected")
    @MethodSource("property11Iterations")
    void property11_escapeHatchCallDetected(int iteration, @TempDir Path tempDir) throws IOException {
        // Pick a random escape hatch
        String escapeHatch = ESCAPE_HATCHES.get(iteration % ESCAPE_HATCHES.size());
        
        // Generate code with escape hatch call
        String code = generateEscapeHatchCode(escapeHatch, iteration);
        
        setupGovernedBlock(tempDir, "block-" + iteration, code);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        assertFalse(findings.isEmpty(), 
            "Escape hatch '" + escapeHatch + "()' should produce at least one finding");
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals(escapeHatch)),
            "Finding surface should match escape hatch '" + escapeHatch + "'");
    }

    @ParameterizedTest(name = "Property 11 - multiple calls iteration {0}: all escape hatches detected")
    @MethodSource("property11Iterations")
    void property11_multipleEscapeHatchCallsDetected(int iteration, @TempDir Path tempDir) throws IOException {
        // Generate code with multiple escape hatch calls
        StringBuilder code = new StringBuilder();
        List<String> usedHatches = new ArrayList<>();
        
        int numCalls = (iteration % 3) + 1; // 1-3 calls
        for (int i = 0; i < numCalls; i++) {
            String escapeHatch = ESCAPE_HATCHES.get((iteration + i) % ESCAPE_HATCHES.size());
            if (!usedHatches.contains(escapeHatch)) {
                usedHatches.add(escapeHatch);
                code.append(generateEscapeHatchCode(escapeHatch, iteration + i));
            }
        }
        
        setupGovernedBlock(tempDir, "block-" + iteration, code.toString());

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        // Should have at least one finding for each unique escape hatch used
        for (String hatch : usedHatches) {
            assertTrue(findings.stream().anyMatch(f -> f.surface().equals(hatch)),
                "Should have finding for escape hatch '" + hatch + "'");
        }
    }

    @ParameterizedTest(name = "Property 11 - variable name iteration {0}: escape hatch as variable name → no finding")
    @MethodSource("property11Iterations")
    void property11_escapeHatchAsVariableNameNoFinding(int iteration, @TempDir Path tempDir) throws IOException {
        // Pick a random escape hatch
        String escapeHatch = ESCAPE_HATCHES.get(iteration % ESCAPE_HATCHES.size());
        
        // Generate code where escape hatch is used as variable name (not a call)
        String code = String.format("""
            %s = "some value"
            print(%s)
            x = %s
            """, escapeHatch, escapeHatch, escapeHatch);
        
        setupGovernedBlock(tempDir, "block-" + iteration, code);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        assertTrue(findings.isEmpty(), 
            "Escape hatch '" + escapeHatch + "' as variable name should produce no findings");
    }

    @ParameterizedTest(name = "Property 11 - TYPE_CHECKING iteration {0}: escape hatch in TYPE_CHECKING → no finding")
    @MethodSource("property11Iterations")
    void property11_escapeHatchInTypeCheckingNoFinding(int iteration, @TempDir Path tempDir) throws IOException {
        // Pick a random escape hatch
        String escapeHatch = ESCAPE_HATCHES.get(iteration % ESCAPE_HATCHES.size());
        
        // Generate code with escape hatch inside TYPE_CHECKING block
        String code = String.format("""
            from typing import TYPE_CHECKING
            
            if TYPE_CHECKING:
                result = %s("1 + 1")
            
            def my_function():
                pass
            """, escapeHatch);
        
        setupGovernedBlock(tempDir, "block-" + iteration, code);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        assertTrue(findings.isEmpty(), 
            "Escape hatch '" + escapeHatch + "()' inside TYPE_CHECKING should produce no findings");
    }

    @ParameterizedTest(name = "Property 11 - test file iteration {0}: escape hatch in test file → no finding")
    @MethodSource("property11Iterations")
    void property11_escapeHatchInTestFileNoFinding(int iteration, @TempDir Path tempDir) throws IOException {
        // Pick a random escape hatch
        String escapeHatch = ESCAPE_HATCHES.get(iteration % ESCAPE_HATCHES.size());
        
        String blockKey = "block-" + iteration;
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        
        // Create test file with escape hatch (should be excluded)
        String testFileName = (iteration % 2 == 0) ? "test_service.py" : "service_test.py";
        Files.writeString(blockRoot.resolve(testFileName), 
            String.format("result = %s('1 + 1')\n", escapeHatch));
        
        // Create clean governed file
        Files.writeString(blockRoot.resolve("service.py"), "# clean\n");

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest(blockKey)));

        assertTrue(findings.isEmpty(), 
            "Escape hatch '" + escapeHatch + "()' in test file should produce no findings");
    }

    @ParameterizedTest(name = "Property 11 - sorted iteration {0}: findings sorted by path then surface")
    @MethodSource("property11Iterations")
    void property11_findingsSortedByPathThenSurface(int iteration, @TempDir Path tempDir) throws IOException {
        String blockKey = "block-" + iteration;
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        
        // Create multiple files with violations in random order
        int numFiles = (iteration % 3) + 2; // 2-4 files
        for (int i = 0; i < numFiles; i++) {
            String fileName = String.format("%c_service.py", (char)('a' + (numFiles - 1 - i)));
            
            // Each file has 1-2 violations
            StringBuilder content = new StringBuilder();
            int numViolations = (iteration + i) % 2 + 1;
            for (int j = 0; j < numViolations; j++) {
                String escapeHatch = ESCAPE_HATCHES.get((iteration + i + j) % ESCAPE_HATCHES.size());
                content.append(generateEscapeHatchCode(escapeHatch, iteration + i + j));
            }
            Files.writeString(blockRoot.resolve(fileName), content.toString());
        }

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest(blockKey)));

        // Verify sorted
        for (int i = 1; i < findings.size(); i++) {
            UndeclaredReachFinding prev = findings.get(i - 1);
            UndeclaredReachFinding curr = findings.get(i);
            
            int pathCompare = prev.path().compareTo(curr.path());
            if (pathCompare > 0) {
                fail("Findings not sorted by path: " + prev.path() + " > " + curr.path());
            } else if (pathCompare == 0) {
                int surfaceCompare = prev.surface().compareTo(curr.surface());
                if (surfaceCompare > 0) {
                    fail("Findings with same path not sorted by surface: " + 
                         prev.surface() + " > " + curr.surface());
                }
            }
        }
    }

    // ========== Helper methods ==========

    private String generateEscapeHatchCode(String escapeHatch, int seed) {
        // Generate different code patterns based on seed
        return switch (seed % 4) {
            case 0 -> String.format("result = %s(\"1 + 1\")\n", escapeHatch);
            case 1 -> String.format("x = %s('print(1)')\n", escapeHatch);
            case 2 -> String.format("value = %s(code_var)\n", escapeHatch);
            default -> String.format("output = %s(\"x = 1\")\n", escapeHatch);
        };
    }

    private void setupGovernedBlock(Path tempDir, String blockKey, String content) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("service.py"), content);
    }

    private WiringManifest makeManifest(String blockKey) {
        return new WiringManifest(
            "1", blockKey, blockKey, blockKey + "Logic", blockKey + "Impl",
            "src/blocks/" + blockKey + "/impl/" + blockKey + "_impl.py",
            "src/blocks/" + blockKey,
            List.of("src/blocks/" + blockKey),
            List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}
