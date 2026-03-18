package com.bear.kernel.target.python.properties;

import com.bear.kernel.target.UndeclaredReachFinding;
import com.bear.kernel.target.WiringManifest;
import com.bear.kernel.target.python.PythonUndeclaredReachScanner;
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
 * Property-based tests for PythonUndeclaredReachScanner.
 * Feature: phase-p2-python-checking
 * 
 * Uses plain JUnit 5 parameterized tests with 100+ iterations.
 */
class UndeclaredReachProperties {

    private static final Random RANDOM = new Random(42); // Deterministic seed
    
    private static final List<String> COVERED_MODULES = List.of(
        "socket", "http", "http.client", "http.server",
        "urllib", "urllib.request", "subprocess", "multiprocessing"
    );
    
    private static final List<String> OS_EXEC_ATTRS = List.of(
        "system", "popen", "execl", "execle", "execlp", "execlpe",
        "execv", "execve", "execvp", "execvpe"
    );

    // ========== Property 5: Covered power-surface import detection ==========
    // **Validates: Requirement 3.1**
    // Any governed file with covered module import → at least one finding with matching surface

    static Stream<Integer> property5Iterations() {
        return IntStream.range(0, 110).boxed();
    }

    @ParameterizedTest(name = "Property 5 - iteration {0}: covered module import detected")
    @MethodSource("property5Iterations")
    void property5_coveredModuleImportDetected(int iteration, @TempDir Path tempDir) throws IOException {
        // Pick a random covered module
        String module = COVERED_MODULES.get(iteration % COVERED_MODULES.size());
        
        // Generate import statement (alternate between import and from...import)
        String importStatement = (iteration % 2 == 0) 
            ? "import " + module + "\n"
            : "from " + module + " import *\n";
        
        setupGovernedBlock(tempDir, "block-" + iteration, importStatement);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        assertFalse(findings.isEmpty(), 
            "Covered module '" + module + "' should produce at least one finding");
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals(module)),
            "Finding surface should match module '" + module + "'");
    }

    // ========== Property 6: os.system/popen/exec* call detection ==========
    // **Validates: Requirements 3.2, 3.3**
    // os.system/popen/exec* call → finding; import os + only os.path usage → no finding

    static Stream<Integer> property6Iterations() {
        return IntStream.range(0, 110).boxed();
    }

    @ParameterizedTest(name = "Property 6 - iteration {0}: os call-site pattern detection")
    @MethodSource("property6Iterations")
    void property6_osCallSitePatternDetection(int iteration, @TempDir Path tempDir) throws IOException {
        // Pick a random os exec attribute
        String attr = OS_EXEC_ATTRS.get(iteration % OS_EXEC_ATTRS.size());
        
        // Generate code with os.* call
        String code = String.format("""
            import os
            result = os.%s("cmd")
            """, attr);
        
        setupGovernedBlock(tempDir, "block-" + iteration, code);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        assertFalse(findings.isEmpty(), 
            "os." + attr + "() call should produce at least one finding");
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("os." + attr)),
            "Finding surface should be 'os." + attr + "'");
    }

    static Stream<Integer> property6NoFindingIterations() {
        return IntStream.range(0, 50).boxed();
    }

    @ParameterizedTest(name = "Property 6 - no finding iteration {0}: import os + os.path only")
    @MethodSource("property6NoFindingIterations")
    void property6_importOsWithPathOnlyNoFinding(int iteration, @TempDir Path tempDir) throws IOException {
        // Generate code with only os.path usage
        String code = """
            import os
            path = os.path.join("a", "b")
            exists = os.path.exists("/tmp")
            dirname = os.path.dirname("/tmp/file.txt")
            """;
        
        setupGovernedBlock(tempDir, "block-" + iteration, code);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        assertTrue(findings.isEmpty(), 
            "import os with only os.path usage should produce no findings");
    }

    // ========== Property 7: from os import system/popen/exec* detection ==========
    // **Validates: Requirement 3.4**
    // from os import system/popen/exec* → finding

    static Stream<Integer> property7Iterations() {
        return IntStream.range(0, 110).boxed();
    }

    @ParameterizedTest(name = "Property 7 - iteration {0}: from os import detection")
    @MethodSource("property7Iterations")
    void property7_fromOsImportDetection(int iteration, @TempDir Path tempDir) throws IOException {
        // Pick a random os exec attribute
        String attr = OS_EXEC_ATTRS.get(iteration % OS_EXEC_ATTRS.size());
        
        // Generate from os import statement
        String code = "from os import " + attr + "\n";
        
        setupGovernedBlock(tempDir, "block-" + iteration, code);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        assertFalse(findings.isEmpty(), 
            "from os import " + attr + " should produce at least one finding");
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("os." + attr)),
            "Finding surface should be 'os." + attr + "'");
    }

    // ========== Property 8: Results sorted by path then surface ascending ==========
    // **Validates: Requirement 3.7**

    static Stream<Integer> property8Iterations() {
        return IntStream.range(0, 110).boxed();
    }

    @ParameterizedTest(name = "Property 8 - iteration {0}: results sorted by path then surface")
    @MethodSource("property8Iterations")
    void property8_resultsSortedByPathThenSurface(int iteration, @TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        
        // Create multiple files with multiple violations in random order
        int numFiles = (iteration % 3) + 2; // 2-4 files
        List<String> fileNames = new ArrayList<>();
        for (int i = 0; i < numFiles; i++) {
            String fileName = String.format("%c_service.py", (char)('a' + (numFiles - 1 - i)));
            fileNames.add(fileName);
            
            // Each file has 1-2 violations
            StringBuilder content = new StringBuilder();
            int numViolations = (iteration + i) % 2 + 1;
            for (int j = 0; j < numViolations; j++) {
                String module = COVERED_MODULES.get((iteration + i + j) % COVERED_MODULES.size());
                content.append("import ").append(module).append("\n");
            }
            Files.writeString(blockRoot.resolve(fileName), content.toString());
        }

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

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

    // ========== Property 9: Findings only for governed files, test files excluded ==========
    // **Validates: Requirement 3.8**

    static Stream<Integer> property9Iterations() {
        return IntStream.range(0, 110).boxed();
    }

    @ParameterizedTest(name = "Property 9 - iteration {0}: only governed files, test files excluded")
    @MethodSource("property9Iterations")
    void property9_onlyGovernedFilesTestFilesExcluded(int iteration, @TempDir Path tempDir) throws IOException {
        String blockKey = "block-" + iteration;
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        
        // Create governed file with violation
        Files.writeString(blockRoot.resolve("service.py"), "import socket\n");
        
        // Create test files with violations (should be excluded)
        Files.writeString(blockRoot.resolve("test_service.py"), "import subprocess\n");
        Files.writeString(blockRoot.resolve("service_test.py"), "import multiprocessing\n");
        
        // Create non-governed file with violation (outside blocks)
        Path scriptsDir = Files.createDirectories(tempDir.resolve("scripts"));
        Files.writeString(scriptsDir.resolve("deploy.py"), "import http\n");
        
        // Create another block not in manifest (should be excluded)
        Path otherBlock = Files.createDirectories(tempDir.resolve("src/blocks/other-block"));
        Files.writeString(otherBlock.resolve("__init__.py"), "");
        Files.writeString(otherBlock.resolve("service.py"), "import urllib\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest(blockKey)));

        // Should only have finding from governed service.py
        assertEquals(1, findings.size(), "Should only have 1 finding from governed file");
        assertTrue(findings.get(0).path().contains("service.py"));
        assertFalse(findings.get(0).path().contains("test_"));
        assertFalse(findings.get(0).path().contains("_test.py"));
        assertEquals("socket", findings.get(0).surface());
    }

    // ========== Property 10: Imports inside TYPE_CHECKING → no findings ==========
    // **Validates: Requirement 3.9**

    static Stream<Integer> property10Iterations() {
        return IntStream.range(0, 110).boxed();
    }

    @ParameterizedTest(name = "Property 10 - iteration {0}: TYPE_CHECKING imports excluded")
    @MethodSource("property10Iterations")
    void property10_typeCheckingImportsExcluded(int iteration, @TempDir Path tempDir) throws IOException {
        // Pick a random covered module
        String module = COVERED_MODULES.get(iteration % COVERED_MODULES.size());
        
        // Generate code with import inside TYPE_CHECKING block
        String code = String.format("""
            from typing import TYPE_CHECKING
            
            if TYPE_CHECKING:
                import %s
            
            def my_function():
                pass
            """, module);
        
        setupGovernedBlock(tempDir, "block-" + iteration, code);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        assertTrue(findings.isEmpty(), 
            "Imports inside TYPE_CHECKING block should produce no findings");
    }

    @ParameterizedTest(name = "Property 10 - from import iteration {0}: TYPE_CHECKING from imports excluded")
    @MethodSource("property10Iterations")
    void property10_typeCheckingFromImportsExcluded(int iteration, @TempDir Path tempDir) throws IOException {
        // Pick a random os exec attribute
        String attr = OS_EXEC_ATTRS.get(iteration % OS_EXEC_ATTRS.size());
        
        // Generate code with from os import inside TYPE_CHECKING block
        String code = String.format("""
            from typing import TYPE_CHECKING
            
            if TYPE_CHECKING:
                from os import %s
            
            def my_function():
                pass
            """, attr);
        
        setupGovernedBlock(tempDir, "block-" + iteration, code);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        assertTrue(findings.isEmpty(), 
            "from os import inside TYPE_CHECKING block should produce no findings");
    }

    // ========== Helper methods ==========

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
