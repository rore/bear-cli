package com.bear.kernel.target.python.properties;

import com.bear.kernel.target.UndeclaredReachFinding;
import com.bear.kernel.target.WiringManifest;
import com.bear.kernel.target.python.PythonDynamicImportEnforcer;
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
 * Property-based tests for PythonDynamicImportEnforcer.
 * Feature: phase-p2-python-checking, Property 12
 * 
 * Uses plain JUnit 5 parameterized tests with 100+ iterations.
 */
class DynamicImportEnforcementProperties {

    private static final List<String> DYNAMIC_IMPORT_PATTERNS = List.of(
        "importlib.import_module", "__import__", "sys.path"
    );

    // ========== Property 12: Dynamic import facility enforcement ==========
    // **Validates: Requirements 5.1, 5.2, 5.3**
    // Any governed file with importlib.import_module, __import__, or sys.path mutation
    // → at least one finding with matching surface

    static Stream<Integer> property12Iterations() {
        return IntStream.range(0, 110).boxed();
    }

    @ParameterizedTest(name = "Property 12 - iteration {0}: importlib.import_module detected")
    @MethodSource("property12Iterations")
    void property12_importlibImportModuleDetected(int iteration, @TempDir Path tempDir) throws IOException {
        // Generate code with importlib.import_module
        String code = generateImportlibImportModuleCode(iteration);
        
        setupGovernedBlock(tempDir, "block-" + iteration, code);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        assertFalse(findings.isEmpty(), 
            "importlib.import_module() should produce at least one finding");
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("importlib.import_module")),
            "Finding surface should be 'importlib.import_module'");
    }

    @ParameterizedTest(name = "Property 12 - iteration {0}: __import__ detected")
    @MethodSource("property12Iterations")
    void property12_dunderImportDetected(int iteration, @TempDir Path tempDir) throws IOException {
        // Generate code with __import__
        String code = generateDunderImportCode(iteration);
        
        setupGovernedBlock(tempDir, "block-" + iteration, code);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        assertFalse(findings.isEmpty(), 
            "__import__() should produce at least one finding");
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("__import__")),
            "Finding surface should be '__import__'");
    }

    @ParameterizedTest(name = "Property 12 - iteration {0}: sys.path mutation detected")
    @MethodSource("property12Iterations")
    void property12_sysPathMutationDetected(int iteration, @TempDir Path tempDir) throws IOException {
        // Generate code with sys.path mutation
        String code = generateSysPathMutationCode(iteration);
        
        setupGovernedBlock(tempDir, "block-" + iteration, code);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        assertFalse(findings.isEmpty(), 
            "sys.path mutation should produce at least one finding");
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("sys.path")),
            "Finding surface should be 'sys.path'");
    }

    @ParameterizedTest(name = "Property 12 - iteration {0}: multiple patterns detected")
    @MethodSource("property12Iterations")
    void property12_multiplePatternsDetected(int iteration, @TempDir Path tempDir) throws IOException {
        // Generate code with multiple dynamic import patterns
        StringBuilder code = new StringBuilder();
        List<String> usedPatterns = new ArrayList<>();
        
        int numPatterns = (iteration % 3) + 1; // 1-3 patterns
        for (int i = 0; i < numPatterns; i++) {
            String pattern = DYNAMIC_IMPORT_PATTERNS.get((iteration + i) % DYNAMIC_IMPORT_PATTERNS.size());
            if (!usedPatterns.contains(pattern)) {
                usedPatterns.add(pattern);
                code.append(generateCodeForPattern(pattern, iteration + i));
            }
        }
        
        setupGovernedBlock(tempDir, "block-" + iteration, code.toString());

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        // Should have at least one finding for each unique pattern used
        for (String pattern : usedPatterns) {
            assertTrue(findings.stream().anyMatch(f -> f.surface().equals(pattern)),
                "Should have finding for pattern '" + pattern + "'");
        }
    }

    @ParameterizedTest(name = "Property 12 - TYPE_CHECKING iteration {0}: patterns in TYPE_CHECKING → no finding")
    @MethodSource("property12Iterations")
    void property12_patternsInTypeCheckingNoFinding(int iteration, @TempDir Path tempDir) throws IOException {
        // Pick a random pattern
        String pattern = DYNAMIC_IMPORT_PATTERNS.get(iteration % DYNAMIC_IMPORT_PATTERNS.size());
        
        // Generate code with pattern inside TYPE_CHECKING block (properly indented)
        String patternCode = generateTypeCheckingPatternCode(pattern, iteration);
        String code = String.format("""
            from typing import TYPE_CHECKING
            import sys
            import importlib
            
            if TYPE_CHECKING:
            %s
            
            def my_function():
                pass
            """, patternCode);
        
        setupGovernedBlock(tempDir, "block-" + iteration, code);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("block-" + iteration)));

        assertTrue(findings.isEmpty(), 
            "Pattern '" + pattern + "' inside TYPE_CHECKING should produce no findings");
    }

    @ParameterizedTest(name = "Property 12 - test file iteration {0}: patterns in test file → no finding")
    @MethodSource("property12Iterations")
    void property12_patternsInTestFileNoFinding(int iteration, @TempDir Path tempDir) throws IOException {
        // Pick a random pattern
        String pattern = DYNAMIC_IMPORT_PATTERNS.get(iteration % DYNAMIC_IMPORT_PATTERNS.size());
        
        String blockKey = "block-" + iteration;
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        
        // Create test file with pattern (should be excluded)
        String testFileName = (iteration % 2 == 0) ? "test_service.py" : "service_test.py";
        Files.writeString(blockRoot.resolve(testFileName), 
            "import sys\nimport importlib\n" + generateCodeForPattern(pattern, iteration));
        
        // Create clean governed file
        Files.writeString(blockRoot.resolve("service.py"), "# clean\n");

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest(blockKey)));

        assertTrue(findings.isEmpty(), 
            "Pattern '" + pattern + "' in test file should produce no findings");
    }

    @ParameterizedTest(name = "Property 12 - sorted iteration {0}: findings sorted by path then surface")
    @MethodSource("property12Iterations")
    void property12_findingsSortedByPathThenSurface(int iteration, @TempDir Path tempDir) throws IOException {
        String blockKey = "block-" + iteration;
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        
        // Create multiple files with violations in random order
        int numFiles = (iteration % 3) + 2; // 2-4 files
        for (int i = 0; i < numFiles; i++) {
            String fileName = String.format("%c_service.py", (char)('a' + (numFiles - 1 - i)));
            
            // Each file has 1-2 violations
            StringBuilder content = new StringBuilder("import sys\nimport importlib\n");
            int numViolations = (iteration + i) % 2 + 1;
            for (int j = 0; j < numViolations; j++) {
                String pattern = DYNAMIC_IMPORT_PATTERNS.get((iteration + i + j) % DYNAMIC_IMPORT_PATTERNS.size());
                content.append(generateCodeForPattern(pattern, iteration + i + j));
            }
            Files.writeString(blockRoot.resolve(fileName), content.toString());
        }

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
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

    @ParameterizedTest(name = "Property 12 - non-governed iteration {0}: non-governed files excluded")
    @MethodSource("property12Iterations")
    void property12_nonGovernedFilesExcluded(int iteration, @TempDir Path tempDir) throws IOException {
        String blockKey = "block-" + iteration;
        
        // Create governed block with clean file
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("service.py"), "# clean\n");
        
        // Create non-governed file with violation
        Path scriptsDir = Files.createDirectories(tempDir.resolve("scripts"));
        String pattern = DYNAMIC_IMPORT_PATTERNS.get(iteration % DYNAMIC_IMPORT_PATTERNS.size());
        Files.writeString(scriptsDir.resolve("deploy.py"), 
            "import sys\nimport importlib\n" + generateCodeForPattern(pattern, iteration));

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest(blockKey)));

        assertTrue(findings.isEmpty(), "Non-governed files should be excluded");
    }

    // ========== Helper methods ==========

    private String generateTypeCheckingPatternCode(String pattern, int seed) {
        // Generate code that goes inside TYPE_CHECKING block (properly indented with 4 spaces)
        return switch (pattern) {
            case "importlib.import_module" -> "    mod = importlib.import_module(\"socket\")\n";
            case "__import__" -> "    mod = __import__(\"socket\")\n";
            case "sys.path" -> switch (seed % 3) {
                case 0 -> "    sys.path.append(\"/tmp\")\n";
                case 1 -> "    sys.path.insert(0, \"/tmp\")\n";
                default -> "    sys.path = [\"/tmp\"]\n";
            };
            default -> throw new IllegalArgumentException("Unknown pattern: " + pattern);
        };
    }

    private String generateImportlibImportModuleCode(int seed) {
        String[] moduleNames = {"socket", "os", "json", "sys", "pathlib"};
        String moduleName = moduleNames[seed % moduleNames.length];
        
        return switch (seed % 4) {
            case 0 -> String.format("import importlib\nmod = importlib.import_module(\"%s\")\n", moduleName);
            case 1 -> String.format("import importlib\nresult = importlib.import_module('%s')\n", moduleName);
            case 2 -> String.format("import importlib\nmodule_name = \"%s\"\nm = importlib.import_module(module_name)\n", moduleName);
            default -> String.format("import importlib\nx = importlib.import_module(\"%s\")\n", moduleName);
        };
    }

    private String generateDunderImportCode(int seed) {
        String[] moduleNames = {"socket", "os", "json", "sys", "pathlib"};
        String moduleName = moduleNames[seed % moduleNames.length];
        
        return switch (seed % 4) {
            case 0 -> String.format("mod = __import__(\"%s\")\n", moduleName);
            case 1 -> String.format("result = __import__('%s')\n", moduleName);
            case 2 -> String.format("module_name = \"%s\"\nm = __import__(module_name)\n", moduleName);
            default -> String.format("x = __import__(\"%s\")\n", moduleName);
        };
    }

    private String generateSysPathMutationCode(int seed) {
        String[] paths = {"/tmp", "/opt/modules", "/home/user/lib", ".", "/var/lib"};
        String path = paths[seed % paths.length];
        
        return switch (seed % 5) {
            case 0 -> String.format("import sys\nsys.path.append(\"%s\")\n", path);
            case 1 -> String.format("import sys\nsys.path.insert(0, \"%s\")\n", path);
            case 2 -> String.format("import sys\nsys.path.insert(-1, '%s')\n", path);
            case 3 -> String.format("import sys\nsys.path = [\"%s\"]\n", path);
            default -> String.format("import sys\nsys.path = sys.path + [\"%s\"]\n", path);
        };
    }

    private String generateCodeForPattern(String pattern, int seed) {
        return switch (pattern) {
            case "importlib.import_module" -> generateImportlibImportModuleCode(seed);
            case "__import__" -> generateDunderImportCode(seed);
            case "sys.path" -> generateSysPathMutationCode(seed);
            default -> throw new IllegalArgumentException("Unknown pattern: " + pattern);
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
