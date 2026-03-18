package com.bear.kernel.target.python;

import com.bear.kernel.target.UndeclaredReachFinding;
import com.bear.kernel.target.WiringManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PythonDynamicImportEnforcer.
 * 
 * Tests importlib.import_module, __import__, sys.path mutation detection,
 * TYPE_CHECKING exclusion, and test file exclusion.
 */
class PythonDynamicImportEnforcerTest {

    // ========== importlib.import_module() detection ==========

    @Test
    void detectsImportlibImportModule(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import importlib
            mod = importlib.import_module("socket")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("importlib.import_module", findings.get(0).surface());
    }

    @Test
    void detectsImportlibImportModuleWithVariable(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import importlib
            module_name = "socket"
            mod = importlib.import_module(module_name)
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("importlib.import_module", findings.get(0).surface());
    }

    // ========== __import__() detection ==========

    @Test
    void detectsDunderImport(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            mod = __import__("socket")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("__import__", findings.get(0).surface());
    }

    @Test
    void detectsDunderImportWithVariable(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            module_name = "socket"
            mod = __import__(module_name)
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("__import__", findings.get(0).surface());
    }

    // ========== sys.path.append() detection ==========

    @Test
    void detectsSysPathAppend(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import sys
            sys.path.append("/tmp/mymodules")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("sys.path", findings.get(0).surface());
    }

    @Test
    void detectsSysPathAppendWithVariable(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import sys
            path = "/tmp/mymodules"
            sys.path.append(path)
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("sys.path", findings.get(0).surface());
    }

    // ========== sys.path.insert() detection ==========

    @Test
    void detectsSysPathInsert(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import sys
            sys.path.insert(0, "/tmp/mymodules")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("sys.path", findings.get(0).surface());
    }

    @Test
    void detectsSysPathInsertAtEnd(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import sys
            sys.path.insert(-1, "/tmp/mymodules")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("sys.path", findings.get(0).surface());
    }

    // ========== sys.path = [...] assignment detection ==========

    @Test
    void detectsSysPathAssignment(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import sys
            sys.path = ["/tmp/mymodules"]
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("sys.path", findings.get(0).surface());
    }

    @Test
    void detectsSysPathAssignmentWithConcatenation(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import sys
            sys.path = sys.path + ["/tmp/mymodules"]
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("sys.path", findings.get(0).surface());
    }

    @Test
    void detectsSysPathAssignmentEmpty(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import sys
            sys.path = []
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("sys.path", findings.get(0).surface());
    }

    // ========== Multiple violations in one file ==========

