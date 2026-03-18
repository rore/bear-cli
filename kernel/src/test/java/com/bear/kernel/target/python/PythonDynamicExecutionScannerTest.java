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
 * Unit tests for PythonDynamicExecutionScanner.
 * 
 * Tests eval/exec/compile detection, TYPE_CHECKING exclusion, and test file exclusion.
 */
class PythonDynamicExecutionScannerTest {

    // ========== eval() detection ==========

    @Test
    void detectsEvalCall(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            result = eval("1 + 1")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("eval", findings.get(0).surface());
    }

    @Test
    void detectsEvalCallWithVariable(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            code = "1 + 1"
            result = eval(code)
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("eval", findings.get(0).surface());
    }

    // ========== exec() detection ==========

    @Test
    void detectsExecCall(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            exec("print('hello')")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("exec", findings.get(0).surface());
    }

    @Test
    void detectsExecCallWithGlobals(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            exec("x = 1", globals())
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("exec", findings.get(0).surface());
    }

    // ========== compile() detection ==========

    @Test
    void detectsCompileCall(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            code = compile("1 + 1", "<string>", "eval")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("compile", findings.get(0).surface());
    }

    @Test
    void detectsCompileCallWithExec(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            code = compile("print('hello')", "<string>", "exec")
            exec(code)
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(2, findings.size());
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("compile")));
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("exec")));
    }

    // ========== eval as variable name (not a call) → no finding ==========

    @Test
    void evalAsVariableNameNoFinding(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            eval = "some value"
            print(eval)
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "eval as variable name should not produce findings");
    }

    @Test
    void execAsVariableNameNoFinding(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            exec = "some value"
            print(exec)
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "exec as variable name should not produce findings");
    }

    @Test
    void compileAsVariableNameNoFinding(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            compile = "some value"
            print(compile)
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "compile as variable name should not produce findings");
    }

    // ========== if TYPE_CHECKING: block → no finding ==========

    @Test
    void typeCheckingBlockExcluded(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            from typing import TYPE_CHECKING
            
            if TYPE_CHECKING:
                result = eval("1 + 1")
                exec("print('hello')")
                code = compile("x", "<string>", "eval")
            
            def my_function():
                pass
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "calls inside TYPE_CHECKING blocks should be excluded");
    }

    @Test
    void mixedTypeCheckingAndRuntimeCalls(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            from typing import TYPE_CHECKING
            
            if TYPE_CHECKING:
                result = eval("1 + 1")  # Should be excluded
            
            exec("print('hello')")  # Should be detected
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertEquals("exec", findings.get(0).surface());
    }

    @Test
    void typingTypeCheckingAttributeExcluded(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            import typing
            
            if typing.TYPE_CHECKING:
                result = eval("1 + 1")
            
            def my_function():
                pass
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "calls inside typing.TYPE_CHECKING blocks should be excluded");
    }

    // ========== Test files excluded ==========

    @Test
    void testFilesExcluded_testPrefix(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("test_service.py"), "result = eval('1 + 1')\n");
        Files.writeString(blockRoot.resolve("service.py"), "# clean\n");

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "test_*.py files should be excluded");
    }

    @Test
    void testFilesExcluded_testSuffix(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("service_test.py"), "result = eval('1 + 1')\n");
        Files.writeString(blockRoot.resolve("service.py"), "# clean\n");

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
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
        Files.writeString(scriptsDir.resolve("deploy.py"), "result = eval('1 + 1')\n");

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
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
        Files.writeString(otherBlock.resolve("service.py"), "result = eval('1 + 1')\n");

        // Only my-block is in the manifest
        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "non-governed blocks should be excluded");
    }

    // ========== Multiple findings sorted by path then surface ==========

    @Test
    void multipleFindingsSortedByPathThenSurface(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/my-block"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        
        // Create files in non-alphabetical order
        Files.writeString(blockRoot.resolve("z_service.py"), "result = eval('1')\n");
        Files.writeString(blockRoot.resolve("a_service.py"), "exec('x=1')\nresult = compile('x', '<s>', 'eval')\n");
        Files.writeString(blockRoot.resolve("m_service.py"), "result = eval('2')\n");

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(4, findings.size());
        
        // Verify sorted by path first
        assertTrue(findings.get(0).path().contains("a_service.py"));
        assertTrue(findings.get(1).path().contains("a_service.py"));
        assertTrue(findings.get(2).path().contains("m_service.py"));
        assertTrue(findings.get(3).path().contains("z_service.py"));
        
        // Verify sorted by surface within same file (a_service.py has compile and exec)
        assertEquals("compile", findings.get(0).surface());
        assertEquals("exec", findings.get(1).surface());
    }

    // ========== SyntaxError in source → no finding (not an error) ==========

    @Test
    void syntaxErrorInSourceNoFinding(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            result = eval("1 + 1")
            def broken_function(
                # Missing closing paren - syntax error
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
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
        Files.writeString(blockRoot.resolve("valid.py"), "result = eval('1 + 1')\n");

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).path().contains("valid.py"));
        assertEquals("eval", findings.get(0).surface());
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

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
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
        Files.writeString(sharedRoot.resolve("util.py"), "result = eval('1 + 1')\n");

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).path().contains("_shared"));
        assertEquals("eval", findings.get(0).surface());
    }

    // ========== Method call on object named eval/exec/compile → no finding ==========

    @Test
    void methodCallOnObjectNamedEvalNoFinding(@TempDir Path tempDir) throws IOException {
        setupGovernedBlock(tempDir, "my-block", """
            class MyClass:
                def eval(self, x):
                    return x
            
            obj = MyClass()
            result = obj.eval("test")
            """);

        List<UndeclaredReachFinding> findings = PythonDynamicExecutionScanner.scan(
            tempDir, List.of(makeManifest("my-block")));

        assertTrue(findings.isEmpty(), "method call obj.eval() should not produce findings");
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
