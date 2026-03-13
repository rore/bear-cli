package com.bear.kernel.target.python;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PythonDynamicImportDetector.
 * Verifies AST-based detection of dynamic import patterns with correct line/column numbers.
 * 
 * Phase P: Detection only, no enforcement.
 */
class PythonDynamicImportDetectorTest {

    private final PythonDynamicImportDetector detector = new PythonDynamicImportDetector();

    @Test
    void detectsImportlibImportModule(@TempDir Path tempDir) throws IOException {
        String source = """
            import importlib
            module = importlib.import_module("my_module")
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);

        assertEquals(1, imports.size());
        PythonDynamicImportDetector.DynamicImport imp = imports.get(0);
        assertEquals("importlib.import_module", imp.pattern());
        assertEquals(2, imp.lineNumber());
        assertEquals(9, imp.columnNumber());
    }

    @Test
    void detectsDunderImport(@TempDir Path tempDir) throws IOException {
        String source = """
            module = __import__("os")
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);

        assertEquals(1, imports.size());
        PythonDynamicImportDetector.DynamicImport imp = imports.get(0);
        assertEquals("__import__", imp.pattern());
        assertEquals(1, imp.lineNumber());
        assertEquals(9, imp.columnNumber());
    }

    @Test
    void detectsImportlibUtilSpecFromFileLocation(@TempDir Path tempDir) throws IOException {
        String source = """
            import importlib.util
            spec = importlib.util.spec_from_file_location("module", "/path/to/module.py")
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);

        assertEquals(1, imports.size());
        PythonDynamicImportDetector.DynamicImport imp = imports.get(0);
        assertEquals("importlib.util.spec_from_file_location", imp.pattern());
        assertEquals(2, imp.lineNumber());
    }

    @Test
    void detectsMultipleDynamicImports(@TempDir Path tempDir) throws IOException {
        String source = """
            import importlib
            
            mod1 = importlib.import_module("module1")
            mod2 = __import__("module2")
            
            import importlib.util
            spec = importlib.util.spec_from_file_location("module3", "/path/to/module3.py")
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);

        assertEquals(3, imports.size());
        assertEquals("importlib.import_module", imports.get(0).pattern());
        assertEquals(3, imports.get(0).lineNumber());
        
        assertEquals("__import__", imports.get(1).pattern());
        assertEquals(4, imports.get(1).lineNumber());
        
        assertEquals("importlib.util.spec_from_file_location", imports.get(2).pattern());
        assertEquals(7, imports.get(2).lineNumber());
    }

    @Test
    void distinguishesFromStaticImports(@TempDir Path tempDir) throws IOException {
        String source = """
            import os
            import sys
            from typing import List
            
            # This is a dynamic import
            module = importlib.import_module("dynamic_module")
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);

        // Should only detect the dynamic import, not the static ones
        assertEquals(1, imports.size());
        assertEquals("importlib.import_module", imports.get(0).pattern());
        assertEquals(6, imports.get(0).lineNumber());
    }

    @Test
    void handlesEmptyFile(@TempDir Path tempDir) throws IOException {
        String source = "";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);

        assertTrue(imports.isEmpty());
    }

    @Test
    void handlesFileWithNoImports(@TempDir Path tempDir) throws IOException {
        String source = """
            def main():
                print("Hello, world!")
            
            if __name__ == "__main__":
                main()
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);

        assertTrue(imports.isEmpty());
    }

    @Test
    void handlesFileWithOnlyStaticImports(@TempDir Path tempDir) throws IOException {
        String source = """
            import os
            import sys
            from typing import List, Dict
            from pathlib import Path
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);

        assertTrue(imports.isEmpty());
    }

    @Test
    void detectsDynamicImportsInFunctionBody(@TempDir Path tempDir) throws IOException {
        String source = """
            def load_plugin(name):
                import importlib
                return importlib.import_module(name)
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);

        assertEquals(1, imports.size());
        assertEquals("importlib.import_module", imports.get(0).pattern());
        assertEquals(3, imports.get(0).lineNumber());
    }

    @Test
    void detectsDynamicImportsInClassMethod(@TempDir Path tempDir) throws IOException {
        String source = """
            class PluginLoader:
                def load(self, name):
                    return __import__(name)
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);

        assertEquals(1, imports.size());
        assertEquals("__import__", imports.get(0).pattern());
        assertEquals(3, imports.get(0).lineNumber());
    }

    @Test
    void handlesInvalidSyntaxGracefully(@TempDir Path tempDir) throws IOException {
        String source = """
            import importlib
            this is not valid python syntax
            module = importlib.import_module("test")
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        // Should not throw, but return empty list due to syntax error
        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);
        
        // Python AST parser will fail on syntax error, returning empty list
        assertTrue(imports.isEmpty());
    }

    @Test
    void detectsAllDynamicImportPatterns(@TempDir Path tempDir) throws IOException {
        String source = """
            import importlib
            import importlib.util
            
            # Pattern 1: importlib.import_module
            mod1 = importlib.import_module("package.module")
            
            # Pattern 2: __import__
            mod2 = __import__("os.path")
            
            # Pattern 3: importlib.util.spec_from_file_location
            spec = importlib.util.spec_from_file_location("mymodule", "/path/to/mymodule.py")
            
            # Static imports should not be detected
            from typing import List
            import sys
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);

        assertEquals(3, imports.size());
        
        assertEquals("importlib.import_module", imports.get(0).pattern());
        assertEquals(5, imports.get(0).lineNumber());
        
        assertEquals("__import__", imports.get(1).pattern());
        assertEquals(8, imports.get(1).lineNumber());
        
        assertEquals("importlib.util.spec_from_file_location", imports.get(2).pattern());
        assertEquals(11, imports.get(2).lineNumber());
    }

    @Test
    void detectsDynamicImportsWithVariableArguments(@TempDir Path tempDir) throws IOException {
        String source = """
            import importlib
            
            module_name = "my_module"
            mod = importlib.import_module(module_name)
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);

        // Should still detect the call even with variable arguments
        assertEquals(1, imports.size());
        assertEquals("importlib.import_module", imports.get(0).pattern());
        assertEquals(4, imports.get(0).lineNumber());
    }

    @Test
    void detectsNestedDynamicImports(@TempDir Path tempDir) throws IOException {
        String source = """
            def outer():
                def inner():
                    return __import__("nested")
                return inner()
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(file, source);

        assertEquals(1, imports.size());
        assertEquals("__import__", imports.get(0).pattern());
        assertEquals(3, imports.get(0).lineNumber());
    }
}