    @Test
    void detectsMultipleViolations(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import sys
            import importlib
            
            sys.path.append("/tmp")
            mod = importlib.import_module("socket")
            other = __import__("os")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(3, findings.size());
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("sys.path")));
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("importlib.import_module")));
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("__import__")));
    }

    // ========== if TYPE_CHECKING: block → no finding ==========

    @Test
    void typeCheckingBlockExcluded(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            from typing import TYPE_CHECKING
            import sys
            import importlib
            
            if TYPE_CHECKING:
                sys.path.append("/tmp")
                mod = importlib.import_module("socket")
                other = __import__("os")
            
            def my_function():
                pass
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "patterns inside TYPE_CHECKING blocks should be excluded");
    }

    @Test
    void mixedTypeCheckingAndRuntimePatterns(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            from typing import TYPE_CHECKING
            import sys
            import importlib
            
            if TYPE_CHECKING:
                mod = importlib.import_module("socket")  # Should be excluded
            
            sys.path.append("/tmp")  # Should be detected
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("sys.path", findings.get(0).surface());
    }

    @Test
    void typingTypeCheckingAttributeExcluded(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import typing
            import sys
            
            if typing.TYPE_CHECKING:
                sys.path.append("/tmp")
            
            def my_function():
                pass
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "patterns inside typing.TYPE_CHECKING blocks should be excluded");
    }

    // ========== Test files excluded ==========

    @Test
    void testFilesExcluded_testPrefix(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("test_service.py"), """
            import sys
            sys.path.append("/tmp")
            """);
        Files.writeString(blockRoot.resolve("service.py"), "# clean\n");

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "test_*.py files should be excluded");
    }

    @Test
    void testFilesExcluded_testSuffix(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("service_test.py"), """
            import importlib
            mod = importlib.import_module("socket")
            """);
        Files.writeString(blockRoot.resolve("service.py"), "# clean\n");

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "*_test.py files should be excluded");
    }

    // ========== Non-governed files excluded ==========

    @Test
    void nonGovernedFilesExcluded(@TempDir Path tempDir) throws IOException {
        // Create governed block
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("service.py"), "# clean\n");
        
        // Create non-governed file outside blocks
        Path scriptsDir = Files.createDirectories(tempDir.resolve("scripts"));
        Files.writeString(scriptsDir.resolve("deploy.py"), """
            import sys
            sys.path.append("/tmp")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "non-governed files should be excluded");
    }

    @Test
    void nonGovernedBlockExcluded(@TempDir Path tempDir) throws IOException {
        // Create governed block
        Path governedBlock = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(governedBlock.resolve("__init__.py"), "");
        Files.writeString(governedBlock.resolve("service.py"), "# clean\n");
        
        // Create another block that is NOT in the wiring manifests
        Path otherBlock = Files.createDirectories(tempDir.resolve("src/blocks/other-block"));
        Files.writeString(otherBlock.resolve("__init__.py"), "");
        Files.writeString(otherBlock.resolve("service.py"), """
            import importlib
            mod = importlib.import_module("socket")
            """);

        // Only my-block is in the manifest
        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "non-governed blocks should be excluded");
    }

    // ========== Multiple findings sorted by path then surface ==========

    @Test
    void multipleFindingsSortedByPathThenSurface(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        
        // Create files in non-alphabetical order
        Files.writeString(blockRoot.resolve("z_service.py"), """
            import importlib
            mod = importlib.import_module("socket")
            """);
        Files.writeString(blockRoot.resolve("a_service.py"), """
            import sys
            sys.path.append("/tmp")
            other = __import__("os")
            """);
        Files.writeString(blockRoot.resolve("m_service.py"), """
            mod = __import__("socket")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(4, findings.size());
        
        // Verify sorted by path first
        assertTrue(findings.get(0).path().contains("a_service.py"));
        assertTrue(findings.get(1).path().contains("a_service.py"));
        assertTrue(findings.get(2).path().contains("m_service.py"));
        assertTrue(findings.get(3).path().contains("z_service.py"));
        
        // Verify sorted by surface within same file (a_service.py has __import__ and sys.path)
        assertEquals("__import__", findings.get(0).surface());
        assertEquals("sys.path", findings.get(1).surface());
    }

    // ========== SyntaxError in source → no finding (not an error) ==========

    @Test
    void syntaxErrorInSourceNoFinding(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import sys
            sys.path.append("/tmp")
            def broken_function(
                # Missing closing paren - syntax error
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "syntax errors should result in no findings, not an error");
    }

    @Test
    void syntaxErrorDoesNotAffectOtherFiles(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        
        // File with syntax error
        Files.writeString(blockRoot.resolve("broken.py"), "def broken(\n");
        
        // Valid file with violation
        Files.writeString(blockRoot.resolve("valid.py"), """
            import sys
            sys.path.append("/tmp")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).path().contains("valid.py"));
        assertEquals("sys.path", findings.get(0).surface());
    }

    // ========== Clean project has no findings ==========

    @Test
    void cleanProjectNoFindings(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import os
            import json
            from pathlib import Path
            
            def my_function():
                return os.path.join("a", "b")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty());
    }

    // ========== Shared root included ==========

    @Test
    void sharedRootIncluded(@TempDir Path tempDir) throws IOException {
        // Create governed block
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("service.py"), "# clean\n");
        
        // Create _shared with violation
        Path sharedRoot = Files.createDirectories(tempDir.resolve("src/blocks/_shared"));
        Files.writeString(sharedRoot.resolve("__init__.py"), "");
        Files.writeString(sharedRoot.resolve("util.py"), """
            import sys
            sys.path.append("/tmp")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).path().contains("_shared"));
        assertEquals("sys.path", findings.get(0).surface());
    }

    // ========== importlib.util.spec_from_file_location detection ==========

    @Test
    void detectsImportlibUtilSpecFromFileLocation(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import importlib.util
            spec = importlib.util.spec_from_file_location("mymodule", "/path/to/module.py")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("importlib.util.spec_from_file_location", findings.get(0).surface());
    }

    // ========== sys.path read (not mutation) → no finding ==========

    @Test
    void sysPathReadNoFinding(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import sys
            print(sys.path)
            paths = list(sys.path)
            for p in sys.path:
                print(p)
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicImportEnforcer.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "reading sys.path should not produce findings");
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
