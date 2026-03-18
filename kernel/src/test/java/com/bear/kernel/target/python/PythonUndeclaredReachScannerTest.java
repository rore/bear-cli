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
 * Unit tests for PythonUndeclaredReachScanner.
 * 
 * Tests covered power-surface module detection, os.* call-site patterns,
 * from os import detection, TYPE_CHECKING exclusion, and test file exclusion.
 */
class PythonUndeclaredReachScannerTest {

    // ========== Covered Module Detection ==========

    @Test
    void detectsSocketImport(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", "import socket\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("socket", findings.get(0).surface());
    }

    @Test
    void detectsHttpImport(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", "import http\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("http", findings.get(0).surface());
    }

    @Test
    void detectsHttpClientImport(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", "import http.client\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("http.client", findings.get(0).surface());
    }

    @Test
    void detectsHttpServerImport(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", "import http.server\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("http.server", findings.get(0).surface());
    }

    @Test
    void detectsUrllibImport(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", "import urllib\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("urllib", findings.get(0).surface());
    }

    @Test
    void detectsUrllibRequestImport(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", "import urllib.request\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("urllib.request", findings.get(0).surface());
    }

    @Test
    void detectsSubprocessImport(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", "import subprocess\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("subprocess", findings.get(0).surface());
    }

    @Test
    void detectsMultiprocessingImport(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", "import multiprocessing\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("multiprocessing", findings.get(0).surface());
    }

    @Test
    void detectsFromImportCoveredModule(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", "from socket import socket\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("socket", findings.get(0).surface());
    }

    // ========== import os alone → no finding ==========

    @Test
    void importOsAloneNoFinding(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import os
            path = os.path.join("a", "b")
            """);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "import os alone should not produce findings");
    }

    @Test
    void importOsWithPathUsageNoFinding(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import os
            exists = os.path.exists("/tmp")
            dirname = os.path.dirname("/tmp/file.txt")
            """);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "os.path usage should not produce findings");
    }

    // ========== os.system() call → finding ==========

    @Test
    void detectsOsSystemCall(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import os
            os.system("ls -la")
            """);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("os.system", findings.get(0).surface());
    }

    @Test
    void detectsOsPopenCall(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import os
            os.popen("ls")
            """);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("os.popen", findings.get(0).surface());
    }

    @Test
    void detectsOsExeclCall(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import os
            os.execl("/bin/ls", "ls", "-la")
            """);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("os.execl", findings.get(0).surface());
    }

    @Test
    void detectsOsExecvCall(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import os
            os.execv("/bin/ls", ["ls", "-la"])
            """);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("os.execv", findings.get(0).surface());
    }

    // ========== from os import system → finding ==========

    @Test
    void detectsFromOsImportSystem(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", "from os import system\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("os.system", findings.get(0).surface());
    }

    @Test
    void detectsFromOsImportPopen(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", "from os import popen\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("os.popen", findings.get(0).surface());
    }

    @Test
    void detectsFromOsImportExecl(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", "from os import execl\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("os.execl", findings.get(0).surface());
    }

    @Test
    void detectsFromOsImportMultipleExecFunctions(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", "from os import execl, execv, execve\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(3, findings.size());
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("os.execl")));
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("os.execv")));
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("os.execve")));
    }

    // ========== Multiple files → sorted by path then surface ==========

    @Test
    void multipleFilesSortedByPathThenSurface(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        
        // Create files in non-alphabetical order
        Files.writeString(blockRoot.resolve("z_service.py"), "import socket\n");
        Files.writeString(blockRoot.resolve("a_service.py"), "import subprocess\nimport multiprocessing\n");
        Files.writeString(blockRoot.resolve("m_service.py"), "import http\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(4, findings.size());
        
        // Verify sorted by path first
        assertTrue(findings.get(0).path().contains("a_service.py"));
        assertTrue(findings.get(1).path().contains("a_service.py"));
        assertTrue(findings.get(2).path().contains("m_service.py"));
        assertTrue(findings.get(3).path().contains("z_service.py"));
        
        // Verify sorted by surface within same file (a_service.py has multiprocessing and subprocess)
        assertEquals("multiprocessing", findings.get(0).surface());
        assertEquals("subprocess", findings.get(1).surface());
    }

    // ========== Test files excluded ==========

    @Test
    void testFilesExcluded_testPrefix(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("test_service.py"), "import socket\n");
        Files.writeString(blockRoot.resolve("service.py"), "# clean\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "test_*.py files should be excluded");
    }

    @Test
    void testFilesExcluded_testSuffix(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("service_test.py"), "import socket\n");
        Files.writeString(blockRoot.resolve("service.py"), "# clean\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
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
        Files.writeString(scriptsDir.resolve("deploy.py"), "import socket\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
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
        Files.writeString(otherBlock.resolve("service.py"), "import socket\n");

        // Only my-block is in the manifest
        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "non-governed blocks should be excluded");
    }

    // ========== if TYPE_CHECKING: block imports → no finding ==========

    @Test
    void typeCheckingBlockImportsExcluded(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            from typing import TYPE_CHECKING
            
            if TYPE_CHECKING:
                import socket
                import subprocess
            
            def my_function():
                pass
            """);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "imports inside TYPE_CHECKING blocks should be excluded");
    }

    @Test
    void typeCheckingBlockWithFromImportExcluded(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            from typing import TYPE_CHECKING
            
            if TYPE_CHECKING:
                from socket import socket
                from os import system
            
            def my_function():
                pass
            """);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "from imports inside TYPE_CHECKING blocks should be excluded");
    }

    @Test
    void mixedTypeCheckingAndRuntimeImports(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            from typing import TYPE_CHECKING
            
            if TYPE_CHECKING:
                import socket  # Should be excluded
            
            import subprocess  # Should be detected
            """);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("subprocess", findings.get(0).surface());
    }

    // ========== SyntaxError in source → no finding (not an error) ==========

    @Test
    void syntaxErrorInSourceNoFinding(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import socket
            def broken_function(
                # Missing closing paren - syntax error
            """);

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
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
        Files.writeString(blockRoot.resolve("valid.py"), "import socket\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).path().contains("valid.py"));
        assertEquals("socket", findings.get(0).surface());
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

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
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
        Files.writeString(sharedRoot.resolve("util.py"), "import socket\n");

        List<UndeclaredReachFinding> findings = PythonUndeclaredReachScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).path().contains("_shared"));
        assertEquals("socket", findings.get(0).surface());
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
